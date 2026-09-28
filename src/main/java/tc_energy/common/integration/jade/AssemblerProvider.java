package tc_energy.common.integration.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.tiles.TileArcaneAssembler;

/**
 * 奥术装配器 Jade 显示：
 * <ul>
 *   <li>网络状态：在线（节点 active）/ 无频道（有能量）/ 离线（无能量）</li>
 *   <li>要素状态：已连接（内存卡配置了 vis 源频率）/ 未连接</li>
 * </ul>
 * 服务端把状态写入 NBT，客户端渲染。
 */
public enum AssemblerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String TAG_ONLINE = "NetworkOnline";
    private static final String TAG_POWERED = "NodePowered";
    private static final String TAG_VIS = "VisConnected";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof TileArcaneAssembler assembler) {
            data.putBoolean(TAG_ONLINE, assembler.isActive());
            data.putBoolean(TAG_POWERED, assembler.isNodePowered());
            data.putBoolean(TAG_VIS, assembler.getVisSourceFreq() != 0);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().contains(TAG_ONLINE)) return;

        boolean online = accessor.getServerData().getBoolean(TAG_ONLINE);
        boolean powered = accessor.getServerData().getBoolean(TAG_POWERED);
        boolean visConnected = accessor.getServerData().getBoolean(TAG_VIS);

        String statusKey;
        int color;
        if (online) {
            statusKey = "tc_energy.jade.provider.online";
            color = 0xFF55FF55;
        } else if (powered) {
            statusKey = "tc_energy.jade.provider.no_channel";
            color = 0xFFFFAA55;
        } else {
            statusKey = "tc_energy.jade.provider.offline";
            color = 0xFFFF5555;
        }

        tooltip.add(Component.translatable("tc_energy.jade.provider.network",
                Component.translatable(statusKey).withStyle(style -> style.withColor(color))));

        // 要素源状态：已连接（绿）/ 未连接（灰）
        tooltip.add(Component.translatable("tc_energy.jade.assembler.vis_source",
                Component.translatable(visConnected
                        ? "tc_energy.jade.assembler.vis_connected"
                        : "tc_energy.jade.assembler.vis_disconnected")
                        .withStyle(style -> style.withColor(visConnected ? 0xFF55FF55 : 0xFFAAAAAA))));
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "assembler");
    }
}
