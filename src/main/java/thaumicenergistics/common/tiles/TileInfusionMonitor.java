package thaumicenergistics.common.tiles;

import net.minecraft.core.BlockPos;
import thaumicenergistics.common.monitor.InfusionMonitorDisplay;
import thaumicenergistics.common.monitor.InfusionMonitorText;
import static thaumicenergistics.common.monitor.InfusionMonitorText.*;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.util.AECableType;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.blockentities.InfusionMatrixBlockEntity;
import thaumicenergistics.init.ModItems;

/**
 * 注魔监视器（灵气观测器）方块实体。
 * <ul>
 *   <li>1 格物品槽：Shift+右键放入魔导手册（book 状态）。</li>
 *   <li>AE2 网格节点：连接 ME 网络后 network 状态=true。</li>
 *   <li>每 tick 扫描附近注魔矩阵，缓存风险数据供 Jade 显示（5 档风险/不稳定度/档位文字）。</li>
 * </ul>
 * 方块状态：
 * <ol>
 *   <li>默认 → off 模型</li>
 *   <li>放手册 → off + book</li>
 *   <li>手册 + 连网 → off + book + on</li>
 *   <li>连网（无手册）→ off + Jade 显示</li>
 * </ol>
 */
public class TileInfusionMonitor extends ThETileBase implements IInWorldGridNodeHost {

    /** 手册槽位。 */
    public static final int BOOK_SLOT = 0;
    private static final int SCAN_INTERVAL = 10; // 每 10 tick 验证一次祭坛（缓存命中时仅 1 次查询）
    /** 未找到祭坛时的扫描退避间隔（tick）：全量扫描约 9800 次 getBlockEntity，附近无祭坛时降低频率控 mspt。 */
    private static final int SCAN_MISS_INTERVAL = 200;
    /** 风险重算限频（tick）：computeRisk 内部也要扫上千次 getBlockEntity（配方估算/源质汇总），控制 mspt。 */
    private static final int RISK_INTERVAL = 40;

