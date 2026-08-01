package thaumicenergistics.common.container;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import thaumicenergistics.init.ModMenuTypes;

/** 源质谐振仓的容器。1 个燃料槽 + 玩家背包。 */
public class ContainerEssentiaVibrationChamber extends ThEContainerBase {
    final ItemStackHandler fuelSlot = new ItemStackHandler(1);

    public ContainerEssentiaVibrationChamber(int id, Inventory inv) {
        super(ModMenuTypes.EVC.get(), id, inv, 1);
        addSlot(new SlotItemHandler(fuelSlot, 0, 80, 42));
    }
    public ContainerEssentiaVibrationChamber(int id, Inventory inv, RegistryFriendlyByteBuf buf) { this(id, inv); }
}
