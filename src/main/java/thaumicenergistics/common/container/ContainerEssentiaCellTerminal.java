package thaumicenergistics.common.container;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import thaumicenergistics.init.ModMenuTypes;

/** 源质元件终端的容器。基于网格的源质视图 + 元件槽。 */
public class ContainerEssentiaCellTerminal extends ThEContainerBase {
    final ItemStackHandler cellSlot = new ItemStackHandler(1);
    final ItemStackHandler viewSlot = new ItemStackHandler(1);

    public ContainerEssentiaCellTerminal(int id, Inventory inv) {
        super(ModMenuTypes.ESSENTIA_TERMINAL.get(), id, inv, 2);
        addSlot(new SlotItemHandler(cellSlot, 0, 26, 16));
        addSlot(new SlotItemHandler(viewSlot, 0, 134, 16));
    }
    public ContainerEssentiaCellTerminal(int id, Inventory inv, RegistryFriendlyByteBuf buf) { this(id, inv); }
}
