package thaumicenergistics.common.container.slot;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 只读展示槽：显示 IItemHandler 中真实物品，但禁止玩家/整理 mod 交互。
 * 与 AE2 ClientReadOnlySlot 等效（保留 inventory 用于渲染，mayPlace/mayPickup/set/remove 空操作）。
 * 用于玩家不交互的纯展示槽（装配器的预存配方槽、目标产物槽）。
 */
public class ReadOnlySlot extends Slot {

    /** 假 inventory 占位：阻止整理 mod 通过 slot 的 inventory/index 交互。 */
    private static final Container EMPTY_INVENTORY = new SimpleContainer(0);

    private final IItemHandler handler;
    private final int handlerIndex;

    public ReadOnlySlot(IItemHandler handler, int index, int xPosition, int yPosition) {
        super(EMPTY_INVENTORY, 0, xPosition, yPosition);
        this.handler = handler;
        this.handlerIndex = index;
    }

    @Override
    public ItemStack getItem() {
        // 读取真实 inventory 用于渲染显示；handlerIndex 是 handler 内索引（Slot.index 是菜单槽序号，勿用）
        return handler.getStackInSlot(handlerIndex);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public final void set(ItemStack stack) {
    }

    @Override
    public int getMaxStackSize() {
        return 0;
    }

    @Override
    public ItemStack remove(int amount) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }
}
