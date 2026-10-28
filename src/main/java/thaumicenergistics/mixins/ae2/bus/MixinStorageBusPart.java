package thaumicenergistics.mixins.ae2.bus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.AECapabilities;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.helpers.IPriorityHost;
import appeng.core.definitions.AEParts;
import appeng.parts.AEBasePart;
import appeng.parts.automation.UpgradeablePart;
import appeng.parts.storagebus.StorageBusPart;

import thaumicenergistics.common.essentia.capability.EssentiaContainerStorage;
import thaumicenergistics.init.ModItems;

/**
 * 插卡后追加相邻源质容器视图，经过总线访问模式和分区过滤。
 * 对已经提供 ME_STORAGE 的目标不重复挂载；卡片变化后请求重新挂载。
 */
@Mixin(StorageBusPart.class)
public abstract class MixinStorageBusPart {

    @Unique
    private MEStorage thE$containerStorage;

    // ===== T-M1-007-⑦b：插卡后追加源质容器挂载 =====

    @Inject(method = "mountInventories", at = @At("TAIL"))
    private void thE$mountEssentiaContainer(IStorageMounts mounts, CallbackInfo ci) {
        AEBasePart self = (AEBasePart) (Object) this;
        if (!thE$hasEssentiaCard() || !self.getMainNode().isOnline()) {
            return;
        }

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

        // 避让：相邻方块已自带 ME_STORAGE（原版存储总线会直接挂它）→ 不重复挂载
        MEStorage external = level.getCapability(AECapabilities.ME_STORAGE, adjacentPos, side.getOpposite());
        if (external != null) {
            return;
        }

        mounts.mount(thE$getContainerStorage(), ((IPriorityHost) (Object) this).getPriority());
    }

    /** 卡插拔后重新挂载，使源质存储的增删即时生效（对齐 AE2 自身的 remountStorage 机制）。 */
    @Inject(method = "upgradesChanged", at = @At("TAIL"))
    private void thE$onUpgradesChanged(CallbackInfo ci) {
        IStorageProvider.requestUpdate(((AEBasePart) (Object) this).getMainNode());
    }

    @Unique
    private MEStorage thE$getContainerStorage() {
        if (this.thE$containerStorage == null) {
            this.thE$containerStorage = new thaumicenergistics.common.storage.EssentiaStorageBusInventory(
                    new EssentiaContainerStorage(
                    this::thE$getAdjacentBlockEntity,
                    // T-M2-REMOVE：本模组存储总线已删，描述名改挂 AE2 原版存储总线（宿主本体）
                    AEParts.STORAGE_BUS.asItem().getDescription(),
                    () -> ((AEBasePart) (Object) this).getSide().getOpposite()),
                    () -> (StorageBusPart) (Object) this);
        }
        return this.thE$containerStorage;
    }

    /** 实时解析相邻方块实体（容器可能被替换 / 移除，挂载视图每次调用重新读取）。 */
    @Unique
    @Nullable
    private BlockEntity thE$getAdjacentBlockEntity() {
        AEBasePart self = (AEBasePart) (Object) this;
        Direction side = self.getSide();
        Level level = self.getLevel();
        if (side == null || level == null) {
            return null;
        }
        BlockPos pos = self.getBlockEntity().getBlockPos().relative(side);
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos);
    }

    /** 卡门控：仅当源质访问卡已插入时挂载源质容器。 */
    @Unique
    private boolean thE$hasEssentiaCard() {
        return ((UpgradeablePart) (Object) this).getUpgrades().isInstalled(ModItems.ESSENTIA_UPGRADE_CARD.get());
    }
}
