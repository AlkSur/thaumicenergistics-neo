package thaumicenergistics.common.integration.neoecoae;

import appeng.api.stacks.AEKeyType;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;
import cn.dancingsnow.neoecoae.api.storage.INativeMEStorageRestrictedItem;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;
import thaumicenergistics.common.items.ItemEssentiaCell;

/**
 * An ECO storage matrix for essentia. Sizes follow ECO's own matrices of the same tier (total bytes, bytes per
 * type, idle drain); the storage itself is an AE2 basic cell inventory, so partitions, the essentia cell workbench
 * and tooltips work as for TE's essentia cells. ECO keeps it out of AE2's ME drive and ME chest.
 */
public final class EssentiaMatrixItem extends ItemEssentiaCell implements IECOStorageCellItem, INativeMEStorageRestrictedItem {

    /** Aspect types per matrix: the partition holds 63, as on TE's cells. */
    public static final int TYPES = 63;

    private final ECOTier ecoTier;
    private final String size;

    EssentiaMatrixItem(Properties props, ECOTier tier, String size) {
        super(props, "eco_" + size, Math.toIntExact(tier.getStorageTotalBytes() / 1024), 1 << (12 + tier.getTier()),
                TYPES, (double) tier.getStorageTotalBytes() / (1 << 20));
        this.ecoTier = tier;
        this.size = size;
    }

    @Override
    public IECOTier getTier() {
        return ecoTier;
    }

    @Override
    public ECOCellType getCellType() {
        return ThENeoEcoStorage.ESSENTIA.get();
    }

    @Override
    public Set<AEKeyType> getKeyTypes() {
        return Set.of(AEssentiaKeyType.INSTANCE);
    }

    @Override
    protected List<ItemStack> disassemblyParts() {
        var component = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("neoecoae", "eco_cell_component_" + size));
        if (component == Items.AIR) return List.of();
        return List.of(new ItemStack(component), new ItemStack(NeoEcoItems.HOUSING.get()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.thaumicenergistics.eco_matrix", ecoTier.getPanelTierName())
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
