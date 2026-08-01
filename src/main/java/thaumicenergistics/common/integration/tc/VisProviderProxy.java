package thaumicenergistics.common.integration.tc;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.visnet.TileVisNode;
import thaumcraft.api.visnet.VisNetHandler;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.common.parts.VisInterfacePart;

import java.lang.ref.WeakReference;

/**
 * 注册到 {@link VisNetHandler} 的虚拟 TC vis 源，位于 Vis Interface "provider" 端前方。
 * 附近 TC 机器（法杖、奥术工作台等）吸取 vis 时，请求转交给所属 {@link VisInterfacePart}，
 * 由其从所面对（或 P2P 连接的另一端）的 TC 中继抽取 vis。
 *
 * 自 1.7.10 (RV3) 移植，适配 1.21.1 TileVisNode API。代理不放置为真实方块实体；
 * 用宿主方块坐标构造，手动注入 level，使 {@link VisNetHandler#addSource} 能按 WorldCoordinates 存储。
 */
public class VisProviderProxy extends TileVisNode {
    /**
     * vis 可被抽取的距离（格）。与 TC 中继范围一致，保证附近机器可用。
     */
    private static final int VIS_RANGE = 8;

    /**
     * 所属 vis interface 部件。用弱引用持有，避免代理使部件（及所在 chunk）无法回收。
     */
    private WeakReference<VisInterfacePart> owner;

    public VisProviderProxy(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VIS_PROVIDER_PROXY.get(), pos, state);
        this.owner = new WeakReference<>(null);
    }

    /**
     * 构造后绑定所属部件与世界（BlockEntityType supplier 无法传入 owner，故在此注入）。
     */
    public void bind(VisInterfacePart part, Level level) {
        this.owner = new WeakReference<>(part);
        // 真实方块实体的 level 由 chunk loader 设置；虚拟代理需手动注入。
        this.setLevel(level);
    }

    @Override
    public int getRange() {
        return VisProviderProxy.VIS_RANGE;
    }

    @Override
    public boolean isSource() {
        return true;
    }

    /**
     * 机器请求 vis 时由 {@link VisNetHandler#drainVis} 调用。
     * 转交所属部件执行实际的中继抽取（消耗 AE 能量）并处理递归保护。
     */
    @Override
    public int consumeVis(Aspect aspect, int centivis) {
        VisInterfacePart part = this.owner.get();
        if (part == null || !part.isActive()) {
            return 0;
        }
        ResourceLocation aspectId = ResourceLocation.fromNamespaceAndPath("thaumcraft", aspect.tag());
        return part.consumeVis(aspectId, centivis);
    }

    /**
     * 无焦点过滤：供应任意要素的 vis（对应常见无焦点中继）。
     * 返回 -1 保持代理与所有请求节点兼容。
     */
    @Override
    public byte getAttunement() {
        return -1;
    }

    @Override
    public void invalidate() {
        VisNetHandler.removeNode(this);
    }
}
