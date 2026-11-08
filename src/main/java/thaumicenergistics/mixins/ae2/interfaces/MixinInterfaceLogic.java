package thaumicenergistics.mixins.ae2.interfaces;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.InterfaceLogic;
import appeng.util.ConfigInventory;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.EssentiaInterfaceState;
import thaumicenergistics.common.integration.appeng.IEssentiaInterfaceLogic;
import thaumicenergistics.common.integration.appeng.InterfaceEssentiaInventory;
import thaumicenergistics.init.ModItems;

/**
 * 为 ME 接口方块与部件添加源质卡门控、容量策略和持久化传输缓冲。
 * 无卡时禁止新增源质和自动补货，已有资源仍可取回。
 */
@Mixin(InterfaceLogic.class)
public abstract class MixinInterfaceLogic implements IEssentiaInterfaceLogic {

    /** 升级卡清单。 */
    @Shadow
    @Final
    private IUpgradeInventory upgrades;

    /** 计划工作表。 */
    @Shadow
    @Final
    private GenericStack[] plannedWork;

    @Shadow
    @Final
    private ConfigInventory config;

    /** 接口本体存储槽；附属可能在原版构造完成后替换整个库存。 */
    @Shadow
    @Final
    private ConfigInventory storage;

    /** 网络存储视图 时赋值，可空）。 */
    @Shadow
    private MEStorage networkStorage;

    /** 接口作为机器自身的操作源。 */
    @Shadow
    @Final
    protected IActionSource actionSource;

    @Unique
    private void thE$ensureBoundedInventories() {
        // Mark in place without reading slots: addons may install live network proxies.
        if (config != null) ((InterfaceEssentiaInventory) config).thE$markInterfaceInventory();
        if (storage != null) ((InterfaceEssentiaInventory) storage).thE$markInterfaceInventory();
    }

    @Inject(method = {"getConfig", "getStorage"}, at = @At("HEAD"))
    private void thE$boundCurrentInventories(CallbackInfoReturnable<ConfigInventory> cir) {
        thE$ensureBoundedInventories();
    }

    @Inject(method = "readFromNBT", at = @At("HEAD"))
    private void thE$boundBeforeLoad(CallbackInfo ci) {
        thE$ensureBoundedInventories();
    }

    @Inject(method = "updatePlan(I)V", at = @At("HEAD"))
    private void thE$normalizeOldEssentiaRequest(int slot, CallbackInfo ci) {
        thE$ensureBoundedInventories();
        var request = config.getStack(slot);
        if (request != null && request.what() instanceof AEssentiaKey
                && request.amount() > InterfaceEssentiaInventory.MAX_ESSENTIA_PER_SLOT) {
            // Native scheduling returns actual excess stock; never truncate it here.
            config.setStack(slot, new GenericStack(request.what(), InterfaceEssentiaInventory.MAX_ESSENTIA_PER_SLOT));
        }
    }

    // ⑦c（路径 A）：卡门控——无卡时只抑制「写入/计划」路径，不动取出

    /** 无卡时取消源质自动补货计划，已有库存保留，等待取出或重新插卡。 */
    @Inject(method = "updatePlan(I)V", at = @At("RETURN"))
    private void thE$gateEssentiaPlan(int slot, CallbackInfo ci) {
        if (thE$hasEssentiaCard()) {
            return;
        }
        GenericStack planned = this.plannedWork[slot];
        if (planned != null && planned.what() instanceof AEssentiaKey) {
            this.plannedWork[slot] = null;
        }
    }

    /** 无卡时拒绝源质写入；过滤器不限制已有资源的取出。 */
    @Inject(method = "isAllowedInStorageSlot(ILappeng/api/stacks/AEKey;)Z", at = @At("HEAD"), cancellable = true)
    private void thE$refuseEssentiaInsert(int slot, AEKey what, CallbackInfoReturnable<Boolean> cir) {
        if (what instanceof AEssentiaKey && !thE$hasEssentiaCard()) {
            cir.setReturnValue(false);
        }
    }

    // ⑦c：破坏救援——不卡门控，插不插卡一律执行

