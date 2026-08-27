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
import thaumicenergistics.common.tiles.TileInfusionMonitor;
import thaumicenergistics.common.tiles.TileInfusionMonitor.RiskData;

/**
 * 韵魔观测器 Jade 显示：
 * <ol>
 *   <li>始终显示 ME 网络状态（在线绿 / 未连接红 / 无能量红 / 无频道橙）。</li>
 *   <li>仅网络在线时展示附近注魔祭坛的风险数据：5 档风险（纯文字，无星星）+ 不稳定度 + 档位描述。</li>
 * </ol>
 * 服务端把 BE 缓存的 RiskData 与节点状态写入 NBT，客户端渲染。
 */
public enum InfusionMonitorProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String KEY = "InfusionMonitorRisk";
    private static final String TAG_ONLINE = "NetworkOnline";
    private static final String TAG_POWERED = "NodePowered";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof TileInfusionMonitor monitor) {
            data.putBoolean(TAG_ONLINE, monitor.isNetworkOnline());
            data.putBoolean(TAG_POWERED, monitor.isNodePowered());

            RiskData risk = monitor.getCachedRisk();
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("found", risk.found());
            tag.putBoolean("crafting", risk.crafting());
            tag.putInt("tier", risk.tier());
            tag.putInt("instability", risk.instability());
            tag.putInt("symmetry", risk.symmetry());
            tag.putInt("recipeInstability", risk.recipeInstability());
            tag.putString("tierText", risk.tierText());
            tag.putString("shortages", risk.shortages());
            tag.putInt("totalNeeded", risk.totalNeeded());
            data.put(KEY, tag);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!accessor.getServerData().contains(TAG_ONLINE)) return;

        // 标题：监视器
        tooltip.add(Component.translatable("thaumicenergistics.jade.monitor.title"));

        // 网络状态（始终显示）：在线绿 / 无频道橙 / 离线红
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

        // 未联网 → 不显示风险数据（风险缓存是每 tick 扫描的，与网络无关，未联网时不应展示）
        if (!online) {
            return;
        }

        if (!accessor.getServerData().contains(KEY)) return;
        CompoundTag tag = accessor.getServerData().getCompound(KEY);
        boolean found = tag.getBoolean("found");

        if (!found) {
            tooltip.add(Component.translatable("thaumicenergistics.jade.monitor.no_matrix"));
            return;
        }

        boolean crafting = tag.getBoolean("crafting");
        int tier = tag.getInt("tier");
        int instability = tag.getInt("instability");
        int symmetry = tag.getInt("symmetry");
        int recipeInstability = tag.getInt("recipeInstability");
        String tierText = tag.getString("tierText");
        String shortages = tag.getString("shortages");
        int totalNeeded = tag.getInt("totalNeeded");

        // 风险档位：纯文字（极低/低/中/高/极高），按档位着色
        tooltip.add(Component.translatable("thaumicenergistics.jade.monitor.tier",
                Component.translatable("thaumicenergistics.jade.tier." + tier), tier)
                .withStyle(style -> style.withColor(tierColor(tier))));

        // 不稳定度（分解：基础 + 祭坛）
        tooltip.add(Component.translatable("thaumicenergistics.jade.monitor.instability",
                instability, recipeInstability, symmetry));

        // 档位文字（后果描述）
        if (!tierText.isEmpty()) {
            tooltip.add(Component.literal(tierText));
        }

        // 源质缺口
        if (crafting) {
            tooltip.add(Component.translatable("thaumicenergistics.jade.monitor.crafting"));
        }
        if (!shortages.isEmpty()) {
            tooltip.add(Component.translatable("thaumicenergistics.jade.monitor.shortages", shortages));
        } else if (totalNeeded > 0) {
            tooltip.add(Component.translatable("thaumicenergistics.jade.monitor.essence_ok"));
        }
    }

    private static int tierColor(int tier) {
        return switch (tier) {
            case 1 -> 0xFF55FF55; // 绿
            case 2 -> 0xFFAAFF55; // 黄绿
            case 3 -> 0xFFFFFF55; // 黄
            case 4 -> 0xFFFFAA55; // 橙
            default -> 0xFFFF5555; // 红
        };
    }

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "infusion_monitor");
    }
}
