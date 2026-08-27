package thaumicenergistics.common.integration.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.tiles.TileProviderBase;

/**
 * 注魔供应器 / 源质供应器 Jade 显示：ME 网络连接状态。
 * <ul>
 *   <li>在线：节点 active（有能量 + 有频道）——绿</li>
 *   <li>无频道：已接线上网但没分到频道——橙</li>
 *   <li>离线：未接线路 / 网络无能量——红</li>
 * </ul>
 * 服务端把节点状态写入 NBT，客户端渲染。
 */
public enum InfusionProviderProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String TAG_ONLINE = "NetworkOnline";
    private static final String TAG_POWERED = "NodePowered";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof TileProviderBase provider) {
            data.putBoolean(TAG_ONLINE, provider.isActive());
            data.putBoolean(TAG_POWERED, provider.getMainNode().isPowered());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().contains(TAG_ONLINE)) return;

        boolean online = accessor.getServerData().getBoolean(TAG_ONLINE);
        boolean powered = accessor.getServerData().getBoolean(TAG_POWERED);

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
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "provider_network");
    }
}
