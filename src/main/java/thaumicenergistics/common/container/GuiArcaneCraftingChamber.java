package thaumicenergistics.common.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics.ThaumicEnergistics;

public class GuiArcaneCraftingChamber extends AbstractContainerScreen<ContainerArcaneCraftingChamber> {
    private final ResourceLocation backgroundTexture;

    public GuiArcaneCraftingChamber(ContainerArcaneCraftingChamber menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        String texture = switch (menu.rows()) {
            case 2 -> "textures/gui/arcane_crafting_chamber_l6.png";
            case 3 -> "textures/gui/arcane_crafting_chamber_l9.png";
            default -> "textures/gui/arcane_crafting_chamber_l4.png";
        };
        this.backgroundTexture = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, texture);
        this.imageWidth = 176;
        this.imageHeight = menu.playerInventoryTop() - 14 + 96;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(backgroundTexture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        int top = menu.rows() * 18 + 17 + 5;
        g.drawString(font, statusLine(), 10, top, 0xFFFFFF, false);
        g.drawString(font, visLine(), 10, top + 10, 0xE0E0E0, false);
    }

    private Component statusLine() {
        var d = menu.data;
        if (d.get(ContainerArcaneCraftingChamber.DATA_ONLINE) == 0) {
            return Component.translatable("gui.thaumicenergistics.chamber.offline");
        }
        if (d.get(ContainerArcaneCraftingChamber.DATA_CRAFTING) != 0) {
            return Component.translatable("gui.thaumicenergistics.chamber.crafting",
                    d.get(ContainerArcaneCraftingChamber.DATA_BATCH), d.get(ContainerArcaneCraftingChamber.DATA_PROGRESS));
        }
        return Component.translatable("gui.thaumicenergistics.chamber.idle", d.get(ContainerArcaneCraftingChamber.DATA_CAPACITY));
    }

    private Component visLine() {
        var d = menu.data;
        int wait = d.get(ContainerArcaneCraftingChamber.DATA_WAIT_ASPECT);
        if (wait > 0 && wait <= ContainerArcaneCraftingChamber.PRIMALS.length) {
            var aspect = ContainerArcaneCraftingChamber.PRIMALS[wait - 1];
            return Component.translatable("gui.thaumicenergistics.chamber.waiting",
                    aspect.displayName(), d.get(ContainerArcaneCraftingChamber.DATA_WAIT_HAVE),
                    d.get(ContainerArcaneCraftingChamber.DATA_WAIT_NEED));
        }
        return Component.translatable(d.get(ContainerArcaneCraftingChamber.DATA_SOURCE) != 0
                ? "gui.thaumicenergistics.chamber.source_ok" : "gui.thaumicenergistics.chamber.no_source");
    }
}