    protected final IManagedGridNode mainNode;
    private final SimpleContainer inventory = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            TileInfusionMonitor.this.setChanged();
            TileInfusionMonitor.this.updateBookState();
        }
    };

    private int scanCounter = 0;
    private int riskCounter = 0;
    private BlockPos matrixPos = null;
    /** 上次扫描到的祭坛注魔中状态（翻转时强制立即重算风险，抓住注魔启动瞬间的高不稳定度）。 */
    private boolean cachedCrafting = false;

    // ===== M4：注魔结束红石脉冲 =====
    /** 脉冲剩余时长（tick）。仅运行期内存，不持久化——重载不回放（边界语义①）。 */
    private static final int PULSE_DURATION = 40; // 2 秒，裁决 #5
    private int pulseTicks = 0;

    /** 区块卸载/方块移除中：禁止 setBlock / markForUpdate（卸载时改方块状态会触发区块重载，停服保存死循环）。 */
    private boolean unloading = false;

    private RiskData cachedRisk = RiskData.NONE;
    private final InfusionMonitorDisplay display = new InfusionMonitorDisplay(this::getLevel, worldPosition);

    private final IGridNodeListener<TileInfusionMonitor> nodeListener = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(TileInfusionMonitor owner, IGridNode node) {
            owner.setChanged();
        }

        @Override
        public void onStateChanged(TileInfusionMonitor owner, IGridNode node, State state) {
            if (state == State.POWER) {
                owner.updateNetworkState();
            }
            owner.markForUpdate();
        }
    };

    public TileInfusionMonitor(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFUSION_MONITOR.get(), pos, state);
        this.mainNode = GridHelper.createManagedNode(this, nodeListener)
                .setVisualRepresentation(ModItems.INFUSION_MONITOR_ITEM.get())
                .setInWorldNode(true)
                .setTagName("proxy")
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class));
        this.mainNode.setIdlePowerUsage(40.0);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        unloading = false;
        if (level != null && !level.isClientSide()) {
            mainNode.create(level, getBlockPos());
        }
        updateBookState();
        updateNetworkState();
    }

    @Override
    public void setRemoved() {
        unloading = true; // 先置标志再销毁网格节点：节点销毁回调会触发 updateNetworkState
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            display.discard();
        }
        if (mainNode != null) {
            mainNode.destroy();
        }
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true; // 同上：卸载期间禁止改方块状态，否则 chunk 被重新标记脏 → 保存循环
        super.onChunkUnloaded();
        if (mainNode != null) {
            mainNode.destroy();
        }
    }

    @Override
    public void onBreakBlock() {
        super.onBreakBlock();
        if (level != null && !level.isClientSide) {
            display.discard();
            Containers.dropContents(level, worldPosition, inventory);
        }
    }

    // ===== tick / 扫描 =====

    public static void tick(Level level, BlockPos pos, BlockState state, TileInfusionMonitor be) {
        if (level.isClientSide()) return;
        // M4 脉冲计时：递减到 0 自动复位；counter 为 0 但方块仍为激活态（掉线重启残留）→ 自愈复位
        if (be.pulseTicks > 0) {
            be.pulseTicks--;
            if (be.pulseTicks == 0) {
                be.updatePulseState(false);
            }
        } else if (state.getValue(ModBlocks.PULSE)) {
            be.updatePulseState(false);
        }
        // 无祭坛缓存时全量扫描退避到 SCAN_MISS_INTERVAL（否则每 10 tick 扫 9800 次 getBlockEntity，
        // 附近没建祭坛时也持续 ~70us/t）
        int interval = be.matrixPos == null ? SCAN_MISS_INTERVAL : SCAN_INTERVAL;
        if (++be.scanCounter >= interval) {
            be.scanCounter = 0;
            be.scanMatrix();
        }
        be.updateNetworkState();
        be.updateBubble();
    }

    /** 扫描附近注魔矩阵并缓存风险数据。
     *  快速路径：已缓存矩阵仍在 → 只做 1 次 getBlockEntity 验证；
     *  全量扫描（33×9×33≈9800 次 getBlockEntity）仅缓存失效时执行，是 mspt 大户。
     *  风险重算限频（配方估算/源质汇总还要上千次查询），但注魔开始/结束瞬间（crafting 翻转）强制立即重算。 */
    private void scanMatrix() {
        BlockPos found = matrixPos;
        boolean craftingChanged = false;
        // 快速路径：缓存矩阵仍在 → 跳过全量扫描
        if (found != null) {
            if (level.getBlockEntity(found) instanceof InfusionMatrixBlockEntity matrix) {
                boolean crafting = matrix.crafting();
                craftingChanged = crafting != cachedCrafting;
                // M4：忙碌→空闲跳变 = 注魔结束（成功/失败均触发；TC port.261 无完成事件，
                // 故挂既有探测循环做下降沿检测，零额外扫描）。
                // cachedCrafting 仅运行期内存 → 重载后从 false 起步，不会把已结束的旧注魔补发脉冲（边界语义①）；
                // 每个 BE 独立检测，多观测器覆盖同一矩阵各自脉冲（边界语义②）。
                if (craftingChanged && cachedCrafting && !crafting) {
                    startPulse();
                }
                cachedCrafting = crafting;
            } else {
                found = null; // 缓存失效 → 走全量扫描
            }
        }
        if (found == null) {
            InfusionRiskCalculator.InfusionMatrixInfo info = InfusionRiskCalculator.scanNearbyMatrix(level, worldPosition);
            found = info != null ? info.matrixPos() : null;
        }

        RiskData newRisk = cachedRisk;
        if (found != null) {
            // 按 tick 累计（每轮扫描间隔 SCAN_INTERVAL）；首次发现即时重算（覆盖层/气泡 0.5 秒内出现），
            // 之后每 RISK_INTERVAL tick 重算一次（按"扫描轮数"计数会变成 40 轮×10 tick=20 秒才出数据）；
            // 注魔开始/结束瞬间（crafting 翻转）也立即重算，抓住启动时的高不稳定度。
            riskCounter += SCAN_INTERVAL;
            if (riskCounter >= RISK_INTERVAL || !cachedRisk.found() || craftingChanged) {
                riskCounter = 0;
                if (level.getBlockEntity(found) instanceof InfusionMatrixBlockEntity matrix) {
                    newRisk = InfusionRiskCalculator.computeRisk(level,
                            new InfusionRiskCalculator.InfusionMatrixInfo(found, matrix));
                } else {
                    newRisk = RiskData.NONE;
                }
            }
        } else {
            newRisk = RiskData.NONE;
            cachedCrafting = false;
        }

        boolean riskChanged = !newRisk.equals(this.cachedRisk);
        this.cachedRisk = newRisk;
        if (riskChanged || !java.util.Objects.equals(found, this.matrixPos)) {
            this.matrixPos = found;
            markForUpdate();
        }
    }

    // ===== M4：注魔结束红石脉冲 =====

    /** 触发单次脉冲：置激活态并计时，PULSE_DURATION tick 后由 tick 自动复位。 */
    private void startPulse() {
        this.pulseTicks = PULSE_DURATION;
        updatePulseState(true);
    }

    /** 写 PULSE 方块状态（与 updateNetworkState 同款防护：卸载/移除中禁止 setBlock）。 */
    private void updatePulseState(boolean pulsing) {
        if (level == null || level.isClientSide() || unloading || isRemoved()) return;
        BlockState st = getBlockState();
        if (st.getValue(ModBlocks.PULSE) != pulsing) {
            level.setBlock(worldPosition, st.setValue(ModBlocks.PULSE, pulsing), 3);
            BlockEntity be = level.getBlockEntity(worldPosition);
            if (be != null && be != this) {
                be.setChanged();
            }
        }
    }

    // ===== 网格节点 / 状态同步 =====

    private void updateNetworkState() {
        if (level == null || level.isClientSide() || unloading || isRemoved()) return;
        boolean net = mainNode.isActive();
        BlockState st = getBlockState();
        if (st.getValue(ModBlocks.NETWORK) != net) {
            level.setBlock(worldPosition, st.setValue(ModBlocks.NETWORK, net), 3);
            // setBlock 后本 BE 仍存在；刷新引用
            BlockEntity be = level.getBlockEntity(worldPosition);
            if (be != null && be != this) {
                be.setChanged();
            }
        }
    }

    private void updateBookState() {
        if (level == null || level.isClientSide() || unloading || isRemoved()) return;
        boolean hasBook = !inventory.getItem(BOOK_SLOT).isEmpty();
        BlockState st = getBlockState();
        if (st.getValue(ModBlocks.BOOK) != hasBook) {
            level.setBlock(worldPosition, st.setValue(ModBlocks.BOOK, hasBook), 3);
            BlockEntity be = level.getBlockEntity(worldPosition);
            if (be != null && be != this) {
                be.setChanged();
            }
        }
    }

    // ===== 悬浮气泡（text_display 实体） =====

    /**
     * 每 tick 更新气泡：条件 = 放入魔导手册 且 连接 AE 网络 且 检测到注魔祭坛。
     * 满足 → 生成/更新气泡；不满足 → 销毁气泡。
     */
    private void updateBubble() {
        if (level == null || level.isClientSide() || unloading) return;
        boolean shouldShow = getBlockState().getValue(ModBlocks.BOOK)
                && getBlockState().getValue(ModBlocks.NETWORK)
                && cachedRisk.found();
        display.update(shouldShow, shouldShow ? InfusionMonitorText.bubble(cachedRisk) : Component.empty());
    }

    /** 放入魔导手册（方块右键拦截后调用，防止书自身 use 打开界面）。 */
    public void placeBook(Player player, ItemStack held) {
        if (level == null || level.isClientSide()) return;
        if (inventory.getItem(BOOK_SLOT).isEmpty()) {
            inventory.setItem(BOOK_SLOT, held.copyWithCount(1));
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            setChanged();
        }
    }

    /** 取回魔导手册（Shift+右键）。 */
    public void retrieveBook(Player player) {
        if (level == null || level.isClientSide()) return;
        ItemStack book = inventory.getItem(BOOK_SLOT);
        if (!book.isEmpty()) {
            if (!player.getInventory().add(book)) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), book);
            }
            inventory.setItem(BOOK_SLOT, ItemStack.EMPTY);
            setChanged();
        }
    }

    /** 右键：聊天框提示。需手册 + 联网才显示详细风险；否则提示缺失项。 */
    public void showDescriptionInChat(Player player) {
        if (level == null || level.isClientSide()) return;
        boolean hasBook = getBlockState().getValue(ModBlocks.BOOK);
        boolean hasNetwork = getBlockState().getValue(ModBlocks.NETWORK);
        if (!hasBook && !hasNetwork) {
            player.sendSystemMessage(Component.literal("§7韵魔观测器: 缺少引导的书籍与网络能量（魔导手册与 AE 网络）"));
            return;
        }
        if (!hasBook) {
            player.sendSystemMessage(Component.literal("§7韵魔观测器: 需要放置魔导手册作为引导"));
            return;
        }
        if (!hasNetwork) {
            player.sendSystemMessage(Component.literal("§7韵魔观测器: 需要接入 AE 网络供能"));
            return;
        }
        if (!cachedRisk.found()) {
            player.sendSystemMessage(Component.literal("§7韵魔观测器: 未检测到注魔祭坛"));
            return;
        }
        String tierWord = tierWord(cachedRisk.tier());
        String desc = tierDesc(cachedRisk.tier());
        Component msg = Component.literal("注魔风险: ")
                .withStyle(style -> style.withColor(tierColor(cachedRisk.tier())))
                .append(Component.literal(tierWord + "§7"))
                .append(Component.literal("\n不稳定度: " + cachedRisk.instability()
                        + " (基础 " + cachedRisk.recipeInstability() + " + 祭坛 " + cachedRisk.symmetry() + ")§f\n"))
                .append(Component.literal(desc));
        player.sendSystemMessage(msg);
    }

    public static boolean isThaumonomicon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "thaumonomicon")));
    }

    // ===== 访问器 =====

    public Container getInventory() {
        return inventory;
    }

    public boolean hasBook() {
        return !inventory.getItem(BOOK_SLOT).isEmpty();
    }

    public RiskData getCachedRisk() {
        return cachedRisk;
    }

    /** ME 网络是否在线（节点 active：有能量 + 有频道）。Jade 显示网络状态用。 */
    public boolean isNetworkOnline() {
        return mainNode.isActive();
    }

    /** 网格节点是否有能量。Jade 状态细分（在线/无频道/离线）用。 */
    public boolean isNodePowered() {
        return mainNode.isPowered();
    }

    // ===== IInWorldGridNodeHost =====

    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    public void markForUpdate() {
        // 卸载/移除中跳过：sendBlockUpdated / blockEntityChanged 会把区块重新标记脏，停服保存时形成死循环
        if (level == null || unloading || isRemoved()) return;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        // blockEntityChanged → 触发 getUpdateTag 同步到客户端（悬浮弹窗数据依赖）
        level.blockEntityChanged(worldPosition);
    }

    // ===== NBT =====

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = super.getUpdateTag(reg);
        saveRiskToTag(tag);
        if (!inventory.getItem(BOOK_SLOT).isEmpty()) {
            tag.put("Book", inventory.getItem(BOOK_SLOT).save(reg));
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider reg) {
        super.handleUpdateTag(tag, reg);
        loadRiskFromTag(tag);
        inventory.setItem(BOOK_SLOT, ItemStack.parseOptional(reg, tag.getCompound("Book")));
    }

    private void saveRiskToTag(CompoundTag tag) {
        tag.putBoolean("RiskFound", cachedRisk.found());
        tag.putBoolean("RiskCrafting", cachedRisk.crafting());
        tag.putInt("RiskTier", cachedRisk.tier());
        tag.putInt("RiskInstability", cachedRisk.instability());
        tag.putInt("RiskSymmetry", cachedRisk.symmetry());
        tag.putInt("RiskRecipeInstability", cachedRisk.recipeInstability());
        tag.putString("RiskTierText", cachedRisk.tierText());
        tag.putString("RiskShortages", cachedRisk.shortages());
        tag.putInt("RiskTotalNeeded", cachedRisk.totalNeeded());
    }

    private void loadRiskFromTag(CompoundTag tag) {
        this.cachedRisk = new RiskData(
                tag.getBoolean("RiskFound"),
                tag.getBoolean("RiskCrafting"),
                tag.getInt("RiskTier"),
                tag.getInt("RiskInstability"),
                tag.getInt("RiskSymmetry"),
                tag.getInt("RiskRecipeInstability"),
                tag.getString("RiskTierText"),
                tag.getString("RiskShortages"),
                tag.getInt("RiskTotalNeeded"));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        if (tag.contains("Book")) {
            inventory.setItem(BOOK_SLOT, ItemStack.parseOptional(reg, tag.getCompound("Book")));
        }
        if (tag.contains("MatrixPos")) {
            matrixPos = BlockPos.of(tag.getLong("MatrixPos"));
        }
        mainNode.loadFromNBT(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        if (!inventory.getItem(BOOK_SLOT).isEmpty()) {
            tag.put("Book", inventory.getItem(BOOK_SLOT).save(reg));
        }
        if (matrixPos != null) {
            tag.putLong("MatrixPos", matrixPos.asLong());
        }
        mainNode.saveToNBT(tag);
    }

    /** 注魔风险缓存数据（Jade 用）。 */
    public record RiskData(boolean found, boolean crafting, int tier, int instability, int symmetry,
                           int recipeInstability, String tierText, String shortages, int totalNeeded) {
        public static final RiskData NONE = new RiskData(false, false, 0, 0, 0, 0, "", "", 0);
    }
}
