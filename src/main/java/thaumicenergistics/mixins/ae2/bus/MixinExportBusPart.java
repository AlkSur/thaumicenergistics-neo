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

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.MachineSource;
import appeng.parts.AEBasePart;
import appeng.parts.automation.ExportBusPart;
import appeng.parts.automation.IOBusPart;
import appeng.parts.automation.UpgradeablePart;
import appeng.util.ConfigInventory;

import thaumicenergistics.common.essentia.capability.EssentiaTransfer;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.EssentiaConversionHelper;
import thaumicenergistics.init.ModItems;

/**
 * 让 AE2 <b>原版输出总线</b>在插入「源质访问卡」后额外具备「ME 网络 → 相邻容器」的源质搬运能力
 * （T-M1-007-⑦a）。
 * <p>
 * <b>additive</b>：只在 {@code ExportBusPart.doBusWork} 末尾（{@code @At("TAIL")}）追加源质搬运，
 * 原版物品/流体导出逻辑一行不改。装卡后两种能力<b>并存</b>；不装卡则完全不干预。
 * <p>
 * <b>要导出哪些要素（卡路径独有）</b>：T-M1-011 起，源质过滤改由宿主<b>原生 config 槽</b>承载
 * （物品/流体/源质共存一格；由 {@code MixinIOBusPart} 的 {@code @ModifyArg} 使 config 接受源质键）：
 * <ul>
 *   <li><b>配置了源质 = 只搬选中的要素</b>——遍历配置的源质键轮询（round-robin），逐个尝试，搬成功即停；</li>
 *   <li><b>未配置源质 = 不搬</b>（R-2 口径）——必须显式配置源质才导出；对齐 AE2 原版输出总线
 *       「空配置不导出」语义，避免插卡未配置就把网络内全部源质倒向相邻容器。</li>
 * </ul>
 * 轮询游标跨 tick 保留（不持久化），避免每次只搬列表首项造成饥饿。
 * <p>
 * 速率（{@code 8 × getOperationsPerTick()}）与能量口径沿用 T-M1 批次对齐的原 ThE 导出总线（T-M2-REMOVE 已删）：
 * 导出侧<b>不做</b>能量实扣，只把折算值记进 {@link #thE$storedEnergy}。
 * <p>
 * 已知既有瑕疵被照搬：原实现记录的是 {@code injected * (int) AE_PER_ESSENTIA}，
 * {@code injected * (int) AE_PER_ESSENTIA}，而 {@code AE_PER_ESSENTIA = 0.3}，{@code (int) 0.3 == 0}，
 * 故该字段恒为 0。本类照搬该表达式以保持行为等价，并原样保留该既有瑕疵（不在本任务内修正）。
 * <p>
 * <b>T-M1-010 根因修正（去 @Shadow）</b>：{@code IOBusPart.source}（:84）与
 * {@code IOBusPart.getOperationsPerTick()}（:194-202）均声明在<b>父类</b>，{@code ExportBusPart} 未覆写；
 * Mixin {@code @Shadow} 只解析<b>目标类自身</b>声明的成员、不沿父类查找 → 原实现整类被拒绝应用
 * （{@code InvalidMixinException: @Shadow method getOperationsPerTick ... was not located}）。
 * 改为经父类公开接口等价重建（见 {@code thE$source()} / {@code thE$operationsPerTick()}）。
 * <p>
 * <b>T-M1-011</b>：过滤来源由退役的鸭子接口 {@code EssentiaFilterHolder}（NBT）改为宿主原生 config；
 * config 经公开 getter {@code IOBusPart.getConfig()} 读取（字段声明在父类，禁 {@code @Shadow}）。
 */
@Mixin(ExportBusPart.class)
public abstract class MixinExportBusPart {

    /**
     * 原版总线的操作源。原版 {@code IOBusPart} 用 {@code new MachineSource(this)} 构造（IOBusPart.java:95），
     * 该字段声明在父类、无法 {@code @Shadow}，故在此等价重建并缓存。
     */
    @Unique
    private IActionSource thE$source;

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

    /** 轮询游标（列表内上次成功项的下一位）。 */
    @Unique
    private int thE$roundRobinIndex = 0;

    /** 已折算的能量记账（照搬三总线：只记不扣）。无 GUI 展示位，不持久化。 */
    @Unique
    private long thE$storedEnergy = 0;

    @Inject(method = "doBusWork", at = @At("TAIL"), cancellable = true)
    private void thE$essentiaExport(IGrid grid, CallbackInfoReturnable<Boolean> cir) {
        if (!thE$hasEssentiaCard()) {
            return; // 未插源质卡 → 不干预原版输出总线
        }

        AEBasePart self = (AEBasePart) (Object) this;
        Direction side = self.getSide();
        if (side == null) {
            return;
        }
        Level levelRaw = self.getLevel();
        if (!(levelRaw instanceof ServerLevel level)) {
            return;
        }
        BlockPos adjacentPos = self.getBlockEntity().getBlockPos().relative(side);
        if (!level.isLoaded(adjacentPos)) {
            return;
        }
        BlockEntity adjacentBE = level.getBlockEntity(adjacentPos);
        if (adjacentBE == null) {
            return;
        }

        int rate = 8 * Math.max(1, thE$operationsPerTick());
        if (rate <= 0) {
            return;
        }

        List<AEssentiaKey> candidates = thE$configuredEssentia();
        if (candidates.isEmpty()) {
            return;
        }

        // 轮询：从游标处开始，逐个尝试，搬成功即停（受单次速率限制）
        int count = candidates.size();
        for (int i = 0; i < count; i++) {
            int index = (this.thE$roundRobinIndex + i) % count;
            AEssentiaKey key = candidates.get(index);
            long injected = EssentiaTransfer.push(
                    grid, thE$source(), key, rate, adjacentBE, side.getOpposite());
            if (injected > 0) {
                this.thE$roundRobinIndex = (index + 1) % count;
                this.thE$storedEnergy = injected * (int) EssentiaConversionHelper.AE_PER_ESSENTIA;
                if (!Boolean.TRUE.equals(cir.getReturnValue())) {
                    cir.setReturnValue(true); // 见 MixinImportBusPart#thE$reportWork 的同类说明
                }
                return;
            }
        }
    }

    /**
     * 收集本轮可尝试的源质要素（T-M1-011：来自宿主原生 config）。
     * <p>
     * <b>未配置源质 → 空集合（不搬）</b>（R-2 口径：必须显式配置才导出）；
     * 配置了源质 → 宿主 config 内的源质键（只搬选中）。
     * <p>
     * config 经公开 getter {@code IOBusPart.getConfig()} 读取——{@code config} 字段声明在父类
     * {@code IOBusPart}（:79），目标类 {@code ExportBusPart} 未声明，故禁 {@code @Shadow}。
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

    /** 卡门控：仅当源质访问卡已插入时生效。 */
    @Unique
    private boolean thE$hasEssentiaCard() {
        return ((UpgradeablePart) (Object) this).getUpgrades().isInstalled(ModItems.ESSENTIA_UPGRADE_CARD.get());
    }
}
