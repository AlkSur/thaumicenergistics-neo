package thaumicenergistics.common.integration.jade;

import appeng.util.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.tiles.TileArcaneAssembler;

/**
 * 奥术装配器 Jade 显示：
 * <ul>
 *   <li>网络状态：在线（节点 active）/ 无频道（有能量）/ 离线（无能量）</li>
 *   <li>魔力源状态：已连接 / 未设置 / 已设置但不可用，以及彩色 P2P 频率</li>
 * </ul>
 * 服务端把状态写入 NBT，客户端渲染。
 */
public enum AssemblerProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String TAG_ONLINE = "NetworkOnline";
    private static final String TAG_POWERED = "NodePowered";
    private static final String TAG_VIS = "VisConnected";
    private static final String TAG_FREQUENCY = "VisFrequency";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof TileArcaneAssembler assembler) {
            data.putBoolean(TAG_ONLINE, assembler.isNodeActive());
            data.putBoolean(TAG_POWERED, assembler.isNodePowered());
            data.putBoolean(TAG_VIS, assembler.isVisSourceAvailable());
            data.putShort(TAG_FREQUENCY, assembler.getVisSourceFreq());
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
            statusKey = "thaumicenergistics.jade.provider.online";
            color = 0xFF55FF55;
        } else if (powered) {
            statusKey = "thaumicenergistics.jade.provider.no_channel";
            color = 0xFFFFAA55;
        } else {
            statusKey = "thaumicenergistics.jade.provider.offline";
            color = 0xFFFF5555;
        }

        tooltip.add(Component.translatable("thaumicenergistics.jade.provider.network",
                Component.translatable(statusKey).withStyle(style -> style.withColor(color))));

        short frequency = accessor.getServerData().getShort(TAG_FREQUENCY);
        if (frequency != 0) {
            tooltip.add(Component.translatable("waila.ae2.P2PFrequency",
                    Platform.p2p().toColoredHexString(frequency)
                            .withStyle(ChatFormatting.BOLD)));
        }
        tooltip.add(Component.translatable("thaumicenergistics.jade.assembler.vis_source",
                Component.translatable(visConnected
                        ? "thaumicenergistics.jade.assembler.vis_connected"
                        : frequency == 0 ? "thaumicenergistics.jade.assembler.vis_disconnected"
                        : "thaumicenergistics.jade.assembler.vis_unavailable")
                        .withStyle(style -> style.withColor(visConnected ? 0xFF55FF55 : 0xFFAAAAAA))));
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "assembler");
    }
}
