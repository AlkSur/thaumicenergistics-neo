package thaumicenergistics.common.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.extensions.IPlayerExtension;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumSet;
import java.util.List;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.util.AECableType;
import net.minecraft.world.item.Items;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModBlocks;

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
    private static final int SCAN_INTERVAL = 10; // 每 10 tick 扫描一次祭坛

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
    private BlockPos matrixPos = null;

    /** 缓存的注魔风险数据（弹窗显示）。 */
    private RiskData cachedRisk = RiskData.NONE;

    /** 悬浮气泡实体（text_display，服务端引用）。 */
    private net.minecraft.world.entity.Display.TextDisplay bubble;

    /** 气泡实体 Tag（用于坐标匹配查找）。 */
    private static final String BUBBLE_TAG = "thaumicenergistics:monitor_bubble";
    /** 气泡背景：半透明深灰（ARGB，Alpha=176）。 */
    private static final int BUBBLE_BG_COLOR = 0xB0333333;
    /** 气泡行宽（换行宽度）。中文全角字符约 12px，最长行"不稳定度: 9 (基础 5 + 祭坛 4)"约 240px——
     *  必须大于最长行，否则行尾空格被换行逻辑丢弃、背景只包住分段 → 右侧看似截断。 */
    private static final int BUBBLE_LINE_WIDTH = 320;

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
                .setVisualRepresentation(thaumicenergistics.init.ModItems.INFUSION_MONITOR_ITEM.get())
                .setInWorldNode(true)
                .setTagName("proxy")
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setExposedOnSides(EnumSet.allOf(Direction.class));
        this.mainNode.setIdlePowerUsage(40.0);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            mainNode.create(level, getBlockPos());
        }
        updateBookState();
        updateNetworkState();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            discardBubble();
        }
        if (mainNode != null) {
            mainNode.destroy();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (mainNode != null) {
            mainNode.destroy();
        }
    }

    @Override
    public void onBreakBlock() {
        super.onBreakBlock();
        if (level != null && !level.isClientSide) {
            discardBubble();
            Containers.dropContents(level, worldPosition, inventory);
        }
    }

    // ===== tick / 扫描 =====

    public static void tick(Level level, BlockPos pos, BlockState state, TileInfusionMonitor be) {
        if (level.isClientSide()) return;
        if (++be.scanCounter >= SCAN_INTERVAL) {
            be.scanCounter = 0;
            be.scanMatrix();
        }
        be.updateNetworkState();
        be.updateBubble();
    }

    /** 扫描附近注魔矩阵并缓存风险数据。 */
    private void scanMatrix() {
        BlockPos found = null;
        InfusionRiskCalculator.InfusionMatrixInfo info = InfusionRiskCalculator.scanNearbyMatrix(level, worldPosition);
        RiskData newRisk = info != null ? InfusionRiskCalculator.computeRisk(level, info) : RiskData.NONE;
        boolean riskChanged = !newRisk.equals(this.cachedRisk);
        this.cachedRisk = newRisk;
        if (info != null) {
            found = info.matrixPos();
        }
        if (riskChanged || !java.util.Objects.equals(found, this.matrixPos)) {
            this.matrixPos = found;
            markForUpdate();
        }
    }

    // ===== 网格节点 / 状态同步 =====

    private void updateNetworkState() {
        if (level == null || level.isClientSide()) return;
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
        if (level == null || level.isClientSide()) return;
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
        if (level == null || level.isClientSide()) return;
        boolean shouldShow = getBlockState().getValue(ModBlocks.BOOK)
                && getBlockState().getValue(ModBlocks.NETWORK)
                && cachedRisk.found();
        if (shouldShow) {
            ensureBubble();
            if (bubble != null) {
                applyBubbleNbt(buildBubbleText(cachedRisk));
            }
        } else {
            discardBubble();
        }
    }

    /** 确保气泡实体存在（不存在则生成；区块重载后按 Tag 找回旧实体）。 */
    private void ensureBubble() {
        if (bubble != null && bubble.isAlive()) return;
        bubble = findBubble();
        if (bubble != null) return;
        bubble = EntityType.TEXT_DISPLAY.create(level);
        if (bubble == null) return;
        bubble.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5);
        // 1.21.1 TextDisplay 配置 API 均为 private/protected：反射调用 readAdditionalSaveData
        // 用 summon 命令同款 NBT 一次配置全部字段（text/background/billboard/shadow/see_through 等）。
        applyBubbleNbt(buildBubbleText(cachedRisk));
        bubble.addTag(BUBBLE_TAG);
        level.addFreshEntity(bubble);
    }

    /** 反射调用 readAdditionalSaveData(CompoundTag) 配置气泡（缓存 Method）。 */
    private static java.lang.reflect.Method READ_NBT_METHOD;

    private void applyBubbleNbt(Component text) {
        if (bubble == null || level == null) return;
        try {
            if (READ_NBT_METHOD == null) {
                READ_NBT_METHOD = net.minecraft.world.entity.Entity.class
                        .getDeclaredMethod("readAdditionalSaveData", CompoundTag.class);
                READ_NBT_METHOD.setAccessible(true);
            }
            CompoundTag nbt = new CompoundTag();
            nbt.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
            nbt.putBoolean("default_background", false);
            nbt.putInt("background", BUBBLE_BG_COLOR);
            nbt.putString("billboard", "vertical");
            nbt.putInt("line_width", BUBBLE_LINE_WIDTH);
            nbt.putBoolean("shadow", true);
            nbt.putBoolean("see_through", false);
            // 文字调小：transformation.scale 整体缩到 0.5（text_display 无字号参数，靠实体缩放）
            CompoundTag transform = new CompoundTag();
            transform.put("scale", floatList(0.5F, 0.5F, 0.5F));
            transform.put("translation", floatList(0F, 0F, 0F));
            transform.put("left_rotation", floatList(0F, 0F, 0F, 1F));
            transform.put("right_rotation", floatList(0F, 0F, 0F, 1F));
            nbt.put("transformation", transform);
            READ_NBT_METHOD.invoke(bubble, nbt);
        } catch (Exception e) {
            ThaumicEnergistics.LOG.warn("[Monitor] 气泡配置失败: {}", e.toString());
        }
    }

    /** 构造 FloatTag 列表（1.21.1 CompoundTag 无 putFloatArray，需 ListTag）。 */
    private static net.minecraft.nbt.ListTag floatList(float... values) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (float v : values) {
            list.add(net.minecraft.nbt.FloatTag.valueOf(v));
        }
        return list;
    }

    /** 按 Tag + 方块附近 AABB 查找已存在的气泡。 */
    private net.minecraft.world.entity.Display.TextDisplay findBubble() {
        if (level == null) return null;
        AABB aabb = new AABB(worldPosition).inflate(0.15, 2.0, 0.15);
        List<net.minecraft.world.entity.Display.TextDisplay> list = level.getEntitiesOfClass(
                net.minecraft.world.entity.Display.TextDisplay.class, aabb,
                e -> e.getTags().contains(BUBBLE_TAG));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 销毁气泡（方块拆除/条件不满足时）。 */
    private void discardBubble() {
        if (bubble != null && bubble.isAlive()) {
            bubble.discard();
        } else {
            net.minecraft.world.entity.Display.TextDisplay found = findBubble();
            if (found != null) {
                found.discard();
            }
        }
        bubble = null;
    }

    /** 风险数据 → 气泡多行文本（2 行：注魔风险/不稳定度；不显示基础+祭坛细分，细分在聊天栏）。 */
    private static Component buildBubbleText(RiskData risk) {
        // 内边距必须用不间断空格（\u00A0）：普通空格在行尾会被字体布局裁剪，
        // 导致背景只包住左侧、右侧贴边。NBSP 有真实字形，两侧都保留。
        String pad = "\u00A0\u00A0"; // 左右内边距（NBSP）
        String tierWord = tierWord(risk.tier());

        Component line1 = Component.literal(pad + "注魔风险: " + tierWord + pad)
                .withStyle(style -> style.withColor(tierColor(risk.tier())));
        Component line2 = Component.literal(pad + "不稳定度: " + risk.instability() + pad)
                .withStyle(style -> style.withColor(0xFFFFFFFF));

        // 上下各留一个空行（垂直内边距）
        return Component.literal("\n")
                .append(line1).append("\n")
                .append(line2).append("\n");
    }

    /** 5 档文字（无星星）。 */
    private static String tierWord(int tier) {
        return switch (tier) {
            case 1 -> "极低";
            case 2 -> "低";
            case 3 -> "中";
            case 4 -> "高";
            case 5 -> "极高";
            default -> "?";
        };
    }

    /** 5 档描述（神秘时代风）。 */
    private static String tierDesc(int tier) {
        return switch (tier) {
            case 1 -> "灵光如镜，仪式安然";
            case 2 -> "灵光微澜，无碍仪式";
            case 3 -> "符文低鸣，灵光流转不宁";
            case 4 -> "裂隙于虚空中睁开，注视着祭坛";
            case 5 -> "咒波翻涌，裂隙将吞没一切";
            default -> "";
        };
    }

    /** 档位颜色：1 绿 → 5 红。 */
    private static int tierColor(int tier) {
        return switch (tier) {
            case 1 -> 0xFF55FF55;
            case 2 -> 0xFFAAFF55;
            case 3 -> 0xFFFFFF55;
            case 4 -> 0xFFFFAA55;
            default -> 0xFFFF5555;
        };
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
        return stack.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "thaumonomicon")));
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

    // ===== IInWorldGridNodeHost =====

    @Override
    public IGridNode getGridNode(Direction dir) {
        return mainNode.getNode();
    }

    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    public void markForUpdate() {
        if (level != null) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            // blockEntityChanged → 触发 getUpdateTag 同步到客户端（悬浮弹窗数据依赖）
            level.blockEntityChanged(worldPosition);
        }
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
        if (tag.contains("Book")) {
            inventory.setItem(BOOK_SLOT, ItemStack.parseOptional(reg, tag.getCompound("Book")));
        }
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
