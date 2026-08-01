package thaumicenergistics.common.items;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.IBasicCellItem;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.items.contents.CellConfig;
import appeng.me.cells.BasicCellInventory;
import appeng.util.ConfigInventory;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;
import thaumicenergistics.config.ThEConfig;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ItemEssentiaCell extends Item implements IBasicCellItem {

    private final String tier;
    private final int totalBytes;
    private final int bytesPerType;
    private final int totalTypes;
    private final double idleDrain;

    public ItemEssentiaCell(Item.Properties props, String tier, int kilobytes, int bytesPerType, int totalTypes, double idleDrain) {
        super(props.stacksTo(1));
        this.tier = tier;
        this.totalBytes = kilobytes * 1024;
        this.bytesPerType = bytesPerType;
        this.totalTypes = totalTypes;
        this.idleDrain = idleDrain;
    }

    @Override
    public AEKeyType getKeyType() {
        return AEssentiaKeyType.INSTANCE;
    }

    @Override
    public int getBytes(ItemStack cellItem) {
        return this.totalBytes;
    }

    @Override
    public int getBytesPerType(ItemStack cellItem) {
        return this.bytesPerType;
    }

    @Override
    public int getTotalTypes(ItemStack cellItem) {
        return this.totalTypes;
    }

    @Override
    public double getIdleDrain() {
        return this.idleDrain;
    }

    @Override
    public boolean isStorageCell(ItemStack i) {
        return true;
    }

    @Override
    public boolean isBlackListed(ItemStack cellItem, appeng.api.stacks.AEKey requestedAddition) {
        if (requestedAddition instanceof thaumicenergistics.common.integration.appeng.AEssentiaKey) {
            return false;
        }
        return true;
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack is) {
        return UpgradeInventories.forItem(is, 3);
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(Set.of(AEssentiaKeyType.INSTANCE), is);
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        this.addCellInformationToTooltip(stack, tooltip);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        // 渲染顶部源质图标（前5种存量最高的源质）
        return this.getCellTooltipImage(stack);
    }

    public String getTier() { return tier; }

    // ===== 分区辅助（基于 AE2 CellConfig，与 ME 终端分区互通） =====

    @Nullable
    private static ItemEssentiaCell getCellItem(ItemStack stack) {
        return stack.getItem() instanceof ItemEssentiaCell cell ? cell : null;
    }

    /** 获取 cell 的分区库存（63 槽 ConfigInventory），非 cell 返回 null。 */
    @Nullable
    public static ConfigInventory getPartitionInventory(ItemStack stack) {
        ItemEssentiaCell cell = getCellItem(stack);
        return cell == null ? null : cell.getConfigInventory(stack);
    }

    /** 获取分区列表（aspect 的 ResourceLocation）。 */
    public static List<ResourceLocation> getPartitionAspects(ItemStack stack) {
        ConfigInventory inv = getPartitionInventory(stack);
        if (inv == null) return List.of();
        List<ResourceLocation> result = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getKey(i) instanceof AEssentiaKey ek) result.add(ek.getId());
        }
        return result;
    }

    /** 添加一个 aspect 到分区；已存在或满返回 false。 */
    public static boolean addAspectToPartition(ItemStack stack, ResourceLocation aspectId) {
        ConfigInventory inv = getPartitionInventory(stack);
        if (inv == null) return false;
        AEssentiaKey key = AEssentiaKey.of(aspectId);
        for (int i = 0; i < inv.size(); i++) {
            if (key.equals(inv.getKey(i))) return false;
        }
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getKey(i) == null) {
                inv.insert(i, key, 1, Actionable.MODULATE);
                return true;
            }
        }
        return false;
    }

    /** 从分区移除一个 aspect；不存在返回 false。 */
    public static boolean removeAspectFromPartition(ItemStack stack, ResourceLocation aspectId) {
        ConfigInventory inv = getPartitionInventory(stack);
        if (inv == null) return false;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getKey(i) instanceof AEssentiaKey ek && ek.getId().equals(aspectId)) {
                inv.setStack(i, null);
                return true;
            }
        }
        return false;
    }

    /** 用 to 替换分区中的 from；from 不存在返回 false。 */
    public static boolean replaceAspectInPartition(ItemStack stack, ResourceLocation from, ResourceLocation to) {
        ConfigInventory inv = getPartitionInventory(stack);
        if (inv == null) return false;
        AEssentiaKey newKey = AEssentiaKey.of(to);
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getKey(i) instanceof AEssentiaKey ek && ek.getId().equals(from)) {
                inv.setStack(i, new GenericStack(newKey, 1));
                return true;
            }
        }
        return false;
    }

    /** 清空全部分区。 */
    public static void clearPartitioning(ItemStack stack) {
        ConfigInventory inv = getPartitionInventory(stack);
        if (inv != null) inv.clear();
    }

    /** 将 cell 当前存储的源质设为分区（对应 1.7.10 的 partitionToCellContents）。 */
    public static void partitionToContents(ItemStack stack) {
        ConfigInventory inv = getPartitionInventory(stack);
        if (inv == null) return;
        inv.clear();
        BasicCellInventory cellInv = BasicCellInventory.createInventory(stack, null);
        if (cellInv == null) return;
        KeyCounter counter = new KeyCounter();
        cellInv.getAvailableStacks(counter);
        int slot = 0;
        for (var entry : counter) {
            if (entry.getKey() instanceof AEssentiaKey ek && slot < inv.size()) {
                inv.insert(slot++, ek, 1, Actionable.MODULATE);
            }
        }
    }

    // ===== 工厂方法 =====
    public static ItemEssentiaCell create1k(Item.Properties props) {
        return new ItemEssentiaCell(props, "1k", 1, 8, ThEConfig.CELL_MAX_TYPES, 0.5);
    }
    public static ItemEssentiaCell create4k(Item.Properties props) {
        return new ItemEssentiaCell(props, "4k", 4, 8, ThEConfig.CELL_MAX_TYPES, 1.0);
    }
    public static ItemEssentiaCell create16k(Item.Properties props) {
        return new ItemEssentiaCell(props, "16k", 16, 8, ThEConfig.CELL_MAX_TYPES, 1.5);
    }
    public static ItemEssentiaCell create64k(Item.Properties props) {
        return new ItemEssentiaCell(props, "64k", 64, 8, ThEConfig.CELL_MAX_TYPES, 2.0);
    }
    public static ItemEssentiaCell createCreative(Item.Properties props) {
        return new ItemEssentiaCell(props, "creative", Integer.MAX_VALUE / 1024, 8, 63, 0.0);
    }
}