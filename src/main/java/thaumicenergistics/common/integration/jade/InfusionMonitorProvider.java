package thaumicenergistics.common.integration.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.tiles.TileInfusionMonitor;
import thaumicenergistics.common.tiles.TileInfusionMonitor.RiskData;

/**
 * 注魔监视器 Jade 显示：网络连接时展示附近注魔祭坛的 5 档风险 + 不稳定度 + 档位文字。
 * 服务端把 BE 缓存的 RiskData 写入 NBT，客户端渲染。
 */
public enum InfusionMonitorProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final String KEY = "InfusionMonitorRisk";
    private static final int TIER_COLOR = 0xFF55FFFF;

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof TileInfusionMonitor monitor) {
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
        if (!accessor.getServerData().contains(KEY)) return;
        CompoundTag tag = accessor.getServerData().getCompound(KEY);
        boolean found = tag.getBoolean("found");

        // 标题：监视器 + 状态
        tooltip.add(Component.translatable("thaumicenergistics.jade.monitor.title"));

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

        // 档位：⭐×tier / 5
        String stars = "★".repeat(Math.max(1, tier)) + "☆".repeat(5 - tier);
        tooltip.add(Component.literal(stars + " (" + tier + "/5)")
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
