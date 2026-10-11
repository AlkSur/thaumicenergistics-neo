package thaumicenergistics.common.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.common.blockentities.InfusionMatrixBlockEntity;
import thaumcraft.common.blockentities.PedestalBlockEntity;
import thaumicenergistics.common.tiles.TileInfusionMonitor.RiskData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.crafting.InfusionRecipe;

/**
 * 注魔风险计算：扫描附近的注魔矩阵，按 5 档风险显示。
 * <p>
 * 判定依据（与 0.2.2.95 {@code InfusionMatrixBlockEntity} 源码一致）：
 * <ul>
 *   <li>单次异常判定概率 = instability / 500（每 10 tick 一次）。</li>
 *   <li>instability = 配方基础不稳定 + 祭坛对称度（上限 25）。</li>
 *   <li>源质不足会持续推高不稳定 → 源质缺口越大档位越高。</li>
 * </ul>
 */
public final class InfusionRiskCalculator {

    /** 扫描祭坛的半径。 */
    private static final int MATRIX_SEARCH_RADIUS = 16;
    /** 源质扫描半径（对应 EssentiaHandler.drainEssentia 的 range=12）。 */
    private static final int ESSENTIA_SCAN_RADIUS = 12;

    private InfusionRiskCalculator() {}

    /** 附近注魔矩阵信息。 */
    public record InfusionMatrixInfo(BlockPos matrixPos, InfusionMatrixBlockEntity matrix) {}

