package thaumicenergistics.common.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.client.gui.AspectGuiRenderer;
import thaumicenergistics.ThaumicEnergistics;

public class GuiDistillationPatternEncoder extends AbstractContainerScreen<ContainerDistillationPatternEncoder> {

    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(
            ThaumicEnergistics.MODID, "textures/gui/distillation_encoder.png");

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 234;

    /** Encode 按钮引用，按配置状态动态启用/禁用。 */
    private Button encodeButton;

    public GuiDistillationPatternEncoder(ContainerDistillationPatternEncoder menu, Inventory inv, Component title) {
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

        // Encode 按钮：对齐 1.7.10（146,94），位于 encoded 槽上方，不覆盖 selected 槽 (116,69)；
        // 仅当配置完成（源物品+选中要素+空白样板就绪）时可点击
        encodeButton = Button.builder(Component.literal("Encode"), btn -> {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
        }).pos(left + 140, top + 94).size(34, 16).build();
        addRenderableWidget(encodeButton);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        g.blit(TEX, x, y, 0, 0, imageWidth, imageHeight);

        // 绘制源物品的 6 个 aspect 图标（aspect 槽 (65,24) 起，每格 18px）；实时缓存解析（源物品变化才调 TC）
        Aspect[] aspects = menu.getSourceAspects();
        for (int i = 0; i < 6; i++) {
            if (i < aspects.length && aspects[i] != null) {
                AspectGuiRenderer.draw(g, aspects[i], x + 65, y + 24 + i * 18, 16, 1.0f);
            }
        }

        // 源物品非空但解析不到要素（未扫描）→ 提示
        if (!menu.getSourceItem().isEmpty() && aspects.length == 0) {
            g.drawCenteredString(font,
                    Component.translatable("thaumicenergistics.gui.distillation_encoder.not_scanned"),
                    x + 73, y + 78, 0xFFAAAAAA);
        }

        // 绘制选中的 aspect 图标（selected 槽 (116,69)）
        Aspect selected = menu.getSelectedAspect();
        if (selected != null) {
            AspectGuiRenderer.draw(g, selected, x + 116, y + 69, 16, 1.0f);
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        // Encode 按钮仅在配置完成（源物品+选中要素+空白样板就绪）时可点击
        if (encodeButton != null) {
            encodeButton.active = menu.canEncode();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
