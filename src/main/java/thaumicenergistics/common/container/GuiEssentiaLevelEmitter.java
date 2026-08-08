package thaumicenergistics.common.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.api.aspects.Aspect;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import thaumicenergistics.ThaumicEnergistics;

/**
 * 源质发信器 Screen — 与 AE2 原版 StorageLevelEmitterScreen 逻辑一致。
 */
public class GuiEssentiaLevelEmitter extends UpgradeableScreen<ContainerEssentiaLevelEmitter> {

    private static final ResourceLocation ASPECT_SLOT_BG =
            ThaumicEnergistics.id("textures/gui/essentia_terminal.png");

    private final SettingToggleButton<RedstoneMode> redstoneMode;
    private final NumberEntryWidget level;
    private long lastReportingValue;

    private static final int ASPECT_SLOT_X = 137;
    private static final int ASPECT_SLOT_Y = 40;

    public GuiEssentiaLevelEmitter(ContainerEssentiaLevelEmitter menu, Inventory playerInventory,
                                   Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.redstoneMode = new ServerSettingToggleButton<>(
                Settings.REDSTONE_EMITTER, RedstoneMode.HIGH_SIGNAL);
        this.addToLeftToolbar(this.redstoneMode);

        this.level = widgets.addNumberEntryWidget("level", NumberEntryType.of(menu.getConfiguredKey()));
        this.level.setTextFieldStyle(style.getWidget("levelInput"));
        this.level.setLongValue(this.menu.reportingValue);
        this.level.setOnChange(this::saveReportingValue);
        this.level.setOnConfirm(this::onClose);
        this.lastReportingValue = this.menu.reportingValue;
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();

        // 更新数字输入类型（筛选可能已改变）
        this.level.setType(NumberEntryType.of(menu.getConfiguredKey()));
        this.level.setActive(true);

        if (this.lastReportingValue != this.menu.reportingValue) {
            this.lastReportingValue = this.menu.reportingValue;
            this.level.setLongValue(this.menu.reportingValue);
        }

        this.redstoneMode.set(this.menu.getRedStoneMode());
        this.redstoneMode.setVisibility(true);
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);

        Aspect aspect = this.menu.getTrackedAspect();
        if (aspect != null) {
            guiGraphics.drawString(this.font, aspect.getName(),
                    ASPECT_SLOT_X + 20, ASPECT_SLOT_Y + 4, 0xFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 右键点击筛选槽清零 reportingValue
        if (isPointInRegion(ASPECT_SLOT_X, ASPECT_SLOT_Y, 16, 16, mouseX, mouseY)) {
            if (button == 1) {
                this.menu.setReportingValue(0);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void saveReportingValue() {
        this.level.getLongValue().ifPresent(menu::setReportingValue);
    }

    private boolean isPointInRegion(int x, int y, int w, int h, double mx, double my) {
        int left = this.leftPos + x;
        int top = this.topPos + y;
        return mx >= left && mx < left + w && my >= top && my < top + h;
    }
}
