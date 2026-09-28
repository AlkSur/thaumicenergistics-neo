package tc_energy.common.container;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import appeng.api.storage.ITerminalHost;
import appeng.menu.me.common.MEStorageMenu;

/**
 * 源质终端的菜单（有线端与无线端共用）。
 * <p>
 * 继承 AE2 原版 {@link MEStorageMenu} 的全部普通物品行为（左键/Shift+点击/拖拽均按普通物品处理）。
 * 源质的存入与取出统一由右键交互承担：
 * <ul>
 *   <li>手持装有源质的罐子/安瓿 + 右键任意处 → 源质存入网络（{@link EssentiaFillHelper#extractContainerContent}）。</li>
 *   <li>手持空罐/空安瓿 + 右键源质条目 → 从网络取出装罐（{@link #fillContainerFromGrid}）。</li>
 * </ul>
 */
public class EssentiaTerminalMenu extends MEStorageMenu {

    public EssentiaTerminalMenu(MenuType<?> menuType, int id, Inventory ip, ITerminalHost host) {
        super(menuType, id, ip, host);
    }

    /**
     * 右键终端源质条目装罐：从网络提取源质，装入手持的源质罐子或玻璃安瓿。
     * 具体逻辑见 {@link EssentiaFillHelper#fillContainerFromGrid}。
     *
     * @param aspectId 源质 aspect 的 ResourceLocation
     * @return true = 处理成功（装罐/装安瓿），false = 无法处理
     */
    public boolean fillContainerFromGrid(ResourceLocation aspectId) {
        if (isClientSide()) {
            return false;
        }
        var player = getPlayerInventory().player;
        var carried = getCarried();
        return EssentiaFillHelper.fillContainerFromGrid(
                this.storage, this.energySource, getActionSource(), player, carried, aspectId);
    }
}
