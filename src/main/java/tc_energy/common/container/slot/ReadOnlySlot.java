package tc_energy.common.container.slot;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 只读展示槽：显示 IItemHandler 中真实物品，但禁止玩家/整理 mod 交互。
 * <p>
 * 与 AE2 {@code ClientReadOnlySlot} 的差别：AE2 那个槽只在客户端创建、不参与同步；
 * 本槽两侧都会 addSlot，因此 {@link #set(ItemStack)} 必须写回 handler 才能接收服务端同步
 * （mayPlace / mayPickup / remove 仍为空实现，玩家交互依旧被完全禁止）。
 * 用于纯展示槽：知识记录仪预产物、装配器预存配方槽/目标产物槽、蒸馏编码台源质槽。
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

    /**
     * 必须把服务端同步来的值写回 handler。
     * <p>
     * ⚠ 不能是空实现！（AE2 {@code ClientReadOnlySlot} 是空实现，但那个槽是<b>纯客户端</b>创建、
     * 不参与同步的。）本槽在客户端同样被 addSlot，MC 的同步链路是：
     * <pre>
     *   服务端 AbstractContainerMenu.synchronizeSlotToRemote → 读 slot.getItem() 比较后发包
     *   客户端 ClientboundContainerSetSlotPacket → AbstractContainerMenu.setItem → slot.set(stack)
     * </pre>
     * 若 set 为空实现 → 服务端有值、客户端永远拿不到 →
     * 只读展示槽（知识记录仪预产物 / 装配器目标产物 / 蒸馏编码台源质）永远空白。
     */
    @Override
    public void set(ItemStack stack) {
        if (this.handler instanceof net.neoforged.neoforge.items.IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(this.handlerIndex, stack);
        }
        this.setChanged();
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
