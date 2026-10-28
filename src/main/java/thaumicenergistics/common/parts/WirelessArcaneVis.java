package thaumicenergistics.common.parts;

import net.minecraft.world.item.ItemStack;
import thaumicenergistics.common.container.IArcaneTerminalInventoryHost;
import thaumicenergistics.common.items.VisConnectorCardItem;

/** 无线终端法杖补充，打开菜单与玩家携带时共用。 */
public final class WirelessArcaneVis {
    private WirelessArcaneVis() { }

    public static boolean replenish(IArcaneTerminalInventoryHost host) {
        var upgrades = host.getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            if (!(upgrades.getStackInSlot(i).getItem() instanceof VisConnectorCardItem)) continue;
            var node = host.getActionableNode();
            if (node == null || !node.isActive()) return false;
            var inventory = host.getSubInventory(ArcaneCraftingTerminalPart.INV_WAND);
            var wand = inventory != null ? inventory.getStackInSlot(0) : ItemStack.EMPTY;
            boolean changed = ArcaneVisReplenish.replenishAuto(node.getGrid(), wand);
            if (changed) host.saveWandInv();
            return changed;
        }
        return false;
    }
}
