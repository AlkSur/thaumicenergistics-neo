package thaumicenergistics.common.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import appeng.client.gui.Icon;
import thaumicenergistics.ThaumicEnergistics;
import org.anti_ad.mc.ipn.api.IPNIgnore;

/**
 * 奥术装配器 GUI。标 @IPNIgnore：IPN 一键整理完全跳过此界面（不渲染排序按钮、不执行整理）。
 *
 * 新版贴图 arcane_assembler.png = 256×256 整张背景。
 * 进度条填充材质在纹理 y=240（7 条 4×16，x 步长 5：0,5,10,15,20,25,30）。
 */
@IPNIgnore
public class GuiArcaneAssembler extends AbstractContainerScreen<ContainerArcaneAssembler> {

    static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(
            ThaumicEnergistics.MODID, "textures/gui/arcane_assembler.png");

    private static final int BAR_W = 4;
    private static final int BAR_H = 16;
    /** 进度条填充材质纹理 y。 */
    private static final int BAR_FILL_Y = 240;

    /** 7 条进度条：{填充材质 x, 目标 x, 目标 y}，顺序 风/水/火/秩序/混沌/地/合成。 */
    private static final int[][] BARS = {
            { 0, 8, 81 },   // 风 AIR
            { 5, 8, 99 },   // 水 WATER
            { 10, 8, 117 }, // 火 FIRE
            { 15, 15, 81 }, // 秩序 ORDER
            { 20, 15, 99 }, // 混沌 ENTROPY
            { 25, 15, 117 },// 地 EARTH
            { 30, 141, 99 } // 合成条
    };

    /** 配方预览 3×3 起点（16×16 格，步距 18）。 */
    private static final int PREVIEW_X = 26;
    private static final int PREVIEW_Y = 81;

    public GuiArcaneAssembler(ContainerArcaneAssembler menu, Inventory inv, Component title) {
        super(menu, inv, title);
        // 主区按 0~175 取中轴线 87.5 居中（imageWidth=175，MC 自动按 175 居中）；
        // 右侧 176~196 作为附加（右上角块），blit 整图时自然延伸，不占主区宽度。
        this.imageWidth = 175;
        this.imageHeight = 231;
        // 物品栏标题下移（136→137）
        this.inventoryLabelY = 137;
        // 奥术装配器标题上移（4→3）
        this.titleLabelY = 3;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTicks, int mx, int my) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;

        // 整图渲染（0~196，含右侧右上角附加），主区 0~175 已居中
        g.blit(TEX, left, top, 0, 0, 196, imageHeight);

        // ===== 空槽图标：加速卡（4 格） =====
        for (int i = 0; i < 4; i++) {
            Slot slot = this.menu.getUpgradeSlot(i);
            if (!slot.hasItem()) {
                Icon.BACKGROUND_UPGRADE.getBlitter().dest(left + slot.x, top + slot.y).blit(g);
            }
        }

        // ===== 配方预览：3×3 材料 + 预产物（数据由 AssemblerPreviewSyncPacket 同步到菜单） =====
        if (this.menu.clientPreviewActive) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = this.menu.clientPreviewGrid[i];
                if (stack == null || stack.isEmpty()) continue;
                int col = i % 3, row = i / 3;
                g.renderItem(stack, left + PREVIEW_X + col * 18, top + PREVIEW_Y + row * 18);
            }
            // 预产物：16×16 居中于 21×21 大格子（113,96 ~ 134,117）
            ItemStack result = this.menu.clientPreviewResult;
            if (!result.isEmpty()) {
                g.renderItem(result, left + 115, top + 98);
            }
        }

        // ===== 进度条：6 要素 + 1 合成条（填充材质 y=240，按比例从底部向上裁剪） =====
        ContainerData data = this.menu.visData;
        final int maxCvis = 187 * 10;
        for (int i = 0; i < BARS.length; i++) {
            int srcX = BARS[i][0];
            int bx = left + BARS[i][1];
            int by = top + BARS[i][2];

            float ratio;
            if (i < 6) {
                int cvis = data.get(ContainerArcaneAssembler.DATA_AIR + i);
                ratio = Math.min(1f, (float) cvis / maxCvis);
            } else {
                if (data.get(ContainerArcaneAssembler.DATA_CRAFTING) == 1) {
                    int tick = data.get(ContainerArcaneAssembler.DATA_CRAFT_TICK);
                    int total = Math.max(1, data.get(ContainerArcaneAssembler.DATA_TICKS_PER_CRAFT));
                    ratio = Math.min(1f, (float) tick / total);
                } else {
                    boolean anyVis = false;
                    for (int a = 0; a < 6; a++) {
                        if (data.get(ContainerArcaneAssembler.DATA_AIR + a) > 0) { anyVis = true; break; }
                    }
                    ratio = anyVis ? 1f : 0f;
                }
            }

            int fillHeight = Math.round(BAR_H * ratio);
            if (fillHeight > 0) {
                int srcY = BAR_FILL_Y + (BAR_H - fillHeight);
                int dstY = by + (BAR_H - fillHeight);
                g.blit(TEX, bx, dstY, srcX, srcY, BAR_W, fillHeight);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
        this.renderTooltip(g, mx, my);
    }
}
