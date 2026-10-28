package thaumicenergistics.mixins.ae2.interfaces;

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

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.helpers.InterfaceLogic;
import appeng.util.ConfigInventory;

import thaumcraft.api.aspects.Aspect;

import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.IEssentiaInterfaceLogic;
import thaumicenergistics.init.ModItems;

/**
 * 原版 ME 接口的源质卡门控与生命周期接入，覆盖方块及部件共用的 InterfaceLogic。
 * 无卡时阻止新增源质存储和自动补货；已有资源仍可取回。
 * 转移、模拟和缓冲持久化委托给 EssentiaInterfaceState；拆除余量转成可恢复掉落。
 */
@Mixin(InterfaceLogic.class)
public abstract class MixinInterfaceLogic implements IEssentiaInterfaceLogic {

    /** 升级卡清单（@see InterfaceLogic.java:81，private final IUpgradeInventory upgrades）。 */
    @Shadow
    @Final
    private IUpgradeInventory upgrades;

    /** 计划工作表（@see InterfaceLogic.java:87，正/负分别表示需从网络补/向网络送）。 */
    @Shadow
    @Final
    private GenericStack[] plannedWork;

    /** 接口本体存储槽（@see InterfaceLogic.java:97）。 */
    @Shadow
    @Final
    private ConfigInventory storage;

    /** 网络存储视图（@see InterfaceLogic.java:73-74，gridChanged() 时赋值，可空）。 */
    @Shadow
    private MEStorage networkStorage;

    /** 接口作为机器自身的操作源（@see InterfaceLogic.java:78，MachineSource）。 */
    @Shadow
    @Final
    protected IActionSource actionSource;

    // ------------------------------------------------------------------
    // ⑦c（路径 A）：卡门控——无卡时只抑制「写入/计划」路径，不动取出
    // ------------------------------------------------------------------

    /**
     * 无卡时丢弃源质计划：原版 {@code updatePlan(int)} 会依 config/storage 差异把源质计划写进
     * {@code plannedWork}，从而被 tick 循环搬进/搬出网络。无卡时置 null 即抑制这条自动搬运。
     * 不主动把已滞留在 storage 槽的源质推回网络（拔卡后留待手动取出或重插卡恢复）。
     */
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

    /**
     * 无卡时拒绝源质键写入 storage 槽。
     * <p>
     * {@code isAllowedInStorageSlot} 是 storage 槽的写入过滤器：经 {@code GenericStackInv.filter}
     * 只在 {@code insert(int,…)}（GenericStackInv.java:156）与菜单放置校验
     * （{@code AppEngSlot.mayPlace}，AppEngSlot.java:94-99）中被消费，<b>不约束取出</b>
     * （取出走 {@code GenericStackInv.extract}:181-213 / {@code InternalInventory.extractItem}:366+，
     * 经 {@code setItemDirect} 绕过过滤器）。
     */
    @Inject(method = "isAllowedInStorageSlot(ILappeng/api/stacks/AEKey;)Z", at = @At("HEAD"), cancellable = true)
    private void thE$refuseEssentiaInsert(int slot, AEKey what, CallbackInfoReturnable<Boolean> cir) {
        if (what instanceof AEssentiaKey && !thE$hasEssentiaCard()) {
            cir.setReturnValue(false);
        }
    }

    // ------------------------------------------------------------------
    // ⑦c：破坏救援——不卡门控，插不插卡一律执行
    // ------------------------------------------------------------------

    /**
     * AE2 的 {@code addDrops}（InterfaceLogic.java:537-552）会把 storage 槽内每个键转成掉落物，
     * 而源质键没有物品形态，会在此被直接损毁。故在转掉落物之前把槽内源质推回网络。
     */
    @Inject(method = "addDrops(Ljava/util/List;)V", at = @At("HEAD"))
    private void thE$rescueEssentiaOnDrop(List<ItemStack> drops, CallbackInfo ci) {
        thE$rescueEssentiaToNetwork();
        thE$state().addBufferedDrops(drops);
    }

    /** 优先退回网络；槽中残留由 AEssentiaKey.addDrops 转成可回收的包装物品。 */
    @Unique
    private void thE$rescueEssentiaToNetwork() {
        if (this.networkStorage == null) {
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

    // ------------------------------------------------------------------
    // T-M2-001：TC 对接面的桥接实现（缓冲 + tick 冲刷 + 持久化）
    // ------------------------------------------------------------------

    @Unique
    private thaumicenergistics.common.integration.appeng.EssentiaInterfaceState thE$state;

    @Unique
    private thaumicenergistics.common.integration.appeng.EssentiaInterfaceState thE$state() {
        if (thE$state == null) {
            thE$state = new thaumicenergistics.common.integration.appeng.EssentiaInterfaceState(
                    () -> networkStorage, () -> storage, () -> upgrades, () -> actionSource,
                    () -> ((InterfaceLogic) (Object) this).getActionableNode());
        }
        return thE$state;
    }

    /**
     * 主冲刷：挂在 {@code updateStorage()}（private，每 tick 由 Ticker 调用）TAIL——原版 item/fluid
     * 计划搬运一行不改，仅追加源质缓冲入网；返回值只在 false→true 单方向提升，让 ticker 维持 URGENT。
     */
    @Inject(method = "updateStorage", at = @At("TAIL"), cancellable = true)
    private void thE$flushEssentiaOnTick(CallbackInfoReturnable<Boolean> cir) {
        boolean flushed = thE$state().thE$flushEssentiaBuffer() > 0;
        if (flushed && !Boolean.TRUE.equals(cir.getReturnValue())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 防睡死滞留：原版 {@code Ticker.getTickingRequest} 用 {@code !hasWorkToDo()} 决定 sleeping——
     * 缓冲非空（网络暂满，待重试）却不报告工作会让装置睡死、缓冲永不冲刷，故提升为 true。
     */
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