    /** 扫描指定位置附近的注魔矩阵（取最近的）。 */
    public static InfusionMatrixInfo scanNearbyMatrix(Level level, BlockPos center) {
        InfusionMatrixBlockEntity nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (int dx = -MATRIX_SEARCH_RADIUS; dx <= MATRIX_SEARCH_RADIUS; dx++) {
            for (int dz = -MATRIX_SEARCH_RADIUS; dz <= MATRIX_SEARCH_RADIUS; dz++) {
                // 注魔矩阵在 y 方向位置固定（中央柱在下方 2 格），扫描 3 层高度
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.getBlockEntity(pos) instanceof InfusionMatrixBlockEntity matrix) {
                        int dist = dx * dx + dz * dz + dy * dy;
                        if (dist < bestDist) {
                            bestDist = dist;
                            nearest = matrix;
                        }
                    }
                }
            }
        }
        return nearest != null ? new InfusionMatrixInfo(nearest.getBlockPos(), nearest) : null;
    }

    /**
     * 计算风险数据。若祭坛正在注魔，用实时 instability；否则估算：
     * 复制 matches() 匹配当前祭坛布局的配方（忽略研究门禁），得出配方基础不稳定 + 祭坛对称度。
     */
    public static RiskData computeRisk(Level level, InfusionMatrixInfo info) {
        InfusionMatrixBlockEntity matrix = info.matrix();
        boolean crafting = matrix.crafting();
        int symmetry = matrix.symmetry();
        int recipeInstability;
        int instability;

        if (crafting) {
            // 注魔中：instability() 实时（可能被源质不足推高）
            instability = Math.min(25, matrix.instability());
            recipeInstability = Math.max(0, instability - symmetry);
        } else {
            // 未开始：估算配方基础不稳定
            recipeInstability = estimateRecipeInstability(level, info);
            instability = Math.min(25, recipeInstability + symmetry);
        }

        // 源质缺口：剩余所需 vs 周围可抽
        String shortages = "";
        int totalNeeded = 0;
        AspectList remaining = matrix.remainingEssentia();
        if (remaining != null && remaining.size() > 0) {
            Map<Aspect, Long> available = scanAvailableEssentia(level, info.matrixPos());
            StringBuilder sb = new StringBuilder();
            for (Aspect aspect : remaining.aspects()) {
                int need = remaining.amount(aspect);
                if (need <= 0) continue;
                totalNeeded += need;
                long have = available.getOrDefault(aspect, 0L);
                if (have < need) {
                    if (!sb.isEmpty()) sb.append("，");
                    sb.append(aspect.getTag()).append(" 缺 ").append(need - have);
                }
            }
            shortages = sb.toString();
        }

        int tier = tierFor(instability, shortages, totalNeeded);
        return new RiskData(true, crafting, tier, instability, symmetry, recipeInstability,
                tierText(tier), shortages, totalNeeded);
    }

    /** 5 档判定：不稳定度 + 源质缺口强制升级。 */
    private static int tierFor(int instability, String shortages, int totalNeeded) {
        int tier;
        if (instability <= 1) tier = 1;
        else if (instability <= 3) tier = 2;
        else if (instability <= 6) tier = 3;
        else if (instability <= 12) tier = 4;
        else tier = 5;
        // 源质缺口强制升级
        if (!shortages.isEmpty()) {
            // 缺口过半（总需 > 0 且缺口文字非空）→ 至少 4 档；严重 → 5 档
            if (totalNeeded > 0 && shortages.length() > 20) {
                tier = Math.max(tier, 5);
            } else {
                tier = Math.max(tier, 4);
            }
        }
        return tier;
    }

    /** 档位对应文字（玩家视角后果）。 */
    public static String tierText(int tier) {
        return switch (tier) {
            case 1 -> "基本安静，偶发弹飞 1 个物品";
            case 2 -> "弹飞常见；闪电/咒波几乎不出现";
            case 3 -> "弹飞+可能电击；咒波罕见";
            case 4 -> "电击/爆炸/咒波都可能；可能销毁物品";
            case 5 -> "频繁销毁/爆炸/咒波/精神污染——不要开始";
            default -> "";
        };
    }

    /** 未注魔时估算匹配配方的配方基础不稳定（复制 matches 但忽略研究门禁）。 */
    private static int estimateRecipeInstability(Level level, InfusionMatrixInfo info) {
        InfusionMatrixBlockEntity matrix = info.matrix();
        try {
            // 取中央柱物品（注魔矩阵正下方 2 格）
            PedestalBlockEntity center = level.getBlockEntity(info.matrixPos().below(2)) instanceof PedestalBlockEntity ped
                    ? ped : null;
            if (center == null || center.heldStack().isEmpty()) {
                return 0;
            }
            ItemStack central = center.heldStack();
            // 收集周围柱物品（与 scanSurroundings 一致的 ±8 范围简化版）
            List<ItemStack> components = new ArrayList<>();
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = 1; dy <= 10; dy++) {
                        BlockEntity be = level.getBlockEntity(info.matrixPos().offset(dx, -dy, dz));
                        if (be instanceof PedestalBlockEntity ped && !ped.heldStack().isEmpty()) {
                            components.add(ped.heldStack());
                            break;
                        }
                    }
                }
            }
            // 找匹配配方（忽略研究：直接比对中央 + 组件，不调 matches 以免研究门禁）
            for (Object o : ThaumcraftApi.getCraftingRecipes()) {
                if (!(o instanceof InfusionRecipe recipe)) continue;
                if (!recipe.getRecipeInput().is(central.getItem())) continue;
                // 简化：中央物品匹配即采用其基础不稳定（组件精确匹配留给 craftingStart 处理）
                return recipe.instability(central);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    /** 扫描周围 12 格内源质容器，汇总每种源质可抽量。 */
    private static Map<Aspect, Long> scanAvailableEssentia(Level level, BlockPos matrixPos) {
        Map<Aspect, Long> totals = new ConcurrentHashMap<>();
        for (int dx = -ESSENTIA_SCAN_RADIUS; dx <= ESSENTIA_SCAN_RADIUS; dx++) {
            for (int dy = -6; dy <= 6; dy++) {
                for (int dz = -ESSENTIA_SCAN_RADIUS; dz <= ESSENTIA_SCAN_RADIUS; dz++) {
                    BlockEntity be = level.getBlockEntity(matrixPos.offset(dx, dy, dz));
                    if (be == null || be == level.getBlockEntity(matrixPos)) continue;
                    if (be instanceof IAspectContainer container) {
                        AspectList aspects = container.getAspects();
                        if (aspects == null) continue;
                        for (Aspect aspect : aspects.aspects()) {
                            int amt = aspects.getAmount(aspect);
                            if (amt > 0) totals.merge(aspect, (long) amt, Long::sum);
                        }
                    }
                }
            }
        }
        return totals;
    }
}
