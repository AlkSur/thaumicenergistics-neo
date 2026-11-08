package thaumicenergistics.integration.jei;

import appeng.api.stacks.GenericStack;
import appeng.core.network.serverbound.InventoryActionPacket;
import appeng.helpers.InventoryAction;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.menu.slot.FakeSlot;
import appeng.parts.encoding.EncodingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.integration.jei.JeiInfusionRecipe;
import thaumcraft.integration.jei.ThaumcraftJeiPlugin;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.TCReflection;

/** Transfers ghost templates through AE2's mode action and checked filter packets. */
final class InfusionPatternTransfer implements IRecipeTransferHandler<PatternEncodingTermMenu, JeiInfusionRecipe> {
    private final IRecipeTransferHandlerHelper helper;
    private final Class<? extends PatternEncodingTermMenu> menuClass;

    static void register(IRecipeTransferRegistration registration) {
        registerForMenu(registration, PatternEncodingTermMenu.class);
        if (ModList.get().isLoaded("ae2wtlib")) {
            try {
                // JEI indexes handlers by concrete menu class. WUT pattern mode uses WETMenu too.
                var wireless = Class.forName("de.mari_023.ae2wtlib.wet.WETMenu")
                        .asSubclass(PatternEncodingTermMenu.class);
                registerForMenu(registration, wireless);
            } catch (ClassNotFoundException | ClassCastException failure) {
                ThaumicEnergistics.LOG.error("Cannot register wireless infusion-pattern transfer", failure);
            }
        }
    }

    private static void registerForMenu(IRecipeTransferRegistration registration,
            Class<? extends PatternEncodingTermMenu> menuClass) {
        registration.addRecipeTransferHandler(
                new InfusionPatternTransfer(registration.getTransferHelper(), menuClass), ThaumcraftJeiPlugin.INFUSION);
    }

    private InfusionPatternTransfer(IRecipeTransferHandlerHelper helper,
            Class<? extends PatternEncodingTermMenu> menuClass) {
        this.helper = helper;
        this.menuClass = menuClass;
    }

    @Override public Class<? extends PatternEncodingTermMenu> getContainerClass() { return menuClass; }
    @Override public Optional<MenuType<PatternEncodingTermMenu>> getMenuType() { return Optional.empty(); }
    @Override public RecipeType<JeiInfusionRecipe> getRecipeType() { return ThaumcraftJeiPlugin.INFUSION; }

    @Override
    public IRecipeTransferError transferRecipe(PatternEncodingTermMenu menu, JeiInfusionRecipe recipe,
            IRecipeSlotsView display, Player player, boolean maxTransfer, boolean doTransfer) {
        try {
            var inputViews = display.getSlotViews(RecipeIngredientRole.INPUT);
            // TC port .261 creates the center first, then at most 12 visible peripherals.
            // Remaining peripherals are invisible JEI ingredients; read them from the recipe,
            // not from flattened alternatives, which lose each ingredient's multiplicity.
            int visibleComponents = Math.min(12, recipe.components().size());
            if (inputViews.size() != visibleComponents + 1)
                throw new IllegalArgumentException("Unexpected infusion ingredient layout");
            var core = inputViews.getFirst().getDisplayedItemStack().orElseThrow();
            requireCandidate(core, recipe.centralInputs());
            var inputs = new ArrayList<GenericStack>();
            inputs.add(GenericStack.fromItemStack(core));
            var peripherals = new ArrayList<GenericStack>();
            for (int i = 0; i < recipe.components().size(); i++) {
                var candidates = recipe.components().get(i);
                ItemStack selected = i < visibleComponents
                        ? inputViews.get(i + 1).getDisplayedItemStack().orElseThrow()
                        : candidates.stream().filter(stack -> !stack.isEmpty()).findFirst().orElseThrow();
                requireCandidate(selected, candidates);
                merge(peripherals, GenericStack.fromItemStack(selected));
            }
            // The core stays input zero even when a peripheral uses the exact same item.
            inputs.addAll(peripherals);
            if (recipe.aspects() == null) throw new IllegalArgumentException("Missing infusion aspects");
            for (var aspect : recipe.aspects().getAspects()) {
                var id = TCReflection.getAspectId(aspect);
                int amount = recipe.aspects().getAmount(aspect);
                if (id == null || amount <= 0) throw new IllegalArgumentException("Invalid infusion aspect");
                inputs.add(new GenericStack(AEssentiaKey.of(id), amount));
            }
            var output = recipe.output();
            if (output.isEmpty()) throw new IllegalArgumentException("Missing infusion result");
            var outputs = List.of(GenericStack.fromItemStack(output));
            if (inputs.size() > menu.getProcessingInputSlots().length
                    || outputs.size() > menu.getProcessingOutputSlots().length)
                return helper.createUserErrorWithTooltip(
                        Component.translatable("message.thaumicenergistics.jei_processing_too_large"));
            // Finish validating before changing mode or writing any ghost slots.
            if (inputs.stream().anyMatch(stack -> stack == null || stack.amount() <= 0))
                throw new IllegalArgumentException("Invalid infusion input count");
            if (doTransfer) {
                menu.setMode(EncodingMode.PROCESSING);
                writeSlots(menu.getProcessingInputSlots(), inputs);
                writeSlots(menu.getProcessingOutputSlots(), outputs);
            }
            return null;
        } catch (RuntimeException failure) {
            if (doTransfer) ThaumicEnergistics.LOG.warn("Cannot read infusion recipe for pattern transfer", failure);
            return helper.createUserErrorWithTooltip(
                    Component.translatable("message.thaumicenergistics.jei_processing_unavailable"));
        }
    }

    private static void requireCandidate(ItemStack selected, List<ItemStack> candidates) {
        if (selected.isEmpty() || candidates.stream().noneMatch(candidate -> ItemStack.matches(selected, candidate)))
            throw new IllegalArgumentException("Missing or mismatched infusion ingredient");
    }

    private static void merge(List<GenericStack> stacks, GenericStack next) {
        if (next == null || next.amount() <= 0) throw new IllegalArgumentException("Invalid peripheral ingredient");
        for (int i = 0; i < stacks.size(); i++) {
            var previous = stacks.get(i);
            if (previous.what().equals(next.what())) {
                stacks.set(i, new GenericStack(previous.what(), Math.addExact(previous.amount(), next.amount())));
                return;
            }
        }
        stacks.add(next);
    }

    private static void writeSlots(FakeSlot[] slots, List<GenericStack> templates) {
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = i < templates.size() ? GenericStack.wrapInItemStack(templates.get(i)) : ItemStack.EMPTY;
            PacketDistributor.sendToServer(new InventoryActionPacket(InventoryAction.SET_FILTER, slots[i].index, stack));
        }
    }
}
