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
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.IArcaneRecipe;

import thaumicenergistics.api.grid.IEssentiaGrid;
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

    /** 基础 vis 折扣（0.8 = 80% 成本）。calculateVisDiscounts 从该值起再减护甲折扣；无护甲时即此值。 */
    private static final float BASE_VIS_DISCOUNT = 0.8F;
    private static final int CVIS_MULTIPLIER = 10;
    private static final int MAX_STORED_CVIS = 187 * CVIS_MULTIPLIER;
    private static final int BASE_TICKS_PER_CRAFT = 20;
    private static final double IDLE_POWER = 0.0;
    private static final double ACTIVE_POWER = 1.5;
    private static final double WARP_POWER_PERCENT = 0.15;
    private static final int MAX_SPEED_UPGRADES = 4;
    /** vis 充能间隔（tick）：每 5 tick 充一次，等效 20 cvis/tick，渐进但够快，避免合成长期卡"等待"。 */
    private static final int VIS_REPLENISH_INTERVAL = 5;
    /** 每次充能上限（cvis/次，每 VIS_REPLENISH_INTERVAL tick 一次 ≈ 20 cVis/tick）。 */
    private static final int MAX_CVIS_PER_REPLENISH = 100;
    /** 每次实际 vis 变化累计达到该值才调用一次 setChanged（约 4×5=20 tick），避免合成期间 chunk 持续处于 unsaved 状态。 */
    private static final int SET_CHANGED_INTERVAL = 4;
    /** 元始珍珠：TC 奥术配方中唯一 retainIngredient 原料（ShapedArcaneRecipe.retainIngredient），合成不消耗。 */
    private static final ResourceLocation PRIMORDIAL_PEARL_ID =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "primordial_pearl");

    public static final int SLOT_COUNT = 31;
    public static final int KCORE_SLOT = 0;
    public static final int PATTERN_SLOT_START = 1;
    public static final int PATTERN_SLOT_END = 21;
    public static final int TARGET_SLOT = 22;
    public static final int ARMOR_SLOT_START = 23;
    public static final int ARMOR_SLOT_END = 26;
    /** 加速卡槽：持久化在 Tile 内（此前放容器局部 handler，关 GUI 即丢失→吞卡）。 */
    public static final int UPGRADE_SLOT_START = 27;
    public static final int UPGRADE_SLOT_END = 30;

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
    /** 当前合成配方的 3×3 材料网格（GUI 右下配方预览用，随合成开始填充、结束清空）。 */
    private ItemStack[] previewGrid = emptyPreviewGrid();

    /** 全 EMPTY 的 9 格预览数组（避免 null 元素导致网络包编码崩溃）。 */
    private static ItemStack[] emptyPreviewGrid() {
        ItemStack[] grid = new ItemStack[9];
        java.util.Arrays.fill(grid, ItemStack.EMPTY);
        return grid;
    }
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
            visDiscount.put(primal, BASE_VIS_DISCOUNT);
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
        // 配置了 vis 源（内存卡设了 VisInterface 频率）就持续充能：放置时要素条也显示充能过程，
        // 合成时直接用缓存，不会卡"等待"。未配置源不充能（要素条空）。
        if ((isCrafting || visSourceFreq != 0) && visTickCounter >= VIS_REPLENISH_INTERVAL) {
            visTickCounter = 0;
            replenishVis(grid);
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
        // 充能前提：必须先用内存卡配置 vis 源（VisInterface P2P 频率）。
        if (visSourceFreq == 0) {
            return;
        }

        appeng.me.service.P2PService p2p = appeng.me.service.P2PService.get(grid);
        if (p2p == null) {
            return;
        }
        appeng.parts.p2p.P2PTunnelPart<?> input = p2p.getInput(visSourceFreq);
        if (!(input instanceof thaumicenergistics.common.parts.VisInterfacePart visSource)) {
            return; // 频率设了但找不到 vis_interface P2P 输入 → 不抽
        }

        // 限速充能。
        // 关键单位换算：TC 的 consumeVis/consumeEnergizedVis 返回的是 vis（玩家看到的"X 点要素"），
        // 而装配器 storedVis 用 cvis（×CVIS_MULTIPLIER）。返回的 vis 必须 ×CVIS_MULTIPLIER 才等于 cvis，
        // 否则合成按 cvis 判定永远不够（之前少了 10 倍导致卡"等待"）。
        for (Aspect primal : PRIMALS) {
            int needed = MAX_STORED_CVIS - storedVis.getAmount(primal);
            if (needed <= 0) continue;
            // 请求的 vis 数 = 需要补齐的 cvis 换算成 vis，且受每次上限约束
            int wantVis = Math.max(1, Math.min(needed / CVIS_MULTIPLIER, MAX_CVIS_PER_REPLENISH / CVIS_MULTIPLIER));
            ResourceLocation id = TCReflection.getAspectId(primal);
            if (id == null) continue;
            int gotVis = visSource.consumeVis(id, wantVis);
            if (gotVis > 0) {
                storedVis.add(primal, gotVis * CVIS_MULTIPLIER);
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
            previewGrid = emptyPreviewGrid();
            thaumicenergistics.common.network.AssemblerPreviewSyncPacket.send(this, false);
            return TickRateModulation.IDLE;
        }

        boolean enoughVis = hasEnoughVisForCraft();
        if (!enoughVis) {
            return TickRateModulation.SAME;
        }

        int ticksPerCraft = ticksPerCraft();

        if (craftTickCounter >= ticksPerCraft) {
            IStorageService storage = grid.getService(IStorageService.class);
            if (storage == null) return TickRateModulation.IDLE;

            ItemStack output = currentResult.copy();
            AEItemKey outputKey = AEItemKey.of(output);
            if (outputKey == null) return TickRateModulation.IDLE;

            // 元始珍珠保留：TC 唯一 retainIngredient 原料，合成后珍珠退回网络（与奥术合成终端一致）。
            // CPU 已按配方从网络扣除珍珠，这里补回 → 净零不消耗。
            var retainedPearls = retainedPearlStacks();

            long inserted = storage.getInventory().insert(
                    outputKey, output.getCount(), Actionable.SIMULATE, actionSource);
            if (inserted < output.getCount()) {
                return TickRateModulation.SAME;
            }
            // 预检：珍珠类型塞满放不回网络则等待，避免合成完成却丢珍珠
            for (ItemStack pearl : retainedPearls) {
                AEItemKey pearlKey = AEItemKey.of(pearl);
                if (pearlKey == null) continue;
                if (storage.getInventory().insert(
                        pearlKey, pearl.getCount(), Actionable.SIMULATE, actionSource) < pearl.getCount()) {
                    return TickRateModulation.SAME;
                }
            }

            consumeVisForCraft();

            storage.getInventory().insert(
                    outputKey, output.getCount(), Actionable.MODULATE, actionSource);
            // 退回珍珠（数量 = 3×3 配方网格中珍珠总数，通常 1 颗）
            for (ItemStack pearl : retainedPearls) {
                AEItemKey pearlKey = AEItemKey.of(pearl);
                if (pearlKey != null) {
                    storage.getInventory().insert(pearlKey, pearl.getCount(), Actionable.MODULATE, actionSource);
                }
            }

            isCrafting = false;
            currentRecipe = null;
            currentResult = ItemStack.EMPTY;
            currentRecipeAspects = new AspectList();
            previewGrid = emptyPreviewGrid();
            internalInventory.setItem(TARGET_SLOT, ItemStack.EMPTY);
            setChanged();
            markForUpdate();
            thaumicenergistics.common.network.AssemblerPreviewSyncPacket.send(this, false);

            return TickRateModulation.URGENT;
        }

        double powerRequired = (ACTIVE_POWER + (ACTIVE_POWER * speedUpgrades) / 2.0) * warpPowerMultiplier;
        IEnergyService energy = grid.getService(IEnergyService.class);
        if (energy != null) {
            double need = powerRequired * ticksSinceLast;
            double extracted = energy.extractAEPower(need, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (extracted >= need * 0.9) {
                craftTickCounter += ticksSinceLast;
                // 节流同步合成进度到客户端（GUI 第7格进度条动画）
                maybeMarkVisDirty();
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
        float discount = visDiscount.getOrDefault(aspect, BASE_VIS_DISCOUNT);
        return Math.max(0, Math.round(baseCost * discount));
    }

    /**
     * 配方 3×3 网格中需保留的元始珍珠栈。TC 奥术配方中珍珠是唯一 retainIngredient 原料：
     * 在奥术工作台/奥术合成终端中作为"催化"留在合成格不消耗，装配器合成完成后也要退回网络。
     */
    private List<ItemStack> retainedPearlStacks() {
        List<ItemStack> pearls = new ArrayList<>();
        if (previewGrid == null) return pearls;
        for (ItemStack stack : previewGrid) {
            if (!stack.isEmpty() && stack.getItem() == net.minecraft.core.registries.BuiltInRegistries.ITEM.get(PRIMORDIAL_PEARL_ID)) {
                pearls.add(stack.copy());
            }
        }
        return pearls;
    }

    private int ticksPerCraft() {
        return Math.max(4, BASE_TICKS_PER_CRAFT - (4 * speedUpgrades));
    }

    private void calculateVisDiscounts() {
        for (Aspect primal : PRIMALS) {
            float discount = BASE_VIS_DISCOUNT;

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
            }
        }
        return result;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
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
                acp.getResearch(), acp.getIngredients());
    }

    private boolean beginCrafting(ArcaneAssemblerPattern arcanePattern) {
        AspectList aspects = arcanePattern.getAspects();
        ItemStack result = arcanePattern.getResultStack();
        if (isCrafting || !mainNode.isActive()) return false;
        if (aspects == null || aspects.isEmpty() || result.isEmpty()) return false;

        this.isCrafting = true;
        this.craftTickCounter = 0;
        this.currentRecipe = arcanePattern.getRecipe();
        this.currentRecipeAspects = aspects.copy();
        this.currentResult = result.copy();
        // 配方预览：复制 3×3 材料网格（不足 9 格补空）
        this.previewGrid = emptyPreviewGrid();
        ItemStack[] grid = arcanePattern.getGrid();
        if (grid != null) {
            for (int i = 0; i < 9 && i < grid.length; i++) {
                this.previewGrid[i] = grid[i] != null ? grid[i].copy() : ItemStack.EMPTY;
            }
        }
        this.internalInventory.setItem(TARGET_SLOT, result.copy());
        setChanged();
        markForUpdate();
        thaumicenergistics.common.network.AssemblerPreviewSyncPacket.send(this, true);
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

    public boolean isCrafting() { return isCrafting; }
    public int getSpeedUpgrades() { return speedUpgrades; }
    public int getCraftTickCounter() { return craftTickCounter; }
    public int getTicksPerCraft() { return ticksPerCraft(); }
    /** 当前合成配方的 3×3 材料网格（GUI 右下配方预览；未合成时为空数组）。 */
    public ItemStack[] getPreviewGrid() { return previewGrid; }
    /** 当前合成产物（GUI 预产物显示；未合成为空）。 */
    public ItemStack getCurrentResult() { return currentResult; }
    public float getCraftProgress() {
        if (!isCrafting || ticksPerCraft() <= 0) return 0f;
        return Math.min(1f, (float) craftTickCounter / ticksPerCraft());
    }
    public AspectList getStoredVis() { return storedVis; }
    public SimpleContainer getInternalInventory() { return internalInventory; }
    public float getVisDiscountForAspect(Aspect aspect) { return visDiscount.getOrDefault(aspect, BASE_VIS_DISCOUNT); }
    public float getWarpPowerMultiplier() { return warpPowerMultiplier; }

    public void setSpeedUpgrades(int count) {
        this.speedUpgrades = Math.min(count, MAX_SPEED_UPGRADES);
        setChanged();
    }

    public short getVisSourceFreq() { return visSourceFreq; }
    /** ME 节点是否有能量（Jade 网络状态细分用）。 */
    public boolean isNodePowered() { return mainNode != null && mainNode.isPowered(); }

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
                ICraftingProvider.requestUpdate(mainNode);
            }
        }
        if (slot >= ARMOR_SLOT_START && slot <= ARMOR_SLOT_END) {
            calculateVisDiscounts();
        }
        if (slot >= UPGRADE_SLOT_START && slot <= UPGRADE_SLOT_END) {
            recountSpeedUpgrades();
        }
        if (slot >= PATTERN_SLOT_START && slot <= PATTERN_SLOT_END) {
            stalePatterns = true;
        }
        setChanged();
    }

    /** 按 Tile 内加速卡槽的实际卡片数量刷新 speedUpgrades（持久化计数，不再依赖菜单存活）。 */
    private void recountSpeedUpgrades() {
        int count = 0;
        for (int i = UPGRADE_SLOT_START; i <= UPGRADE_SLOT_END; i++) {
            if (!internalInventory.getItem(i).isEmpty()) count++;
        }
        setSpeedUpgrades(count);
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
        // 配方预览 3×3 材料（GUI 右下角显示）
        CompoundTag gridTag = new CompoundTag();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = previewGrid[i];
            if (stack != null && !stack.isEmpty()) {
                gridTag.put("g" + i, stack.save(reg));
            }
        }
        tag.put("PreviewGrid", gridTag);
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
            previewGrid = emptyPreviewGrid();
            if (tag.contains("PreviewGrid")) {
                CompoundTag gridTag = tag.getCompound("PreviewGrid");
                for (int i = 0; i < 9; i++) {
                    if (gridTag.contains("g" + i)) {
                        previewGrid[i] = ItemStack.parseOptional(reg, gridTag.getCompound("g" + i));
                    }
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
        recountSpeedUpgrades();
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
        /** 3×3 配方网格（知识核心存储的 9 格材料，GUI 配方预览用）。 */
        private final ItemStack[] grid;

        public ArcaneAssemblerPattern(AEItemKey definition, IArcaneRecipe recipe,
                                      AspectList aspects, ItemStack resultStack,
                                      List<GenericStack> inputStacks) {
            this(definition, recipe, aspects, resultStack, inputStacks, null, null);
        }

        public ArcaneAssemblerPattern(AEItemKey definition, IArcaneRecipe recipe,
                                      AspectList aspects, ItemStack resultStack,
                                      List<GenericStack> inputStacks, String research) {
            this(definition, recipe, aspects, resultStack, inputStacks, research, null);
        }

        public ArcaneAssemblerPattern(AEItemKey definition, IArcaneRecipe recipe,
                                      AspectList aspects, ItemStack resultStack,
                                      List<GenericStack> inputStacks, String research, ItemStack[] grid) {
            this.definition = definition;
            this.recipe = recipe;
            this.aspects = aspects;
            this.resultStack = resultStack;
            this.research = research;
            this.grid = grid;
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
                        // 元始珍珠作为"催化"原料：声明合成后它会返回网络（AE2 原生容器机制）。
                        // 规划端据此按"每次 1 单"顺序模拟、执行端把珍珠计入 CPU waitingFor，
                        // 机器合成完成把珍珠退回网络后 CPU 自动捕获续下一单——1 颗珍珠可连续多单。
                        if (what instanceof AEItemKey itemKey
                                && itemKey.getItem() == net.minecraft.core.registries.BuiltInRegistries.ITEM.get(PRIMORDIAL_PEARL_ID)) {
                            return what;
                        }
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
        /** 3×3 配方网格（可能为 null/短数组，GUI 预览用）。 */
        @Nullable
        public ItemStack[] getGrid() { return grid; }
        /** 对应 TC 奥术配方的研究键；无研究要求/老数据时可能为 null。 */
        @Nullable
        public String getResearch() { return research; }
    }
}
