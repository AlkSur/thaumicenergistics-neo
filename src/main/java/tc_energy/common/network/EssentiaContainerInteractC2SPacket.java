package tc_energy.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import tc_energy.ThaumicEnergistics;
import tc_energy.common.container.EssentiaFillHelper;
import dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi;
import tc_energy.common.integration.appeng.AEssentiaKey;
import tc_energy.common.integration.tc.ThEEssentiaContainers;

/**
 * 源质容器（罐子/安瓿）通用存取 C2S 包 —— 替代旧 MixinMEStorageScreen 的全部交互。
 * <p>
 * 交互矩阵（ScreenEvent 拦截 + 本包，无 mixin）：
 * <ul>
 *   <li>装满容器 右键 → 存入手中 1 个容器全部源质（容器留空）</li>
 *   <li>装满容器 Shift+右键 → 存入手中整组容器全部源质</li>
 *   <li>空容器 右键 + 网格条目 → 取出一整罐/整瓶（罐 64 / 瓶 8）</li>
 *   <li>空容器 Shift+右键 + 网格条目 → 尽可能装满手中整组容器</li>
 *   <li>左键 → 不拦截，走 AE2 原生（容器物品本身入网络）</li>
 * </ul>
 * mode: DEPOSIT（存入）/ FILL（取出装填）。wholeGroup: 是否处理手中全部容器。
 */
