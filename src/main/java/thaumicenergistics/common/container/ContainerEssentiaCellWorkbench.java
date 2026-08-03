package thaumicenergistics.common.container;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.common.items.ItemEssentiaCell;
import thaumicenergistics.common.tiles.TileEssentiaCellWorkbench;
import thaumicenergistics.init.ModMenuTypes;

/**
 * 源质元件工作台容器：cell 槽绑定到方块实体（持久化），并提供分区编辑操作。
 * 对应 1.7.10 的 {@code ContainerEssentiaCellWorkbench}，分区数据存储在 cell 的 AE2 CellConfig 上。
 */
public class ContainerEssentiaCellWorkbench extends AbstractContainerMenu {

    /** cell 槽编号。 */
    public static final int SLOT_CELL = 0;
    /** 玩家背包起始编号（1..27 背包，28..36 热栏）。 */
    private static final int PLAYER_INV_START = 1;
    private static final int PLAYER_INV_END = 28;
    private static final int HOTBAR_START = 28;
    private static final int HOTBAR_END = 37;

    private final TileEssentiaCellWorkbench workbench;

    /** 兜底用空库存（workbench 为 null 时）。 */
    private final IItemHandler fallbackCell = new ItemStackHandler(1);

    public ContainerEssentiaCellWorkbench(int id, Inventory inv) {
        this(id, inv, (TileEssentiaCellWorkbench) null);
    }

    public ContainerEssentiaCellWorkbench(int id, Inventory inv, TileEssentiaCellWorkbench workbench) {
        super(ModMenuTypes.ESSENTIA_CELL_WORKBENCH.get(), id);
        this.workbench = workbench;

        IItemHandler cellHandler = workbench != null ? workbench.getCellInventory() : fallbackCell;
        addSlot(new SlotItemHandler(cellHandler, 0, 152, 8));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, 167 + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, 225));
    }

    /** 客户端打开菜单用（IMenuTypeExtension 注册）；workbench 为 null，分区操作走 C2S 到服务端。 */
    public ContainerEssentiaCellWorkbench(int id, Inventory inv, net.minecraft.network.RegistryFriendlyByteBuf buf) {
        this(id, inv);
    }

    // ===== 分区操作（由 GUI / 网络调用，服务端执行） =====

    public boolean hasCell() {
        return workbench != null && workbench.hasCell();
    }

    public List<ResourceLocation> getPartitionAspects() {
        if (workbench == null || !workbench.hasCell()) return List.of();
        return ItemEssentiaCell.getPartitionAspects(workbench.getCell());
    }

    public boolean addAspectToPartition(ResourceLocation aspectId) {
        if (workbench == null || !workbench.hasCell()) return false;
        boolean changed = ItemEssentiaCell.addAspectToPartition(workbench.getCell(), aspectId);
        if (changed) workbench.setChanged();
        return changed;
    }

    public boolean removeAspectFromPartition(ResourceLocation aspectId) {
        if (workbench == null || !workbench.hasCell()) return false;
        boolean changed = ItemEssentiaCell.removeAspectFromPartition(workbench.getCell(), aspectId);
        if (changed) workbench.setChanged();
        return changed;
    }

    public boolean replaceAspectInPartition(ResourceLocation from, ResourceLocation to) {
        if (workbench == null || !workbench.hasCell()) return false;
        boolean changed = ItemEssentiaCell.replaceAspectInPartition(workbench.getCell(), from, to);
        if (changed) workbench.setChanged();
        return changed;
    }

    public void clearPartitioning() {
        if (workbench == null || !workbench.hasCell()) return;
        ItemEssentiaCell.clearPartitioning(workbench.getCell());
        workbench.setChanged();
    }

    public void partitionToContents() {
        if (workbench == null || !workbench.hasCell()) return;
        ItemEssentiaCell.partitionToContents(workbench.getCell());
        workbench.setChanged();
    }

    /**
     * 从物品中提取第一个源质方面（罐子/法杖等 IEssentiaContainerItem）。
     */
    public static ResourceLocation getAspectFromItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof IEssentiaContainerItem containerItem) {
            AspectList aspects = containerItem.getAspects(stack);
            if (aspects != null && aspects.size() > 0) {
                Aspect first = aspects.getAspects()[0];
                if (first != null) return TCReflection.getAspectId(first);
            }
        }
        return null;
    }

    // ===== 快捷移动 / 生命周期 =====

    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        if (workbench == null || idx < 0 || idx >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(idx);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (idx == SLOT_CELL) {
            // cell 槽 → 玩家背包
            if (!moveItemStackTo(stack, PLAYER_INV_START, HOTBAR_END, true)) return ItemStack.EMPTY;
        } else if (idx < HOTBAR_END) {
            // 玩家背包 → cell 槽（仅当 cell 槽空且物品是源质元件）
            if (stack.getItem() instanceof ItemEssentiaCell && workbench.getCell().isEmpty()) {
                if (!moveItemStackTo(stack, SLOT_CELL, SLOT_CELL + 1, false)) return ItemStack.EMPTY;
            } else {
                // 含源质物品 → 尝试添加分区（物品不消费，仅添加分区）
                ResourceLocation aspectId = getAspectFromItem(stack);
                if (aspectId != null && addAspectToPartition(aspectId)) {
                    return original; // 物品留在原槽
                }
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return workbench == null || workbench.hasLevel() && player.level().getBlockEntity(workbench.getBlockPos()) == workbench;
    }
}
