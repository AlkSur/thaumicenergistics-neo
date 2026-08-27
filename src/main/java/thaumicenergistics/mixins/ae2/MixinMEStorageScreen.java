package thaumicenergistics.mixins.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.RepoSlot;

import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics.common.network.EssentiaTerminalDepositC2SPacket;
import thaumicenergistics.common.network.EssentiaTerminalFillC2SPacket;

/**
 * 让所有 AE2 ME 终端 GUI（ME 终端、合成终端、无线终端等）都支持源质右键交互：
 * <ul>
 *   <li>Shift+右键玩家背包格：格内是源质容器 → 源质存入网络（空容器留原格）。</li>
 *   <li>右键玩家物品栏/背包槽 → 放行（走 AE2 原版物品交互）。</li>
 *   <li>右键网络区域（源质条目或空白）：手持装有源质的罐子/安瓿 → 源质存入 ME 网络（空容器回背包）。</li>
 *   <li>手持空罐/空安瓿 + 右键源质条目 → 从网络取出源质装罐/装安瓿。</li>
 *   <li>左键仍是正常物品操作（不受影响）。</li>
 * </ul>
 * 源质终端（{@code GuiEssentiaTerminal}）已覆写 {@code mouseClicked}，子类覆写优先于 mixin；
 * 此 mixin 覆盖其余未覆写的 ME 终端 GUI。
 */
@Mixin(targets = "appeng.client.gui.me.common.MEStorageScreen", remap = false)
public abstract class MixinMEStorageScreen {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void thE_onMouseClickedEssentia(double xCoord, double yCoord, int btn,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (btn != 1) {
            return;
        }
        MEStorageScreen<?> self = (MEStorageScreen<?>) (Object) this;
        Slot hovered = self.getSlotUnderMouse();
        boolean shiftDown = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.hasShiftDown();
        var carried = self.getMenu().getCarried();
        // 源质物品来源：优先菜单 carried（拾取物品）；为空时回退到玩家主手物品
        // （GUI 打开时主手物品不自动进入 carried，除非先左键点击拿起）
        ItemStack sourceStack = !carried.isEmpty() ? carried
                : self.getMenu().getPlayerInventory().player.getMainHandItem();
        boolean isContainer = isSourceContainer(sourceStack);
        boolean containerHasEssentia = isContainer && hasContainerEssentia(sourceStack);

        // Shift+右键玩家物品栏/背包格：格内是源质容器 → 存入源质（空容器留原格）
        if (shiftDown && hovered != null && self.getMenu().isPlayerSideSlot(hovered)) {
            var slotStack = hovered.getItem();
            // 仅要素安瓿/要素罐子；天域之华等其余 IEssentiaContainerItem 一律放行
            if (thaumicenergistics.common.integration.tc.ThEEssentiaContainers.isEssentiaContainer(slotStack)) {
                PacketDistributor.sendToServer(EssentiaTerminalDepositC2SPacket.fromSlot(
                        self.getMenu().containerId, hovered.getSlotIndex(), slotStack));
                cir.setReturnValue(true);
                return;
            }
            // 非源质容器 → 放行（AE2 原版 Shift+右键快速移动）
            return;
        }

        // 普通右键点击玩家物品栏/背包槽 → 放行（AE2 原版物品交互）
        if (!shiftDown && hovered != null && self.getMenu().isPlayerSideSlot(hovered)) {
            return;
        }

        // Shift+右键：手持源质容器（满或空）→ 拦截。满→存入源质；空→阻止被当作普通物品放入网络
        if (shiftDown && isContainer) {
            if (containerHasEssentia) {
                // 立即本地清空 carried/主手，防止 AE2 后续把空容器当普通物品放入网络
                clearSource(self, sourceStack, carried);
                PacketDistributor.sendToServer(EssentiaTerminalDepositC2SPacket.fromClientStack(
                        self.getMenu().containerId, sourceStack));
            }
            cir.setReturnValue(true);
            return;
        }

        // 普通右键网络区域：手持满容器 → 存入源质
        if (containerHasEssentia) {
            // 立即本地清空 carried/主手，防止 AE2 后续把空容器当普通物品放入网络
            clearSource(self, sourceStack, carried);
            PacketDistributor.sendToServer(EssentiaTerminalDepositC2SPacket.fromClientStack(
                    self.getMenu().containerId, sourceStack));
            cir.setReturnValue(true);
            return;
        }

        // 手持空容器：右键源质条目 → 取出装罐；右键网络其他 → 拦截（防被放入网络）
        if (isContainer) {
            if (hovered instanceof RepoSlot repoSlot) {
                var entry = repoSlot.getEntry();
                if (entry != null && entry.getWhat() instanceof thaumicenergistics.common.integration.appeng.AEssentiaKey key) {
                    PacketDistributor.sendToServer(EssentiaTerminalFillC2SPacket.create(
                            self.getMenu().containerId, key.getId(), sourceStack));
                    cir.setReturnValue(true);
                    return;
                }
            }
            cir.setReturnValue(true);
            return;
        }

        // 非源质容器 → 放行（AE2 原版右键）
    }

    /** 清空 source 所在位置（carried 或主手），防止被 AE2 后续当普通物品处理。 */
    private static void clearSource(MEStorageScreen<?> self, ItemStack source, ItemStack carried) {
        if (!carried.isEmpty() && carried == source) {
            self.getMenu().setCarried(net.minecraft.world.item.ItemStack.EMPTY);
        } else {
            self.getMenu().getPlayerInventory().player.getInventory().setItem(
                    self.getMenu().getPlayerInventory().player.getInventory().selected,
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
