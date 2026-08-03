package thaumicenergistics.common.container;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import appeng.api.client.AEKeyRendering;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.network.CellWorkbenchC2SPacket;

/**
 * 源质元件工作台 GUI：显示 63 个分区格（9×7），支持点击添加/移除/替换源质分区，两个分区按钮。
 * 对应 1.7.10 的 {@code GuiEssentiaCellWorkbench}。
 */
public class GuiEssentiaCellWorkbench extends AbstractContainerScreen<ContainerEssentiaCellWorkbench> {

    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "textures/gui/essentia_cell_workbench.png");
    private static final int GUI_WIDTH = 176, GUI_HEIGHT = 251;
    /** 分区格布局常量（JEI ghost 拖拽也使用，故公开）。 */
    public static final int WIDGET_POS_X = 8, WIDGET_POS_Y = 29;
    public static final int WIDGETS_PER_ROW = 9, WIDGET_ROWS = 7, NUMBER_OF_WIDGETS = 63;
    public static final int WIDGET_SIZE = 18;

    /** 当前显示的分区列表（大小 63，null = 空分区格）。由 S2C 同步更新。 */
    private final List<ResourceLocation> partitionSlots = new ArrayList<>(Collections.nCopies(NUMBER_OF_WIDGETS, null));

    /** 上次检测到的 cell 槽内容，用于在放入/拿走 cell 时触发分区刷新。 */
    private ItemStack lastCellStack = ItemStack.EMPTY;

    public GuiEssentiaCellWorkbench(ContainerEssentiaCellWorkbench menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        // 分区为当前（P）与清除分区（C）按钮，位置对应 1.7.10（GUI 左外侧）
        addRenderableWidget(Button.builder(Component.literal("P"), b -> CellWorkbenchC2SPacket.sendPartitionToContents())
                .bounds(leftPos - 18, topPos + 8, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("C"), b -> CellWorkbenchC2SPacket.sendClear())
                .bounds(leftPos - 18, topPos + 28, 18, 18).build());
        // 打开时请求服务端分区列表
        CellWorkbenchC2SPacket.sendRequestPartitions();
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = (width - imageWidth) / 2, y = (height - imageHeight) / 2;
        g.blit(TEX, x, y, 0, 0, imageWidth, imageHeight);

        for (int i = 0; i < NUMBER_OF_WIDGETS; i++) {
            ResourceLocation id = partitionSlots.get(i);
            if (id != null) {
                int col = i % WIDGETS_PER_ROW, row = i / WIDGETS_PER_ROW;
                AEKeyRendering.drawInGui(minecraft, g,
                        x + WIDGET_POS_X + col * WIDGET_SIZE,
                        y + WIDGET_POS_Y + row * WIDGET_SIZE,
                        AEssentiaKey.of(id));
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (int i = 0; i < NUMBER_OF_WIDGETS; i++) {
            int col = i % WIDGETS_PER_ROW, row = i / WIDGETS_PER_ROW;
            int wx = leftPos + WIDGET_POS_X + col * WIDGET_SIZE;
            int wy = topPos + WIDGET_POS_Y + row * WIDGET_SIZE;
            if (mx >= wx && mx < wx + WIDGET_SIZE && my >= wy && my < wy + WIDGET_SIZE) {
                handlePartitionClick(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    /**
     * 点击分区格：空 + 手持含源质物品 → 添加；有源质 + 空手 → 移除；有源质 + 不同源质 → 替换。
     */
    private void handlePartitionClick(int index) {
        ResourceLocation current = index < partitionSlots.size() ? partitionSlots.get(index) : null;
        ResourceLocation held = ContainerEssentiaCellWorkbench.getAspectFromItem(menu.getCarried());
        if (current == null && held != null) {
            CellWorkbenchC2SPacket.sendAddAspect(held);
        } else if (current != null && held == null) {
            CellWorkbenchC2SPacket.sendRemoveAspect(current);
        } else if (current != null && held != null && !current.equals(held)) {
            CellWorkbenchC2SPacket.sendReplaceAspect(current, held);
        }
    }

    /**
     * 服务端同步分区列表（S2C handler 调用）。
     */
    public void updatePartitions(List<ResourceLocation> list) {
        partitionSlots.clear();
        if (list.size() > NUMBER_OF_WIDGETS) {
            partitionSlots.addAll(list.subList(0, NUMBER_OF_WIDGETS));
        } else {
            partitionSlots.addAll(list);
        }
        for (int i = partitionSlots.size(); i < NUMBER_OF_WIDGETS; i++) {
            partitionSlots.add(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        // 检测 cell 槽变化：放入已配置 cell → 重新请求分区；拿走 cell → 立即清空显示
        ItemStack cell = menu.slots.get(0).getItem();
        if (!ItemStack.matches(cell, lastCellStack)) {
            lastCellStack = cell.copy();
            if (cell.isEmpty()) {
                updatePartitions(List.of());
            } else {
                CellWorkbenchC2SPacket.sendRequestPartitions();
            }
        }
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }
}
