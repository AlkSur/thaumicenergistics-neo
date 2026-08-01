package thaumicenergistics.common.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics.ThaumicEnergistics;

/**
 * 知识记录仪 GUI。
 * 从 1.7.10 GuiKnowledgeInscriber 移植。
 */
public class GuiKnowledgeInscriber extends AbstractContainerScreen<ContainerKnowledgeInscriber> {

    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(
            ThaumicEnergistics.MODID, "textures/gui/knowledge_inscriber.png");

    private static final int GUI_WIDTH = 210;
    private static final int GUI_HEIGHT = 244;

    private Button saveButton;
    private Button clearButton;

    private ContainerKnowledgeInscriber.CoreSaveState saveState =
            ContainerKnowledgeInscriber.CoreSaveState.Disabled_MissingCore;

    public GuiKnowledgeInscriber(ContainerKnowledgeInscriber menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();

        int left = (width - imageWidth) / 2;
        int top = (height - imageHeight) / 2;

        saveButton = Button.builder(Component.literal(""), btn -> {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
        }).pos(left + 141, top + 109).size(38, 18).build();
        addRenderableWidget(saveButton);

        clearButton = Button.builder(Component.literal("C"), btn -> {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 1);
        }).pos(left + 80, top + 89).size(8, 8).build();
        addRenderableWidget(clearButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ContainerKnowledgeInscriber.CoreSaveState newState = menu.getCurrentSaveState();
        if (newState != saveState) {
            saveState = newState;
            updateSaveButton();
        }
    }

    private void updateSaveButton() {
        switch (saveState) {
            case Disabled_MissingCore:
                saveButton.active = false;
                saveButton.setMessage(Component.literal("No Core"));
                break;
            case Disabled_InvalidRecipe:
                saveButton.active = false;
                saveButton.setMessage(Component.literal("Invalid"));
                break;
            case Disabled_CoreFull:
                saveButton.active = false;
                saveButton.setMessage(Component.literal("Full"));
                break;
            case Enabled_Save:
                saveButton.active = true;
                saveButton.setMessage(Component.literal("Save"));
                break;
            case Enabled_Delete:
                saveButton.active = true;
                saveButton.setMessage(Component.literal("Delete"));
                break;
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        g.blit(TEX, x, y, 0, 0, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