    /** 拆除前尝试将本地源质退回网络，未回收缓冲保留为可恢复掉落。 */
    @Inject(method = "addDrops(Ljava/util/List;)V", at = @At("HEAD"))
    private void thE$rescueEssentiaOnDrop(List<ItemStack> drops, CallbackInfo ci) {
        thE$rescueEssentiaToNetwork();
        thE$state().addBufferedDrops(drops);
    }

    /** 优先退回网络；槽中残留由 AEssentiaKey.addDrops 转成可回收的包装物品。 */
    @Unique
    private void thE$rescueEssentiaToNetwork() {
        // Only the concrete AE2 inventory is known to own these local resources.
        // Addon subclasses may project the network; never refund a virtual view.
        if (this.networkStorage == null || this.storage.getClass() != ConfigInventory.class) {
            return;
        }
        for (int i = 0; i < this.storage.size(); i++) {
            GenericStack stored = this.storage.getStack(i);
            if (stored == null || !(stored.what() instanceof AEssentiaKey)) {
                continue;
            }
            long inserted = this.networkStorage.insert(
                    stored.what(), stored.amount(), Actionable.MODULATE, this.actionSource);
            if (inserted > 0) {
                this.storage.extract(i, stored.what(), inserted, Actionable.MODULATE);
            }
        }
    }

    /** 卡门控：实时读取升级清单，仅当源质访问卡已插入时放行原生源质搬运。 */
    @Unique
    private boolean thE$hasEssentiaCard() {
        return this.upgrades.isInstalled(ModItems.ESSENTIA_UPGRADE_CARD.get());
    }

    // TC 对接面的桥接实现（缓冲 + tick 冲刷 + 持久化）

    @Unique
    private EssentiaInterfaceState thE$state;

    @Unique
    private EssentiaInterfaceState thE$state() {
        if (thE$state == null) {
            thE$state = new EssentiaInterfaceState(
                    () -> networkStorage, () -> storage, () -> upgrades, () -> actionSource,
                    () -> ((InterfaceLogic) (Object) this).getActionableNode());
        }
        return thE$state;
    }

    /** 原生库存更新后冲刷源质缓冲；发生传输时保持 ticker 活跃。 */
    @Inject(method = "updateStorage", at = @At("TAIL"), cancellable = true)
    private void thE$flushEssentiaOnTick(CallbackInfoReturnable<Boolean> cir) {
        boolean flushed = thE$state().thE$flushEssentiaBuffer() > 0;
        if (flushed && !Boolean.TRUE.equals(cir.getReturnValue())) {
            cir.setReturnValue(true);
        }
    }

    /** 缓冲非空时保持唤醒，网络恢复容量后继续重试。 */
    @Inject(method = "hasWorkToDo", at = @At("RETURN"), cancellable = true)
    private void thE$keepAwakeForEssentia(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && thE$state().hasBuffered()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void thE$saveEssentiaBuffer(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        thE$state().save(tag);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void thE$loadEssentiaBuffer(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        thE$state().load(tag);
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void thE$clearEssentiaBuffer(CallbackInfo ci) {
        thE$state().clear();
    }

    @Override
    public boolean thE$essentiaActive() { return thE$state().thE$essentiaActive(); }

    @Override
    public int thE$simulateAcceptEssentia(Aspect aspect, int amount) { return thE$state().thE$simulateAcceptEssentia(aspect, amount); }

    @Override
    public int thE$acceptEssentia(Aspect aspect, int amount) { return thE$state().thE$acceptEssentia(aspect, amount); }

    @Override
    public Aspect thE$stockAspect() { return thE$state().thE$stockAspect(); }

    @Override
    public int thE$stockAmount(Aspect aspect) { return thE$state().thE$stockAmount(aspect); }

    @Override
    public int thE$simulateTakeFromStock(Aspect aspect, int amount) { return thE$state().thE$simulateTakeFromStock(aspect, amount); }

    @Override
    public int thE$takeFromStock(Aspect aspect, int amount) { return thE$state().thE$takeFromStock(aspect, amount); }
}
