package thaumicenergistics.common.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.tiles.TileArcaneAssembler;

/**
 * 奥术装配器 GUI。
 *
 * 贴图 arcane_assembler.png = 256×256 画布，包含4块不连续区域：
 *   第1块 主矩形：(0,0) → 176×197
 *   第2块 右上不规则：(179,0) → 上部32w(0~15y)，下部55w(16~103y)
 *   第3块 右下护甲区：(180,129) → 68×68
 *   第4块 底部7个Vis条：每个 4×16 像素，起始(42,202)，步长18（42-45, 60-63, 78-81...）
 *
 * 仿1.7.10纹理裁剪方式：GUI分块blit，bar区域不随纹理整体绘制，
 * 而是先画暗色背景，再从纹理Y=202处裁剪满bar按百分比叠加。
 */
public class GuiArcaneAssembler extends AbstractContainerScreen<ContainerArcaneAssembler> {

    static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(
            ThaumicEnergistics.MODID, "textures/gui/arcane_assembler.png");

    // ===== 第4块 Vis条 =====
    private static final int VIS_BAR_X     = 42;
    private static final int VIS_BAR_Y     = 202;
    private static final int VIS_BAR_W     = 4;
    private static final int VIS_BAR_H     = 16;
    private static final int VIS_BAR_STEP  = 18;

    // ===== 6原质 =====
    private static final Aspect[] VIS_ASPECTS = {
            Aspect.AIR, Aspect.FIRE, Aspect.WATER,
            Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY
    };

    public GuiArcaneAssembler(ContainerArcaneAssembler menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = 254;
        this.imageHeight = 226;
        this.inventoryLabelY = 115 - 12;
        this.titleLabelY     = 5;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mx, int my) {
        int left = (this.width  - this.imageWidth)  / 2;
        int top  = (this.height - this.imageHeight) / 2;

        // ===== 分块绘制纹理（仿1.7.10，bar区域不参与blit） =====

        // 第1块：主矩形 176×197
        g.blit(TEX, left, top, 0, 0, 176, 197);

        // 第2块：右上不规则区域（上部 32×16 + 下部 55×88）
        g.blit(TEX, left + 179, top, 179, 0, 32, 16);
        g.blit(TEX, left + 179, top + 16, 179, 16, 55, 88);

        // 第3块：右下正方形 68×68
        g.blit(TEX, left + 180, top + 129, 180, 129, 68, 68);

        // ===== 第4块：Vis条（纹理裁剪方式，仿1.7.10 drawVisBar） =====
        TileArcaneAssembler tile = this.menu.assembler;
        final int maxCvis = 187 * 10;
        for (int i = 0; i < 7; i++) {
            int bx = left + VIS_BAR_X + i * VIS_BAR_STEP;
            int by = top  + VIS_BAR_Y;

            float ratio = 0f;
            if (tile != null) {
                if (i < 6) {
                    int cvis = tile.getStoredVis() != null ? tile.getStoredVis().getAmount(VIS_ASPECTS[i]) : 0;
                    ratio = Math.min(1f, (float) cvis / maxCvis);
                } else {
                    ratio = tile.getCraftProgress();
                }
            }

            // 空bar暗色背景（满bar不在此处，由纹理裁剪提供）
            g.fill(bx, by, bx + VIS_BAR_W, by + VIS_BAR_H, 0x77222222);

            // 从纹理Y=202处裁剪满bar，按高度百分比叠加
            int fillHeight = Math.round(VIS_BAR_H * ratio);
            if (fillHeight > 0) {
                int srcX = VIS_BAR_X + i * VIS_BAR_STEP;
                int srcY = VIS_BAR_Y + (VIS_BAR_H - fillHeight);
                g.blit(TEX, bx, by + (VIS_BAR_H - fillHeight), srcX, srcY, VIS_BAR_W, fillHeight);
            }

            // bar边框
            g.fill(bx - 1, by - 1, bx, by + VIS_BAR_H + 1, 0xAA000000);
            g.fill(bx + VIS_BAR_W, by - 1, bx + VIS_BAR_W + 1, by + VIS_BAR_H + 1, 0xAA000000);
            g.fill(bx - 1, by - 1, bx + VIS_BAR_W + 1, by, 0xAA000000);
            g.fill(bx - 1, by + VIS_BAR_H, bx + VIS_BAR_W + 1, by + VIS_BAR_H + 1, 0xAA000000);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
    }
}