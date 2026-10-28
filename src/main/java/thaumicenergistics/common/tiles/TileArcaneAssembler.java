package thaumicenergistics.common.tiles;

import java.util.ArrayList;
import thaumicenergistics.common.crafting.ArcaneAssemblerPattern;
import thaumicenergistics.common.crafting.ArcaneCraftJob;
import java.util.EnumSet;
import java.util.Hashtable;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
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
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;

import thaumcraft.api.IVisDiscountGear;
import thaumcraft.api.IWarpingGear;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;

import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.integration.tc.ArcaneCraftingPattern;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.common.inventory.HandlerKnowledgeCore;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModItems;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.me.service.P2PService;
import appeng.parts.p2p.P2PTunnelPart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics.common.network.AssemblerPreviewSyncPacket;
import thaumicenergistics.common.parts.VisInterfacePart;

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
    /** vis 变化累计达到该值才同步客户端（约 4×5=20 tick）；存档变化即时标记。 */
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
    private final ArcaneCraftJob job = new ArcaneCraftJob();
    /** 旧存档缺少订单定义时保留忙碌状态，等待核心提供唯一配方。 */
    private boolean legacyCraftBlocked;

    /**
     * 从内存卡粘贴的 vis_interface P2P 频率；装配器优先从该频率的 vis_interface P2P 端抽 vis。
     * 0 = 未设置，走 essentia 网络。对应 1.7.10 的 visSourceInfo，但用 P2P 频率替代位置（适配 neo P2P 设计）。
     */
    private short visSourceFreq = 0;
    private AspectList storedVis = new AspectList();
    private Hashtable<Aspect, Float> visDiscount = new Hashtable<>();
    private float warpPowerMultiplier = 1.0F;
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
            // 制作前退真实材料，制作后只掉落尚未输出的产物和催化剂。
            var remaining = job.produced() ? job.outputs() : java.util.Arrays.asList(job.inputs());
            if (isCrafting) {
                for (ItemStack stack : remaining) {
                    if (!stack.isEmpty()) Containers.dropItemStack(level,
                            worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack.copy());
                }
                finishCrafting();
            }
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

        P2PService p2p = P2PService.get(grid);
        if (p2p == null) {
            return;
        }
        P2PTunnelPart<?> input = p2p.getInput(visSourceFreq);
        if (!(input instanceof VisInterfacePart visSource)) {
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
     * replenishVis 抽到 vis 时调用：累计变化次数，每次保存变化，SET_CHANGED_INTERVAL 次才同步客户端一次。
     * markForUpdate = setChanged + sendBlockUpdated（同步 storedVis 到客户端 BE，GUI 进度条依赖客户端 storedVis）。
     */
    private void maybeMarkVisDirty() {
        setChanged();
        if (++visSetChangedCounter >= SET_CHANGED_INTERVAL) {
            visSetChangedCounter = 0;
            markForUpdate();
        }
    }

    private TickRateModulation craftingTick(IGrid grid, int ticksSinceLast) {
        if (job.produced()) return flushCraftOutputs(grid);
        if (job.result().isEmpty()) {
            if (!legacyCraftBlocked) restoreLegacyCraft();
            if (job.result().isEmpty()) return TickRateModulation.IDLE;
        }

        boolean enoughVis = hasEnoughVisForCraft();
        if (!enoughVis) {
            return TickRateModulation.SAME;
        }

        int ticksPerCraft = ticksPerCraft();

        if (craftTickCounter >= ticksPerCraft) {
            consumeVisForCraft();
            job.produce(retainedPearlStacks());
            setChanged();
            return flushCraftOutputs(grid);
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

    private TickRateModulation flushCraftOutputs(IGrid grid) {
        var storage = grid.getStorageService().getInventory();
        for (var output : job.outputs()) {
            if (output.isEmpty()) continue;
            long accepted = storage.insert(AEItemKey.of(output), output.getCount(), Actionable.MODULATE, actionSource);
            if (accepted > 0) {
                output.shrink((int) Math.min(accepted, output.getCount()));
                setChanged();
            }
        }
        job.outputs().removeIf(ItemStack::isEmpty);
        if (!job.outputs().isEmpty()) return TickRateModulation.SAME;
        finishCrafting();
        return TickRateModulation.URGENT;
    }

    private void finishCrafting() {
        isCrafting = false;
        legacyCraftBlocked = false;
        job.clear();
        internalInventory.setItem(TARGET_SLOT, ItemStack.EMPTY);
        mainNode.setIdlePowerUsage(IDLE_POWER);
        markForUpdate();
        AssemblerPreviewSyncPacket.send(this, false);
    }

    private void consumeVisForCraft() {
        if (job.aspects() == null || job.aspects().isEmpty()) return;
        for (Aspect aspect : job.aspects().getAspects()) {
            int requiredCvis = getRequiredCvisForAspect(aspect);
            int current = storedVis.getAmount(aspect);
            storedVis.reduce(aspect, Math.min(requiredCvis, current));
        }
        maybeMarkVisDirty();
    }

    private boolean hasEnoughVisForCraft() {
        if (job.aspects() == null || job.aspects().isEmpty()) return true;
        for (Aspect aspect : job.aspects().getAspects()) {
            int requiredCvis = getRequiredCvisForAspect(aspect);
            if (storedVis.getAmount(aspect) < requiredCvis) return false;
        }
        return true;
    }

    private int getRequiredCvisForAspect(Aspect aspect) {
        if (job.aspects() == null) return 0;
        int baseCost = job.aspects().getAmount(aspect) * CVIS_MULTIPLIER;
        float discount = visDiscount.getOrDefault(aspect, BASE_VIS_DISCOUNT);
        return Math.max(0, Math.round(baseCost * discount));
    }

    /**
     * 配方 3×3 网格中需保留的元始珍珠栈。TC 奥术配方中珍珠是唯一 retainIngredient 原料：
     * 在奥术工作台/奥术合成终端中作为"催化"留在合成格不消耗，装配器合成完成后也要退回网络。
     */
    private List<ItemStack> retainedPearlStacks() {
        List<ItemStack> pearls = new ArrayList<>();
        if (job.inputs() == null) return pearls;
        for (ItemStack stack : job.inputs()) {
            if (!stack.isEmpty() && stack.getItem() == BuiltInRegistries.ITEM.get(PRIMORDIAL_PEARL_ID)) {
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
            return beginCrafting(arcanePattern, inputHolder);
        }
        return false;
    }

    public static ArcaneAssemblerPattern buildArcanePattern(ArcaneCraftingPattern acp) {
        ItemStack result = acp.getResult();
        if (result == null || result.isEmpty()) return null;

        AspectList aspects = acp.getAspects();
        ItemStack[] ingredients = acp.getIngredients();

        List<GenericStack> inputs = new ArrayList<>();
        List<GenericStack[]> alternatives = new ArrayList<>();
        if (ingredients != null) {
            for (ItemStack ing : ingredients) {
                if (ing == null || ing.isEmpty()) continue;
                AEItemKey key = AEItemKey.of(ing);
                if (key != null) {
                    inputs.add(new GenericStack(key, ing.getCount()));
                    alternatives.add(expandTagAlternates(ing));
                }
            }
        }

        return new ArcaneAssemblerPattern(thaumicenergistics.common.crafting.ArcanePatternDecoder.definition(acp), null, aspects, result, inputs,
                acp.getResearch(), acp.getIngredients(), alternatives);
    }

    /**
     * Keep the encoded stack as the primary input shown by the crafting planner.
     * Explicit component patches are part of its identity and must never be
     * replaced with bare items from a tag. Component-free tag recipes can still
     * substitute other valid variants, after the originally selected item.
     */
    private static GenericStack[] expandTagAlternates(ItemStack ing) {
        GenericStack encoded = new GenericStack(AEItemKey.of(ing), ing.getCount());
        if (!ing.getComponentsPatch().isEmpty()) {
            return new GenericStack[]{encoded};
        }
        for (TagKey<Item> tag : ing.getItemHolder().tags().toList()) {
            if (!tag.location().getNamespace().equals(ThaumicEnergistics.MODID)) continue;
            var named = BuiltInRegistries.ITEM.getTag(tag);
            if (named.isEmpty()) continue;
            List<GenericStack> alts = new ArrayList<>();
            alts.add(encoded);
            for (Holder<Item> holder : named.get()) {
                ItemStack candidate = holder.value().getDefaultInstance();
                // The generic wisp essence has no aspect without its component.
                // Tag membership alone does not make it a usable alternative.
                if (tag.location().getPath().equals("wisp_essences")
                        && !hasWispAspect(candidate)) continue;
                AEItemKey key = AEItemKey.of(candidate);
                if (key != null && alts.stream().noneMatch(alt -> alt.what().equals(key))) {
                    alts.add(new GenericStack(key, ing.getCount()));
                }
            }
            if (alts.size() > 1) return alts.toArray(new GenericStack[0]);
        }
        return new GenericStack[]{encoded};
    }

    private static boolean hasWispAspect(ItemStack stack) {
        if (!(stack.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem container)) {
            return false;
        }
        AspectList contents = container.getAspects(stack);
        return contents != null && !contents.isEmpty();
    }

    private boolean beginCrafting(ArcaneAssemblerPattern arcanePattern, KeyCounter[] inputHolder) {
        AspectList aspects = arcanePattern.getAspects();
        ItemStack result = arcanePattern.getResultStack();
        if (isCrafting || !mainNode.isActive()) return false;
        if (aspects == null || aspects.isEmpty() || result.isEmpty()) return false;
        var actualGrid = arcanePattern.materializeInputs(inputHolder, level);
        if (actualGrid == null) return false;

        this.isCrafting = true;
        this.craftTickCounter = 0;
        job.start(aspects, result, actualGrid);
        for (var holder : inputHolder) holder.reset();
        this.internalInventory.setItem(TARGET_SLOT, result.copy());
        setChanged();
        markForUpdate();
        AssemblerPreviewSyncPacket.send(this, true);
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
                Component.translatable("block.thaumicenergistics.arcane_assembler"),
                List.of()
        );
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
        if (patternDetails instanceof ArcaneAssemblerPattern arcanePattern) {
            return beginCrafting(arcanePattern, inputs);
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
    public ItemStack[] getPreviewGrid() { return job.inputs(); }
    /** 当前合成产物（GUI 预产物显示；未合成为空）。 */
    public ItemStack getCurrentResult() { return job.result(); }
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
    public void onMemoryCardActivate(Player player,
                                     IMemoryCard memoryCard,
                                     net.minecraft.world.item.ItemStack heldItem) {
        Short freq = heldItem.get(AEComponents.EXPORTED_P2P_FREQUENCY);
        if (freq != null) {
            // 卡上带 P2P 频率（从 vis_interface shift+右键复制来的）→ 设为 vis 源
            this.visSourceFreq = freq;
            this.setChanged();
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
        } else if (heldItem.get(AEComponents.EXPORTED_SETTINGS_SOURCE) == null) {
            // 空白卡 → 清空 vis 源
            if (this.visSourceFreq != 0) {
                this.visSourceFreq = 0;
                this.setChanged();
            }
            memoryCard.notifyUser(player, MemoryCardMessages.SETTINGS_CLEARED);
        } else {
            // 卡上有其它配置（非 P2P 频率）→ 不兼容
            memoryCard.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
        }
    }

    public void onInventoryChanged(int slot) {
        if (slot == KCORE_SLOT) {
            legacyCraftBlocked = false;
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
            ItemStack stack = job.inputs()[i];
            if (stack != null && !stack.isEmpty()) {
                gridTag.put("g" + i, stack.save(reg));
            }
        }
        tag.put("PreviewGrid", gridTag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider reg) {
        loadingFromClientTag = true;
        try {
            super.handleUpdateTag(tag, reg);
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (tag.contains("Slot" + i)) {
                    ItemStack stack = ItemStack.parseOptional(reg, tag.getCompound("Slot" + i));
                    internalInventory.setItem(i, stack);
                } else {
                    internalInventory.setItem(i, ItemStack.EMPTY);
                }
            }
            job.clearPreview();
            if (tag.contains("PreviewGrid")) {
                CompoundTag gridTag = tag.getCompound("PreviewGrid");
                for (int i = 0; i < 9; i++) {
                    if (gridTag.contains("g" + i)) {
                        job.inputs()[i] = ItemStack.parseOptional(reg, gridTag.getCompound("g" + i));
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

        job.load(tag, reg);
        legacyCraftBlocked = false;
        if (isCrafting && job.result().isEmpty()) restoreLegacyCraft();
        // 制作电力由 craftingTick 实扣，重载不能额外叠加节点闲置耗电。
        mainNode.setIdlePowerUsage(IDLE_POWER);

        updatePatternSlots();
        stalePatterns = true;
        calculateVisDiscounts();
        recountSpeedUpgrades();
    }

    /** 老存档只保存目标槽：仅在知识核心能唯一确定配方时恢复，避免猜错配方。 */
    private void restoreLegacyCraft() {
        var target = internalInventory.getItem(TARGET_SLOT);
        var core = internalInventory.getItem(KCORE_SLOT);
        if (!target.isEmpty() && core.is(ModItems.KNOWLEDGE_CORE.get())) {
            var matches = new HandlerKnowledgeCore(core).getPatterns().stream()
                    .filter(pattern -> pattern != null && pattern.isPatternValid()
                            && ItemStack.matches(pattern.getResult(), target)).toList();
            if (matches.size() == 1) {
                var pattern = matches.getFirst();
                job.start(pattern.getAspects(), target, pattern.getIngredients());
                legacyCraftBlocked = false;
                return;
            }
        }
        ThaumicEnergistics.LOG.warn("Pausing legacy arcane craft at {}: missing or ambiguous pattern; restore a unique knowledge-core recipe to resume", worldPosition);
        legacyCraftBlocked = true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("AttachSide", attachmentSide.get3DDataValue());
        mainNode.saveToNBT(tag);
        tag.putBoolean("IsCrafting", isCrafting);
        tag.putInt("CraftTick", craftTickCounter);
        tag.putInt("SpeedUpgrades", speedUpgrades);
        job.save(tag, reg);
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

}
