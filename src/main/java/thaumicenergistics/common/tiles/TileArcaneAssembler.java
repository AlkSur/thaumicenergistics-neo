package thaumicenergistics.common.tiles;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Hashtable;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;

import thaumcraft.api.IVisDiscountGear;
import thaumcraft.api.IWarpingGear;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.IArcaneRecipe;

import thaumicenergistics.api.grid.IEssentiaGrid;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.integration.tc.ArcaneCraftingPattern;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.common.inventory.HandlerKnowledgeCore;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModItems;

public class TileArcaneAssembler extends ThETileBase
        implements IInWorldGridNodeHost, IActionHost, IGridTickable, ICraftingProvider, ICraftingMachine {

    public static final Aspect[] PRIMALS = {
            Aspect.AIR, Aspect.FIRE, Aspect.WATER,
            Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY
    };

    private static final int CVIS_MULTIPLIER = 10;
    private static final int MAX_STORED_CVIS = 187 * CVIS_MULTIPLIER;
    private static final int BASE_TICKS_PER_CRAFT = 20;
    private static final double IDLE_POWER = 0.0;
    private static final double ACTIVE_POWER = 1.5;
    private static final double WARP_POWER_PERCENT = 0.15;
    private static final int MAX_SPEED_UPGRADES = 4;
    private static final int VIS_REPLENISH_INTERVAL = 20;
    /** 每次实际 vis 变化累计达到该值才调用一次 setChanged（约 4×5=20 tick），避免合成期间 chunk 持续处于 unsaved 状态。 */
    private static final int SET_CHANGED_INTERVAL = 4;

    public static final int SLOT_COUNT = 27;
    public static final int KCORE_SLOT = 0;
    public static final int PATTERN_SLOT_START = 1;
    public static final int PATTERN_SLOT_END = 21;
    public static final int TARGET_SLOT = 22;
    public static final int ARMOR_SLOT_START = 23;
    public static final int ARMOR_SLOT_END = 26;

    protected final IManagedGridNode mainNode;
    private final IActionSource actionSource;

    private final IGridNodeListener<TileArcaneAssembler> nodeListener = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(TileArcaneAssembler owner, IGridNode node) {
            owner.setChanged();
        }

        @Override
        public void onStateChanged(TileArcaneAssembler owner, IGridNode node, State state) {
            if (state == State.POWER) {
                owner.isActive = owner.mainNode.isActive();
            }
            owner.markForUpdate();
        }
    };

    private boolean isCrafting = false;
    private int craftTickCounter = 0;
    private int speedUpgrades = 0;
    /**
     * 从内存卡粘贴的 vis_interface P2P 频率；装配器优先从该频率的 vis_interface P2P 端抽 vis。
     * 0 = 未设置，走 essentia 网络。对应 1.7.10 的 visSourceInfo，但用 P2P 频率替代位置（适配 neo P2P 设计）。
     */
    private short visSourceFreq = 0;
    private AspectList storedVis = new AspectList();
    private Hashtable<Aspect, Float> visDiscount = new Hashtable<>();
    private float warpPowerMultiplier = 1.0F;
    private IArcaneRecipe currentRecipe = null;
    private AspectList currentRecipeAspects = new AspectList();
    private ItemStack currentResult = ItemStack.EMPTY;
    private int visTickCounter = 0;
    /** replenishVis 的 setChanged 节流计数。 */
    private int visSetChangedCounter = 0;
    private boolean stalePatterns = false;
    /** 客户端接收全量槽位同步包时置 true，跳过 setItem 连锁的 setChanged/onInventoryChanged（客户端无网格，这些逻辑无意义）。 */
    private boolean loadingFromClientTag = false;
    private Direction attachmentSide = Direction.NORTH;

    private final SimpleContainer internalInventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setItem(int slot, ItemStack stack) {
            super.setItem(slot, stack);
            if (loadingFromClientTag) return;
            TileArcaneAssembler.this.setChanged();
            TileArcaneAssembler.this.onInventoryChanged(slot);
        }
    };

    public TileArcaneAssembler(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_ASSEMBLER.get(), pos, state);

        mainNode = GridHelper.createManagedNode(this, nodeListener)
                .setVisualRepresentation(thaumicenergistics.init.ModItems.ARCANE_ASSEMBLER_ITEM.get())
                .setInWorldNode(true)
                .setTagName("proxy")
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .addService(IGridTickable.class, this)
                .addService(ICraftingProvider.class, this);
        mainNode.setIdlePowerUsage(IDLE_POWER);
        actionSource = IActionSource.ofMachine(this);

        for (Aspect primal : PRIMALS) {
            visDiscount.put(primal, 0.8F);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            mainNode.create(level, getBlockPos());
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (mainNode != null) mainNode.destroy();
    }

    @Override
    public void onBreakBlock() {
        super.onBreakBlock();
        if (level != null && !level.isClientSide) {
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (i >= PATTERN_SLOT_START && i <= PATTERN_SLOT_END) continue;
                if (i == TARGET_SLOT) continue;
                ItemStack stack = internalInventory.getItem(i);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
                }
            }
        }
    }

    @Override
    @Nullable
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    @Override
    @Nullable
    public IGridNode getActionableNode() {
        return mainNode.getNode();
    }

    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        // 第三个参数是 isSleeping。之前传 !isCrafting，装配器放置时 isCrafting=false → isSleeping=true，
        // 节点一注册就休眠且无法自唤醒（tickingRequest 不被调用就永远无法返回 WAKE），
        // 导致 stalePatterns 的延迟 requestUpdate 永不触发、vis 不补充、合成不推进。
        // 改为 false（不休眠），由 tickingRequest 返回的 TickRateModulation 动态控制速率。
        return new TickingRequest(1, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (level == null || level.isClientSide()) return TickRateModulation.SLEEP;
        IGrid grid = node.getGrid();
        if (grid == null) return TickRateModulation.IDLE;

        if (!mainNode.isActive()) return TickRateModulation.IDLE;

        if (stalePatterns) {
            ICraftingProvider.requestUpdate(mainNode);
            stalePatterns = false;
        }

        visTickCounter++;
        if (visTickCounter >= VIS_REPLENISH_INTERVAL) {
            visTickCounter = 0;
            // 仅合成中或 vis 未满时才抽（满时每 5 tick 空转会白跑 P2P/网格查询）
            if (isCrafting || !storedVisFull()) {
                replenishVis(grid);
            }
        }

        if (isCrafting) {
            return craftingTick(grid, ticksSinceLast);
        }

        return TickRateModulation.IDLE;
    }

    /** 全部原质元素均已达到缓存上限。 */
    private boolean storedVisFull() {
        for (Aspect primal : PRIMALS) {
            if (storedVis.getAmount(primal) < MAX_STORED_CVIS) {
                return false;
            }
        }
        return true;
    }

    private void replenishVis(IGrid grid) {
        // 优先从内存卡配置的 vis_interface P2P 源抽 vis（1.7.10 vis 源机制，适配 neo P2P）
        if (visSourceFreq != 0) {
            appeng.me.service.P2PService p2p = appeng.me.service.P2PService.get(grid);
            if (p2p != null) {
                appeng.parts.p2p.P2PTunnelPart<?> input = p2p.getInput(visSourceFreq);
                if (input instanceof thaumicenergistics.common.parts.VisInterfacePart visSource) {
                    for (Aspect primal : PRIMALS) {
                        int needed = MAX_STORED_CVIS - storedVis.getAmount(primal);
                        if (needed <= 0) continue;
                        ResourceLocation id = TCReflection.getAspectId(primal);
                        if (id == null) continue;
                        int got = visSource.consumeVis(id, needed);
                        if (got > 0) {
                            storedVis.add(primal, got);
                            maybeMarkVisDirty();
                        }
                    }
                    return; // 有 vis 源时只用它（1.7.10 行为：源不可用则不抽）
                }
            }
            return; // 频率设了但找不到 vis_interface P2P 输入 → 不抽
        }

        // 无 vis 源：从 essentia 网络抽（neo 默认行为）
        IEssentiaGrid essentiaGrid = grid.getService(IEssentiaGrid.class);
        if (essentiaGrid == null) return;

        for (Aspect primal : PRIMALS) {
            int currentCvis = storedVis.getAmount(primal);
            int needed = MAX_STORED_CVIS - currentCvis;
            if (needed <= 0) continue;

            ResourceLocation id = TCReflection.getAspectId(primal);
            if (id == null) continue;

            int neededEssentia = (needed + CVIS_MULTIPLIER - 1) / CVIS_MULTIPLIER;
            long extracted = essentiaGrid.extractEssentia(id, neededEssentia, false);
            if (extracted > 0) {
                int cvisGained = (int) (extracted * CVIS_MULTIPLIER);
                storedVis.add(primal, cvisGained);
                maybeMarkVisDirty();
            }
        }
    }

    /**
     * replenishVis 抽到 vis 时调用：累计变化次数，每 SET_CHANGED_INTERVAL 次才 markForUpdate 一次。
     * markForUpdate = setChanged + sendBlockUpdated（同步 storedVis 到客户端 BE，GUI 进度条依赖客户端 storedVis）。
     */
    private void maybeMarkVisDirty() {
        if (++visSetChangedCounter >= SET_CHANGED_INTERVAL) {
            visSetChangedCounter = 0;
            markForUpdate();
        }
    }

    private TickRateModulation craftingTick(IGrid grid, int ticksSinceLast) {
        if (currentResult.isEmpty()) {
            isCrafting = false;
            return TickRateModulation.IDLE;
        }

        boolean enoughVis = hasEnoughVisForCraft();
        if (!enoughVis) {
            ThaumicEnergistics.LOG.debug("[AA] craftingTick: vis 不足，等待补充 (storedVis cvis={})", storedVis.visSize());
            return TickRateModulation.SAME;
        }

        int ticksPerCraft = ticksPerCraft();

        if (craftTickCounter >= ticksPerCraft) {
            IStorageService storage = grid.getService(IStorageService.class);
            if (storage == null) return TickRateModulation.IDLE;

            ItemStack output = currentResult.copy();
            AEItemKey outputKey = AEItemKey.of(output);
            if (outputKey == null) return TickRateModulation.IDLE;

            long inserted = storage.getInventory().insert(
                    outputKey, output.getCount(), Actionable.SIMULATE, actionSource);
            if (inserted < output.getCount()) {
                ThaumicEnergistics.LOG.debug("[AA] craftingTick: 产物无法插入网络(模拟 inserted={}/{})，等待", inserted, output.getCount());
                return TickRateModulation.SAME;
            }

            consumeVisForCraft();

            storage.getInventory().insert(
                    outputKey, output.getCount(), Actionable.MODULATE, actionSource);
            ThaumicEnergistics.LOG.debug("[AA] craftingTick: 完成！插入产物 {}x{} 到网络", outputKey, output.getCount());

            isCrafting = false;
            currentRecipe = null;
            currentResult = ItemStack.EMPTY;
            currentRecipeAspects = new AspectList();
            internalInventory.setItem(TARGET_SLOT, ItemStack.EMPTY);
            setChanged();
            markForUpdate();

            return TickRateModulation.URGENT;
        }

        double powerRequired = (ACTIVE_POWER + (ACTIVE_POWER * speedUpgrades) / 2.0) * warpPowerMultiplier;
        IEnergyService energy = grid.getService(IEnergyService.class);
        if (energy != null) {
            double need = powerRequired * ticksSinceLast;
            double extracted = energy.extractAEPower(need, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (extracted >= need * 0.9) {
                craftTickCounter += ticksSinceLast;
                ThaumicEnergistics.LOG.debug("[AA] craftingTick: 推进 {}/{} (抽电 {}/{})", craftTickCounter, ticksPerCraft, extracted, need);
                // 节流同步合成进度到客户端（GUI 第7格进度条动画）
                maybeMarkVisDirty();
            } else {
                ThaumicEnergistics.LOG.debug("[AA] craftingTick: 能量不足 (抽到 {}/{})，不推进", extracted, need);
            }
        }

        return TickRateModulation.SAME;
    }

    private void consumeVisForCraft() {
        if (currentRecipeAspects == null || currentRecipeAspects.isEmpty()) return;
        for (Aspect aspect : currentRecipeAspects.getAspects()) {
            int requiredCvis = getRequiredCvisForAspect(aspect);
            int current = storedVis.getAmount(aspect);
            storedVis.reduce(aspect, Math.min(requiredCvis, current));
        }
        maybeMarkVisDirty();
    }

    private boolean hasEnoughVisForCraft() {
        if (currentRecipeAspects == null || currentRecipeAspects.isEmpty()) return true;
        for (Aspect aspect : currentRecipeAspects.getAspects()) {
            int requiredCvis = getRequiredCvisForAspect(aspect);
            if (storedVis.getAmount(aspect) < requiredCvis) return false;
        }
        return true;
    }

    private int getRequiredCvisForAspect(Aspect aspect) {
        if (currentRecipeAspects == null) return 0;
        int baseCost = currentRecipeAspects.getAmount(aspect) * CVIS_MULTIPLIER;
        float discount = visDiscount.getOrDefault(aspect, 1.0F);
        return Math.max(0, Math.round(baseCost * discount));
    }

    private int ticksPerCraft() {
        return Math.max(4, BASE_TICKS_PER_CRAFT - (4 * speedUpgrades));
    }

    private void calculateVisDiscounts() {
        for (Aspect primal : PRIMALS) {
            float discount = 0.8F;

            for (int i = 0; i < 4; i++) {
                ItemStack armor = internalInventory.getItem(ARMOR_SLOT_START + i);
                if (!armor.isEmpty() && armor.getItem() instanceof IVisDiscountGear gear) {
                    try {
                        discount -= gear.getVisDiscount(armor, null, primal) / 100.0F;
                    } catch (Exception ignored) {}
                }
            }

            visDiscount.put(primal, Math.max(0.1F, discount));
        }

        warpPowerMultiplier = 1.0F;
        for (int i = 0; i < 4; i++) {
            ItemStack armor = internalInventory.getItem(ARMOR_SLOT_START + i);
            if (!armor.isEmpty() && armor.getItem() instanceof IWarpingGear gear) {
                try {
                    warpPowerMultiplier += gear.getWarp(armor, null) * WARP_POWER_PERCENT;
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        ItemStack kCore = internalInventory.getItem(KCORE_SLOT);
        if (kCore.isEmpty() || !kCore.is(ModItems.KNOWLEDGE_CORE.get())) {
            return List.of();
        }

        HandlerKnowledgeCore handler = new HandlerKnowledgeCore(kCore);
        if (!handler.hasCore()) {
            return List.of();
        }

        List<IPatternDetails> result = new ArrayList<>();
        List<ArcaneCraftingPattern> patterns = handler.getPatterns();
        for (ArcaneCraftingPattern acp : patterns) {
            if (acp == null || !acp.isPatternValid()) continue;
            ArcaneAssemblerPattern ap = buildArcanePattern(acp);
            if (ap != null) {
                result.add(ap);
                ThaumicEnergistics.LOG.debug("[AA] getAvailablePatterns: pattern primaryOutput={}, definition={}",
                        ap.getPrimaryOutput(), ap.getDefinition());
            }
        }
        ThaumicEnergistics.LOG.debug("[AA] getAvailablePatterns: kCore valid={}, handler patterns={}, returned={}",
                !kCore.isEmpty(), patterns.size(), result.size());
        return result;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        ThaumicEnergistics.LOG.info("[AA] pushPattern(provider) called, pattern={}, isArcanePattern={}",
                patternDetails, patternDetails instanceof ArcaneAssemblerPattern);
        if (patternDetails instanceof ArcaneAssemblerPattern arcanePattern) {
            return beginCrafting(arcanePattern);
        }
        return false;
    }

    private ArcaneAssemblerPattern buildArcanePattern(ArcaneCraftingPattern acp) {
        ItemStack result = acp.getResult();
        if (result == null || result.isEmpty()) return null;

        AspectList aspects = acp.getAspects();
        ItemStack[] ingredients = acp.getIngredients();

        List<GenericStack> inputs = new ArrayList<>();
        if (ingredients != null) {
            for (ItemStack ing : ingredients) {
                if (ing == null || ing.isEmpty()) continue;
                AEItemKey key = AEItemKey.of(ing);
                if (key != null) {
                    inputs.add(new GenericStack(key, ing.getCount()));
                }
            }
        }

        return new ArcaneAssemblerPattern(AEItemKey.of(result), null, aspects, result, inputs,
                acp.getResearch());
    }

    private boolean beginCrafting(ArcaneAssemblerPattern arcanePattern) {
        AspectList aspects = arcanePattern.getAspects();
        ItemStack result = arcanePattern.getResultStack();
        ThaumicEnergistics.LOG.info("[AA] beginCrafting: isCrafting={}, active={}, aspectsEmpty={}, resultEmpty={}",
                isCrafting, mainNode.isActive(),
                aspects == null || aspects.isEmpty(), result.isEmpty());
        if (isCrafting || !mainNode.isActive()) return false;
        if (aspects == null || aspects.isEmpty() || result.isEmpty()) return false;

        this.isCrafting = true;
        this.craftTickCounter = 0;
        this.currentRecipe = arcanePattern.getRecipe();
        this.currentRecipeAspects = aspects.copy();
        this.currentResult = result.copy();
        this.internalInventory.setItem(TARGET_SLOT, result.copy());
        setChanged();
        markForUpdate();
        ThaumicEnergistics.LOG.info("[AA] beginCrafting: STARTED, result={}, aspects={}", result, aspects.visSize());
        return true;
    }

    @Override
    public boolean isBusy() {
        return isCrafting;
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        ItemStack icon = new ItemStack(thaumicenergistics.init.ModItems.ARCANE_ASSEMBLER_ITEM.get());
        return new PatternContainerGroup(
                AEItemKey.of(icon),
                net.minecraft.network.chat.Component.translatable("block.thaumicenergistics.arcane_assembler"),
                List.of()
        );
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
        if (patternDetails instanceof ArcaneAssemblerPattern arcanePattern) {
            return beginCrafting(arcanePattern);
        }
        return false;
    }

    @Override
    public boolean acceptsPlans() {
        return !isCrafting;
    }

    @Nullable
    public IArcaneRecipe findMatchingArcaneRecipe(Container craftingGrid) {
        if (level == null) return null;
        for (Object recipeObj : ThaumcraftApi.getCraftingRecipes()) {
            if (recipeObj instanceof IArcaneRecipe recipe) {
                try {
                    if (recipe.matches(craftingGrid, level, null)) {
                        return recipe;
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    public boolean isCrafting() { return isCrafting; }
    public int getSpeedUpgrades() { return speedUpgrades; }
    public int getCraftTickCounter() { return craftTickCounter; }
    public int getTicksPerCraft() { return ticksPerCraft(); }
    public float getCraftProgress() {
        if (!isCrafting || ticksPerCraft() <= 0) return 0f;
        return Math.min(1f, (float) craftTickCounter / ticksPerCraft());
    }
    public AspectList getStoredVis() { return storedVis; }
    public SimpleContainer getInternalInventory() { return internalInventory; }
    public float getVisDiscountForAspect(Aspect aspect) { return visDiscount.getOrDefault(aspect, 1.0F); }
    public float getWarpPowerMultiplier() { return warpPowerMultiplier; }

    public void setSpeedUpgrades(int count) {
        this.speedUpgrades = Math.min(count, MAX_SPEED_UPGRADES);
        setChanged();
    }

    public short getVisSourceFreq() { return visSourceFreq; }

    /**
     * 内存卡右键装配器：读 P2P 频率，设为 vis 源。
     * 复刻 1.7.10 onMemoryCardActivate，但用 P2P 频率替代 DigiVisSourceData 位置（适配 neo vis_interface P2P）。
     * 卡上有 EXPORTED_P2P_FREQUENCY → 存频率；空白卡 → 清空；其它 → 无效。
     */
    public void onMemoryCardActivate(net.minecraft.world.entity.player.Player player,
                                     appeng.api.implementations.items.IMemoryCard memoryCard,
                                     net.minecraft.world.item.ItemStack heldItem) {
        Short freq = heldItem.get(appeng.api.ids.AEComponents.EXPORTED_P2P_FREQUENCY);
        if (freq != null) {
            // 卡上带 P2P 频率（从 vis_interface shift+右键复制来的）→ 设为 vis 源
            this.visSourceFreq = freq;
            this.setChanged();
            memoryCard.notifyUser(player, appeng.api.implementations.items.MemoryCardMessages.SETTINGS_LOADED);
        } else if (heldItem.get(appeng.api.ids.AEComponents.EXPORTED_SETTINGS_SOURCE) == null) {
            // 空白卡 → 清空 vis 源
            if (this.visSourceFreq != 0) {
                this.visSourceFreq = 0;
                this.setChanged();
            }
            memoryCard.notifyUser(player, appeng.api.implementations.items.MemoryCardMessages.SETTINGS_CLEARED);
        } else {
            // 卡上有其它配置（非 P2P 频率）→ 不兼容
            memoryCard.notifyUser(player, appeng.api.implementations.items.MemoryCardMessages.INVALID_MACHINE);
        }
    }

    public void onInventoryChanged(int slot) {
        if (slot == KCORE_SLOT) {
            stalePatterns = true;
            updatePatternSlots();
            // 直接通知 AE2 合成服务重新拉取 getAvailablePatterns()。
            // 之前仅靠 tickingRequest 里处理 stalePatterns 再 requestUpdate，
            // 但节点休眠时 tickingRequest 不被调用，AE2 永远学不到 kCore 里的样板 → ME 终端无可合成项。
            // 守卫 getGrid()!=null：装配器未连 ME 网络（无相邻 cable）时 getGrid() 为 null，
            // 此时 requestUpdate 内部 node.getGrid().getCraftingService() 会 NPE，导致 kCore 插入事务回滚 → "无法绑定"。
            if (level != null && !level.isClientSide() && mainNode.getGrid() != null) {
                ThaumicEnergistics.LOG.info("[AA] kCore slot changed → requestUpdate");
                ICraftingProvider.requestUpdate(mainNode);
            }
        }
        if (slot >= ARMOR_SLOT_START && slot <= ARMOR_SLOT_END) {
            calculateVisDiscounts();
        }
        if (slot >= PATTERN_SLOT_START && slot <= PATTERN_SLOT_END) {
            stalePatterns = true;
        }
        setChanged();
    }

    private void updatePatternSlots() {
        ItemStack kCore = internalInventory.getItem(KCORE_SLOT);
        if (kCore.isEmpty() || !kCore.is(ModItems.KNOWLEDGE_CORE.get())) {
            for (int i = PATTERN_SLOT_START; i <= PATTERN_SLOT_END; i++) {
                internalInventory.setItem(i, ItemStack.EMPTY);
            }
            return;
        }

        HandlerKnowledgeCore handler = new HandlerKnowledgeCore(kCore);
        ArrayList<ItemStack> outputs = handler.hasCore() ? handler.getStoredOutputs() : new ArrayList<>();
        for (int i = PATTERN_SLOT_START; i <= PATTERN_SLOT_END; i++) {
            int idx = i - PATTERN_SLOT_START;
            if (idx < outputs.size()) {
                internalInventory.setItem(i, outputs.get(idx).copy());
            } else {
                internalInventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    public void markForUpdate() {
        if (level != null) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            // 关键：blockEntityChanged 触发 BE 数据（getUpdateTag）同步到客户端。
            // setChanged 只标记区块保存、sendBlockUpdated 只触发重渲染——缺这个 GUI 永远拿不到 storedVis/isCrafting。
            level.blockEntityChanged(worldPosition);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = super.getUpdateTag(reg);
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = internalInventory.getItem(i);
            if (!stack.isEmpty()) {
                tag.put("Slot" + i, stack.save(reg));
            }
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider reg) {
        super.handleUpdateTag(tag, reg);
        loadingFromClientTag = true;
        try {
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (tag.contains("Slot" + i)) {
                    ItemStack stack = ItemStack.parseOptional(reg, tag.getCompound("Slot" + i));
                    internalInventory.setItem(i, stack);
                } else {
                    internalInventory.setItem(i, ItemStack.EMPTY);
                }
            }
        } finally {
            loadingFromClientTag = false;
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        mainNode.loadFromNBT(tag);
        mainNode.setIdlePowerUsage(isCrafting ? ACTIVE_POWER : IDLE_POWER);

        if (tag.contains("AttachSide")) attachmentSide = Direction.from3DDataValue(tag.getInt("AttachSide"));
        isCrafting = tag.getBoolean("IsCrafting");
        craftTickCounter = tag.getInt("CraftTick");
        speedUpgrades = tag.getInt("SpeedUpgrades");
        visSourceFreq = tag.contains("VisSourceFreq") ? tag.getShort("VisSourceFreq") : 0;

        storedVis = new AspectList();
        if (tag.contains("StoredVis")) {
            CompoundTag visTag = tag.getCompound("StoredVis");
            for (Aspect primal : PRIMALS) {
                String key = primal.getTag();
                if (visTag.contains(key)) {
                    storedVis.add(primal, visTag.getInt(key));
                }
            }
        }

        for (int i = 0; i < SLOT_COUNT; i++) {
            if (tag.contains("Slot" + i)) {
                ItemStack stack = ItemStack.parseOptional(reg, tag.getCompound("Slot" + i));
                internalInventory.setItem(i, stack);
            } else {
                internalInventory.setItem(i, ItemStack.EMPTY);
            }
        }

        updatePatternSlots();
        stalePatterns = true;
        calculateVisDiscounts();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("AttachSide", attachmentSide.get3DDataValue());
        mainNode.saveToNBT(tag);
        tag.putBoolean("IsCrafting", isCrafting);
        tag.putInt("CraftTick", craftTickCounter);
        tag.putInt("SpeedUpgrades", speedUpgrades);
        if (visSourceFreq != 0) tag.putShort("VisSourceFreq", visSourceFreq);

        CompoundTag visTag = new CompoundTag();
        for (Aspect primal : PRIMALS) {
            int amount = storedVis.getAmount(primal);
            if (amount > 0) {
                visTag.putInt(primal.getTag(), amount);
            }
        }
        tag.put("StoredVis", visTag);

        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = internalInventory.getItem(i);
            if (!stack.isEmpty()) {
                tag.put("Slot" + i, stack.save(reg));
            }
        }
    }

    public static class ArcaneAssemblerPattern implements IPatternDetails {
        private final AEItemKey definition;
        private final IArcaneRecipe recipe;
        private final AspectList aspects;
        private final ItemStack resultStack;
        private final IInput[] inputs;
        private final String research;

        public ArcaneAssemblerPattern(AEItemKey definition, IArcaneRecipe recipe,
                                      AspectList aspects, ItemStack resultStack,
                                      List<GenericStack> inputStacks) {
            this(definition, recipe, aspects, resultStack, inputStacks, null);
        }

        public ArcaneAssemblerPattern(AEItemKey definition, IArcaneRecipe recipe,
                                      AspectList aspects, ItemStack resultStack,
                                      List<GenericStack> inputStacks, String research) {
            this.definition = definition;
            this.recipe = recipe;
            this.aspects = aspects;
            this.resultStack = resultStack;
            this.research = research;
            this.inputs = new IInput[inputStacks.size()];
            for (int i = 0; i < inputStacks.size(); i++) {
                GenericStack stack = inputStacks.get(i);
                this.inputs[i] = new IInput() {
                    @Override
                    public GenericStack[] getPossibleInputs() {
                        return new GenericStack[]{stack};
                    }

                    @Override
                    public long getMultiplier() {
                        return 1;
                    }

                    @Override
                    public boolean isValid(AEKey input, Level level) {
                        return input instanceof AEItemKey itemKey
                                && stack.what() instanceof AEItemKey stackKey
                                && itemKey.equals(stackKey);
                    }

                    @Nullable
                    @Override
                    public AEKey getRemainingKey(AEKey what) {
                        return null;
                    }
                };
            }
        }

        @Override
        public AEItemKey getDefinition() { return definition; }

        @Override
        public IInput[] getInputs() { return inputs; }

        @Override
        public List<GenericStack> getOutputs() {
            return List.of(new GenericStack(AEItemKey.of(resultStack), resultStack.getCount()));
        }

        @Override
        public boolean supportsPushInputsToExternalInventory() {
            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ArcaneAssemblerPattern other)) return false;
            return definition != null && definition.equals(other.definition);
        }

        @Override
        public int hashCode() {
            return definition != null ? definition.hashCode() : 0;
        }

        public IArcaneRecipe getRecipe() { return recipe; }
        public AspectList getAspects() { return aspects; }
        public ItemStack getResultStack() { return resultStack; }
        /** 对应 TC 奥术配方的研究键；无研究要求/老数据时可能为 null。 */
        @Nullable
        public String getResearch() { return research; }
    }
}
