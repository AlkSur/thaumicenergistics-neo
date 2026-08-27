package thaumicenergistics.mixins.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.me.common.RepoSlot;
import appeng.menu.me.common.MEStorageMenu;

import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics.common.network.EssentiaTerminalDepositC2SPacket;
import thaumicenergistics.common.network.EssentiaTerminalFillC2SPacket;

/**
 * 让 WCWT（ME 综合工作终端）的屏幕支持源质右键交互。
 * <p>
 * WCWT 的 {@code WirelessComprehensiveWorkTerminalScreen} 覆写了 {@code mouseClicked}，
 * 因此注入到 {@code MEStorageScreen} 的 {@code MixinMEStorageScreen} 不会生效——
 * 需直接对 WCWT 屏幕注入。逻辑与 {@code MixinMEStorageScreen} 完全一致：
 * <ul>
 *   <li>Shift+右键玩家背包格（源质容器）→ 存入网络。</li>
 *   <li>右键网络区域手持满容器 → 存入网络。</li>
 *   <li>手持空容器右键源质条目 → 取出装罐。</li>
 * </ul>
 */
@Mixin(targets = "com.lhy.wcwt.client.WirelessComprehensiveWorkTerminalScreen", remap = false)
public abstract class MixinWCWTScreen {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void thE_onMouseClickedEssentia(double xCoord, double yCoord, int btn,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (btn != 1) {
            return;
        }
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        MEStorageMenu menu = (MEStorageMenu) self.getMenu();
        Slot hovered = self.getSlotUnderMouse();
        boolean shiftDown = AbstractContainerScreen.hasShiftDown();
        var carried = menu.getCarried();
        // 源质物品来源：优先菜单 carried（拾取物品）；为空时回退到玩家主手物品
        ItemStack sourceStack = !carried.isEmpty() ? carried
                : menu.getPlayerInventory().player.getMainHandItem();
        boolean isContainer = isSourceContainer(sourceStack);
        boolean containerHasEssentia = isContainer && hasContainerEssentia(sourceStack);

        // Shift+右键玩家物品栏/背包格：格内是源质容器 → 存入源质（空容器留原格）
        if (shiftDown && hovered != null && menu.isPlayerSideSlot(hovered)) {
            var slotStack = hovered.getItem();
            // 仅要素安瓿/要素罐子；天域之华等其余 IEssentiaContainerItem 一律放行
            if (thaumicenergistics.common.integration.tc.ThEEssentiaContainers.isEssentiaContainer(slotStack)) {
                PacketDistributor.sendToServer(EssentiaTerminalDepositC2SPacket.fromSlot(
                        menu.containerId, hovered.getSlotIndex(), slotStack));
                cir.setReturnValue(true);
                return;
            }
            // 非源质容器 → 放行（AE2 原版 Shift+右键快速移动）
            return;
        }

        // 普通右键点击玩家物品栏/背包槽 → 放行（AE2 原版物品交互）
        if (!shiftDown && hovered != null && menu.isPlayerSideSlot(hovered)) {
            return;
        }

        // Shift+右键：手持源质容器（满或空）→ 拦截。满→存入源质；空→阻止被当作普通物品放入网络
        if (shiftDown && isContainer) {
            if (containerHasEssentia) {
                clearSource(menu, sourceStack, carried);
                PacketDistributor.sendToServer(EssentiaTerminalDepositC2SPacket.fromClientStack(
                        menu.containerId, sourceStack));
            }
            cir.setReturnValue(true);
            return;
        }

        // 普通右键网络区域：手持满容器 → 存入源质
        if (containerHasEssentia) {
            clearSource(menu, sourceStack, carried);
            PacketDistributor.sendToServer(EssentiaTerminalDepositC2SPacket.fromClientStack(
                    menu.containerId, sourceStack));
            cir.setReturnValue(true);
            return;
        }

        // 手持空容器：右键源质条目 → 取出装罐；右键网络其他 → 拦截（防被放入网络）
        if (isContainer) {
            if (hovered instanceof RepoSlot repoSlot) {
                var entry = repoSlot.getEntry();
                if (entry != null && entry.getWhat() instanceof thaumicenergistics.common.integration.appeng.AEssentiaKey key) {
                    PacketDistributor.sendToServer(EssentiaTerminalFillC2SPacket.create(
                            menu.containerId, key.getId(), sourceStack));
                    cir.setReturnValue(true);
                    return;
                }
            }
            cir.setReturnValue(true);
            return;
        }

        // 非源质容器 → 放行（WCWT 原版右键）
    }

    /** 清空 source 所在位置（carried 或主手），防止被 AE2 后续当普通物品处理。 */
    private static void clearSource(MEStorageMenu menu, ItemStack source, ItemStack carried) {
        if (!carried.isEmpty() && carried == source) {
            menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
        } else {
            menu.getPlayerInventory().player.getInventory().setItem(
                    menu.getPlayerInventory().player.getInventory().selected,
                    net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    /** 源质容器：仅要素安瓿与要素罐子（天域之华/水晶等按 Kind 排除）。 */
    private static boolean isSourceContainer(ItemStack stack) {
        return thaumicenergistics.common.integration.tc.ThEEssentiaContainers.isEssentiaContainer(stack);
    }

    /** 若手持物品是源质容器且非空，返回 true。 */
    private static boolean hasContainerEssentia(ItemStack stack) {
        return thaumicenergistics.common.integration.tc.ThEEssentiaContainers.hasEssentia(stack);
    }
}
