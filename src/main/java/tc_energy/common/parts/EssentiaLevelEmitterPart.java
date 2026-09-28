package tc_energy.common.parts;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingWatcherNode;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.IConfigInvHost;
import appeng.hooks.ticking.TickHandler;
import appeng.items.parts.PartModels;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.parts.PartModel;
import appeng.parts.automation.AbstractLevelEmitterPart;
import appeng.util.ConfigInventory;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.integration.appeng.AEssentiaKey;
import tc_energy.common.integration.appeng.AEssentiaKeyType;
import tc_energy.common.integration.tc.TCReflection;
import tc_energy.init.ModMenuTypes;

/**
 * 源质发信器：与 AE2 原版 StorageLevelEmitterPart 逻辑一致，
 * 但 ConfigInventory 仅支持 AEssentiaKeyType。
 * 使用 IStorageWatcherNode 实时监听源质变化，而非轮询。
 */
public class EssentiaLevelEmitterPart extends AbstractLevelEmitterPart
        implements IConfigInvHost, ICraftingProvider {

    @PartModels
    public static final ResourceLocation RL_BASE_OFF = ThaumicEnergistics.id("parts/essentia_level_emitter_base_off");
    @PartModels
    public static final ResourceLocation RL_BASE_ON = ThaumicEnergistics.id("parts/essentia_level_emitter_base_on");
    @PartModels
    public static final ResourceLocation RL_STATUS_OFF = ThaumicEnergistics.id("parts/essentia_level_emitter_status_off");
    @PartModels
    public static final ResourceLocation RL_STATUS_ON = ThaumicEnergistics.id("parts/essentia_level_emitter_status_on");
    @PartModels
    public static final ResourceLocation RL_STATUS_HAS_CHANNEL = ThaumicEnergistics.id("parts/essentia_level_emitter_status_has_channel");

    public static final PartModel MODEL_OFF_OFF = new PartModel(RL_BASE_OFF, RL_STATUS_OFF);
    public static final PartModel MODEL_OFF_ON = new PartModel(RL_BASE_OFF, RL_STATUS_ON);
    public static final PartModel MODEL_OFF_HAS_CHANNEL = new PartModel(RL_BASE_OFF, RL_STATUS_HAS_CHANNEL);
    public static final PartModel MODEL_ON_OFF = new PartModel(RL_BASE_ON, RL_STATUS_OFF);
    public static final PartModel MODEL_ON_ON = new PartModel(RL_BASE_ON, RL_STATUS_ON);
    public static final PartModel MODEL_ON_HAS_CHANNEL = new PartModel(RL_BASE_ON, RL_STATUS_HAS_CHANNEL);

    private final ConfigInventory config = ConfigInventory.configTypes(1)
            .supportedType(AEssentiaKeyType.INSTANCE)
            .changeListener(this::configureWatchers)
            .build();

    private IStackWatcher storageWatcher;
    private IStackWatcher craftingWatcher;
    private long lastUpdateTick = -1;

    private final IStorageWatcherNode stackWatcherNode = new IStorageWatcherNode() {
        @Override
        public void updateWatcher(IStackWatcher newWatcher) {
            storageWatcher = newWatcher;
            configureWatchers();
        }

        @Override
        public void onStackChange(AEKey what, long amount) {
            if (what.equals(getConfiguredKey())) {
                lastReportedValue = amount;
                updateState();
            } else {
                // 无筛选或模糊匹配时，限制每 tick 只更新一次
                long currentTick = TickHandler.instance().getCurrentTick();
                if (currentTick != lastUpdateTick) {
                    lastUpdateTick = currentTick;
                    updateReportingValue(getGridNode().getGrid());
                }
            }
        }
    };

    private final ICraftingWatcherNode craftingWatcherNode = new ICraftingWatcherNode() {
        @Override
        public void updateWatcher(IStackWatcher newWatcher) {
            craftingWatcher = newWatcher;
            configureWatchers();
        }

        @Override
        public void onRequestChange(AEKey what) {
            updateState();
        }

        @Override
        public void onCraftableChange(AEKey what) {
        }
    };

    public EssentiaLevelEmitterPart(IPartItem<?> partItem) {
        super(partItem);

        getMainNode().addService(IStorageWatcherNode.class, stackWatcherNode);
        getMainNode().addService(ICraftingWatcherNode.class, craftingWatcherNode);
        getMainNode().addService(ICraftingProvider.class, this);
    }

    @Nullable
    private AEKey getConfiguredKey() {
        return config.getKey(0);
    }

    @Override
    protected final int getUpgradeSlots() {
        return 1;
    }

    @Override
    public final void upgradesChanged() {
        this.configureWatchers();
    }

    @Override
    protected boolean hasDirectOutput() {
        return false; // 源质发信器不支持合成卡
    }

    @Override
    protected boolean getDirectOutput() {
        return false;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return List.of();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return false;
    }

    @Override
    public boolean isBusy() {
        return true;
    }

    @Override
    public Set<AEKey> getEmitableItems() {
        return Set.of();
    }

    @Override
    protected void onReportingValueChanged() {
        getMainNode().ifPresent(this::updateReportingValue);
    }

    @Override
    protected void configureWatchers() {
        var myStack = getConfiguredKey();

        if (this.storageWatcher != null) {
            this.storageWatcher.reset();
        }

        if (this.craftingWatcher != null) {
            this.craftingWatcher.reset();
        }

        ICraftingProvider.requestUpdate(getMainNode());

        if (this.storageWatcher != null) {
            if (myStack == null) {
                this.storageWatcher.setWatchAll(true);
            } else {
                this.storageWatcher.add(myStack);
            }
        }

        getMainNode().ifPresent(this::updateReportingValue);
        updateState();
    }

    private void updateReportingValue(IGrid grid) {
        if (grid == null) return;
        var myStack = getConfiguredKey();
        var allStacks = grid.getStorageService().getInventory().getAvailableStacks();

        if (myStack == null) {
            this.lastReportedValue = 0;
            // 遍历所有存储中的源质（直接查询可用堆叠，非缓存）
            for (var st : allStacks) {
                if (st.getKey() instanceof AEssentiaKey) {
                    this.lastReportedValue += st.getLongValue();
                    if (this.lastReportedValue > this.getReportingValue()) {
                        break;
                    }
                }
            }
        } else {
            // KeyCounter.get() 用 Reference2LongMap（引用比较），自定义 AEKey 需要 equals() 遍历
            this.lastReportedValue = 0;
            for (var st : allStacks) {
                if (st.getKey() instanceof AEssentiaKey ek && ek.equals(myStack)) {
                    this.lastReportedValue = st.getLongValue();
                    break;
                }
            }
        }

        this.updateState();
    }

    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        config.readFromChildTag(data, "config", registries);
    }

    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        config.writeToChildTag(data, "config", registries);
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!isClientSide()) {
            MenuOpener.open(ModMenuTypes.ESSENTIA_LEVEL_EMITTER.get(), player, MenuLocators.forPart(this));
        }
        return true;
    }

    @Override
    public ConfigInventory getConfig() {
        return config;
    }

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return this.isLevelEmitterOn() ? MODEL_ON_HAS_CHANNEL : MODEL_OFF_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return this.isLevelEmitterOn() ? MODEL_ON_ON : MODEL_OFF_ON;
        } else {
            return this.isLevelEmitterOn() ? MODEL_ON_OFF : MODEL_OFF_OFF;
        }
    }

    // ========== 向后兼容方法（供 Container/GUI 使用） ==========

    /**
     * 获取网络中当前存储量（= lastReportedValue）。
     */
    public long getCurrentLevel() {
        return lastReportedValue;
    }

    @Nullable
    public AEssentiaKey getConfiguredEssentiaKey() {
        AEKey key = config.getKey(0);
        return key instanceof AEssentiaKey ek ? ek : null;
    }

    @Nullable
    public AspectId getTrackedAspect() {
        AEssentiaKey key = getConfiguredEssentiaKey();
        if (key == null) return null;
        return dev.tc4port.thaumcraft.api.aspect.AspectId.of(key.getId());
    }

    public void setTrackedAspect(AspectId aspectId) {
        if (aspectId == null) {
            config.setStack(0, null);
        } else {
            config.setStack(0, new appeng.api.stacks.GenericStack(AEssentiaKey.of(aspectId.value()), 1));
        }
    }
}
