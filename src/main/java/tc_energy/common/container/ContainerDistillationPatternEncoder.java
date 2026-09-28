package tc_energy.common.container;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import dev.tc4port.thaumcraft.api.aspect.AspectQueryApi;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.research.ResearchApi;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import dev.tc4port.thaumcraft.api.research.ScanApi;
import tc_energy.common.container.slot.ReadOnlySlot;
import tc_energy.common.integration.appeng.AEssentiaKey;
import tc_energy.common.integration.tc.TCReflection;
import tc_energy.common.network.DistillationEncoderSetSourceC2SPacket;
import tc_energy.common.tiles.TileDistillationPatternEncoder;
import tc_energy.init.ModMenuTypes;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ContainerDistillationPatternEncoder extends AbstractContainerMenu {

    /** 蒸馏编码统一要求的研究节点（要素蒸馏）。 */
    private static final String RESEARCH_DISTILESSENTIA = "DISTILESSENTIA";
    private static final String NBTKEY_RESEARCH = "research";

    private static final int PLAYER_INV_Y = 150;
    private static final int HOTBAR_Y = 208;
    private static final int PLAYER_INV_X = 8;

    private static final int SLOT_SOURCE_X = 15;
    private static final int SLOT_SOURCE_Y = 69;

    private static final int SLOT_ASPECTS_X = 65;
    private static final int SLOT_ASPECTS_Y = 24;
    private static final int SLOT_ASPECTS_COUNT = 6;

    private static final int SLOT_SELECTED_X = 116;
    private static final int SLOT_SELECTED_Y = 69;

    private static final int SLOT_BLANK_X = 146;
    private static final int SLOT_BLANK_Y = 75;

    private static final int SLOT_ENCODED_X = 146;
    private static final int SLOT_ENCODED_Y = 113;

    private static final int PLAYER_SLOTS = 36;
    private static final int ASPECT_SLOT_START = 36;
    private static final int SELECTED_SLOT = 42;
    private static final int SOURCE_SLOT = 43;
    private static final int BLANK_SLOT = 44;
    private static final int ENCODED_SLOT = 45;

    private final TileDistillationPatternEncoder encoder;
    private final Inventory playerInv;

    private final ItemStackHandler aspectInventory = new ItemStackHandler(SLOT_ASPECTS_COUNT + 1);

    private AspectId[] cachedAspects = new AspectId[0];
    private int selectedAspectIndex = -1;

    /** 上次解析的源物品，用于判定是否需要重新解析 aspects（避免每帧调用 getObjectAspects）。 */
    private ItemStack lastSourceItem = ItemStack.EMPTY;

    public ContainerDistillationPatternEncoder(int id, Inventory inv) {
        this(id, inv, (TileDistillationPatternEncoder) null);
    }

    public ContainerDistillationPatternEncoder(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, (TileDistillationPatternEncoder) null);
    }

    public ContainerDistillationPatternEncoder(int id, Inventory inv, TileDistillationPatternEncoder encoder) {
        super(ModMenuTypes.DISTILLATION_ENCODER.get(), id);
        this.encoder = encoder;
        this.playerInv = inv;

        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++)
                addSlot(new Slot(inv, c + r * 9 + 9, PLAYER_INV_X + c * 18, PLAYER_INV_Y + r * 18));
        for (int c = 0; c < 9; c++)
            addSlot(new Slot(inv, c, PLAYER_INV_X + c * 18, HOTBAR_Y));

        for (int i = 0; i < SLOT_ASPECTS_COUNT; i++) {
            int y = SLOT_ASPECTS_Y + i * 18;
            addSlot(new ReadOnlySlot(aspectInventory, i, SLOT_ASPECTS_X, y));
        }

        addSlot(new ReadOnlySlot(aspectInventory, SLOT_ASPECTS_COUNT, SLOT_SELECTED_X, SLOT_SELECTED_Y));

        if (encoder != null) {
            // 源物品槽：ghost 模板（背包拖入不消耗——实际物品还在手里；空手点击清空；JEI 拖拽设置模板）
            addSlot(new SlotItemHandler(encoder.inventory, TileDistillationPatternEncoder.SLOT_SOURCE_ITEM, SLOT_SOURCE_X, SLOT_SOURCE_Y) {
                @Override
                public int getMaxStackSize() { return 1; }
                @Override
                public boolean mayPlace(ItemStack s) { return true; }
                @Override
                public boolean mayPickup(Player p) { return false; }
            });
            addSlot(new SlotItemHandler(encoder.inventory, TileDistillationPatternEncoder.SLOT_BLANK_PATTERNS, SLOT_BLANK_X, SLOT_BLANK_Y) {
                @Override
                public boolean mayPlace(ItemStack s) { return AEItems.BLANK_PATTERN.is(s); }
                @Override
                public int getMaxStackSize() { return 64; }
            });
            addSlot(new SlotItemHandler(encoder.inventory, TileDistillationPatternEncoder.SLOT_ENCODED_PATTERN, SLOT_ENCODED_X, SLOT_ENCODED_Y) {
                @Override
                public boolean mayPlace(ItemStack s) { return false; }
            });
        } else {
            // 客户端兜底：三个槽各自独立（避免共享槽导致放入互相覆盖）
            ItemStackHandler dummy = new ItemStackHandler(3);
            addSlot(new SlotItemHandler(dummy, TileDistillationPatternEncoder.SLOT_SOURCE_ITEM, SLOT_SOURCE_X, SLOT_SOURCE_Y) {
                @Override
                public int getMaxStackSize() { return 1; }
                @Override
                public boolean mayPlace(ItemStack s) { return true; }
                @Override
                public boolean mayPickup(Player p) { return false; }
            });
            addSlot(new SlotItemHandler(dummy, TileDistillationPatternEncoder.SLOT_BLANK_PATTERNS, SLOT_BLANK_X, SLOT_BLANK_Y) {
                @Override
                public boolean mayPlace(ItemStack s) { return AEItems.BLANK_PATTERN.is(s); }
                @Override
                public int getMaxStackSize() { return 64; }
            });
            addSlot(new SlotItemHandler(dummy, TileDistillationPatternEncoder.SLOT_ENCODED_PATTERN, SLOT_ENCODED_X, SLOT_ENCODED_Y) {
                @Override
                public boolean mayPlace(ItemStack s) { return false; }
            });
        }
    }

    /** 源物品槽内容。服务端从 Tile 槽 0 读（权威）；客户端从槽同步内容读（encoder 为 null）。 */
    public ItemStack getSourceItem() {
        if (encoder != null) {
            return encoder.inventory.getStackInSlot(TileDistillationPatternEncoder.SLOT_SOURCE_ITEM);
        }
        if (SOURCE_SLOT >= 0 && SOURCE_SLOT < slots.size()) {
            return slots.get(SOURCE_SLOT).getItem();
        }
        return ItemStack.EMPTY;
    }

    /** 服务端：设置源物品（JEI 拖拽）——写入 encoder 槽 0 + 刷新 aspects。 */
    public void setSourceItemOnServer(ItemStack stack) {
        if (encoder == null) return;
        encoder.inventory.setStackInSlot(TileDistillationPatternEncoder.SLOT_SOURCE_ITEM, stack.copy());
        encoder.setChanged();
        updateAspects();
    }

    /**
     * 源物品的 aspects：源物品变化（含 NBT/扫描数据）时才调用 {@code getObjectAspects} 重新解析，
     * 否则返回缓存——每帧渲染调用仅做 O(1) 物品比较，不频繁请求 TC。
     */
    public AspectId[] getSourceAspects() {
        ItemStack source = getSourceItem();
        if (!ItemStack.isSameItemSameComponents(source, lastSourceItem)) {
            lastSourceItem = source.copy();
            if (source.isEmpty()) {
                cachedAspects = new AspectId[0];
                if (selectedAspectIndex != -1) selectedAspectIndex = -1;
            } else {
                // 玩家必须已用魔导透镜扫描过"该物品"才显示其要素（Ren 版 ScanApi 物品扫描检查）
                if (!ScanApi.isObjectScanned(playerInv.player, source)) {
                    cachedAspects = new AspectId[0];
                } else {
                    // 20723 API：物体（扫描）要素查询 = AspectQueryApi.item(stack)（旧 ThaumcraftApiHelper.getObjectAspects 已移除）
                    var aspects = AspectQueryApi.item(source);
                    if (aspects == null || aspects.amounts().isEmpty()) {
                        cachedAspects = new AspectId[0];
                    } else {
                        // 排序保证客户端/服务端解析顺序一致（GUI 第 X 格 = 编码输出的第 X 个要素）
                        cachedAspects = aspects.amounts().keySet().stream()
                                .sorted(java.util.Comparator.comparing(a -> a.serialized()))
                                .toArray(AspectId[]::new);
                    }
                }
                if (selectedAspectIndex >= cachedAspects.length) selectedAspectIndex = -1;
            }
        }
        return cachedAspects;
    }

    private void updateAspects() {
        // 统一走缓存解析逻辑
        getSourceAspects();
        // aspect 槽只读显示，图标由 GUI 绘制（保持槽空）
        for (int i = 0; i < SLOT_ASPECTS_COUNT; i++) {
            aspectInventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    private void clearAspectSlots() {
        for (int i = 0; i <= SLOT_ASPECTS_COUNT; i++) {
            aspectInventory.setStackInSlot(i, ItemStack.EMPTY);
        }
        selectedAspectIndex = -1;
    }

    public AspectId[] getCachedAspects() {
        return cachedAspects;
    }

    public int getSelectedAspectIndex() {
        return selectedAspectIndex;
    }

    public AspectId getSelectedAspect() {
        if (selectedAspectIndex >= 0 && selectedAspectIndex < cachedAspects.length) {
            return cachedAspects[selectedAspectIndex];
        }
        return null;
    }

    /**
     * Encode 按钮可用的条件：源物品已配置 + 已选要素 + 空白样板槽有样板 + 成品槽为空。
     * 服务端从 Tile 读（权威）；客户端 encoder 为 null，从同步的槽内容判断。
     * 先执行 getSourceAspects()：重开 GUI 后源物品仍在 Tile 中，但本容器缓存为空，
     * 不先解析会误判"未选要素"导致按钮置灰/编码无效。
     */
    public boolean canEncode() {
        getSourceAspects();
        if (getSelectedAspect() == null) return false;
        if (getSourceItem().isEmpty()) return false;
        if (encoder != null) {
            if (encoder.inventory.getStackInSlot(TileDistillationPatternEncoder.SLOT_BLANK_PATTERNS).isEmpty()) return false;
            return encoder.inventory.getStackInSlot(TileDistillationPatternEncoder.SLOT_ENCODED_PATTERN).isEmpty();
        }
        if (BLANK_SLOT < 0 || BLANK_SLOT >= slots.size() || slots.get(BLANK_SLOT).getItem().isEmpty()) return false;
        return ENCODED_SLOT >= 0 && ENCODED_SLOT < slots.size() && slots.get(ENCODED_SLOT).getItem().isEmpty();
    }

    public void onEncodePattern() {
        if (encoder == null) return;

        // 研究限制：必须解锁"要素蒸馏"才能配置物品→源质蒸馏样板（Ren 版 data pack 研究）
        if (!ResearchApi.isComplete(playerInv.player, ResearchKey.parse(RESEARCH_DISTILESSENTIA))) {
            return;
        }

        // 重开 GUI 后服务端缓存为空：先从 Tile 的源物品重新解析要素，
        // 否则 getSelectedAspect() 恒为 null（客户端已选、服务端无缓存 → 点击 Encode 无效）。
        getSourceAspects();
        AspectId selected = getSelectedAspect();
        ItemStack source = getSourceItem();
        ItemStack blankSlot = encoder.inventory.getStackInSlot(TileDistillationPatternEncoder.SLOT_BLANK_PATTERNS);
        ItemStack encodedSlot = encoder.inventory.getStackInSlot(TileDistillationPatternEncoder.SLOT_ENCODED_PATTERN);

        if (selected == null) return;

        if (source.isEmpty()) return;

        if (blankSlot.isEmpty()) return;

        // 验证必须是 AE2 空白样板，否则不消耗不编码（日志定位 is() 运行时行为）
        if (!AEItems.BLANK_PATTERN.is(blankSlot)) {
            return;
        }

        if (!encodedSlot.isEmpty()) return;

        blankSlot.shrink(1);
        if (blankSlot.isEmpty()) {
            encoder.inventory.setStackInSlot(TileDistillationPatternEncoder.SLOT_BLANK_PATTERNS, ItemStack.EMPTY);
        }

        AEItemKey sourceKey = AEItemKey.of(source);
        GenericStack input = new GenericStack(sourceKey, 1);

        // 蒸馏语义：源物品 → 选中源质，输出数量 = 源物品中该要素的数量（如骨头食欲=2 → 输出食欲×2）
        ResourceLocation aspectId = selected.value();
        // 20723 API：改用 AspectQueryApi.item(stack)
        var objAspects = AspectQueryApi.item(source);
        long amount = objAspects.amount(selected);
        if (amount < 1) amount = 1;
        GenericStack output = new GenericStack(AEssentiaKey.of(aspectId), amount);

        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(input),
                List.of(output)
        );

        // 在样板上标记研究键（供使用端按玩家校验）
        CompoundTag researchTag = new CompoundTag();
        researchTag.putString(NBTKEY_RESEARCH, RESEARCH_DISTILESSENTIA);
        encoded.set(DataComponents.CUSTOM_DATA, CustomData.of(researchTag));

        encoder.inventory.setStackInSlot(TileDistillationPatternEncoder.SLOT_ENCODED_PATTERN, encoded);
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size()) {
            Slot slot = slots.get(slotId);

            if (slotId == SOURCE_SLOT) {
                // ghost 模板语义：手持物品→设模板（不消耗，实际物品留在手里）；空手→清空
                ItemStack carried = getCarried();
                ItemStack newSource = carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
                if (player.level().isClientSide) {
                    if (slotId < slots.size()) {
                        slots.get(slotId).set(newSource);
                    }
                    PacketDistributor.sendToServer(new DistillationEncoderSetSourceC2SPacket(containerId, newSource));
                } else {
                    setSourceItemOnServer(newSource);
                }
                updateAspects();
                return;
            }

            if (slotId >= ASPECT_SLOT_START && slotId < ASPECT_SLOT_START + SLOT_ASPECTS_COUNT) {
                int aspectIdx = slotId - ASPECT_SLOT_START;
                // 始终记录用户选择（越界由 getSelectedAspect 校验），避免因 cachedAspects 状态导致服务端选择丢失
                selectedAspectIndex = aspectIdx;
                // selected 槽留空，图标由 GUI 绘制
                aspectInventory.setStackInSlot(SELECTED_SLOT - ASPECT_SLOT_START, ItemStack.EMPTY);
                return;
            }
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) {
            onEncodePattern();
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int idx) {
        Slot slot = slots.get(idx);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (idx < PLAYER_SLOTS) {
            // 空白样板/其他物品 shift → 尝试放入 blank 槽（mayPlace 已放宽，Encode 时验证）
            if (!moveItemStackTo(stack, BLANK_SLOT, BLANK_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (idx == BLANK_SLOT || idx == ENCODED_SLOT) {
            if (!moveItemStackTo(stack, 0, PLAYER_SLOTS, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();

        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (encoder != null && !encoder.getLevel().isClientSide) {
            encoder.setChanged();
        }
    }
}
