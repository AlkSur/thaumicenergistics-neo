package thaumicenergistics.common.items;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.features.Locatables;
import appeng.api.ids.AEComponents;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ILinkStatus;
import appeng.api.storage.MEStorage;
import appeng.api.storage.SupplierStorage;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.me.storage.NullInventory;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import net.neoforged.fml.ModList;

import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.container.IArcaneTerminalInventoryHost;
import thaumicenergistics.common.parts.ArcaneCraftingTerminalPart;
import thaumicenergistics.init.ModComponents;

/**
 * 无线奥术合成终端的 MenuHost：复刻 AE2 官方 {@code WirelessCraftingTerminalMenuHost} 范式，
 * 合成格/法杖槽持久化进物品组件（关菜单或存背包不丢料）。
 * 子库存 id 复用有线部件常量，{@code ContainerArcaneCraftingTerminal} 无需改动即可识别本宿主。
 * <p>
 * 完整 AE2WT 存在时，物品工厂选择 ItemWT 实现并使用 AE2WTArcaneTerminalMenuHost，
 * 本类只用于独立物品；旧桥卡兼容保留在此，不参与 AE2WT 宿主的扣费或连接更新。
 */
public class WirelessArcaneCraftingTerminalMenuHost
        extends WirelessTerminalMenuHost<WirelessArcaneCraftingTerminalItem>
        implements ISegmentedInventory, IArcaneTerminalInventoryHost {

    // ==================== WTBRIDGE 软依赖常量（id 来源：ae2wtlib 19.5.1，升级对齐点） ====================
    /** 量子桥卡物品 id（ae2wtlib AE2wtlibItems.QUANTUM_BRIDGE_CARD 注册名）。 */
    public static final ResourceLocation AE2WTLIB_QUANTUM_BRIDGE_CARD =
            ResourceLocation.fromNamespaceAndPath("ae2wtlib", "quantum_bridge_card");
    /** 奇点组件类型注册名（ae2wtlib AE2wtlibComponents register("singularity",...)，挂在终端 ItemStack 上）。 */
    public static final ResourceLocation AE2WTLIB_SINGULARITY_COMPONENT =
            ResourceLocation.fromNamespaceAndPath("ae2wtlib", "singularity");
    /** ae2wtlib modid（ModList 守护用）。 */
    public static final String AE2WTLIB_MODID = "ae2wtlib";
    /** WTBRIDGE-SLOT：奇点子库存寻址 id（getSubInventory 用，本模组命名空间）。 */
    public static final ResourceLocation INV_SINGULARITY =
            ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "wact_singularity");

    private final AppEngInternalInventory craftingGrid;
    private final AppEngInternalInventory wandInv;
    /** WTBRIDGE-SLOT：量子缠绕奇点槽（1 格），槽变即整包写回 ae2wtlib:singularity 组件。 */
    private final AppEngInternalInventory singularityInv;
    /** 桥卡链路当前是否有效（本类自有状态：AE2 基类 linkStatus 字段 private 不可直改）。 */
    private boolean quantumBridgeLinked;
    @Nullable
    private IActionHost quantumBridge;
    /** 库存与合成节点必须取自同一链路，不能只把界面的连接状态改成成功。 */
    private final MEStorage quantumStorage = new SupplierStorage(() -> {
        var node = getActionableNode();
        return node != null && node.getGrid() != null
                ? node.getGrid().getStorageService().getInventory() : NullInventory.of();
    });

    public WirelessArcaneCraftingTerminalMenuHost(
            WirelessArcaneCraftingTerminalItem item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
        this.craftingGrid = thaumicenergistics.common.inventory.PersistedTerminalInventories.createPersistedInv(player, this.getItemStack(), 9, AEComponents.CRAFTING_INV);
        this.wandInv = thaumicenergistics.common.inventory.PersistedTerminalInventories.createPersistedInv(player, this.getItemStack(), 1, ModComponents.WAND_INV.get());
        this.singularityInv = createSingularityInv(player, this.getItemStack());
    }

    /**
     * WTBRIDGE-SLOT：奇点子库存——ae2wtlib 语义中奇点装在终端 ItemStack 的
     * {@code ae2wtlib:singularity} 组件（值 = 奇点 ItemStack）上，槽变即整包写回；
     * 组件类型经注册表软查，ae2wtlib 缺席时跳过读写（准入同时拦截，物品进不来不会丢）。
     */
    @SuppressWarnings("unchecked")
    private AppEngInternalInventory createSingularityInv(Player player, ItemStack stack) {
        var inv = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override
            public void saveChangedInventory(AppEngInternalInventory changed) {
                var type = BuiltInRegistries.DATA_COMPONENT_TYPE
                        .getOptional(AE2WTLIB_SINGULARITY_COMPONENT).orElse(null);
                if (type != null) {
                    stack.set((DataComponentType<ItemStack>) type, changed.getStackInSlot(0));
                }
            }

            @Override
            public boolean isClientSide() {
                return player.level().isClientSide();
            }
        }, 1);
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE
                .getOptional(AE2WTLIB_SINGULARITY_COMPONENT).orElse(null);
        if (type != null) {
            ItemStack persisted = stack.getOrDefault((DataComponentType<ItemStack>) type, ItemStack.EMPTY);
            if (!persisted.isEmpty()) {
                inv.setItemDirect(0, persisted);
            }
        }
        return inv;
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(ArcaneCraftingTerminalPart.INV_CRAFTING)) {
            return craftingGrid;
        } else if (id.equals(ArcaneCraftingTerminalPart.INV_WAND)) {
            return wandInv;
        } else if (id.equals(INV_SINGULARITY)) {
            return singularityInv;
        }
        return null;
    }

    /** 法杖槽内容写回终端物品组件（直改法杖 stack 组件不触发库存回调，充能后由菜单显式调用）。 */
    public void saveWandInv() {
        this.getItemStack().set(ModComponents.WAND_INV.get(), this.wandInv.toItemContainerContents());
    }

    /** 契约实现：本宿主奇点子库存即 WTBRIDGE-SLOT 自有库存（本模组命名空间 id）。 */
    @Override
    public void refreshConnection() {
        updateConnectedAccessPoint();
        updateLinkStatus();
    }

    @Override
    public ResourceLocation getSingularityInvId() {
        return INV_SINGULARITY;
    }

    // ==================== WTBRIDGE：量子桥卡连接逻辑 ====================

    /**
     * 桥卡链路有效时 connected（跨维/无限距离）；否则回落 AE2 原生状态。
     */
    @Override
    public ILinkStatus getLinkStatus() {
        if (this.quantumBridgeLinked) {
            return ILinkStatus.ofConnected();
        }
        return super.getLinkStatus();
    }

    @Override
    public MEStorage getInventory() {
        return quantumStorage;
    }

    @Nullable
    @Override
    public IGridNode getActionableNode() {
        if (!getPlayer().level().isClientSide() && quantumBridge != null) {
            return quantumBridge.getActionableNode();
        }
        return super.getActionableNode();
    }

    @Override
    protected void updateConnectedAccessPoint() {
        super.updateConnectedAccessPoint();
        // AE2 的父类构造器也调用此方法；解析只依赖终端物品，不能依赖尚未创建的子库存。
        quantumBridge = findQuantumBridge();
    }

    /**
     * 先判桥卡链路（有效且有余能 → connected），否则完整回落 AE2 原生判定（访问点/范围/电量）。
     */
    @Override
    protected void updateLinkStatus() {
        this.quantumBridgeLinked = false;
        if (quantumBridge != null && consumeIdlePower(Actionable.SIMULATE)) {
            this.quantumBridgeLinked = true;
            return;
        }
        super.updateLinkStatus();
    }

    /**
     * 复刻 ae2wtlib {@code WTMenuHost.isQuantumLinked}：卡在槽 → 奇点组件 → 频率查桥。
     * 全程零 ae2wtlib 编译引用（注册表软查 + AE2 公开 API），缺席或链路无效时返回 null。
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private IActionHost findQuantumBridge() {
        if (getPlayer().level().isClientSide() || !ModList.get().isLoaded(AE2WTLIB_MODID)) {
            return null;
        }
        // 1. 桥卡物品软查 + 是否已装入 WACT 升级槽（AE2 标准 IUpgradeInventory，准入映射见 ThaumicEnergistics）
        var card = BuiltInRegistries.ITEM.getOptional(AE2WTLIB_QUANTUM_BRIDGE_CARD).orElse(null);
        if (card == null || card == Items.AIR) {
            return null;
        }
        if (!this.getItem().getUpgrades(this.getItemStack()).isInstalled(card)) {
            return null;
        }
        // 2. 奇点组件类型软查（ae2wtlib 注册于 BuiltInRegistries.DATA_COMPONENT_TYPE）
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(AE2WTLIB_SINGULARITY_COMPONENT).orElse(null);
        if (type == null) {
            return null;
        }
        ItemStack singularity = this.getItemStack().getOrDefault((DataComponentType<ItemStack>) type, ItemStack.EMPTY);
        if (singularity.isEmpty()) {
            return null;
        }
        // 3. 缠绕奇点频率（AE2 公开组件）→ Locatables 查桥（对齐 ae2wtlib findQuantumBridge 的 ±freq 语义）
        Long freq = singularity.get(AEComponents.ENTANGLED_SINGULARITY_ID);
        if (freq == null) {
            return null;
        }
        var level = this.getPlayer().level();
        var bridges = Locatables.quantumNetworkBridges();
        var bridge = bridges.get(level, freq);
        if (bridge == null) {
            bridge = bridges.get(level, -freq);
        }
        if (bridge == null || bridge.getActionableNode() == null) {
            return null;
        }
        var grid = bridge.getActionableNode().getGrid();
        if (grid == null || !grid.getEnergyService().isNetworkPowered()) {
            return null;
        }
        // 对齐 AE2WT：已经绑定到另一个有效网络时，不能悄悄切到奇点所在网络。
        var linkedGrid = getItem().getLinkedGrid(getItemStack(), level, null);
        return linkedGrid == null || linkedGrid == grid ? bridge : null;
    }

    @Override
    protected double getPowerDrainPerTick() {
        return quantumBridge != null ? 22.5 : super.getPowerDrainPerTick();
    }

    @Override
    public boolean consumeIdlePower(Actionable action) {
        if (action == Actionable.SIMULATE) {
            rechargeFromQuantumBridge();
        }
        boolean success = super.consumeIdlePower(action);
        if (action == Actionable.SIMULATE) {
            rechargeFromQuantumBridge();
        }
        return success;
    }

    private void rechargeFromQuantumBridge() {
        if (quantumBridge == null || quantumBridge.getActionableNode() == null) {
            return;
        }
        var grid = quantumBridge.getActionableNode().getGrid();
        if (grid == null) {
            return;
        }
        var stack = getItemStack();
        double missing = getItem().getAEMaxPower(stack) - getItem().getAECurrentPower(stack);
        if (missing <= 0) {
            return;
        }
        var energy = grid.getEnergyService();
        // 对齐 AE2WT 的半容量储能保护，避免给终端充电把 ME 网络抽停。
        double safePower = energy.getStoredPower() - energy.getMaxStoredPower() / 2;
        if (safePower <= 0) {
            return;
        }
        double extracted = energy.extractAEPower(
                Math.min(missing, safePower), Actionable.MODULATE, PowerMultiplier.ONE);
        getItem().injectAEPower(stack, extracted, Actionable.MODULATE);
    }


}
