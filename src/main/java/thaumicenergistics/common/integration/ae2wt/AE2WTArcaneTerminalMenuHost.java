package thaumicenergistics.common.integration.ae2wt;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import appeng.api.ids.AEComponents;
import appeng.api.inventories.InternalInventory;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;

import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

import thaumicenergistics.common.container.IArcaneTerminalInventoryHost;
import thaumicenergistics.common.parts.ArcaneCraftingTerminalPart;
import thaumicenergistics.init.ModComponents;

/**
 * AE2WT 通用终端的奥术模式宿主：继承 {@code WTMenuHost} 复用其模式切换、升级卡库存、
 * 奇点库存与量子连接/充电全套机制（不在本类重复实现，避免双库存与重复扣费），
 * 只补齐奥术合成所需的合成格与法杖槽——持久化进终端物品组件，与独立无线奥术终端同一范式。
 * <p>
 * 仅 ae2wtlib 存在时由 {@code AE2WTCompat} 注册链路加载；奇点子库存 id 用字符串软查
 * （{@code ae2wtlib:singularity}，WTMenuHost.INV_SINGULARITY 实证值），不暴露可选 API 类型。
 */
public class AE2WTArcaneTerminalMenuHost extends WTMenuHost implements IArcaneTerminalInventoryHost {

    /** WTMenuHost 奇点子库存 id（AE2wtlibAPI.id("singularity") 字节码实证，软查免编译暴露）。 */
    public static final ResourceLocation SINGULARITY_INV_ID =
            ResourceLocation.fromNamespaceAndPath("ae2wtlib", "singularity");

    private final AppEngInternalInventory craftingGrid;
    private final AppEngInternalInventory wandInv;

    public AE2WTArcaneTerminalMenuHost(
            ItemWT item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
        // WTMenuHost 构造器会触发连接状态更新，但只依赖终端物品；子库存在此之后创建。
        this.craftingGrid = thaumicenergistics.common.inventory.PersistedTerminalInventories.createPersistedInv(
                player, this.getItemStack(), 9, AEComponents.CRAFTING_INV);
        this.wandInv = thaumicenergistics.common.inventory.PersistedTerminalInventories.createPersistedInv(
                player, this.getItemStack(), 1, ModComponents.WAND_INV.get());
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(ArcaneCraftingTerminalPart.INV_CRAFTING)) {
            return craftingGrid;
        } else if (id.equals(ArcaneCraftingTerminalPart.INV_WAND)) {
            return wandInv;
        }
        // 奇点（ae2wtlib:singularity）等其余子库存由 WTMenuHost 基类处理。
        return super.getSubInventory(id);
    }

    @Override
    public void saveWandInv() {
        this.getItemStack().set(ModComponents.WAND_INV.get(), this.wandInv.toItemContainerContents());
    }

    @Override
    public void refreshConnection() {
        updateConnectedAccessPoint();
        updateLinkStatus();
    }

    @Override
    public ResourceLocation getSingularityInvId() {
        return SINGULARITY_INV_ID;
    }

    @Override
    public boolean useAE2WTSingularitySemantics() {
        return true;
    }
}
