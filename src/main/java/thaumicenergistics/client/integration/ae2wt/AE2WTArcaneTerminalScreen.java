package thaumicenergistics.client.integration.ae2wt;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;
import de.mari_023.ae2wtlib.api.AE2wtlibAPI;
import de.mari_023.ae2wtlib.api.gui.ScrollingUpgradesPanel;
import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import thaumicenergistics.common.container.ContainerArcaneCraftingTerminal;
import thaumicenergistics.common.container.GuiArcaneCraftingTerminal;
import org.anti_ad.mc.ipn.api.IPNIgnore;

/** 通用终端切换入口属于屏幕 API；仅接入物品和宿主不会自动创建切换按钮。 */
@IPNIgnore
public final class AE2WTArcaneTerminalScreen extends GuiArcaneCraftingTerminal
        implements IUniversalTerminalCapable {
    /** AE2WT 官方滚动升级面板（奇点居首、量子桥卡门控显隐，槽多经滚动条翻页）。 */
    private ScrollingUpgradesPanel upgradesPanel;

    public AE2WTArcaneTerminalScreen(ContainerArcaneCraftingTerminal menu, Inventory inventory,
            Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        // 官方滚动升级面板接管右侧卡槽（普通 UpgradesPanel 经无线屏 JSON 挪出屏外防双面板
        // 抢位）；槽源 = SINGULARITY + UPGRADE 两组拼接。WUT 合并多终端后升级库存扩容，
        // 普通面板纵向溢出的「插卡槽乱」由本面板的滚动窗口解决。
        upgradesPanel = addUpgradePanel(widgets, getMenu());
        if (AE2wtlibAPI.isUniversalTerminal(getHost().getItemStack())) {
            addToLeftToolbar(cycleTerminalButton());
        }
    }

    @Override
    public void init() {
        super.init();
        // 固定四行可视窗口（含奇点格）；量子桥卡未装且奇点为空时官方逻辑自动隐藏奇点格
        // （singularitySlotHidden），滚动条范围由面板内部随可见格数自适应。
        upgradesPanel.setMaxRows(4);
    }

    @Override
    public WTMenuHost getHost() {
        return (WTMenuHost) getMenu().getHost();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return checkForTerminalKeys(keyCode, scanCode)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }
}
