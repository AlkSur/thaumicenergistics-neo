package tc_energy.common.tiles;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

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

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.item.VisDiscountGear;
import dev.tc4port.thaumcraft.api.item.WarpingGear;
import dev.tc4port.thaumcraft.api.recipe.ArcaneCraftingRecipe;

import tc_energy.api.grid.IEssentiaGrid;
import tc_energy.common.integration.tc.ArcaneCraftingPattern;
import tc_energy.common.integration.tc.TCReflection;
import tc_energy.common.inventory.HandlerKnowledgeCore;
import tc_energy.init.ModBlockEntities;
import tc_energy.init.ModItems;

public class TileArcaneAssembler extends ThETileBase
        implements IInWorldGridNodeHost, IActionHost, IGridTickable, ICraftingProvider, ICraftingMachine {

    public static final AspectId[] PRIMALS = {
            AspectId.parse("aer"), AspectId.parse("ignis"), AspectId.parse("aqua"),
            AspectId.parse("terra"), AspectId.parse("ordo"), AspectId.parse("perditio")
    };

    /** 鍩虹 vis 鎶樻墸锛?.8 = 80% 鎴愭湰锛夈€俢alculateVisDiscounts 浠庤鍊艰捣鍐嶅噺鎶ょ敳鎶樻墸锛涙棤鎶ょ敳鏃跺嵆姝ゅ€笺€?*/
    private static final float BASE_VIS_DISCOUNT = 0.8F;
    private static final int CVIS_MULTIPLIER = 10;
    private static final int MAX_STORED_CVIS = 187 * CVIS_MULTIPLIER;
    private static final int BASE_TICKS_PER_CRAFT = 20;
    private static final double IDLE_POWER = 0.0;
    private static final double ACTIVE_POWER = 1.5;
    private static final double WARP_POWER_PERCENT = 0.15;
    private static final int MAX_SPEED_UPGRADES = 4;
    /** vis 鍏呰兘闂撮殧锛坱ick锛夛細姣?5 tick 鍏呬竴娆★紝绛夋晥 20 cvis/tick锛屾笎杩涗絾澶熷揩锛岄伩鍏嶅悎鎴愰暱鏈熷崱"绛夊緟"銆?*/
    private static final int VIS_REPLENISH_INTERVAL = 5;
    /** 姣忔鍏呰兘涓婇檺锛坈vis/娆★紝姣?VIS_REPLENISH_INTERVAL tick 涓€娆?鈮?20 cVis/tick锛夈€?*/
    private static final int MAX_CVIS_PER_REPLENISH = 100;
    /** 姣忔瀹為檯 vis 鍙樺寲绱杈惧埌璇ュ€兼墠璋冪敤涓€娆?setChanged锛堢害 4脳5=20 tick锛夛紝閬垮厤鍚堟垚鏈熼棿 chunk 鎸佺画澶勪簬 unsaved 鐘舵€併€?*/
    private static final int SET_CHANGED_INTERVAL = 4;
    /** 鍏冨鐝嶇彔锛歍C 濂ユ湳閰嶆柟涓敮涓€ retainIngredient 鍘熸枡锛圫hapedArcaneRecipe.retainIngredient锛夛紝鍚堟垚涓嶆秷鑰椼€?*/
    private static final ResourceLocation PRIMORDIAL_PEARL_ID =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "primordial_pearl");

    public static final int SLOT_COUNT = 31;
    public static final int KCORE_SLOT = 0;
    public static final int PATTERN_SLOT_START = 1;
    public static final int PATTERN_SLOT_END = 21;
    public static final int TARGET_SLOT = 22;
    public static final int ARMOR_SLOT_START = 23;
    public static final int ARMOR_SLOT_END = 26;
    /** 鍔犻€熷崱妲斤細鎸佷箙鍖栧湪 Tile 鍐咃紙姝ゅ墠鏀惧鍣ㄥ眬閮?handler锛屽叧 GUI 鍗充涪澶扁啋鍚炲崱锛夈€?*/
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
    /** 褰撳墠鍚堟垚閰嶆柟鐨?3脳3 鏉愭枡缃戞牸锛圙UI 鍙充笅閰嶆柟棰勮鐢紝闅忓悎鎴愬紑濮嬪～鍏呫€佺粨鏉熸竻绌猴級銆?*/
    private ItemStack[] previewGrid = emptyPreviewGrid();

    /** 鍏?EMPTY 鐨?9 鏍奸瑙堟暟缁勶紙閬垮厤 null 鍏冪礌瀵艰嚧缃戠粶鍖呯紪鐮佸穿婧冿級銆?*/
    private static ItemStack[] emptyPreviewGrid() {
        ItemStack[] grid = new ItemStack[9];
        java.util.Arrays.fill(grid, ItemStack.EMPTY);
        return grid;
    }
    /**
     * 浠庡唴瀛樺崱绮樿创鐨?vis_interface P2P 棰戠巼锛涜閰嶅櫒浼樺厛浠庤棰戠巼鐨?vis_interface P2P 绔娊 vis銆?     * 0 = 鏈缃紝璧?essentia 缃戠粶銆傚搴?1.7.10 鐨?visSourceInfo锛屼絾鐢?P2P 棰戠巼鏇夸唬浣嶇疆锛堥€傞厤 neo P2P 璁捐锛夈€?     */
    private short visSourceFreq = 0;
    private Map<AspectId, Integer> storedVis = new java.util.LinkedHashMap<>();
    private Hashtable<AspectId, Float> visDiscount = new Hashtable<>();
    private float warpPowerMultiplier = 1.0F;
    private ArcaneCraftingRecipe currentRecipe = null;
    private Map<AspectId, Integer> currentRecipeAspects = new java.util.LinkedHashMap<>();
    private ItemStack currentResult = ItemStack.EMPTY;
    private int visTickCounter = 0;
    /** replenishVis 鐨?setChanged 鑺傛祦璁℃暟銆?*/
    private int visSetChangedCounter = 0;
    private boolean stalePatterns = false;
    /** 瀹㈡埛绔帴鏀跺叏閲忔Ы浣嶅悓姝ュ寘鏃剁疆 true锛岃烦杩?setItem 杩為攣鐨?setChanged/onInventoryChanged锛堝鎴风鏃犵綉鏍硷紝杩欎簺閫昏緫鏃犳剰涔夛級銆?*/
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
                .setVisualRepresentation(tc_energy.init.ModItems.ARCANE_ASSEMBLER_ITEM.get())
                .setInWorldNode(true)
                .setTagName("proxy")
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .addService(IGridTickable.class, this)
                .addService(ICraftingProvider.class, this);
        mainNode.setIdlePowerUsage(IDLE_POWER);
        actionSource = IActionSource.ofMachine(this);

        for (AspectId primal : PRIMALS) {
            visDiscount.put(primal, BASE_VIS_DISCOUNT);
        }
        for (AspectId primal : PRIMALS) {
            storedVis.put(primal, 0);
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
        // 绗笁涓弬鏁版槸 isSleeping銆備箣鍓嶄紶 !isCrafting锛岃閰嶅櫒鏀剧疆鏃?isCrafting=false 鈫?isSleeping=true锛?        // 鑺傜偣涓€娉ㄥ唽灏变紤鐪犱笖鏃犳硶鑷敜閱掞紙tickingRequest 涓嶈璋冪敤灏辨案杩滄棤娉曡繑鍥?WAKE锛夛紝
        // 瀵艰嚧 stalePatterns 鐨勫欢杩?requestUpdate 姘镐笉瑙﹀彂銆乿is 涓嶈ˉ鍏呫€佸悎鎴愪笉鎺ㄨ繘銆?        // 鏀逛负 false锛堜笉浼戠湢锛夛紝鐢?tickingRequest 杩斿洖鐨?TickRateModulation 鍔ㄦ€佹帶鍒堕€熺巼銆?
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
        // 閰嶇疆浜?vis 婧愶紙鍐呭瓨鍗¤浜?VisInterface 棰戠巼锛夊氨鎸佺画鍏呰兘锛氭斁缃椂瑕佺礌鏉′篃鏄剧ず鍏呰兘杩囩▼锛?        // 鍚堟垚鏃剁洿鎺ョ敤缂撳瓨锛屼笉浼氬崱"绛夊緟"銆傛湭閰嶇疆婧愪笉鍏呰兘锛堣绱犳潯绌猴級銆?
        if ((isCrafting || visSourceFreq != 0) && visTickCounter >= VIS_REPLENISH_INTERVAL) {
            visTickCounter = 0;
            replenishVis(grid);
        }

        if (isCrafting) {
            return craftingTick(grid, ticksSinceLast);
        }

        return TickRateModulation.IDLE;
    }

    /** 鍏ㄩ儴鍘熻川鍏冪礌鍧囧凡杈惧埌缂撳瓨涓婇檺銆?*/
    private boolean storedVisFull() {
        for (AspectId primal : PRIMALS) {
            if (storedVis.getOrDefault(primal, 0) < MAX_STORED_CVIS) {
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
        if (!(input instanceof tc_energy.common.parts.VisInterfacePart visSource)) {
            return;
        }
        // 限速充能。单位换算：consumeVis 返回 vis；storedVis 用 cvis（x CVIS_MULTIPLIER）。
        for (AspectId primal : PRIMALS) {
            int needed = MAX_STORED_CVIS - storedVis.getOrDefault(primal, 0);
            if (needed <= 0) continue;
            int wantVis = Math.max(1, Math.min(needed / CVIS_MULTIPLIER, MAX_CVIS_PER_REPLENISH / CVIS_MULTIPLIER));
            int gotVis = visSource.consumeVis(primal.value(), wantVis);
            if (gotVis > 0) {
                storedVis.merge(primal, gotVis * CVIS_MULTIPLIER, Integer::sum);
                maybeMarkVisDirty();
            }
        }
    }

    /**
     * replenishVis 鎶藉埌 vis 鏃惰皟鐢細绱鍙樺寲娆℃暟锛屾瘡 SET_CHANGED_INTERVAL 娆℃墠 markForUpdate 涓€娆°€?     * markForUpdate = setChanged + sendBlockUpdated锛堝悓姝?storedVis 鍒板鎴风 BE锛孏UI 杩涘害鏉′緷璧栧鎴风 storedVis锛夈€?     */
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
            tc_energy.common.network.AssemblerPreviewSyncPacket.send(this, false);
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

            // 鍏冨鐝嶇彔淇濈暀锛歍C 鍞竴 retainIngredient 鍘熸枡锛屽悎鎴愬悗鐝嶇彔閫€鍥炵綉缁滐紙涓庡ゥ鏈悎鎴愮粓绔竴鑷达級銆?            // 元始珍珠保留：合成后珍珠退回网络（与奥术合成终端一致）。
            var retainedPearls = retainedPearlStacks();

            long inserted = storage.getInventory().insert(
                    outputKey, output.getCount(), Actionable.SIMULATE, actionSource);
            if (inserted < output.getCount()) {
                return TickRateModulation.SAME;
            }
            // 棰勬锛氱弽鐝犵被鍨嬪婊℃斁涓嶅洖缃戠粶鍒欑瓑寰咃紝閬垮厤鍚堟垚瀹屾垚鍗翠涪鐝嶇彔
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
            // 閫€鍥炵弽鐝狅紙鏁伴噺 = 3脳3 閰嶆柟缃戞牸涓弽鐝犳€绘暟锛岄€氬父 1 棰楋級
            for (ItemStack pearl : retainedPearls) {
                AEItemKey pearlKey = AEItemKey.of(pearl);
                if (pearlKey != null) {
                    storage.getInventory().insert(pearlKey, pearl.getCount(), Actionable.MODULATE, actionSource);
                }
            }

            isCrafting = false;
            currentRecipe = null;
            currentResult = ItemStack.EMPTY;
            currentRecipeAspects = new java.util.LinkedHashMap<>();
            previewGrid = emptyPreviewGrid();
            internalInventory.setItem(TARGET_SLOT, ItemStack.EMPTY);
            setChanged();
            markForUpdate();
            tc_energy.common.network.AssemblerPreviewSyncPacket.send(this, false);

            return TickRateModulation.URGENT;
        }

        double powerRequired = (ACTIVE_POWER + (ACTIVE_POWER * speedUpgrades) / 2.0) * warpPowerMultiplier;
        IEnergyService energy = grid.getService(IEnergyService.class);
        if (energy != null) {
            double need = powerRequired * ticksSinceLast;
            double extracted = energy.extractAEPower(need, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (extracted >= need * 0.9) {
                craftTickCounter += ticksSinceLast;
                // 鑺傛祦鍚屾鍚堟垚杩涘害鍒板鎴风锛圙UI 绗?鏍艰繘搴︽潯鍔ㄧ敾锛?                maybeMarkVisDirty();
            }
        }

        return TickRateModulation.SAME;
    }

    private void consumeVisForCraft() {
        if (currentRecipeAspects == null || currentRecipeAspects.isEmpty()) return;
        for (Map.Entry<AspectId, Integer> entry : currentRecipeAspects.entrySet()) {
            AspectId aspect = entry.getKey();
            int requiredCvis = getRequiredCvisForAspect(aspect);
            int current = storedVis.getOrDefault(aspect, 0);
            storedVis.put(aspect, current - Math.min(requiredCvis, current));
        }
        maybeMarkVisDirty();
    }

    private boolean hasEnoughVisForCraft() {
        if (currentRecipeAspects == null || currentRecipeAspects.isEmpty()) return true;
        for (Map.Entry<AspectId, Integer> entry : currentRecipeAspects.entrySet()) {
            int requiredCvis = getRequiredCvisForAspect(entry.getKey());
            if (storedVis.getOrDefault(entry.getKey(), 0) < requiredCvis) return false;
        }
        return true;
    }

    private int getRequiredCvisForAspect(AspectId aspect) {
        if (currentRecipeAspects == null) return 0;
        // Ren 鐗堥厤鏂?vis 涓?whole-vis锛涙棫鎺ュ彛姝ゅ鎸?centivis 鍒ゅ畾锛屾晠 脳CVIS_MULTIPLIER
        int baseCost = currentRecipeAspects.getOrDefault(aspect, 0) * CVIS_MULTIPLIER * CVIS_MULTIPLIER / CVIS_MULTIPLIER;
        float discount = visDiscount.getOrDefault(aspect, BASE_VIS_DISCOUNT);
        return Math.max(0, Math.round(baseCost * discount));
    }

    /**
     * 閰嶆柟 3脳3 缃戞牸涓渶淇濈暀鐨勫厓濮嬬弽鐝犳爤銆俆C 濂ユ湳閰嶆柟涓弽鐝犳槸鍞竴 retainIngredient 鍘熸枡锛?     * 鍦ㄥゥ鏈伐浣滃彴/濂ユ湳鍚堟垚缁堢涓綔涓?鍌寲"鐣欏湪鍚堟垚鏍间笉娑堣€楋紝瑁呴厤鍣ㄥ悎鎴愬畬鎴愬悗涔熻閫€鍥炵綉缁溿€?     */
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
        for (AspectId primal : PRIMALS) {
            float discount = BASE_VIS_DISCOUNT;

            for (int i = 0; i < 4; i++) {
                ItemStack armor = internalInventory.getItem(ARMOR_SLOT_START + i);
                if (!armor.isEmpty() && armor.getItem() instanceof VisDiscountGear gear) {
                    try {
                        discount -= gear.thaumcraftVisDiscount(armor, null,
                                dev.tc4port.thaumcraft.api.aspect.VisChannel.fromAspectId(primal).orElse(null)) / 100.0F;
                    } catch (Exception ignored) {}
                }
            }

            visDiscount.put(primal, Math.max(0.1F, discount));
        }

        warpPowerMultiplier = 1.0F;
        for (int i = 0; i < 4; i++) {
            ItemStack armor = internalInventory.getItem(ARMOR_SLOT_START + i);
            if (!armor.isEmpty() && armor.getItem() instanceof WarpingGear gear) {
                try {
                    warpPowerMultiplier += gear.thaumcraftWarp(armor, java.util.Optional.empty()) * WARP_POWER_PERCENT;
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

        Map<AspectId, Integer> aspects = acp.getAspects();
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
        Map<AspectId, Integer> aspects = arcanePattern.getAspects();
        ItemStack result = arcanePattern.getResultStack();
        if (isCrafting || !mainNode.isActive()) return false;
        if (aspects == null || aspects.isEmpty() || result.isEmpty()) return false;

        this.isCrafting = true;
        this.craftTickCounter = 0;
        this.currentRecipe = arcanePattern.getRecipe();
        this.currentRecipeAspects = new java.util.LinkedHashMap<>(aspects);
        this.currentResult = result.copy();
        // 閰嶆柟棰勮锛氬鍒?3脳3 鏉愭枡缃戞牸锛堜笉瓒?9 鏍艰ˉ绌猴級
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
        tc_energy.common.network.AssemblerPreviewSyncPacket.send(this, true);
        return true;
    }

    @Override
    public boolean isBusy() {
        return isCrafting;
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        ItemStack icon = new ItemStack(tc_energy.init.ModItems.ARCANE_ASSEMBLER_ITEM.get());
        return new PatternContainerGroup(
                AEItemKey.of(icon),
                net.minecraft.network.chat.Component.translatable("block.tc_energy.arcane_assembler"),
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
    /** 褰撳墠鍚堟垚閰嶆柟鐨?3脳3 鏉愭枡缃戞牸锛圙UI 鍙充笅閰嶆柟棰勮锛涙湭鍚堟垚鏃朵负绌烘暟缁勶級銆?*/
    public ItemStack[] getPreviewGrid() { return previewGrid; }
    /** 褰撳墠鍚堟垚浜х墿锛圙UI 棰勪骇鐗╂樉绀猴紱鏈悎鎴愪负绌猴級銆?*/
    public ItemStack getCurrentResult() { return currentResult; }
    public float getCraftProgress() {
        if (!isCrafting || ticksPerCraft() <= 0) return 0f;
        return Math.min(1f, (float) craftTickCounter / ticksPerCraft());
    }
    public java.util.Map<AspectId, Integer> getStoredVis() { return storedVis; }
    public SimpleContainer getInternalInventory() { return internalInventory; }
    public float getVisDiscountForAspect(AspectId aspect) { return visDiscount.getOrDefault(aspect, BASE_VIS_DISCOUNT); }
    public float getWarpPowerMultiplier() { return warpPowerMultiplier; }

    public void setSpeedUpgrades(int count) {
        this.speedUpgrades = Math.min(count, MAX_SPEED_UPGRADES);
        setChanged();
    }

    public short getVisSourceFreq() { return visSourceFreq; }
    /** ME 鑺傜偣鏄惁鏈夎兘閲忥紙Jade 缃戠粶鐘舵€佺粏鍒嗙敤锛夈€?*/
    public boolean isNodePowered() { return mainNode != null && mainNode.isPowered(); }

    /**
     * 鍐呭瓨鍗″彸閿閰嶅櫒锛氳 P2P 棰戠巼锛岃涓?vis 婧愩€?     * 澶嶅埢 1.7.10 onMemoryCardActivate锛屼絾鐢?P2P 棰戠巼鏇夸唬 DigiVisSourceData 浣嶇疆锛堥€傞厤 neo vis_interface P2P锛夈€?     * 鍗′笂鏈?EXPORTED_P2P_FREQUENCY 鈫?瀛橀鐜囷紱绌虹櫧鍗?鈫?娓呯┖锛涘叾瀹?鈫?鏃犳晥銆?     */
    public void onMemoryCardActivate(net.minecraft.world.entity.player.Player player,
                                     appeng.api.implementations.items.IMemoryCard memoryCard,
                                     net.minecraft.world.item.ItemStack heldItem) {
        Short freq = heldItem.get(appeng.api.ids.AEComponents.EXPORTED_P2P_FREQUENCY);
        if (freq != null) {
            // 鍗′笂甯?P2P 棰戠巼锛堜粠 vis_interface shift+鍙抽敭澶嶅埗鏉ョ殑锛夆啋 璁句负 vis 婧?
            this.visSourceFreq = freq;
            this.setChanged();
            memoryCard.notifyUser(player, appeng.api.implementations.items.MemoryCardMessages.SETTINGS_LOADED);
        } else if (heldItem.get(appeng.api.ids.AEComponents.EXPORTED_SETTINGS_SOURCE) == null) {
            // 绌虹櫧鍗?鈫?娓呯┖ vis 婧?
            if (this.visSourceFreq != 0) {
                this.visSourceFreq = 0;
                this.setChanged();
            }
            memoryCard.notifyUser(player, appeng.api.implementations.items.MemoryCardMessages.SETTINGS_CLEARED);
        } else {
            // 鍗′笂鏈夊叾瀹冮厤缃紙闈?P2P 棰戠巼锛夆啋 涓嶅吋瀹?            memoryCard.notifyUser(player, appeng.api.implementations.items.MemoryCardMessages.INVALID_MACHINE);
        }
    }

    public void onInventoryChanged(int slot) {
        if (slot == KCORE_SLOT) {
            stalePatterns = true;
            updatePatternSlots();
            // 鐩存帴閫氱煡 AE2 鍚堟垚鏈嶅姟閲嶆柊鎷夊彇 getAvailablePatterns()銆?            // 涔嬪墠浠呴潬 tickingRequest 閲屽鐞?stalePatterns 鍐?requestUpdate锛?            // 浣嗚妭鐐逛紤鐪犳椂 tickingRequest 涓嶈璋冪敤锛孉E2 姘歌繙瀛︿笉鍒?kCore 閲岀殑鏍锋澘 鈫?ME 缁堢鏃犲彲鍚堟垚椤广€?            // 瀹堝崼 getGrid()!=null锛氳閰嶅櫒鏈繛 ME 缃戠粶锛堟棤鐩搁偦 cable锛夋椂 getGrid() 涓?null锛?            // 姝ゆ椂 requestUpdate 鍐呴儴 node.getGrid().getCraftingService() 浼?NPE锛屽鑷?kCore 鎻掑叆浜嬪姟鍥炴粴 鈫?"鏃犳硶缁戝畾"銆?
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

    /** 鎸?Tile 鍐呭姞閫熷崱妲界殑瀹為檯鍗＄墖鏁伴噺鍒锋柊 speedUpgrades锛堟寔涔呭寲璁℃暟锛屼笉鍐嶄緷璧栬彍鍗曞瓨娲伙級銆?*/
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
            // 鍏抽敭锛歜lockEntityChanged 瑙﹀彂 BE 鏁版嵁锛坓etUpdateTag锛夊悓姝ュ埌瀹㈡埛绔€?            // setChanged 鍙爣璁板尯鍧椾繚瀛樸€乻endBlockUpdated 鍙Е鍙戦噸娓叉煋鈥斺€旂己杩欎釜 GUI 姘歌繙鎷夸笉鍒?storedVis/isCrafting銆?            level.blockEntityChanged(worldPosition);
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
        // 閰嶆柟棰勮 3脳3 鏉愭枡锛圙UI 鍙充笅瑙掓樉绀猴級
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

        storedVis = new java.util.LinkedHashMap<>();
        for (AspectId primal : PRIMALS) {
            storedVis.put(primal, 0);
        }
        if (tag.contains("StoredVis")) {
            CompoundTag visTag = tag.getCompound("StoredVis");
            for (AspectId primal : PRIMALS) {
                String key = primal.serialized();
                if (visTag.contains(key)) {
                    storedVis.put(primal, visTag.getInt(key));
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
        for (AspectId primal : PRIMALS) {
            int amount = storedVis.getOrDefault(primal, 0);
            if (amount > 0) {
                visTag.putInt(primal.serialized(), amount);
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
        private final ArcaneCraftingRecipe recipe;
        private final Map<AspectId, Integer> aspects;
        private final ItemStack resultStack;
        private final IInput[] inputs;
        private final String research;
        /** 3脳3 閰嶆柟缃戞牸锛堢煡璇嗘牳蹇冨瓨鍌ㄧ殑 9 鏍兼潗鏂欙紝GUI 閰嶆柟棰勮鐢級銆?*/
        private final ItemStack[] grid;

        public ArcaneAssemblerPattern(AEItemKey definition, ArcaneCraftingRecipe recipe,
                                      Map<AspectId, Integer> aspects, ItemStack resultStack,
                                      List<GenericStack> inputStacks) {
            this(definition, recipe, aspects, resultStack, inputStacks, null, null);
        }

        public ArcaneAssemblerPattern(AEItemKey definition, ArcaneCraftingRecipe recipe,
                                      Map<AspectId, Integer> aspects, ItemStack resultStack,
                                      List<GenericStack> inputStacks, String research) {
            this(definition, recipe, aspects, resultStack, inputStacks, research, null);
        }

        public ArcaneAssemblerPattern(AEItemKey definition, ArcaneCraftingRecipe recipe,
                                      Map<AspectId, Integer> aspects, ItemStack resultStack,
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
                        // 鍏冨鐝嶇彔浣滀负"鍌寲"鍘熸枡锛氬０鏄庡悎鎴愬悗瀹冧細杩斿洖缃戠粶锛圓E2 鍘熺敓瀹瑰櫒鏈哄埗锛夈€?                        // 瑙勫垝绔嵁姝ゆ寜"姣忔 1 鍗?椤哄簭妯℃嫙銆佹墽琛岀鎶婄弽鐝犺鍏?CPU waitingFor锛?                        // 鏈哄櫒鍚堟垚瀹屾垚鎶婄弽鐝犻€€鍥炵綉缁滃悗 CPU 鑷姩鎹曡幏缁笅涓€鍗曗€斺€? 棰楃弽鐝犲彲杩炵画澶氬崟銆?
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

        public ArcaneCraftingRecipe getRecipe() { return recipe; }
        public Map<AspectId, Integer> getAspects() { return aspects; }
        public ItemStack getResultStack() { return resultStack; }
        /** 3脳3 閰嶆柟缃戞牸锛堝彲鑳戒负 null/鐭暟缁勶紝GUI 棰勮鐢級銆?*/
        @Nullable
        public ItemStack[] getGrid() { return grid; }
        /** 瀵瑰簲 TC 濂ユ湳閰嶆柟鐨勭爺绌堕敭锛涙棤鐮旂┒瑕佹眰/鑰佹暟鎹椂鍙兘涓?null銆?*/
        @Nullable
        public String getResearch() { return research; }
    }
}
