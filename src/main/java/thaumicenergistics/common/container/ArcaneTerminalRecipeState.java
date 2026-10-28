package thaumicenergistics.common.container;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import appeng.api.inventories.InternalInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.items.TCFunctionalItems;
import thaumcraft.common.lib.crafting.ThaumcraftCraftingManager;

/** 配方解析与法杖成本计算；菜单只负责槽位及同步状态。 */
public final class ArcaneTerminalRecipeState {
    public record Match(ItemStack output, AspectList cost, boolean arcane) { }

    private ArcaneTerminalRecipeState() { }

    public static Match resolve(InternalInventory grid, ItemStack wand, Player player) {
        var level = player.level();
        if (level.getServer() != null) {
            var container = grid.toContainer();
            var output = ThaumcraftCraftingManager.findMatchingArcaneRecipe(container, player);
            if (!output.isEmpty()) {
                var cost = ThaumcraftCraftingManager.findMatchingArcaneRecipeAspects(container, player);
                return new Match(hasEnoughVis(wand, cost, player) ? output : ItemStack.EMPTY, cost, true);
            }
        }
        var items = new ArrayList<ItemStack>(grid.size());
        for (int i = 0; i < grid.size(); i++) items.add(grid.getStackInSlot(i).copy());
        var input = CraftingInput.of(3, 3, items);
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        return new Match(recipe.map(holder -> holder.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY), null, false);
    }

    public static boolean hasEnoughVis(ItemStack stack, AspectList cost, Player player) {
        if (cost == null || cost.isEmpty()) return true;
        if (stack.isEmpty() || !(stack.getItem() instanceof TCFunctionalItems.WandCastingItem wand)) return false;
        var centivis = new AspectList();
        for (var aspect : cost.aspects()) centivis.add(aspect, cost.amount(aspect) * 100);
        return wand.consumeVisCost(stack, player, centivis, false, true);
    }

    public static String displayedCosts(ItemStack stack, AspectList cost, Player player) {
        if (cost == null || cost.isEmpty()) return "";
        var result = new StringBuilder();
        for (var entry : cost.aspects.entrySet()) {
            if (!result.isEmpty()) result.append(',');
            float modifier = stack.getItem() instanceof TCFunctionalItems.WandCastingItem wand
                    ? wand.consumptionModifier(stack, player, entry.getKey(), true) : 1.0F;
            result.append(entry.getKey().getTag()).append('=')
                    .append(Math.max(0, Math.round(entry.getValue() * modifier)));
        }
        return result.toString();
    }

    public static Map<String, Integer> parseCosts(String data) {
        var result = new LinkedHashMap<String, Integer>();
        for (var part : data.split(",")) {
            var pair = part.split("=", 2);
            if (pair.length != 2) continue;
            try { result.put(pair[0], Integer.parseInt(pair[1])); }
            catch (NumberFormatException ignored) { }
        }
        return result;
    }
}
