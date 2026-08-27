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

    /** 保存/删除样板按钮材质：常态 (0,0,32,13)，悬停/按下 (0,15,32,12)。 */
    private static final ResourceLocation BUTTON_TEX = ResourceLocation.fromNamespaceAndPath(
            ThaumicEnergistics.MODID, "textures/gui/button.png");

    // 主区宽度 175（中轴线 87.5，主区对称居中）；知识核心槽在右侧单独渲染（x=176~209 边栏），不计入主区宽度。
    private static final int GUI_WIDTH = 175;
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

        // 保存/删除样板按钮：放预览产物槽（结果槽 116,108）上方，等比 32×13。
        // 按钮中心与结果槽中心（x=125）关于同一 y 轴对称 → 按钮 x = 125-16 = 109。
        // 材质 button.png：常态 (0,0,32,13)，悬停/按下 (0,15,32,12)。
        saveButton = new Button(
                left + 109, top + 90, 32, 13, Component.empty(),
                btn -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0),
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
                    var font = net.minecraft.client.Minecraft.getInstance().font;
                    int tw = font.width(s);
                    g.drawString(font, s,
                            this.getX() + (this.width - tw) / 2,
                            this.getY() + (this.height - 8) / 2,
                            0xFFFFFF, false);
                }
            }
        };
        addRenderableWidget(saveButton);
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
        // 主区（0~175）对称居中
        g.blit(TEX, x, y, 0, 0, GUI_WIDTH, imageHeight);
        // 右侧知识核心槽边栏（x=176~209）单独渲染，不占主区宽度
        g.blit(TEX, x + 176, y, 176, 0, 34, imageHeight);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
