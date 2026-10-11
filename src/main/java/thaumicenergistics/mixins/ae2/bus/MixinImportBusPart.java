package thaumicenergistics.mixins.ae2.bus;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.MachineSource;
import appeng.parts.AEBasePart;
import appeng.parts.automation.IOBusPart;
import appeng.parts.automation.ImportBusPart;
import appeng.parts.automation.UpgradeablePart;
import appeng.util.ConfigInventory;

import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.common.essentia.capability.EssentiaTransfer;
import thaumicenergistics.common.essentia.capability.EssentiaTransferBuffer;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.EssentiaConversionHelper;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.init.ModItems;

/**
 * 让 AE2 <b>原版输入总线</b>在插入「源质访问卡」后额外具备「相邻容器 → ME 网络」的源质搬运能力
 * （T-M1-007-⑦a）。
 * <p>
 * <b>additive</b>：只在 {@code ImportBusPart.doBusWork} 末尾（{@code @At("TAIL")}）追加源质搬运，
 * 原版物品/流体搬运逻辑一行不改。装卡后两种能力<b>并存</b>（同一个 tick 内先由原版逻辑搬物品，
 * 末尾再搬源质）；不装卡则完全不干预。
 * <p>
 * 挂载点说明：{@code IOBusPart.doBusWork} 是 {@code protected abstract}（无方法体，无法注入），
 * 其实现分别在 {@code ImportBusPart} / {@code ExportBusPart} 中，故需两处独立注入（本类负责导入侧）。
 * <p>
 * 行为沿用 T-M1 批次对齐的原 ThE 导入总线（T-M2-REMOVE 已删）：抽取顺序、速率（{@code 8 × getOperationsPerTick()}）、
 * 残留缓冲重试、能量按实际入网量实扣（{@code AE_PER_ESSENTIA}）全部一致。
 * <p>
 * 卡路径特性：读取宿主<b>原生 config 槽</b>里配置的源质键（T-M1-011，物品/流体/源质共存一格）——
 * <b>未配置源质 = 全搬</b>（抽到什么都行），<b>配置了源质 = 只搬选中的要素</b>。
 * <p>
 * <b>T-M1-010 根因修正（去 @Shadow）</b>：{@code IOBusPart.source}（:84）与
 * {@code IOBusPart.getOperationsPerTick()}（:194-202）均声明在<b>父类</b>，{@code ImportBusPart} 未覆写；
 * Mixin {@code @Shadow} 只解析<b>目标类自身</b>声明的成员、不沿父类查找 → 原实现整类被拒绝应用
 * （{@code InvalidMixinException: @Shadow method getOperationsPerTick ... was not located}）。
 * 改为经父类公开接口等价重建（见 {@code thE$source()} / {@code thE$operationsPerTick()}）。
 * <p>
 * <b>T-M1-011</b>：过滤来源由退役的鸭子接口 {@code EssentiaFilterHolder}（NBT）改为宿主原生 config；
 * config 经公开 getter {@code IOBusPart.getConfig()} 读取（字段声明在父类，禁 {@code @Shadow}）。
 */
@Mixin(ImportBusPart.class)
public abstract class MixinImportBusPart {

    /**
     * 原版总线的操作源。原版 {@code IOBusPart} 用 {@code new MachineSource(this)} 构造（IOBusPart.java:95），
     * 该字段声明在父类、无法 {@code @Shadow}，故在此等价重建并缓存。
     */
    @Unique
    private IActionSource thE$source;

    /** 能力缺失告警去重标记（一次性上报，避免刷屏）。 */
    @Unique
    private boolean thE$warnedMissingCapability;

    @Unique
    private IActionSource thE$source() {
        if (thE$source == null) {
            thE$source = new MachineSource((IActionHost) (Object) this);
        }
        return thE$source;
    }

