package thaumicenergistics.common.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics.ThaumicEnergistics;
import net.minecraft.client.Minecraft;

/**
 * 知识记录仪 GUI。
 * 从 1.7.10 GuiKnowledgeInscriber 移植。
 */
public class GuiKnowledgeInscriber extends AbstractContainerScreen<ContainerKnowledgeInscriber> {

    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(
            ThaumicEnergistics.MODID, "textures/gui/knowledge_inscriber.png");

    /** 保存/删除样板按钮材质：常态 (0,0,32,13)，悬停/按下 (0,15,32,12)。 */
    private static final ResourceLocation BUTTON_TEX = ResourceLocation.fromNamespaceAndPath(
            ThaumicEnergistics.MODID, "textures/gui/button.png");

    // All slots now fit inside the 176-pixel main panel, including the knowledge core.
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 244;

    private Button saveButton;

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

        // Center the status/save/delete button above the knowledge core slot (145 + 8).
        saveButton = new Button(
                left + 137, top + 103, 32, 13, Component.empty(),
                btn -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ContainerKnowledgeInscriber.BUTTON_SAVE_DELETE),
                b -> Component.empty()) {
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                // 仅鼠标悬停/按下显示第二帧；用 isHovered() 而非 isHoveredOrFocused()——
                // 后者在按钮获得键盘焦点（点击后）时也 true，会导致写入配方后一直保持蓝色
                int v = this.isHovered() ? 15 : 0;
                g.blit(BUTTON_TEX, this.getX(), this.getY(), 0, v, this.width, this.height, 32, 32);
                // 按钮文字居中
                String s = this.getMessage().getString();
                if (!s.isEmpty()) {
                    var font = Minecraft.getInstance().font;
                    int tw = font.width(s);
                    g.drawString(font, s,
                            this.getX() + (this.width - tw) / 2,
                            this.getY() + (this.height - 8) / 2,
                            0xFFFFFF, false);
                }
            }
        };
        addRenderableWidget(saveButton);

        // The 8x8 cross is already part of the supplied texture; keep its exact appearance.
        Button clearButton = new Button(left + 71, top + 89, 8, 8,
                Component.translatable("thaumicenergistics.gui.knowledge_inscriber.clear_grid"), btn -> {
                    menu.onClearGrid();
                    saveState = menu.getCurrentSaveState();
                    updateSaveButton();
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                            ContainerKnowledgeInscriber.BUTTON_CLEAR_GRID);
                }, narration -> narration.get()) {
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                if (isHoveredOrFocused()) {
                    g.fill(getX(), getY(), getX() + width, getY() + height, 0x40FFFFFF);
                }
            }
        };
        clearButton.setTooltip(Tooltip.create(Component.translatable("thaumicenergistics.gui.knowledge_inscriber.clear_grid")));
        addRenderableWidget(clearButton);
        // 立即设置按钮文字/激活（F11 全屏切换会重建按钮，init 里必须重设，否则文字丢失）
        updateSaveButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        saveState = menu.getCurrentSaveState();
        // 每 tick 重设（而非仅状态变化时）：F11 resize 重建按钮后也能恢复文字/激活
        updateSaveButton();
    }

    private void updateSaveButton() {
        switch (saveState) {
            case Disabled_MissingCore:
                saveButton.active = false;
                saveButton.setMessage(Component.translatable("thaumicenergistics.gui.knowledge_inscriber.no_core"));
                break;
            case Disabled_InvalidRecipe:
                saveButton.active = false;
                saveButton.setMessage(Component.translatable("thaumicenergistics.gui.knowledge_inscriber.invalid"));
                break;
            case Disabled_CoreFull:
                saveButton.active = false;
                saveButton.setMessage(Component.translatable("thaumicenergistics.gui.knowledge_inscriber.full"));
                break;
            case Enabled_Save:
                saveButton.active = true;
                saveButton.setMessage(Component.translatable("thaumicenergistics.gui.knowledge_inscriber.save"));
                break;
            case Enabled_Delete:
                saveButton.active = true;
                saveButton.setMessage(Component.translatable("thaumicenergistics.gui.knowledge_inscriber.delete"));
                break;
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        g.blit(TEX, x, y, 0, 0, GUI_WIDTH, imageHeight);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
