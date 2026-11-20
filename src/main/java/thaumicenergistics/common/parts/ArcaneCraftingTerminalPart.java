package thaumicenergistics.common.parts;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.core.AppEng;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractTerminalPart;
import appeng.util.inv.AppEngInternalInventory;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.init.ModMenuTypes;

public class ArcaneCraftingTerminalPart extends AbstractTerminalPart implements IGridTickable {

    public static final ResourceLocation INV_CRAFTING = AppEng.makeId("arcane_crafting_terminal_crafting");
    public static final ResourceLocation INV_WAND = ThaumicEnergistics.id("arcane_crafting_terminal_wand");

    @PartModels
    public static final ResourceLocation RL_BASE = ThaumicEnergistics.id("parts/arcane_crafting_terminal_base");
    @PartModels
    public static final ResourceLocation RL_OFF = ThaumicEnergistics.id("parts/arcane_crafting_terminal_off");
    @PartModels
    public static final ResourceLocation RL_HAS_CHANNEL = ThaumicEnergistics.id("parts/arcane_crafting_terminal_has_channel");
    @PartModels
    public static final ResourceLocation RL_ON = ThaumicEnergistics.id("parts/arcane_crafting_terminal_on");

    public static final IPartModel MODELS_OFF = new PartModel(RL_BASE, RL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(RL_BASE, RL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(RL_BASE, RL_ON, MODEL_STATUS_HAS_CHANNEL);

    public static final int WAND_SLOT_INDEX = 0;

    /** 充能间隔：每 tick 抽取一次。与 TC 能量节点池子每 tick 重置的频率、原版充能中继一致；无需放大。 */
    private static final int VIS_REPLENISH_INTERVAL = 1;
    private static final String NBT_KEY_VIS_SOURCE_FREQ = "visSourceFreq";

    /** 内存卡设定的 vis 源 P2P 频率；0 = 未设置（不自动充能）。 */
    private short visSourceFreq = 0;

    private final AppEngInternalInventory craftingGrid = new AppEngInternalInventory(this, 9);
    private final AppEngInternalInventory wandInv = new AppEngInternalInventory(this, 1);

    public ArcaneCraftingTerminalPart(IPartItem<?> partItem) {
        super(partItem);
        this.getMainNode().setIdlePowerUsage(0.5);
        this.getMainNode().addService(IGridTickable.class, this);
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.onUseWithoutItem(player, pos) && !player.level().isClientSide) {
            MenuOpener.open(getMenuType(player), player, MenuLocators.forPart(this));
        }
        return true;
    }

    /**
     * 内存卡右键终端：读取 Vis Interface 的 P2P 频率，设为法杖充能的 vis 源。
     * 复刻奥术装配器 {@code TileArcaneAssembler.onMemoryCardActivate} 的行为：
     * 卡上带 P2P 频率（从 vis_interface shift+右键复制来的）→ 设为 vis 源；
     * 空白卡 → 清空；其它 → 交给 AE2 标准内存卡逻辑。
     */
    @Override
    public boolean onUseItemOn(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos) {
        if (heldItem.getItem() instanceof IMemoryCard memoryCard) {
            Short freq = heldItem.get(AEComponents.EXPORTED_P2P_FREQUENCY);
            if (freq != null) {
                var node = getMainNode().getNode();
                var problem = ArcaneVisReplenish.checkFrequency(node != null ? node.getGrid() : null, freq);
                if (problem != null) {
                    player.displayClientMessage(problem, true);
                    return true;
                }
                this.visSourceFreq = freq;
                wakeCharging();
                getHost().markForSave();
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
                return true;
            } else if (heldItem.get(AEComponents.EXPORTED_SETTINGS_SOURCE) == null) {
                // 空白卡 → 清空 vis 源
                if (this.visSourceFreq != 0) {
                    this.visSourceFreq = 0;
                    getHost().markForSave();
                }
                memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_CLEARED);
                return true;
            }
        }
        return super.onUseItemOn(heldItem, player, hand, pos);
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // Charges every tick while vis flows; drifts to every 20 ticks while no source has vis.
        return new TickingRequest(VIS_REPLENISH_INTERVAL, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        // Charge every tick while there is something to charge; sleep otherwise and wake on the
        // changes that can create work (wand slot, vis spent by crafting, memory card) (TEA2-10).
        var wand = wandInv.getStackInSlot(0);
        if (ArcaneVisReplenish.isFull(wand)) {
            return TickRateModulation.SLEEP;
        }
        // Bound by memory card: that interface. Unbound: any vis interface on the network (TEU-04).
        boolean charged = visSourceFreq != 0
                ? ArcaneVisReplenish.replenish(node.getGrid(), visSourceFreq, wand)
                : ArcaneVisReplenish.replenishAuto(node.getGrid(), wand);
        if (charged) {
            // 法杖 vis 存储在 ItemStack NBT，手动标记保存
            getHost().markForSave();
            return TickRateModulation.URGENT;
        }
        return TickRateModulation.IDLE;
    }

    public static final int VIS_SOURCE_NONE = 0, VIS_SOURCE_UNAVAILABLE = 1, VIS_SOURCE_OK = 2;

    /** No frequency, a frequency whose vis interface has no vis behind it, or a working source (TEU-05). */
    public int visSourceState() {
        var node = getMainNode().getNode();
        var grid = node != null ? node.getGrid() : null;
        if (ArcaneVisReplenish.hasAvailableSource(grid, visSourceFreq)) return VIS_SOURCE_OK;
        return visSourceFreq == 0 ? VIS_SOURCE_NONE : VIS_SOURCE_UNAVAILABLE;
    }

    /** The wand changed in place (vis paid by crafting): persist it and resume charging. */
    public void onWandChanged() {
        if (getHost() != null) getHost().markForSave();
        wakeCharging();
    }

    private void wakeCharging() {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        super.onChangeInventory(inv, slot);
        if (inv == wandInv) wakeCharging();
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return ModMenuTypes.ARCANE_CRAFTING_TERMINAL.get();
    }

    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        for (var is : this.craftingGrid) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
        for (var is : this.wandInv) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        craftingGrid.clear();
        wandInv.clear();
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.craftingGrid.readFromNBT(data, "craftingGrid", registries);
        this.wandInv.readFromNBT(data, "wandInv", registries);
        this.visSourceFreq = data.contains(NBT_KEY_VIS_SOURCE_FREQ) ? data.getShort(NBT_KEY_VIS_SOURCE_FREQ) : 0;
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        this.craftingGrid.writeToNBT(data, "craftingGrid", registries);
        this.wandInv.writeToNBT(data, "wandInv", registries);
        if (visSourceFreq != 0) {
            data.putShort(NBT_KEY_VIS_SOURCE_FREQ, visSourceFreq);
        }
    }

    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(INV_CRAFTING)) {
            return craftingGrid;
        } else if (id.equals(INV_WAND)) {
            return wandInv;
        } else {
            return super.getSubInventory(id);
        }
    }
}
