package thaumicenergistics.common.essentia.capability;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;

import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.TCReflection;

/**
 * 源质搬运能力：把源质输入 / 输出总线的搬运核心算法抽取为无状态、可复用的操作，
 * 供既有三总线与后续「源质访问卡」共用（升级卡挂到 AE2 宿主后调用同一套逻辑）。
 * <p>
 * 本类只负责「一次搬运」的算法，不含 tick 调度、GUI、配置栏、能量结算等宿主相关逻辑；
 * 调用方自行提供速率、记能量账、持久化缓冲。依赖方向单向向下（AE2 / TC 集成层），
 * 不反向依赖菜单 / 客户端表现层。
 */
public final class EssentiaTransfer {

    private EssentiaTransfer() {}

    public record Push(long moved, AspectStack leftover) {
        public static final Push NONE = new Push(0, AspectStack.EMPTY);
    }

    /** 供应器输入已由 CPU 交付，此入口只向目标交付，不再从网络抽取。 */
    public static long insertOwned(AEssentiaKey key, long amount, BlockEntity container,
            @Nullable Direction accessSide) {
        if (container == null || key == null || amount <= 0) return 0;
        var aspect = TCReflection.findAspectById(key.getId());
        if (aspect == null) return 0;
        int wanted = (int) Math.min(amount, Integer.MAX_VALUE);
        return thaumicenergistics.common.integration.tc.EssentiaEndpoints.insert(
                container, key.getId(), wanted, accessSide);
    }

    /**
     * 容器 → 网络 的搬运产物。
     *
     * @param moved    实际注入 AE 网络的源质量
     * @param leftover 已从容器抽出、但本网络装不下的源质（调用方须暂存重试，不得丢弃；无残留时为 {@link AspectStack#EMPTY}）
     */
    public record Pull(long moved, AspectStack leftover) {
        /** 无搬运（未抽到 / 网络拒收 / 参数非法）。 */
        public static final Pull NONE = new Pull(0, AspectStack.EMPTY);
    }

    /**
     * 从相邻源质容器抽取一次源质并入网（原源质输入总线逻辑，逐行等价）。
     * <p>
     * 顺序：非破坏性窥视 → 模拟入网测容量 → 定向抽取 → 实插；
     * 模拟与实插量不一致（例如挂载的容器存储无法模拟）时，把残留交回调用方缓冲重试。
     *
     * @param container 被抽取的源质容器（要素罐子 / 奥术蒸馏器 / 荒古炼金炉 / 源质管道）
     * @param grid      目标 ME 网络
     * @param source    操作源（入网记账用）
     * @param rate      单次最大搬运量
     * @return 搬运产物；无搬运时为 {@link Pull#NONE}
     */
    public static Pull pull(@Nullable BlockEntity container, IGrid grid, IActionSource source, int rate,
            @Nullable Direction accessSide) {
        return pull(container, grid, source, rate, accessSide, id -> true);
    }

    public static Pull pull(@Nullable BlockEntity container, IGrid grid, IActionSource source, int rate,
            @Nullable Direction accessSide, Predicate<ResourceLocation> filter) {
        if (container == null || rate <= 0) return Pull.NONE;
        // 多要素容器不能因首项被过滤或网络已满而饿死后续可接收项。
        for (var available : thaumicenergistics.common.integration.tc.EssentiaEndpoints.contents(container, accessSide)) {
            if (available.isEmpty() || !filter.test(available.aspectId())) continue;
            var key = AEssentiaKey.of(available.aspectId());
            long acceptable = grid.getStorageService().getInventory().insert(
                    key, Math.min(rate, available.amount()), Actionable.SIMULATE, source);
            if (acceptable <= 0) continue;
            return pullAvailable(container, grid, source, rate, accessSide, available, key, acceptable);
        }
        return Pull.NONE;
    }

    private static Pull pullAvailable(BlockEntity container, IGrid grid, IActionSource source, int rate,
            @Nullable Direction accessSide, AspectStack available, AEssentiaKey key, long acceptable) {

        // 3) 定向抽取（取「容器能给」与「网络能收」的较小值）
        int toExtract = (int) Math.min(rate, Math.min(available.amount(), acceptable));
        AspectStack[] leftover = {AspectStack.EMPTY};
        long injected = EssentiaEnergy.transfer(grid, toExtract, allowed -> {
            var extracted = thaumicenergistics.common.integration.tc.EssentiaEndpoints.extract(
                    container, available.aspectId(), (int) allowed, accessSide);
            if (extracted == null || extracted.isEmpty()) return 0;
            long inserted = grid.getStorageService().getInventory().insert(
                    key, extracted.amount(), Actionable.MODULATE, source);
            if (inserted < extracted.amount()) {
                leftover[0] = new AspectStack(extracted.aspectId(), extracted.amount() - inserted);
            }
            return inserted;
        });
        return new Pull(injected, leftover[0]);
    }

    /**
     * 网络 → 容器 的搬运：从网络定向抽取源质注入相邻容器（原源质输出总线逻辑，逐行等价）。
     * <p>
     * 注入失败或只注入了一部分时，未注入部分会回滚回网络，绝不凭空消失。
     *
     * @param container  接收源质的相邻方块（罐子 / 蒸馏器 / 荒古炼金炉，或 TC 源质管道）
     * @param accessSide 容器接收面的朝向（TC 管道 {@code addEssentia} 需要；可空）
     * @return 实际注入容器的源质量；0 表示本次无搬运（调用方据此判「无所作为」）
     */
    public static Push push(IGrid grid, IActionSource source, @Nullable AEssentiaKey key, long amount,
                            @Nullable BlockEntity container, @Nullable Direction accessSide) {
        if (container == null || key == null || amount <= 0) return Push.NONE;
        AspectStack[] pending = {AspectStack.EMPTY};
        long moved = EssentiaEnergy.transfer(grid, amount, allowed -> {
            var inventory = grid.getStorageService().getInventory();
            long extracted = inventory.extract(key, allowed, Actionable.MODULATE, source);
            long inserted = insertOwned(key, extracted, container, accessSide);
            long remaining = extracted - inserted;
            if (remaining > 0) {
                long returned = inventory.insert(key, remaining, Actionable.MODULATE, source);
                if (returned < remaining) {
                    pending[0] = new AspectStack(key.getId(), remaining - returned);
                }
            }
            return inserted;
        });
        return new Push(moved, pending[0]);
    }
}