public record EssentiaContainerInteractC2SPacket(int containerId, int mode, boolean wholeGroup,
        ResourceLocation aspectId, ItemStack sourceStack) implements CustomPacketPayload {

    public static final int MODE_DEPOSIT = 0;
    public static final int MODE_FILL = 1;

    public static final Type<EssentiaContainerInteractC2SPacket> TYPE =
            new Type<>(ThaumicEnergistics.id("essentia_container_interact"));

    /**
     * 可空 ResourceLocation 编解码。
     * <p>
     * ⚠ {@code ResourceLocation.STREAM_CODEC} 不接受 null（encode 时 NPE），
     * 而 DEPOSIT 模式的 {@code aspectId} 恒为 null —— 直接使用会导致
     * {@code Failed encoding custom payload tc_energy:essentia_container_interact} 断连。
     */
    private static final StreamCodec<RegistryFriendlyByteBuf, ResourceLocation> OPTIONAL_RESOURCE_LOCATION =
            StreamCodec.of(
                    (buf, value) -> {
                        buf.writeBoolean(value != null);
                        if (value != null) {
                            ResourceLocation.STREAM_CODEC.encode(buf, value);
                        }
                    },
                    buf -> buf.readBoolean() ? ResourceLocation.STREAM_CODEC.decode(buf) : null
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaContainerInteractC2SPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EssentiaContainerInteractC2SPacket::containerId,
                    ByteBufCodecs.VAR_INT, EssentiaContainerInteractC2SPacket::mode,
                    ByteBufCodecs.BOOL, EssentiaContainerInteractC2SPacket::wholeGroup,
                    OPTIONAL_RESOURCE_LOCATION, EssentiaContainerInteractC2SPacket::aspectId,
                    ItemStack.OPTIONAL_STREAM_CODEC, EssentiaContainerInteractC2SPacket::sourceStack,
                    EssentiaContainerInteractC2SPacket::new
            );

    public static EssentiaContainerInteractC2SPacket deposit(int containerId, boolean wholeGroup, ItemStack stack) {
        return new EssentiaContainerInteractC2SPacket(containerId, MODE_DEPOSIT, wholeGroup, null, stack.copy());
    }

    public static EssentiaContainerInteractC2SPacket fill(int containerId, boolean wholeGroup,
            ResourceLocation aspectId, ItemStack stack) {
        return new EssentiaContainerInteractC2SPacket(containerId, MODE_FILL, wholeGroup, aspectId, stack.copy());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handleOnServer(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer player)
                    || player.containerMenu.containerId != this.containerId
                    || !(player.containerMenu instanceof appeng.menu.me.common.MEStorageMenu menu)) {
                return;
            }
            // 服务端权威来源：carried（拾取态）或主手
            ItemStack hand = !menu.getCarried().isEmpty() ? menu.getCarried() : player.getMainHandItem();
            if (this.mode == MODE_DEPOSIT) {
                handleDeposit(menu, player, hand);
            } else {
                handleFill(menu, player, hand);
            }
            menu.broadcastChanges();
        });
    }

    /** 存入：wholeGroup=false 恰好存手中 1 个容器；true 逐个存完整组。 */
    private void handleDeposit(appeng.menu.me.common.MEStorageMenu menu,
            net.minecraft.server.level.ServerPlayer player, ItemStack hand) {
        if (hand.isEmpty() || !ThEEssentiaContainers.isEssentiaContainer(hand)) {
            return;
        }
        var storage = tc_energy.common.container.MenuAccess.getStorage(menu);
        var energy = tc_energy.common.container.MenuAccess.getEnergySource(menu);
        final int loops = this.wholeGroup ? hand.getCount() : 1;
        for (int i = 0; i < loops; i++) {
            if (hand.isEmpty() || !ThEEssentiaContainers.isEssentiaContainer(hand)) {
                break;
            }
            // 每次都从"当前手牌"重新取单件快照，保证一轮只消耗 1 个容器
            ItemStack single = hand.copyWithCount(1);
            ItemStack result = EssentiaFillHelper.extractContainerContent(storage, energy,
                    menu.getActionSource(), single);
            // result == single（值相等）说明网络没接收 → 立即停止，剩余容器保持原样
            if (result == null || ItemStack.matches(result, single)) {
                break;
            }
            hand.shrink(1);
            if (!result.isEmpty() && !player.getInventory().add(result)) {
                player.drop(result, false);
            }
        }
    }

    /**
     * 取出装填。
     * <ul>
     *   <li>瓶（essence_phial，容量 8）：Ren 版转移规则要求"整 8"，网络不足 8 则拒绝。</li>
     *   <li>罐（warded_jar，容量 64）：<b>允许不满</b> —— 网络有多少装多少（最多 64）。</li>
     *   <li>wholeGroup=false：只装 1 个容器；true：尽可能装满手中整组。</li>
     * </ul>
     */
    private void handleFill(appeng.menu.me.common.MEStorageMenu menu,
            net.minecraft.server.level.ServerPlayer player, ItemStack hand) {
        if (hand.isEmpty() || !ThEEssentiaContainers.isEssentiaContainer(hand) || this.aspectId == null) {
            return;
        }
        var storage = tc_energy.common.container.MenuAccess.getStorage(menu);
        var key = AEssentiaKey.of(this.aspectId);
        if (key == null) {
            return;
        }
        final int capacity = dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi.capacity(hand);
        if (capacity <= 0) {
            return;
        }
        final boolean phial = capacity == EssentiaFillHelper.PHIAL_CAPACITY;
        final int loops = this.wholeGroup ? hand.getCount() : 1;
        for (int i = 0; i < loops; i++) {
            if (hand.isEmpty() || !ThEEssentiaContainers.isEssentiaContainer(hand)) {
                break;
            }
            // 每轮重新读库存（上一轮可能已消耗）
            long inGrid = storage.getAvailableStacks().get(key);
            int fillable = (int) Math.min(inGrid, capacity);
            if (fillable <= 0) {
                break;
            }
            // 瓶必须整 8；罐允许部分
            if (phial && fillable < capacity) {
                break;
            }
            // ⚠ 顺序：先造出成品容器，再抽网络。
            //   造不出来就不抽，避免"源质已抽出但容器没生成"导致源质蒸发。
            ItemStack filled = EssentiaFillHelper.filledContainer(this.aspectId, capacity, fillable);
            if (filled.isEmpty()) {
                break;
            }
            long moved = storage.extract(key, fillable, appeng.api.config.Actionable.MODULATE,
                    menu.getActionSource());
            if (moved < fillable) {
                // 抽取不足（并发/库存变动）：回滚已抽部分，停止
                if (moved > 0) {
                    storage.insert(key, moved, appeng.api.config.Actionable.MODULATE, menu.getActionSource());
                }
                break;
            }
            hand.shrink(1);
            if (!player.getInventory().add(filled)) {
                player.drop(filled, false);
            }
        }
    }
}
