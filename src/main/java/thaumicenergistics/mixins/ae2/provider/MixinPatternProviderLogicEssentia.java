package thaumicenergistics.mixins.ae2.provider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;

import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;

import thaumicenergistics.common.integration.appeng.ThECompositePushTarget;
import thaumicenergistics.common.integration.appeng.ThEEssentiaPushTarget;
import thaumicenergistics.init.ModItems;

/**
 * T-M2-002（源质交互分工制）：样板供应器 = <b>纯输出侧</b>——ME 网络下单时，
 * 经 {@code findAdapter} 挂点把源质输入样板推进相邻 TC 机器；无订单时零输出
 * （沿用 AE2 原生样板推送语义），且<b>不保留任何收入能力</b>
 * （原 O-A 拉取 / O-B 吸力 / addEssentia 接收路径已整体移除；物品/流体产物回收走 AE2 原生）。
 * <p>
 * <b>为什么挂 {@code findAdapter}</b>：AE2 19.2.17 的推送链路（{@code pushPattern} 与
 * {@code sendStacksOut} 重试）取相邻目标的<b>唯一出口</b>是
 * {@code PatternProviderLogic.findAdapter(Direction)}——在此 RETURN 处接管，即可让源质输入
 * 样板复用全部原生推送语义（adapterAcceptsAll 预检、pushInputsToExternalInventory、
 * addToSendList → hasWorkToDo → sendStacksOut 重试，<b>订单保持、绝不丢料</b>，无需自建）。
 * CPU 侧全程泛型 AEKey，源质输入样板无需改动。
 * <p>
 * <b>接管条件</b>（缺一不可）：源质访问卡在装（无卡 = 原版供应器，源质输入样板推不进 TC 机器）
 * && 相邻方块实体为 TC 源质端点（{@code IAspectContainer} 或 {@code IEssentiaTransport}）。
 * 原 target 为 null → 直接返回自建 {@link ThEEssentiaPushTarget}；非 null（罕见双能力机器）
 * → {@link ThECompositePushTarget} 包装（物品走原、源质走自建）。
 * <p>
 * <b>落点范式</b>：自建 target 复用导出总线范式（IAspectContainer 优先 + addEssentia 回退），
 * SIMULATE 为类型级近似、不验余量（P-A 裁决，余量由 sendList 原生重试兜底）——
 * 细节见 {@link ThEEssentiaPushTarget} 类注释。
 * <p>
 * <b>速率</b>：订单推送的限速由 AE2 原生 sendList 链路承担（每 tick {@code doWork} →
 * {@code sendStacksOut} 按其自身节奏重试），不再自建 operationsPerTick（原字段已删，避免死码）。
 * 能量按实际搬运量 double 记账（{@code moved × AE_PER_ESSENTIA}），落在
 * {@link ThEEssentiaPushTarget}（与导出总线口径一致）。
 * <p>
 * <b>与卡槽半边的关系</b>：升级清单读取经公开 {@link IUpgradeableObject#getUpgrades()}——
 * 槽位由 {@link MixinPatternProviderLogic}（AppFlux 缺席）或 AppFlux（在场）建的都通用，
 * 见 {@code thE$sharedUpgrades()}。
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class MixinPatternProviderLogicEssentia {

    /** 宿主（Part 或 BlockEntity），声明于本类自身（PatternProviderLogic.java:96）。 */
    @Final
    @Shadow
    private PatternProviderLogicHost host;

    /** 网格节点，声明于本类自身（PatternProviderLogic.java:97）。 */
    @Final
    @Shadow
    private IManagedGridNode mainNode;

    /** 供应器自身的操作源（PatternProviderLogic.java:98，MachineSource），供网络抽取记账用。 */
    @Final
    @Shadow
    private IActionSource actionSource;

    /**
     * 挂在 {@code findAdapter} RETURN：卡在装且相邻 BE 为 TC 源质端点时，把返回 target
     * 换成/包装成源质感知实现；否则一行不干预（无卡 = 原版供应器）。
     * <p>
     * 推送失败重试由 AE2 原生链路承载（未注入量 → addToSendList → sendStacksOut），
     * 本注入点无状态、每 tick 重入安全。
     */
    @Inject(method = "findAdapter", at = @At("RETURN"), cancellable = true)
    private void thE$essentiaPushTarget(Direction side, CallbackInfoReturnable<PatternProviderTarget> cir) {
        if (!thE$hasEssentiaCard()) {
            return; // 无卡 = 原版供应器：源质输入样板推不进 TC 机器
        }
        IGrid grid = this.mainNode.getGrid();
        if (grid == null) {
            return;
        }
        BlockEntity hostBe = this.host.getBlockEntity();
        if (hostBe == null) {
            return;
        }
        Level level = hostBe.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos adjPos = hostBe.getBlockPos().relative(side);
        if (!serverLevel.isLoaded(adjPos)) {
            return;
        }
        BlockEntity container = serverLevel.getBlockEntity(adjPos);
        if (!thE$isEssentiaEndpoint(container)) {
            return; // 非 TC 源质端点 → 原版 target 照常生效
        }
        var essentiaTarget = new ThEEssentiaPushTarget(
                grid, this.actionSource, container, side.getOpposite());
        var original = cir.getReturnValue();
        // 原 target 为 null → 直接接管；非 null（罕见双能力机器）→ 包装分流（物品走原、源质走自建）
        cir.setReturnValue(original == null
                ? essentiaTarget
                : new ThECompositePushTarget(original, essentiaTarget));
    }

    /** TC 源质端点判定：能按 IAspectContainer 收源质，或实现了 TC 源质运输接口（含 common 子接口）。 */
    @Unique
    private static boolean thE$isEssentiaEndpoint(BlockEntity be) {
        return be instanceof IAspectContainer || be instanceof IEssentiaTransport;
    }

    /** 卡门控：仅当源质访问卡已插入时接管推送。 */
    @Unique
    private boolean thE$hasEssentiaCard() {
        IUpgradeInventory upgrades = thE$sharedUpgrades();
        return upgrades != null && upgrades.isInstalled(ModItems.ESSENTIA_UPGRADE_CARD.get());
    }

    /**
     * 升级清单（公开接口读取，<b>不</b> @Shadow 卡槽半边的 {@code thE$upgrades}）——
     * 槽位由谁建都通：AppFlux 缺席时来自 {@link MixinPatternProviderLogic}，在场时来自 AppFlux 自身实现。
     * 用 {@code instanceof} 兜底（防两组同时缺席导致 CCE）。
     */
    @Unique
    private IUpgradeInventory thE$sharedUpgrades() {
        Object self = this;
        if (self instanceof IUpgradeableObject up) {
            return up.getUpgrades();
        }
        return null;
    }
}