    /**
     * 单次搬运量 = 基础速率 8 × 每 tick 操作次数（受加速卡影响），与本模组三总线一致。
     * <p>
     * 公式照抄 {@code IOBusPart.getOperationsPerTick()}（IOBusPart.java:194-202）；原方法声明在父类、
     * 无法 {@code @Shadow}，故经公开接口 {@code getInstalledUpgrades} 自算。仅计 AE2 加速卡，源质卡天然不计入。
     */
    @Unique
    private int thE$operationsPerTick() {
        return switch (((IUpgradeableObject) (Object) this).getInstalledUpgrades(AEItems.SPEED_CARD)) {
            default -> 1;
            case 1 -> 8;
            case 2 -> 32;
            case 3 -> 64;
            case 4 -> 96;
        };
    }

    @Inject(method = "doBusWork", at = @At("TAIL"), cancellable = true)
    private void thE$essentiaImport(IGrid grid, CallbackInfoReturnable<Boolean> cir) {
        if (!thE$hasEssentiaCard()) {
            return; // 未插源质卡 → 不干预原版导入总线
        }

        if (!((Object) this instanceof EssentiaTransferBuffer buffer)) {
            thE$warnMissingCapability("EssentiaTransferBuffer");
            return;
        }
        boolean worked = thE$flushBuffer(grid, buffer);

        // 定位相邻方块（容器）
        AEBasePart self = (AEBasePart) (Object) this;
        Direction side = self.getSide();
        if (side == null) {
            thE$reportWork(worked, cir);
            return;
        }
        Level levelRaw = self.getLevel();
        if (!(levelRaw instanceof ServerLevel level)) {
            thE$reportWork(worked, cir);
            return;
        }
        BlockPos adjacentPos = self.getBlockEntity().getBlockPos().relative(side);
        if (!level.isLoaded(adjacentPos)) {
            thE$reportWork(worked, cir);
            return;
        }
        BlockEntity adjacentBE = level.getBlockEntity(adjacentPos);
        if (adjacentBE == null) {
            thE$reportWork(worked, cir);
            return;
        }

        // 缓冲仍有残留 = 网络装不下 → 本次不再从容器抽取（源质留在原容器里，绝不"吸走却存不下"）
        if (!buffer.thE$getTransferBuffer().isEmpty()) {
            thE$reportWork(worked, cir);
            return;
        }

        int rate = 8 * Math.max(1, thE$operationsPerTick());
        if (rate <= 0) {
            thE$reportWork(worked, cir);
            return;
        }

        // 卡路径过滤（T-M1-011）：宿主 config 配置了源质键 → 只搬选中的要素（配置外要素本轮跳过，留在容器里）
        List<AEssentiaKey> configured = thE$configuredEssentia();
        if (!configured.isEmpty()) {
            AspectStack offered = TCReflection.peekEssentia(adjacentBE, rate);
            if (offered == null || offered.isEmpty()) {
                thE$reportWork(worked, cir);
                return;
            }
            AEssentiaKey offeredKey = AEssentiaKey.of(offered.aspectId());
            if (offeredKey == null || !configured.contains(offeredKey)) {
                thE$reportWork(worked, cir);
                return;
            }
        }

        // 容器 → 网络（能力模块：窥视 → 模拟测容量 → 定向抽取 → 实插）
        EssentiaTransfer.Pull pull = EssentiaTransfer.pull(adjacentBE, grid, thE$source(), rate);

        if (!pull.leftover().isEmpty()) {
            // 模拟与实插不一致 → 剩余留在缓冲里重试，绝不归还 / 丢弃
            buffer.thE$setTransferBuffer(pull.leftover());
        }
        if (pull.moved() > 0 || !pull.leftover().isEmpty()) {
            // 能量按实际入网量结算（与三总线一致：抽到但一分为入网时也为 0 消耗）
            grid.getEnergyService().extractAEPower(
                    pull.moved() * EssentiaConversionHelper.AE_PER_ESSENTIA,
                    Actionable.MODULATE, PowerMultiplier.CONFIG);
        }

        thE$reportWork(worked || pull.moved() > 0, cir);
    }

