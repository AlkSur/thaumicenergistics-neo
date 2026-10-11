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
 * 给 AE2 <b>原版</b>存储总线挂载源质支持（T-M1-007-⑦b）：{@code mountInventories} TAIL 插卡后把相邻源质容器
 * 挂载为网络存储。纯 additive，不碰原版挂载存储 / 优先级等既有逻辑。存储总线不继承 {@code IOBusPart}，
 * 故需独立一处注入。
 * <p>
 * <b>T-M1-011 退役项</b>：原「源质过滤 NBT」（{@code ThEEssentiaFilterAspects}）持久化与鸭子接口
 * {@code EssentiaFilterHolder} 已随本任务删除——本宿主的分区/过滤改由<b>原生 config inventory</b> 承载
 * （{@code StorageBusPart.getConfig()}，AE2 自身以其构建 {@code IPartitionList} 施加到挂载句柄），
 * 本节不再承载任何过滤数据。
 * <p>
 * ⑦b 的两条要点：
 * <ul>
 *   <li><b>追加不替换</b>：AE2 自己的 {@code handler}（items/fluids 视图）照旧挂载，本类只往
 *       {@link IStorageMounts} 里<b>再</b>挂一个源质视图（{@link EssentiaContainerStorage}）；</li>
 *   <li><b>避让</b>：相邻方块自身已暴露 {@code AECapabilities.ME_STORAGE}（AE2 原版存储总线会直接挂它）
 *       时不再挂源质视图，避免同一个容器出现两套视图。</li>
 * </ul>
 * 挂载视图与优先级跟随总线自身（{@link IPriorityHost#getPriority()}）；卡插拔时通过
 * {@code upgradesChanged} TAIL 触发一次重新挂载，使存储增删即时生效。
 */
@Mixin(StorageBusPart.class)
public abstract class MixinStorageBusPart {

    @Unique
    private EssentiaContainerStorage thE$containerStorage;

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
    private EssentiaContainerStorage thE$getContainerStorage() {
        if (this.thE$containerStorage == null) {
            this.thE$containerStorage = new EssentiaContainerStorage(
                    this::thE$getAdjacentBlockEntity,
                    // T-M2-REMOVE：本模组存储总线已删，描述名改挂 AE2 原版存储总线（宿主本体）
                    AEParts.STORAGE_BUS.asItem().getDescription());
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
