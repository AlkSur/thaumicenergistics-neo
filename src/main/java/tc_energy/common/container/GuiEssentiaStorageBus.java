package tc_energy.common.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.api.config.ActionItems;
import appeng.api.config.FuzzyMode;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ActionButton;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import appeng.core.definitions.AEItems;
import appeng.core.localization.GuiText;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.YesNo;
import tc_energy.init.ModItems;

/**
 * 源质存储总线GUI。
 * 使用 AE2 原生 /screens/storage_bus.json（布局/工具栏/优先级按钮/尺寸完全一致），
 * 仅通过 setTextContent("dialog_title", ...) 覆盖标题为"源质存储总线"。
 */
public class GuiEssentiaStorageBus extends UpgradeableScreen<ContainerEssentiaStorageBus> {

    private final SettingToggleButton<AccessRestriction> rwMode;
    private final SettingToggleButton<StorageFilter> storageFilter;
    private final SettingToggleButton<YesNo> filterOnExtract;
    private final SettingToggleButton<FuzzyMode> fuzzyMode;

    public GuiEssentiaStorageBus(ContainerEssentiaStorageBus menu, Inventory playerInventory,
                                 Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        // ✅ 覆盖JSON中的 dialog_title 文本（原本是 gui.ae2.StorageBus → "存储总线"）
        setTextContent("dialog_title", ModItems.ESSENTIA_STORAGE_BUS.get().getDescription());

        // 右上角优先级按钮（AE2原生JSON里定义了 openPriority widget）
        widgets.addOpenPriorityButton();

        // 左侧工具栏：清空 / 分区（始终显示）
        addToLeftToolbar(new ActionButton(ActionItems.CLOSE, btn -> menu.clear()));
        addToLeftToolbar(new ActionButton(ActionItems.COG, btn -> menu.partition()));

        // 存储过滤模式（始终显示）
        this.storageFilter = new ServerSettingToggleButton<>(Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        this.addToLeftToolbar(this.storageFilter);

        // 提取时应用过滤（始终显示）
        this.filterOnExtract = new ServerSettingToggleButton<>(Settings.FILTER_ON_EXTRACT, YesNo.YES);
        this.addToLeftToolbar(this.filterOnExtract);

        // 模糊模式（需要模糊升级卡才显示）
        this.fuzzyMode = new ServerSettingToggleButton<>(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.addToLeftToolbar(this.fuzzyMode);

        // 读写访问模式（始终显示）
        this.rwMode = new ServerSettingToggleButton<>(Settings.ACCESS, AccessRestriction.READ_WRITE);
        this.addToLeftToolbar(this.rwMode);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.storageFilter.set(this.menu.getStorageFilter());
        this.rwMode.set(this.menu.getReadWriteMode());
        this.filterOnExtract.set(this.menu.getFilterOnExtract());
        this.fuzzyMode.set(this.menu.getFuzzyMode());
        this.fuzzyMode.setVisibility(menu.hasUpgrade(AEItems.FUZZY_CARD));
    }

    @Override
    public void drawFG(GuiGraphics guiGraphics, int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(guiGraphics, offsetX, offsetY, mouseX, mouseY);
        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(10, 17, 0);
        poseStack.scale(0.6f, 0.6f, 1);
        int color = style.getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        if (menu.getConnectedTo() != null) {
            guiGraphics.drawString(font, GuiText.AttachedTo.text(menu.getConnectedTo()), 0, 0, color, false);
        } else {
            guiGraphics.drawString(font, GuiText.Unattached.text(), 0, 0, color, false);
        }
        poseStack.popPose();
    }
}
