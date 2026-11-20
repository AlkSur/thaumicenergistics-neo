package thaumicenergistics.common.tiles;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.common.blockentities.InfusionMatrixBlockEntity;
import thaumcraft.common.blockentities.PedestalBlockEntity;
import thaumicenergistics.common.tiles.TileInfusionMonitor.RiskData;

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
        InfusionMatrixBlockEntity[] nearest = {null};
        int[] bestDist = {Integer.MAX_VALUE};
        forEachLoadedBlockEntity(level,
                center.offset(-MATRIX_SEARCH_RADIUS, -4, -MATRIX_SEARCH_RADIUS),
                center.offset(MATRIX_SEARCH_RADIUS, 4, MATRIX_SEARCH_RADIUS), be -> {
                    if (be instanceof InfusionMatrixBlockEntity matrix) {
                        int dist = (int) be.getBlockPos().distSqr(center);
                        if (dist < bestDist[0]) {
                            bestDist[0] = dist;
                            nearest[0] = matrix;
                        }
                    }
                });
        return nearest[0] != null ? new InfusionMatrixInfo(nearest[0].getBlockPos(), nearest[0]) : null;
    }

    /**
     * Visit the block entities inside a box, chunk by chunk, skipping unloaded chunks. Probing every
     * position cost thousands of lookups per scan and could load chunks at the edge (TET-11).
     */
    static void forEachLoadedBlockEntity(Level level, BlockPos min, BlockPos max,
            java.util.function.Consumer<BlockEntity> action) {
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
                    BlockPos pos = be.getBlockPos();
                    if (pos.getX() >= min.getX() && pos.getX() <= max.getX()
                            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()) {
                        action.accept(be);
                    }
                }
            }
        }
    }

    /**
     * 计算风险数据。若祭坛正在注魔，用实时 instability；否则估算：
     * 复制 matches 匹配当前祭坛布局的配方（忽略研究门禁），得出配方基础不稳定 + 祭坛对称度。
     */
    public static RiskData computeRisk(Level level, InfusionMatrixInfo info) {
        InfusionMatrixBlockEntity matrix = info.matrix();
        boolean crafting = matrix.crafting();
        int symmetry = matrix.symmetry();
        int recipeInstability;
        int instability;

        if (crafting) {
            // 注魔中：instability 实时（可能被源质不足推高）
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
        long missing = 0;
        AspectList remaining = matrix.remainingEssentia();
        if (remaining != null && remaining.size() > 0) {
            Map<Aspect, Long> available = scanAvailableEssentia(level, info.matrixPos(), remaining);
            StringBuilder sb = new StringBuilder();
            for (Aspect aspect : remaining.aspects()) {
                int need = remaining.amount(aspect);
                if (need <= 0) continue;
                totalNeeded += need;
                long have = available.getOrDefault(aspect, 0L);
                if (have < need) {
                    if (!sb.isEmpty()) sb.append(';');
                    sb.append(aspect.getTag()).append('=').append(need - have);
                    missing += need - have;
                }
            }
            shortages = sb.toString();
        }

        int tier = tierFor(instability, missing, totalNeeded);
        return new RiskData(true, crafting, tier, instability, symmetry, recipeInstability,
                tierText(tier), shortages, totalNeeded);
    }

    /** 5 档判定：不稳定度 + 源质缺口强制升级。 */
    private static int tierFor(int instability, long missing, int totalNeeded) {
        int tier;
        if (instability <= 1) tier = 1;
        else if (instability <= 3) tier = 2;
        else if (instability <= 6) tier = 3;
        else if (instability <= 12) tier = 4;
        else tier = 5;
        // A shortage raises the tier: to 5 when half or more of what is still needed is missing, else to 4.
        // (It used to count the characters of the Chinese shortage text.)
        if (missing > 0) {
            tier = Math.max(tier, totalNeeded > 0 && missing * 2 >= totalNeeded ? 5 : 4);
        }
        return tier;
    }

    /** Language key of the tier's consequences, stored in RiskData.tierText and rendered by the reader. */
    public static String tierText(int tier) {
        return tier >= 1 && tier <= 5 ? "thaumicenergistics.monitor.tier_effect." + tier : "";
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
            // Pedestal items around the matrix: per column, the highest pedestal below it (TC's layout).
            java.util.Map<Long, PedestalBlockEntity> columns = new java.util.HashMap<>();
            BlockPos m = info.matrixPos();
            forEachLoadedBlockEntity(level, m.offset(-8, -10, -8), m.offset(8, -1, 8), be -> {
                if (!(be instanceof PedestalBlockEntity ped) || ped.heldStack().isEmpty()) return;
                BlockPos p = be.getBlockPos();
                if (p.getX() == m.getX() && p.getZ() == m.getZ()) return;
                long column = ((long) p.getX() << 32) ^ (p.getZ() & 0xffffffffL);
                columns.merge(column, ped, (a1, b1) -> a1.getBlockPos().getY() >= b1.getBlockPos().getY() ? a1 : b1);
            });
            List<ItemStack> components = new java.util.ArrayList<>();
            for (var ped : columns.values()) components.add(ped.heldStack());
            // The recipe whose central item and components both match, ignoring research. If none or several
            // match, take the highest instability among recipes for this central item: a warning that errs high
            // beats showing another recipe's number (TET-10).
            int matched = -1;
            int matches = 0;
            int highest = 0;
            for (Object o : ThaumcraftApi.getCraftingRecipes()) {
                if (!(o instanceof InfusionRecipe recipe)) continue;
                if (recipe.getRecipeInput() == null || !recipe.getRecipeInput().is(central.getItem())) continue;
                int value = recipe.instability(central);
                highest = Math.max(highest, value);
                if (InfusionRecipe.matchesComponents(components, recipe.components(central))) {
                    matched = value;
                    matches++;
                }
            }
            return matches == 1 ? matched : highest;
        } catch (Exception ignored) {
        }
        return 0;
    }

    /**
     * What the matrix could drain: the same sources and box as TC's EssentiaHandler (IAspectSource only,
     * ±12 across, -12..+11 vertically). Asking each source by aspect counts ME-backed infusion providers,
     * whose getAspects() is empty, and one network behind several providers is counted once (TET-03).
     */
    private static Map<Aspect, Long> scanAvailableEssentia(Level level, BlockPos matrixPos, AspectList needed) {
        Map<Aspect, Long> totals = new java.util.HashMap<>();
        java.util.Set<Object> grids = new java.util.HashSet<>();
        forEachLoadedBlockEntity(level,
                matrixPos.offset(-ESSENTIA_SCAN_RADIUS, -ESSENTIA_SCAN_RADIUS, -ESSENTIA_SCAN_RADIUS),
                matrixPos.offset(ESSENTIA_SCAN_RADIUS, ESSENTIA_SCAN_RADIUS - 1, ESSENTIA_SCAN_RADIUS), be -> {
                    if (be.getBlockPos().equals(matrixPos) || be.isRemoved()
                            || !(be instanceof IAspectSource source)) return;
                    if (be instanceof TileProviderBase provider) {
                        var grid = provider.getMainNode().getGrid();
                        if (grid != null && !grids.add(grid)) return;
                    }
                    for (Aspect aspect : needed.aspects()) {
                        int amount = source.containerContains(aspect);
                        if (amount > 0) totals.merge(aspect, (long) amount, Long::sum);
                    }
                });
        return totals;
    }
}
