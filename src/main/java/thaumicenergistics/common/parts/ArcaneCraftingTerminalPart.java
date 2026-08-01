package thaumicenergistics.common.parts;

import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import appeng.api.inventories.InternalInventory;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.core.AppEng;
import appeng.items.parts.PartModels;
import appeng.menu.locator.MenuLocators;
import appeng.menu.MenuOpener;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.init.ModMenuTypes;

public class ArcaneCraftingTerminalPart extends AbstractTerminalPart {

    public static final ResourceLocation INV_CRAFTING = AppEng.makeId("arcane_crafting_terminal_crafting");
    public static final ResourceLocation INV_WAND = ThaumicEnergistics.id("arcane_crafting_terminal_wand");

    @PartModels
    public static final ResourceLocation RL_BASE = ThaumicEnergistics.id("parts/arcane_crafting_terminal_base");
    @PartModels
    public static final ResourceLocation RL_OFF = ThaumicEnergistics.id("parts/arcane_crafting_terminal_off");
    @PartModels
    public static final ResourceLocation RL_HAS_CHANNEL = ThaumicEnergistics.id("parts/arcane_crafting_terminal_has_channel");
    @PartModels
    public static final ResourceLocation RL_ON = ThaumicEnergistics.id("parts/arcane_crafting_terminal_on");

    public static final IPartModel MODELS_OFF = new PartModel(RL_BASE, RL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(RL_BASE, RL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(RL_BASE, RL_ON, MODEL_STATUS_HAS_CHANNEL);

    public static final int WAND_SLOT_INDEX = 0;

    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, 9);
    private final AppEngInternalInventory wandInv = new AppEngInternalInventory(this, 1);

    public ArcaneCraftingTerminalPart(IPartItem<?> partItem) {
        super(partItem);
        this.getMainNode().setIdlePowerUsage(0.5);
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.onUseWithoutItem(player, pos) && !player.level().isClientSide) {
            MenuOpener.open(getMenuType(player), player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get();
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (var is : this.craftingGrid) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
        for (var is : this.wandInv) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        craftingGrid.clear();
        wandInv.clear();
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.craftingGrid.readFromNBT(data, "craftingGrid", registries);
        this.wandInv.readFromNBT(data, "wandInv", registries);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        this.craftingGrid.writeToNBT(data, "craftingGrid", registries);
        this.wandInv.writeToNBT(data, "wandInv", registries);
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(INV_CRAFTING)) {
            return craftingGrid;
        } else if (id.equals(INV_WAND)) {
            return wandInv;
        } else {
            return super.getSubInventory(id);
        }
    }
}
