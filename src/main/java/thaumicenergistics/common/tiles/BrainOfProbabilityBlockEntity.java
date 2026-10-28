package thaumicenergistics.common.tiles;

import java.util.ArrayList;
import thaumicenergistics.common.crafting.ProbabilityBrainRules;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.research.TCResearchManager;

import thaumicenergistics.common.blocks.BrainOfProbabilityBlock;
import thaumicenergistics.common.blocks.BrainOfProbabilityTopBlock;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModItems;

/**
 * 概率之脑 BE（M6）：网格节点 + 每秒一次的周期 tick。
 * 脑槽、4 张加速卡、绑定宿主、进行中的转动与屏幕闪烁都在此记账；
 * 概率规则参考 CE（GachaOdds/GachaTurn），网络持续耗能 50 AE/t，
 * 燃料 cognitio 从 ME 源质存储扣取（2/周期），产出经 TCResearchManager.addAspectPool 落入宿主认知要素池。
 */
public class BrainOfProbabilityBlockEntity extends ThETileBase
        implements IInWorldGridNodeHost, IActionHost, IGridTickable {

    /** 转动按秒走，每秒醒一次。 */
    private static final int TICKS_PER_SECOND = 20;
    /** 网络持续耗能，由 AE2 能量服务统一扣除，不另收每轮启动费用。 */
    public static final double AE_PER_TICK = 50.0;
    /** 每周期 cognitio 燃料（CE 同值）。 */
    public static final int COGNITIO_PER_TURN = 2;
    /** 加速卡上限（CE 同值）。 */
    public static final int MAX_CARDS = 4;
    /** 结果在屏幕上停留秒数（CE FLASH_SECONDS=2）。 */
    private static final int FLASH_SECONDS = 2;

    // 公开常量保持兼容，规则集中在 ProbabilityBrainRules。
    public static final int MIN_SECONDS = ProbabilityBrainRules.MIN_SECONDS;
    public static final int LONGEST_SECONDS = ProbabilityBrainRules.LONGEST_SECONDS;
    public static final int SECONDS_PER_CARD = ProbabilityBrainRules.SECONDS_PER_CARD;
    public static final int LONGEST_WITH_CARDS = ProbabilityBrainRules.LONGEST_WITH_CARDS;
    public static final double CHANCE_AT_MIN_SECONDS = ProbabilityBrainRules.CHANCE_AT_MIN_SECONDS;
    public static final double CHANCE_AT_LONGEST_SECONDS = ProbabilityBrainRules.CHANCE_AT_LONGEST_SECONDS;
    public static final int MIN_DRAWS = ProbabilityBrainRules.MIN_DRAWS;
    public static final int MAX_DRAWS = ProbabilityBrainRules.MAX_DRAWS;
    public static final int MAX_POINTS_PER_DRAW = ProbabilityBrainRules.MAX_POINTS_PER_DRAW;

    private final IManagedGridNode mainNode;
    private final IActionSource actionSource;

    private final IGridNodeListener<BrainOfProbabilityBlockEntity> nodeListener = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(BrainOfProbabilityBlockEntity owner, IGridNode node) {
            owner.setChanged();
        }

        @Override
        public void onStateChanged(BrainOfProbabilityBlockEntity owner, IGridNode node, State state) {
            if (state == State.POWER) {
                owner.isActive = owner.mainNode.isActive();
            }
        }
    };

    // ===== 内容物与状态 =====
    private ItemStack brain = ItemStack.EMPTY;
    private final ItemStack[] cards = new ItemStack[MAX_CARDS];
    /** 绑定宿主（放入脑的玩家）；null = 未认领。 */
    @Nullable
    private UUID owner;
    private String ownerName = "";
    /** 进行中转动的剩余秒数；0 = 空闲。 */
    private int turnSeconds;
    /** 本次转动是否为给奖类。 */
    private boolean turnPays;
    /** 结果闪屏剩余秒数（不入存档）。 */
    private int flashSeconds;
    private boolean flashPaid;
    /** 最近一次出货明细（要素 tag → 点数，保序）；空 = 尚无出货记录。NBT 持久化，Jade 展示用。 */
    private final LinkedHashMap<String, Integer> lastPayout = new LinkedHashMap<>();

    public BrainOfProbabilityBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BRAIN_OF_PROBABILITY.get(), pos, state);
        Arrays.fill(this.cards, ItemStack.EMPTY);
        mainNode = GridHelper.createManagedNode(this, nodeListener)
                .setVisualRepresentation(ModItems.BRAIN_OF_PROBABILITY_ITEM.get())
                .setInWorldNode(true)
                .setTagName("probability_brain")
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .addService(IGridTickable.class, this);
        mainNode.setIdlePowerUsage(AE_PER_TICK);
        actionSource = IActionSource.ofMachine(this);
    }

    // ===== 生命周期 =====

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
            dropContents();
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

    // ===== 周期主循环（CE BlockEntityGachaBox.tickingRequest 同构） =====

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(TICKS_PER_SECOND, TICKS_PER_SECOND, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (!(level instanceof ServerLevel server)) {
            return TickRateModulation.IDLE;
        }
        // 结果停留片刻，否则下一次转动会盖掉玩家还没看到的闪光
        if (flashSeconds > 0) {
            flashSeconds--;
            showScreen(flashPaid ? BrainOfProbabilityBlock.Screen.SUCCESS : BrainOfProbabilityBlock.Screen.FAILED);
            return TickRateModulation.SAME;
        }
        WaitReason wait = waitReason(server);
        if (wait != null) {
            showScreen(wait.blankScreen() ? BrainOfProbabilityBlock.Screen.OFF : BrainOfProbabilityBlock.Screen.ON);
            return TickRateModulation.SAME;
        }
        showScreen(BrainOfProbabilityBlock.Screen.WORKING);
        if (turnSeconds <= 0) {
            beginTurn(server);
            return TickRateModulation.SAME;
        }
        turnSeconds--;
        setChanged();
        if (turnSeconds <= 0) {
            finishTurn(server);
        }
        return TickRateModulation.SAME;
    }

    /** 第一个挡路的条件；null 表示可以转。 */
    private @Nullable WaitReason waitReason(ServerLevel server) {
        WaitReason setup = setupReason(server);
        if (setup != null) {
            return setup;
        }
        // 进行中的转动已经付过费，这里只查下一次的燃料
        if (turnSeconds > 0) {
            return null;
        }
        if (!hasCognitioFuel()) {
            return WaitReason.NO_ESSENTIA;
        }
        return null;
    }

    /** 装置自身缺什么；缺一项就没东西可转。 */
    private @Nullable WaitReason setupReason(ServerLevel server) {
        if (!structureComplete()) {
            return WaitReason.NO_STRUCTURE;
        }
        if (!hasBrain()) {
            return WaitReason.NO_BRAIN;
        }
        if (!isBound()) {
            return WaitReason.UNBOUND_BRAIN;
        }
        if (!mainNode.isActive()) {
            return WaitReason.NO_CHANNEL;
        }
        // 宿主不在线就等着，不把产出存起来
        if (ownerPlayer(server) == null) {
            return WaitReason.OWNER_OFFLINE;
        }
        return null;
    }

    /** cognitio 燃料预检：ME 源质存储须有 2 点可扣。 */
    private boolean hasCognitioFuel() {
        IGrid grid = mainNode.getGrid();
        AEKey cog = cognitioKey();
        if (grid == null || cog == null) {
            return false;
        }
        IStorageService storage = grid.getService(IStorageService.class);
        if (storage == null) {
            return false;
        }
        return storage.getInventory().extract(cog, COGNITIO_PER_TURN, Actionable.SIMULATE, actionSource) >= COGNITIO_PER_TURN;
    }

    /** 一次抽取同时定下时长和赔率，付过费才开转。 */
    private void beginTurn(ServerLevel server) {
        var drawn = ProbabilityBrainRules.roll(server.getRandom(), cardCount());
        if (!payForTurn()) {
            return;
        }
        this.turnSeconds = drawn.seconds();
        this.turnPays = drawn.pays();
        setChanged();
    }

    /** 每轮只扣 2 点 cognitio，电力通过节点的持续耗能结算。 */
    private boolean payForTurn() {
        IGrid grid = mainNode.getGrid();
        AEKey cog = cognitioKey();
        if (grid == null || cog == null) {
            return false;
        }
        IStorageService storage = grid.getService(IStorageService.class);
        if (storage == null) {
            return false;
        }
        MEStorage inventory = storage.getInventory();
        // 燃料 SIMULATE → MODULATE（竞态兜底：不足则退回已扣部分）
        if (inventory.extract(cog, COGNITIO_PER_TURN, Actionable.SIMULATE, actionSource) < COGNITIO_PER_TURN) {
            return false;
        }
        long got = inventory.extract(cog, COGNITIO_PER_TURN, Actionable.MODULATE, actionSource);
        if (got < COGNITIO_PER_TURN) {
            if (got > 0) {
                inventory.insert(cog, got, Actionable.MODULATE, actionSource);
            }
            return false;
        }
        return true;
    }

    /** 转动结束：闪屏 2 秒；给奖转动结算产出；白转清空出货记录（Jade 回显「暂无」）。 */
    private void finishTurn(ServerLevel server) {
        this.flashSeconds = FLASH_SECONDS;
        this.flashPaid = this.turnPays;
        boolean paid = this.turnPays;
        this.turnPays = false;
        setChanged();
        if (!paid) {
            // 白转：清掉上次成功出货的 Jade 残留（T-M6-JADE-FIX）
            this.lastPayout.clear();
            setChanged();
            return;
        }
        ServerPlayer bound = ownerPlayer(server);
        if (bound == null) {
            return;
        }
        payout(bound);
    }

    /**
     * 产出结算（T-M6-GACHA-REV，用户拍板）：掷中后从候选池随机挑 5~7 个不重复要素、
     * 每要素 1~3 点，逐要素入池。候选池 = 全部元始要素 + 投放者已发现的复合要素
     * （未发现复合要素不进池，防剧透）；候选池不足时有多少给多少，至少 1 类也发。
     * 不保底认知——认知只当燃料（payForTurn 零触碰）；出货率公式（payoutChance）零触碰。
     * port.261 实证：addAspectPool 为 per-aspect Map 键控（aspectPool 返回 Map<Aspect,Integer>），
     * 任意 Aspect 实例均合法持久化。
     */
    private void payout(ServerPlayer bound) {
        RandomSource random = bound.getRandom();
        int draws = MIN_DRAWS + random.nextInt(MAX_DRAWS - MIN_DRAWS + 1);
        List<Aspect> pool = candidatePool(bound);
        // 候选池不足 5~7 时有多少给多少；空池（理论不可达：元始要素至少 6）无事发生
        int kinds = Math.min(draws, pool.size());
        if (kinds <= 0) {
            return;
        }
        // 部分 Fisher-Yates：只洗前 kinds 个（RandomSource 非 java.util.Random，不能走 Collections.shuffle）
        for (int i = 0; i < kinds; i++) {
            int j = i + random.nextInt(pool.size() - i);
            Aspect tmp = pool.get(i);
            pool.set(i, pool.get(j));
            pool.set(j, tmp);
        }
        int totalPoints = 0;
        LinkedHashMap<String, Integer> detail = new LinkedHashMap<>();
        for (int i = 0; i < kinds; i++) {
            Aspect aspect = pool.get(i);
            int points = 1 + random.nextInt(MAX_POINTS_PER_DRAW);
            TCResearchManager.addAspectPool(bound, aspect, points);
            detail.put(aspect.getTag(), points);
            totalPoints += points;
        }
        this.lastPayout.clear();
        this.lastPayout.putAll(detail);
        setChanged();
        bound.displayClientMessage(payoutMessage(detail.size(), totalPoints, detail), true);
    }

    /** 出货候选池：全部元始要素 + 投放者已发现的复合要素（防剧透）。 */
    private static List<Aspect> candidatePool(ServerPlayer bound) {
        List<Aspect> pool = new ArrayList<>(Aspect.getPrimalAspects());
        for (Aspect aspect : TCResearchManager.discoveredAspects(bound)) {
            if (!aspect.isPrimal() && !pool.contains(aspect)) {
                pool.add(aspect);
            }
        }
        return pool;
    }

    /** actionbar 明细：「本轮研究收获：秩序 3、火 1（共 2 类 4 点）」。 */
    private static Component payoutMessage(int kinds, int totalPoints, Map<String, Integer> detail) {
        Component sep = Component.translatable("thaumicenergistics.brain_prob.payout_sep");
        MutableComponent parts = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : detail.entrySet()) {
            if (!first) {
                parts = parts.append(sep);
            }
            first = false;
            parts = parts.append(Component.translatable("tc.aspect." + entry.getKey()))
                    .append(Component.literal(" " + entry.getValue()));
        }
        return Component.translatable("thaumicenergistics.brain_prob.payout_detail",
                parts, kinds, totalPoints);
    }

    // ===== 骰子（CE GachaOdds 同构） =====

    /** 箱子为什么停着不动：blankScreen = 该修装置（死屏），其余为待机屏。 */
    private enum WaitReason {
        NO_STRUCTURE, NO_BRAIN, UNBOUND_BRAIN, NO_CHANNEL, OWNER_OFFLINE, NO_ESSENTIA;

        boolean blankScreen() {
            return this == NO_STRUCTURE || this == NO_CHANNEL;
        }
    }

    /** 方面 tag → AEssentiaKey（运行期查找，cognitio 无静态常量，vitium 同款范式）。 */
    private static AEKey cognitioKey() {
        Aspect aspect = Aspect.get("cognitio");
        if (aspect == null) {
            return null;
        }
        var id = TCReflection.getAspectId(aspect);
        return id == null ? null : AEssentiaKey.of(id);
    }

    // ===== 状态查询 =====

    /** 最近一次出货明细（要素 tag → 点数，只读视图；Jade「本轮奖励」展示）。 */
    public Map<String, Integer> lastPayout() {
        return Collections.unmodifiableMap(lastPayout);
    }

    /** 进行中转动的剩余秒数；空闲为 0（Jade「下次抽奖」倒计时）。 */
    public int turnSecondsRemaining() {
        return Math.max(0, turnSeconds);
    }

    public boolean hasBrain() {
        return !brain.isEmpty();
    }

    public int cardCount() {
        int count = 0;
        for (ItemStack card : cards) {
            if (!card.isEmpty()) count++;
        }
        return count;
    }

    public boolean hasRoomForCard() {
        return cardCount() < MAX_CARDS;
    }

    /** 上半部分（形态格）立在底座上才算结构完整。 */
    public boolean structureComplete() {
        if (level == null) {
            return false;
        }
        return level.getBlockState(getBlockPos().above()).getBlock() instanceof BrainOfProbabilityTopBlock;
    }

    public boolean isBound() {
        return owner != null;
    }

    /** 脑在槽里却无人认领：存档中断留下的，点一下即可认领。 */
    public boolean hasUnboundBrain() {
        return hasBrain() && !isBound();
    }

    /** 能否取回脑：绑定者本人；无主脑谁都可以。 */
    public boolean mayTakeBrain(Player player) {
        return owner == null || owner.equals(player.getUUID());
    }

    @Nullable
    public String ownerName() {
        return owner == null ? null : ownerName;
    }

    /** 绑定的在线且在世宿主；离线或死亡返回 null（§G.1：任一不成立本周期不执行，离线不消耗，不存产出）。 */
    @Nullable
    ServerPlayer ownerPlayer(ServerLevel server) {
        if (owner == null) {
            return null;
        }
        return server.getPlayerByUUID(owner) instanceof ServerPlayer bound && bound.isAlive() ? bound : null;
    }

    // ===== 装填与取出（B3 批次由方块层调用） =====

    /** 放入一颗罐装脑（thaumcraft:brain_jar）并绑定宿主；作废上一任宿主的转动。 */
    public void addBrain(Player player, ItemStack held) {
        this.brain = held.copyWithCount(1);
        bind(player);
        syncJarState();
    }

    /** 无主脑认领：一次点击修复存档中断状态。 */
    public void bind(Player player) {
        this.owner = player.getUUID();
        this.ownerName = player.getName().getString();
        resetTurn();
        setChanged();
    }

    /** 解绑（取出脑时）：转动作废不保存。 */
    public void unbind() {
        this.owner = null;
        this.ownerName = "";
        resetTurn();
        setChanged();
    }

    /** 取出脑（调用方已校验 mayTakeBrain）。 */
    public ItemStack takeBrain() {
        ItemStack taken = this.brain.copy();
        this.brain = ItemStack.EMPTY;
        syncJarState();
        return taken;
    }

    /** 放入一张加速卡；已满返回 false。 */
    public boolean addCard(ItemStack held) {
        for (int slot = 0; slot < MAX_CARDS; slot++) {
            if (cards[slot].isEmpty()) {
                cards[slot] = held.copyWithCount(1);
                setChanged();
                return true;
            }
        }
        return false;
    }

    /** 取出全部加速卡（顺序无关）。 */
    public List<ItemStack> takeCards() {
        List<ItemStack> taken = new ArrayList<>(MAX_CARDS);
        for (int slot = 0; slot < MAX_CARDS; slot++) {
            if (!cards[slot].isEmpty()) {
                taken.add(cards[slot]);
                cards[slot] = ItemStack.EMPTY;
            }
        }
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    /** 破坏方块时倾倒全部内容物。 */
    public void dropContents() {
        if (level == null) {
            return;
        }
        spill(takeBrain());
        for (ItemStack card : takeCards()) {
            spill(card);
        }
    }

    private void spill(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        Containers.dropItemStack(level,
                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5,
                stack);
    }

    /** 转动进行中/闪屏状态复位（装填/解绑时调用）。 */
    public void resetTurn() {
        this.turnSeconds = 0;
        this.turnPays = false;
        this.flashSeconds = 0;
        this.flashPaid = false;
        setChanged();
    }

    /** jar 方块状态跟随脑槽走（客户端渲染依赖）。 */
    private void syncJarState() {
        if (level == null || level.isClientSide || isRemoved()) {
            return;
        }
        BlockState live = level.getBlockState(worldPosition);
        boolean present = hasBrain();
        if (live.getBlock() instanceof BrainOfProbabilityBlock && live.getValue(BrainOfProbabilityBlock.JAR) != present) {
            level.setBlock(worldPosition, live.setValue(BrainOfProbabilityBlock.JAR, present), 3);
        }
    }

    /** 五态屏落盘并同步客户端（方块状态自带同步）。 */
    void showScreen(BrainOfProbabilityBlock.Screen screen) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (state.getBlock() instanceof BrainOfProbabilityBlock
                && state.getValue(BrainOfProbabilityBlock.SCREEN) != screen) {
            level.setBlockAndUpdate(worldPosition, state.setValue(BrainOfProbabilityBlock.SCREEN, screen));
        }
    }

    /** 空手右键状态反馈（actionbar 单行）。 */
    public net.minecraft.network.chat.Component buildStatus() {
        net.minecraft.network.chat.Component state;
        if (!hasBrain()) {
            state = Component.translatable("thaumicenergistics.brain_prob.status.idle");
        } else if (turnSeconds > 0) {
            state = Component.translatable("thaumicenergistics.brain_prob.status.working", turnSeconds);
        } else {
            state = Component.translatable("thaumicenergistics.brain_prob.status.waiting");
        }
        net.minecraft.network.chat.Component ownerDisplay = isBound()
                ? Component.literal(ownerName)
                : Component.translatable("thaumicenergistics.brain_prob.status.unbound");
        return Component.translatable("thaumicenergistics.brain_prob.status", state, cardCount(), ownerDisplay);
    }

    // ===== NBT =====

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);

        this.brain = ItemStack.parseOptional(registries, tag.getCompound("Brain"));
        ListTag savedCards = tag.getList("Cards", Tag.TAG_COMPOUND);
        for (int slot = 0; slot < MAX_CARDS; slot++) {
            this.cards[slot] = slot < savedCards.size()
                    ? ItemStack.parseOptional(registries, savedCards.getCompound(slot))
                    : ItemStack.EMPTY;
        }
        this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.ownerName = tag.getString("OwnerName");
        this.turnSeconds = Math.max(0, tag.getInt("TurnSeconds"));
        this.turnPays = tag.getBoolean("TurnPays");
        this.flashSeconds = 0;
        this.flashPaid = false;
        // 最近一次出货明细读回（无键保持空 = 尚无出货记录）
        this.lastPayout.clear();
        ListTag savedPayout = tag.getList("LastPayout", Tag.TAG_COMPOUND);
        for (int i = 0; i < savedPayout.size(); i++) {
            CompoundTag item = savedPayout.getCompound(i);
            String aspectTag = item.getString("tag");
            if (!aspectTag.isEmpty() && Aspect.getAspect(aspectTag) != null) {
                this.lastPayout.put(aspectTag, item.getInt("points"));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
        if (!brain.isEmpty()) {
            tag.put("Brain", brain.saveOptional(registries));
        }
        ListTag savedCards = new ListTag();
        for (ItemStack card : cards) {
            savedCards.add(card.saveOptional(registries));
        }
        tag.put("Cards", savedCards);
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        if (turnSeconds > 0) {
            tag.putInt("TurnSeconds", turnSeconds);
            tag.putBoolean("TurnPays", turnPays);
        }
        // 最近一次出货明细（Jade「本轮奖励」展示，重启保留）
        if (!lastPayout.isEmpty()) {
            ListTag savedPayout = new ListTag();
            for (Map.Entry<String, Integer> entry : lastPayout.entrySet()) {
                CompoundTag item = new CompoundTag();
                item.putString("tag", entry.getKey());
                item.putInt("points", entry.getValue());
                savedPayout.add(item);
            }
            tag.put("LastPayout", savedPayout);
        }
    }
}
