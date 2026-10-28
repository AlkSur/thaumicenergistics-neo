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
import thaumicenergistics.init.ModItems;

/**
 * 原版导出总线插入源质访问卡后，追加显式配置要素的轮询输出。
 * 模拟不扣料；实际转移由统一服务结算，退回失败的余量持久化并优先重试。
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

        if ((Object) this instanceof thaumicenergistics.common.essentia.capability.EssentiaTransferBuffer buffer
                && !buffer.thE$getTransferBuffer().isEmpty()) {
            var pending = buffer.thE$getTransferBuffer();
            long delivered = thaumicenergistics.common.essentia.capability.EssentiaEnergy.transfer(
                    grid, pending.amount(), allowed -> EssentiaTransfer.insertOwned(
                            AEssentiaKey.of(pending.aspectId()), allowed, adjacentBE, side.getOpposite()));
            buffer.thE$setTransferBuffer(delivered == pending.amount()
                    ? thaumicenergistics.api.storage.AspectStack.EMPTY
                    : new thaumicenergistics.api.storage.AspectStack(pending.aspectId(), pending.amount() - delivered));
            if (delivered > 0) cir.setReturnValue(true);
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
            var transfer = EssentiaTransfer.push(
                    grid, thE$source(), key, rate, adjacentBE, side.getOpposite());
            if (!transfer.leftover().isEmpty()
                    && (Object) this instanceof thaumicenergistics.common.essentia.capability.EssentiaTransferBuffer buffer) {
                buffer.thE$setTransferBuffer(transfer.leftover());
            }
            long injected = transfer.moved();
            if (injected > 0) {
                this.thE$roundRobinIndex = (index + 1) % count;
                if (!Boolean.TRUE.equals(cir.getReturnValue())) {
                    cir.setReturnValue(true); // 见 MixinImportBusPart#thE$reportWork 的同类说明
                }
                return;
            }
            if (transfer.leftover().isEmpty()
                    && thaumicenergistics.common.essentia.capability.ExportBusEssentiaTransport.reserveForConsumer(
                            (ExportBusPart) (Object) this, key, rate, adjacentBE, side.getOpposite(), grid, thE$source())) {
                this.thE$roundRobinIndex = (index + 1) % count;
                cir.setReturnValue(true);
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