    /**
     * 从宿主<b>原生 config</b>（物品/流体/源质共存一格）读出已配置的源质键。
     * <p>
     * 经公开 getter {@code IOBusPart.getConfig()} 读取——{@code config} 字段声明在父类 {@code IOBusPart}
     * （:79），目标类 {@code ImportBusPart} 未声明，故禁 {@code @Shadow}（Mixin 只解析目标类自身声明的成员）。
     */
    @Unique
    private List<AEssentiaKey> thE$configuredEssentia() {
        ConfigInventory config = ((IOBusPart) (Object) this).getConfig();
        List<AEssentiaKey> configured = new ArrayList<>();
        for (int i = 0; i < config.size(); i++) {
            if (config.getKey(i) instanceof AEssentiaKey key && !configured.contains(key)) {
                configured.add(key);
            }
        }
        return configured;
    }

    /**
     * 把缓冲里的源质注入网络（沿用原 ThE 导入总线 flushBuffer 语义，该总线 T-M2-REMOVE 已删）。
     *
     * @return 本次是否注入了任意数量
     */
    @Unique
    private boolean thE$flushBuffer(IGrid grid, EssentiaTransferBuffer buffer) {
        AspectStack pending = buffer.thE$getTransferBuffer();
        if (pending.isEmpty()) {
            return false;
        }
        AEssentiaKey key = AEssentiaKey.of(pending.aspectId());
        if (key == null) {
            buffer.thE$setTransferBuffer(AspectStack.EMPTY);
            return false;
        }
        long injected = grid.getStorageService().getInventory().insert(
                key, pending.amount(), Actionable.MODULATE, thE$source());
        if (injected <= 0) {
            return false;
        }
        long left = pending.amount() - injected;
        buffer.thE$setTransferBuffer(left > 0 ? new AspectStack(pending.aspectId(), left) : AspectStack.EMPTY);
        grid.getEnergyService().extractAEPower(
                injected * EssentiaConversionHelper.AE_PER_ESSENTIA,
                Actionable.MODULATE, PowerMultiplier.CONFIG);
        return true;
    }

    /**
     * 把「本次搬了源质」回传给原版 {@code doBusWork} 的返回值。
     * <p>
     * AE2 的 {@code IOBusPart.tickingRequest}（{@code final}）按该返回值决定
     * {@code TickRateModulation.FASTER / SLOWER}：返回 false 会让总线逐步退避到最慢 tick。
     * 本模组三总线在搬了源质时返回 true，为使卡路径与之等价（§八 回归口径），
     * 仅当原版未报告工作（false）且本次搬了源质时把它提升为 true——只在 false→true 一个方向调整，
     * 原版物品搬运的返回值不受影响。
     */
    @Unique
    private void thE$reportWork(boolean worked, CallbackInfoReturnable<Boolean> cir) {
        if (worked && !Boolean.TRUE.equals(cir.getReturnValue())) {
            cir.setReturnValue(true);
        }
    }

    /** 卡门控：仅当源质访问卡已插入时生效。 */
    @Unique
    private boolean thE$hasEssentiaCard() {
        return ((UpgradeablePart) (Object) this).getUpgrades().isInstalled(ModItems.ESSENTIA_UPGRADE_CARD.get());
    }

    /** 能力缺失告警（一次性）：{@code MixinIOBusPart} 未应用时鸭子的源质接口不存在——禁止静默跳过（T-M1-010）。 */
    @Unique
    private void thE$warnMissingCapability(String capability) {
        if (!thE$warnedMissingCapability) {
            thE$warnedMissingCapability = true;
            ThaumicEnergistics.LOG.warn(
                    "ThaumicEnergistics: 原版总线 {} 未暴露 {} 能力（MixinIOBusPart 可能未应用），源质搬运本轮跳过。",
                    ((Object) this).getClass().getName(), capability);
        }
    }
}
