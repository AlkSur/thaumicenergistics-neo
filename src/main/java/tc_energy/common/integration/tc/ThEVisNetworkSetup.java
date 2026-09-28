package tc_energy.common.integration.tc;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;

import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.node.VisSource;
import dev.tc4port.thaumcraft.api.node.VisNetworkApi;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.parts.VisInterfacePart;

/**
 * ThE 对 Ren 版 vis 网络的接入（替代旧 VisProviderProxy/TileVisNode）。
 * <p>
 * Vis Interface 部件以 provider 模式运行时（隧道另端面对中继、本端不面对），
 * 会把本部件前方一格注册为 vis 网络外部源（registerSource）。Ren 版 vis 网络在
 * 附近机器抽 vis 时通过 {@code VisSourceRegistry.sources(blockEntity)} 解析该位置的
 * 外部源——本 provider 将宿主线缆方块实体映射到一个 {@link VisSource}，
 * takeVis 委托回 VisInterfacePart（经 P2P 隧道到面对中继的一端抽取，消耗 AE 能量）。
 * <p>
 * 拓扑要点（Ren 版 VisNetwork）：外部源的 networkEndpoint 必须是某个"parentPos==源位置"
 * 的中继；TC 机器通过 drain(level, machinePos) 在 SOURCE_RANGE(8) 内发现源端点。
 * 注册在 common setup 一次，provider 持有 mod 生命周期。
 */
public final class ThEVisNetworkSetup {

    private static boolean registered = false;

    private ThEVisNetworkSetup() {}

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        VisNetworkApi.registerProvider(providerId(), ThEVisNetworkSetup::sourcesFor);
        ThaumicEnergistics.LOG.info("ThE vis network provider registered.");
    }

    private static ResourceLocation providerId() {
        return ThaumicEnergistics.id("vis_interface");
    }

    /**
     * 把线缆总线宿主（含 provider 模式的 VisInterfacePart）映射为 VisSource 列表。
     * 一个位置可有多个部件，但外部源按位置共享一个 graph root——取第一个 provider 端即可。
     */
    private static List<? extends VisSource> sourcesFor(BlockEntity host) {
        if (!(host instanceof appeng.api.parts.IPartHost partHost)) {
            return List.of();
        }
        for (var side : net.minecraft.core.Direction.values()) {
            if (partHost.getPart(side) instanceof VisInterfacePart visPart && visPart.isVisProvider()) {
                return List.of(new InterfaceVisSource(visPart, host.getBlockPos()));
            }
        }
        return List.of();
    }

    /**
     * VisSource 实现：canSupplyVis = 部件活跃；takeVis 委托回部件 consumeVis。
     * networkEndpoint 返回宿主前方位置？——不：Ren 版要求 endpoint 是 parentPos==sourcePos 的中继。
     * ThE 的外部源直接以部件前方为"源位置"注册（registerSource(front)），
     * 而源位置本身是虚构节点（无中继附着），networkEndpoint 返回 null 会被 registry 过滤。
     * 因此约定：provider 端注册的是"面对中继那端"的中继位置——即 P2P 另一端面对的中继，
     * 该中继在 Ren 版 vis 图中 parentPos 指向哪个源由 TC 自行维护；本源以该中继为 endpoint，
     * 机器在 8 格内 drain 到该中继即落到本 VisSource。
     */
    private static final class InterfaceVisSource implements VisSource {

        private final VisInterfacePart part;
        private final BlockPos hostPos;

        InterfaceVisSource(VisInterfacePart part, BlockPos hostPos) {
            this.part = part;
            this.hostPos = hostPos;
        }

        @Override
        public boolean canSupplyVis() {
            return part.isActive();
        }

        @Override
        public BlockPos networkEndpoint() {
            // P2P 另一端（面对中继端）面对的中继位置。
            VisInterfacePart end = part.findRelayEndForNetwork();
            return end != null ? end.getFrontRelayPos() : null;
        }

        @Override
        public int takeVis(VisChannel channel, int amount, VisAction action, int reservedCentivis) {
            if (!canSupplyVis() || amount <= 0) {
                return 0;
            }
            int request = amount;
            if (action == VisAction.SIMULATE) {
                return part.simulateVisDraw(channel.aspectId().value(), request);
            }
            return part.consumeVis(channel.aspectId().value(), request);
        }
    }
}
