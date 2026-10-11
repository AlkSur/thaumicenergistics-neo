package thaumicenergistics.common.items;

import java.util.function.BiConsumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import appeng.api.config.Actionable;
import appeng.api.features.Locatables;
import appeng.api.ids.AEComponents;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.storage.ILinkStatus;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import net.neoforged.fml.ModList;

import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.parts.ArcaneCraftingTerminalPart;
import thaumicenergistics.init.ModComponents;

/**
 * 无线奥术合成终端的 MenuHost：复刻 AE2 官方 {@code WirelessCraftingTerminalMenuHost} 范式，
 * 合成格/法杖槽持久化进物品组件（关菜单或存背包不丢料）。
 * 子库存 id 复用有线部件常量，{@code ContainerArcaneCraftingTerminal} 无需改动即可识别本宿主。
 * <p>
 * WTBRIDGE：量子桥卡联动为何复刻而非引用——ae2wtlib 桥卡逻辑在其自有
 * {@code WTMenuHost}（类层级硬绑定其 {@code ItemWT}），软依赖 + Java 单继承下无法继承复用，
 * 故在本类覆写 {@code updateLinkStatus}/{@code getLinkStatus} 复刻其公开 API 语义。
 */
public class WirelessArcaneCraftingTerminalMenuHost
        extends WirelessTerminalMenuHost<WirelessArcaneCraftingTerminalItem>
        implements ISegmentedInventory {

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

    public WirelessArcaneCraftingTerminalMenuHost(
            WirelessArcaneCraftingTerminalItem item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
        this.craftingGrid = createPersistedInv(player, this.getItemStack(), 9, AEComponents.CRAFTING_INV);
        this.wandInv = createPersistedInv(player, this.getItemStack(), 1, ModComponents.WAND_INV.get());
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

    // ==================== WTBRIDGE：量子桥卡生效逻辑（照抄 ae2wtlib isQuantumLinked 语义） ====================

    /**
     * 桥卡链路有效时强制 connected（跨维/无限距离）；否则逐字节回落 AE2 原生状态。
     */
    @Override
    public ILinkStatus getLinkStatus() {
        if (this.quantumBridgeLinked) {
            return ILinkStatus.ofConnected();
        }
        return super.getLinkStatus();
    }

    /**
     * 先判桥卡链路（有效且有余能 → connected），否则完整回落 AE2 原生判定（访问点/范围/电量）。
     */
    @Override
    protected void updateLinkStatus() {
        this.quantumBridgeLinked = false;
        if (isQuantumLinkedViaBridgeCard() && consumeIdlePower(Actionable.SIMULATE)) {
            this.quantumBridgeLinked = true;
            return;
        }
        super.updateLinkStatus();
    }

    /**
     * 复刻 ae2wtlib {@code WTMenuHost.isQuantumLinked}：卡在槽 → 奇点组件 → 频率查桥。
     * 全程零 ae2wtlib 编译引用（注册表软查 + AE2 公开 API），ae2wtlib 缺席时恒 false。
     */
    @SuppressWarnings("unchecked")
    private boolean isQuantumLinkedViaBridgeCard() {
        if (!ModList.get().isLoaded(AE2WTLIB_MODID)) {
            return false;
        }
        // 1. 桥卡物品软查 + 是否已装入 WACT 升级槽（AE2 标准 IUpgradeInventory，准入映射见 ThaumicEnergistics）
        var card = BuiltInRegistries.ITEM.getOptional(AE2WTLIB_QUANTUM_BRIDGE_CARD).orElse(null);
        if (card == null || card == Items.AIR) {
            return false;
        }
        if (!this.getItem().getUpgrades(this.getItemStack()).isInstalled(card)) {
            return false;
        }
        // 2. 奇点组件类型软查（ae2wtlib 注册于 BuiltInRegistries.DATA_COMPONENT_TYPE）
        var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(AE2WTLIB_SINGULARITY_COMPONENT).orElse(null);
        if (type == null) {
            return false;
        }
        ItemStack singularity = this.getItemStack().getOrDefault((DataComponentType<ItemStack>) type, ItemStack.EMPTY);
        if (singularity.isEmpty()) {
            return false;
        }
        // 3. 缠绕奇点频率（AE2 公开组件）→ Locatables 查桥（对齐 ae2wtlib findQuantumBridge 的 ±freq 语义）
        Long freq = singularity.get(AEComponents.ENTANGLED_SINGULARITY_ID);
        if (freq == null) {
            return false;
        }
        var level = this.getPlayer().level();
        var bridges = Locatables.quantumNetworkBridges();
        return bridges.get(level, freq) != null || bridges.get(level, -freq) != null;
    }

    /** 官方 createCraftingInv 推广版：子库存读写进指定物品组件。 */
    private static AppEngInternalInventory createPersistedInv(Player player, ItemStack stack, int size,
                                                              DataComponentType<ItemContainerContents> component) {
        var inv = new AppEngInternalInventory(new InternalInventoryHost() {
            @Override
            public void saveChangedInventory(AppEngInternalInventory changed) {
                stack.set(component, changed.toItemContainerContents());
            }

            @Override
            public boolean isClientSide() {
                return player.level().isClientSide();
            }
        }, size);
        inv.fromItemContainerContents(stack.getOrDefault(component, ItemContainerContents.EMPTY));
        return inv;
    }
}
