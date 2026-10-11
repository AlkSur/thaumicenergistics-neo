package thaumicenergistics.mixins.ae2.interfaces;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
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
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.util.ConfigInventory;

import thaumcraft.api.aspects.Aspect;

import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.IEssentiaInterfaceLogic;
import thaumicenergistics.common.integration.tc.EssentiaConversionHelper;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.init.ModItems;

/**
 * 给 AE2 <b>原版</b> ME 接口叠加「源质访问卡」相关的两段互不重叠的 additive 注入（T-M1-007-⑦c）。
 * <p>
 * 关键：接口的<b>部件形态</b>（{@code InterfacePart}）与<b>方块形态</b>（{@code InterfaceBlockEntity}）
 * 的搬运逻辑都委托给同一个 {@link InterfaceLogic}，故此处<b>一处注入即覆盖两形态</b>。
 * <p>
 * <ul>
 *   <li><b>卡门控</b>（路径 A）：无卡时抑制原版 plannedWork 机制对源质的搬运，
 *       并把源质键挡在 storage 槽写入之外——只动写入/计划路径，不碰取出现有源质；</li>
 *   <li><b>破坏救援</b>：{@code addDrops} HEAD 把 storage 槽内源质推回网络，
 *       因 AE2 会把槽内容转成掉落物而源质无物品形态会被损毁。此段<b>不做卡门控</b>。</li>
 * </ul>
 * 与参考项目 {@code InterfaceLogicMixin} 注入点相同（{@code updatePlan(int)} RETURN +
 * {@code isAllowedInStorageSlot} HEAD），但门控极性相反：参考项目「有卡拒收走自建行」，
 * 本模组「无卡拒收保原生」。
 * <p>
 * <b>T-M1-011 退役项</b>：原「源质过滤 NBT」（{@code ThEEssentiaFilterAspects}，经
 * {@code readFromNBT}/{@code writeToNBT} TAIL 注入）与鸭子接口 {@code EssentiaFilterHolder} 已随本任务删除
 * ——接口的源质配置改由<b>原生 config inventory</b> 承载（{@code config} = AE2 的补货清单，
 * 源质键驱动补货即 ⑦c 既定行为）。
 * <p>
 * <b>T-M2-001（TC 对接面）</b>：本类实现 {@link IEssentiaInterfaceLogic} 桥接面，把 ME 接口变成可被
 * TC 管网寻址、可被动推入/拉出的源质端点。对外接口本体落在<b>方块实体</b>
 * （{@link MixinInterfaceBlockEntity} implements {@code thaumcraft.common.essentia.EssentiaTransport}）。
 * 桥接面只复用本类<b>既有</b>的 {@code storage}/{@code networkStorage}/{@code actionSource}/{@code upgrades}
 * 影子成员，<b>零新增 @Shadow</b>；新增 {@code thE$essentiaBuffer} 缓冲与三个 tick 注入点
 * （{@code updateStorage} TAIL 主冲刷 / {@code hasWorkToDo} RETURN 防睡死 / {@code writeToNBT}·{@code readFromNBT}·
 * {@code clearContent} TAIL 持久化），与 ⑦c 注入点零重叠。
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
    }

    /** 把 storage 槽内全部源质尽力推回网络（网络已满时残留留在槽内，不做进一步处理）。 */
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

    /** 待注入网络的源质缓冲（网络装不下时暂存重试，绝不丢弃）。 */
    @Unique
    private AspectStack thE$essentiaBuffer = AspectStack.EMPTY;

    /**
     * 主冲刷：挂在 {@code updateStorage()}（private，每 tick 由 Ticker 调用）TAIL——原版 item/fluid
     * 计划搬运一行不改，仅追加源质缓冲入网；返回值只在 false→true 单方向提升，让 ticker 维持 URGENT。
     */
    @Inject(method = "updateStorage", at = @At("TAIL"), cancellable = true)
    private void thE$flushEssentiaOnTick(CallbackInfoReturnable<Boolean> cir) {
        boolean flushed = thE$flushEssentiaBuffer() > 0;
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
        if (!cir.getReturnValueZ() && !this.thE$essentiaBuffer.isEmpty()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void thE$saveEssentiaBuffer(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (!this.thE$essentiaBuffer.isEmpty()) {
            tag.putString("ThEEssentiaInterfaceBufferAspect", this.thE$essentiaBuffer.aspectId().toString());
            tag.putLong("ThEEssentiaInterfaceBufferAmount", this.thE$essentiaBuffer.amount());
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void thE$loadEssentiaBuffer(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        this.thE$essentiaBuffer = AspectStack.EMPTY;
        if (tag.contains("ThEEssentiaInterfaceBufferAspect") && tag.getLong("ThEEssentiaInterfaceBufferAmount") > 0) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("ThEEssentiaInterfaceBufferAspect"));
            if (id != null) {
                this.thE$essentiaBuffer = new AspectStack(id, tag.getLong("ThEEssentiaInterfaceBufferAmount"));
            }
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void thE$clearEssentiaBuffer(CallbackInfo ci) {
        this.thE$essentiaBuffer = AspectStack.EMPTY;
    }

    @Override
    public boolean thE$essentiaActive() {
        // 联网判据用 networkStorage（InterfaceLogic 自身即以它作「是否挂网」的判断，见 getInventory()）
        return thE$hasEssentiaCard() && this.networkStorage != null;
    }

    @Override
    public int thE$simulateAcceptEssentia(Aspect aspect, int amount) {
        if (!thE$essentiaActive() || aspect == null || amount <= 0) {
            return 0;
        }
        AEssentiaKey key = thE$essentiaKey(aspect);
        if (key == null) {
            return 0;
        }
        // 模拟带良性 MODULATE 副作用：异类占用时先冲缓冲（真实入网），本轮冲不掉则与 acceptEssentia 的「拒收」分支一致地返回 0
        if (!this.thE$essentiaBuffer.isEmpty() && !this.thE$essentiaBuffer.aspectId().equals(key.getId())) {
            thE$flushEssentiaBuffer();
            if (!this.thE$essentiaBuffer.isEmpty() && !this.thE$essentiaBuffer.aspectId().equals(key.getId())) {
                return 0;
            }
        }
        int want = Math.min(amount, thE$transportLimit());
        long room = this.networkStorage.insert(key, want, Actionable.SIMULATE, this.actionSource);
        return (int) Math.min(room, want);
    }

    @Override
    public int thE$acceptEssentia(Aspect aspect, int amount) {
        if (!thE$essentiaActive() || aspect == null || amount <= 0) {
            return 0;
        }
        AEssentiaKey key = thE$essentiaKey(aspect);
        if (key == null) {
            return 0;
        }
        // 缓冲被异类占用 → 先冲；仍占用则拒收（源质留在外部，绝不丢弃）
        if (!this.thE$essentiaBuffer.isEmpty() && !this.thE$essentiaBuffer.aspectId().equals(key.getId())) {
            thE$flushEssentiaBuffer();
            if (!this.thE$essentiaBuffer.isEmpty() && !this.thE$essentiaBuffer.aspectId().equals(key.getId())) {
                return 0;
            }
        }
        int want = Math.min(amount, thE$transportLimit());
        long room = this.networkStorage.insert(key, want, Actionable.SIMULATE, this.actionSource);
        if (room <= 0) {
            return 0;
        }
        int accepted = (int) Math.min(room, want);
        long existing = this.thE$essentiaBuffer.isEmpty() ? 0 : this.thE$essentiaBuffer.amount();
        this.thE$essentiaBuffer = new AspectStack(key.getId(), existing + accepted);
        thE$saveHost();
        // 尽力立即注入（残留留缓冲，交 tick 循环下轮冲）
        thE$flushEssentiaBuffer();
        return accepted;
    }

    @Override
    public Aspect thE$stockAspect() {
        if (!thE$essentiaActive()) {
            return null;
        }
        // TC EssentiaTransport 是单要素契约：遍历 9 个缓存槽，取首个非空源质槽
        for (int i = 0; i < this.storage.size(); i++) {
            if (this.storage.getKey(i) instanceof AEssentiaKey key) {
                return TCReflection.findAspectById(key.getId());
            }
        }
        return null;
    }

    @Override
    public int thE$stockAmount(Aspect aspect) {
        if (!thE$essentiaActive() || aspect == null) {
            return 0;
        }
        AEssentiaKey key = thE$essentiaKey(aspect);
        if (key == null) {
            return 0;
        }
        long sum = 0;
        for (int i = 0; i < this.storage.size(); i++) {
            if (this.storage.getKey(i) instanceof AEssentiaKey k && k.equals(key)) {
                sum += this.storage.getAmount(i);
            }
        }
        return (int) Math.min(sum, Integer.MAX_VALUE);
    }

    @Override
    public int thE$simulateTakeFromStock(Aspect aspect, int amount) {
        if (!thE$essentiaActive() || aspect == null || amount <= 0) {
            return 0;
        }
        AEssentiaKey key = thE$essentiaKey(aspect);
        if (key == null) {
            return 0;
        }
        long canTake = this.storage.extract(key, amount, Actionable.SIMULATE, this.actionSource);
        return (int) Math.min(canTake, amount);
    }

    @Override
    public int thE$takeFromStock(Aspect aspect, int amount) {
        if (!thE$essentiaActive() || aspect == null || amount <= 0) {
            return 0;
        }
        AEssentiaKey key = thE$essentiaKey(aspect);
        if (key == null) {
            return 0;
        }
        // 仅从缓存格（stock 槽）取出——禁止直取网络
        long taken = this.storage.extract(key, amount, Actionable.MODULATE, this.actionSource);
        return (int) taken;
    }

    /** 把缓冲里的源质注入网络（成功即落盘 + 按实际入网量 double 记账）。@return 本次注入量 */
    @Unique
    private long thE$flushEssentiaBuffer() {
        if (this.thE$essentiaBuffer.isEmpty() || this.networkStorage == null) {
            return 0;
        }
        AEssentiaKey key = AEssentiaKey.of(this.thE$essentiaBuffer.aspectId());
        if (key == null) {
            this.thE$essentiaBuffer = AspectStack.EMPTY;
            return 0;
        }
        long injected = this.networkStorage.insert(
                key, this.thE$essentiaBuffer.amount(), Actionable.MODULATE, this.actionSource);
        if (injected <= 0) {
            return 0;
        }
        long left = this.thE$essentiaBuffer.amount() - injected;
        this.thE$essentiaBuffer = left > 0
                ? new AspectStack(this.thE$essentiaBuffer.aspectId(), left)
                : AspectStack.EMPTY;
        thE$saveHost();
        thE$chargeEnergy(injected);
        return injected;
    }

    /** 按实际入网量 double 记账（{@code moved × AE_PER_ESSENTIA}，不继承 (int) 截断缺陷）。 */
    @Unique
    private void thE$chargeEnergy(long moved) {
        if (moved <= 0) {
            return;
        }
        IGrid grid = thE$grid();
        if (grid != null) {
            grid.getEnergyService().extractAEPower(
                    moved * EssentiaConversionHelper.AE_PER_ESSENTIA,
                    Actionable.MODULATE, PowerMultiplier.CONFIG);
        }
    }

    /** 通知宿主保存（缓冲为新增字段，需显式落盘）。经节点 owner 取得宿主，不新增 @Shadow。 */
    @Unique
    private void thE$saveHost() {
        IGridNode node = thE$actionableNode();
        Object owner = node != null ? node.getOwner() : null;
        if (owner instanceof InterfaceLogicHost host) {
            host.saveChanges();
        }
    }

    /** 本逻辑所在网格（未挂网 → null）。经公开 getActionableNode() 取得，避开父类陷阱。 */
    @Unique
    private IGrid thE$grid() {
        IGridNode node = thE$actionableNode();
        return node != null ? node.getGrid() : null;
    }

    @Unique
    private IGridNode thE$actionableNode() {
        return ((InterfaceLogic) (Object) this).getActionableNode();
    }

    /** 单次搬运上限 = 8 × 每 tick 操作次数（仅计 AE2 加速卡，与供应器同口径）。 */
    @Unique
    private int thE$transportLimit() {
        int installed = this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD);
        int ops = switch (installed) {
            default -> 1;
            case 1 -> 8;
            case 2 -> 32;
            case 3 -> 64;
            case 4 -> 96;
        };
        return 8 * Math.max(1, ops);
    }

    /** Aspect → AEssentiaKey（转换为空 → null）。 */
    @Unique
    private AEssentiaKey thE$essentiaKey(Aspect aspect) {
        if (aspect == null) {
            return null;
        }
        ResourceLocation id = TCReflection.getAspectId(aspect);
        return id == null ? null : AEssentiaKey.of(id);
    }
}
