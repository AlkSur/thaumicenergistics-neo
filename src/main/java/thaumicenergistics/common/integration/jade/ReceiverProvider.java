package thaumicenergistics.common.integration.jade;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.tiles.TileEssentiaProviderConnection;

/** Wireless receiver: which provider it is linked to and whether that provider is online (TEU-05). */
public enum ReceiverProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String TAG_LINK = "LinkedProvider";
    private static final String TAG_ONLINE = "ProviderOnline";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof TileEssentiaProviderConnection receiver) {
            BlockPos linked = receiver.getLinkedProviderPos();
            if (linked != null) {
                data.putLong(TAG_LINK, linked.asLong());
                var provider = receiver.getLinkedProvider();
                data.putBoolean(TAG_ONLINE, provider != null && provider.thE_isActive());
            }
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(TAG_LINK)) {
            tooltip.add(Component.translatable("thaumicenergistics.jade.receiver.unlinked").withStyle(ChatFormatting.GRAY));
            return;
        }
        BlockPos pos = BlockPos.of(data.getLong(TAG_LINK));
        boolean online = data.getBoolean(TAG_ONLINE);
        tooltip.add(Component.translatable("thaumicenergistics.jade.receiver.linked", pos.getX(), pos.getY(), pos.getZ())
                .withStyle(online ? ChatFormatting.GREEN : ChatFormatting.GOLD));
        if (!online) {
            tooltip.add(Component.translatable("thaumicenergistics.jade.receiver.provider_offline")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "receiver");
    }
}
