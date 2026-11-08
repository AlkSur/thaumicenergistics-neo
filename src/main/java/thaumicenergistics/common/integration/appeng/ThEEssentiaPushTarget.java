package thaumicenergistics.common.integration.appeng;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderTarget;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectContainer;
import thaumicenergistics.common.essentia.capability.EssentiaEnergy;
import thaumicenergistics.common.essentia.capability.EssentiaTransfer;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.EssentiaEndpoints;
import thaumicenergistics.common.integration.tc.TCReflection;

/**
 * 样板供应器的源质输出目标。CPU 已交付输入，因此只插入已持有资源，不再次提取网络。
 * 模拟按可接受类型预检，实际拒收余量由 AE2 sendList 保存并重试。
 */
public final class ThEEssentiaPushTarget implements PatternProviderTarget {

    private final IGrid grid;
    private final IActionSource source;
    private final BlockEntity container;
    /** 容器接收面的朝向（TC 管道 addEssentia 回退路径需要）。 */
    private final Direction accessSide;
    private final BooleanSupplier enabled;

    public ThEEssentiaPushTarget(IGrid grid, IActionSource source, BlockEntity container, Direction accessSide) {
        this(grid, source, container, accessSide, () -> true);
    }

    public ThEEssentiaPushTarget(IGrid grid, IActionSource source, BlockEntity container, Direction accessSide,
            BooleanSupplier enabled) {
        this.grid = grid;
        this.source = source;
        this.container = container;
        this.accessSide = EssentiaEndpoints.accessSide(container, accessSide);
        this.enabled = enabled;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable type) {
        if (!(what instanceof AEssentiaKey key) || amount <= 0 || !enabled.getAsBoolean()) {
            return 0; // 源质落点不收物品/流体（回收走 AE2 原生机器推回链路）
        }
        if (type == Actionable.SIMULATE) {
            // P-A 类型级近似：不验余量，只要容器「接受该类型」即放行推送；
            // 实际注入不足的部分由 pushPattern 的 addToSendList → sendStacksOut 原生重试
            return thE$typeAccepts(key) ? amount : 0;
        }
        // CPU 已经交付输入；这里只交付已持有资源，未接收部分由供应器 sendList 重试。
        return EssentiaEnergy.transfer(
                grid, amount, allowed -> EssentiaTransfer.insertOwned(
                        key, allowed, container, accessSide));
    }

    /**
     * blocking 语义：相邻机器内已有某个所需源质时拦推，不要求机器允许向外输出。
     * 只读查询使用本地库存观察接口；查询异常时<b>降级放行</b>（返 false），
     * 误推的余量由 sendList 兜底，不会丢料。
     */
    @Override
    public boolean containsPatternInput(Set<AEKey> patternInputs) {
        if (!enabled.getAsBoolean()) return false;
        try {
            for (var advertised : EssentiaEndpoints.storedContents(this.container, accessSide)) {
                for (var key : patternInputs) {
                    if (key instanceof AEssentiaKey essentiaKey
                            && advertised.aspectId().equals(essentiaKey.getId())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // 遇阻降级：判不出就放行推送
        }
        return false;
    }

    /**
     * 类型级接收判定（P-A）：拉取式机器（如神秘炼金塔，配方激活后才实收）以机器自身
     * {@code isConnectable && canInputFrom} 作类型级放行；没有传输接口的容器回退
     * {@code IAspectContainer.doesContainerAccept}。不验余量；MODULATE 收 0 的空转
     * 由 AE2 sendList 原生重试兜底（不丢料）。
     */
    private boolean thE$typeAccepts(AEssentiaKey key) {
        Aspect aspect = TCReflection.findAspectById(key.getId());
        if (aspect == null) {
            return false;
        }
        var transport = EssentiaEndpoints.transport(container, accessSide);
        if (transport != null) {
            // A container fallback must not override a transport's closed input face.
            // Keep type-level preflight for pull-driven machines: real rejected
            // resources remain in the provider's own retry queue.
            return this.accessSide != null && transport.isConnectable(this.accessSide)
                    && transport.canInputFrom(this.accessSide);
        }
        return this.container instanceof IAspectContainer aspectContainer
                && aspectContainer.doesContainerAccept(aspect);
    }
}
