package tc_energy.common.integration.appeng;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;

/**
 * AE2 官方 {@link ContainerItemStrategy} 扩展：让 AE2 原生识别 TC4 源质容器
 * （要素安瓿 essence_phial / 要素罐子 warded_jar）。
 * <p>
 * 注册后 AE2 原生链路自动生效（替代旧 MixinConfigMenuInventory / MixinMEStorageScreen /
 * MixinWCWTScreen 三个 mixin）：
 * <ul>
 *   <li>筛选槽拖拽源质容器 → {@code getContainedStack} 转成 {@link AEssentiaKey}</li>
 *   <li>ME 终端左键源质条目 → FILL_ITEM（{@code ctx.insert} 装罐/装安瓿）</li>
 *   <li>ME 终端右键手持源质容器 → EMPTY_ITEM（{@code ctx.extract} 存入网络）</li>
 * </ul>
 * 实现模板参照 AE2 {@code FluidContainerItemStrategy}（CarriedContext / PlayerInvContext）。
 */
public final class EssentiaContainerItemStrategy
        implements ContainerItemStrategy<AEssentiaKey, EssentiaContainerItemStrategy.Context> {

    private static final EssentiaContainerItemStrategy INSTANCE = new EssentiaContainerItemStrategy();

    private EssentiaContainerItemStrategy() {}

    /** 在 ThEAppliedEnergistics.init() 调用一次。 */
    public static void register() {
        ContainerItemStrategy.register(AEssentiaKeyType.INSTANCE, AEssentiaKey.class, INSTANCE);
    }

    /**
     * 容器物品内容 → {@link GenericStack}(AEssentiaKey, amount)；非容器 / 空容器返回 null。
     * <p>
     * ⚠ <b>绝不要限制 {@code stack.getCount() == 1}</b>。
     * AE2 右键分支（{@code MEStorageScreen.handleGridInventoryEntryMouseClick}）逻辑是：
     * <pre>
     *   var emptyingAction = ContainerItemStrategies.getEmptyingAction(menu.getCarried());
     *   if (emptyingAction != null &amp;&amp; menu.isKeyVisible(emptyingAction.what())) {
     *       menu.handleInteraction(-1, EMPTY_ITEM / EMPTY_ENTIRE_ITEM);
     *       return;                       // ← 正确分支：倒空容器进网络
     *   }
     *   ...
     *   menu.handleInteraction(-1, SPLIT_OR_PLACE_SINGLE);   // ← falling through：把 carried 当【普通物品】存进 ME 网络
     * </pre>
     * 一旦手持<b>一叠</b>（count &gt; 1）时这里返回 null，AE2 的 emptyingAction 就是 null，
     * 于是跳过倒空分支、落到 "把容器当普通物品存进网络" 分支 —— 这正是
     * 「一次右键消耗 2 个安瓿：1 个被本 mod 倒空源质、1 个被 AE2 当物品存进网络」的根因。
     * <p>
     * 参考实现同样不做该限制：官方 Ren 版移植版
     * {@code dev.thaumicenergistics.integration.EssentiaContainerItemStrategy} 与 1.20.1 版。
     * {@code contents()} 读的是物品自身的 ASPECTS 组件（每件含量），与堆叠数量无关。
     */
    @Override
    @Nullable
    public GenericStack getContainedStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        if (EssentiaContainerApi.capacity(stack) <= 0) {
            return null;
        }
        var contents = EssentiaContainerApi.contents(stack);
        if (contents.amounts().isEmpty()) {
            return null;
        }
        // 罐子/安瓿只装单一种源质
        var entry = contents.amounts().entrySet().iterator().next();
        AspectId id = entry.getKey();
        return new GenericStack(AEssentiaKey.of(id.value()), entry.getValue());
    }

    @Override
    @Nullable
    public Context findCarriedContext(Player player, AbstractContainerMenu menu) {
        if (isCandidate(menu.getCarried())) {
            return new CarriedContext(player, menu);
        }
        return null;
    }

    @Override
    @Nullable
    public Context findPlayerSlotContext(Player player, int slot) {
        if (isCandidate(player.getInventory().getItem(slot))) {
            return new PlayerInvContext(player, slot);
        }
        return null;
    }

    private static boolean isCandidate(ItemStack stack) {
        return !stack.isEmpty() && EssentiaContainerApi.capacity(stack) > 0;
    }

    /**
     * 从容器抽出源质（<b>容器 → 网络</b>方向）。
     * <p>
     * ⚠ 这里必须返回 0（不做任何抽取）：该方向由本 mod 自有链路独占
     * （{@code EssentiaContainerScreenHandler} + {@code EssentiaContainerInteractC2SPacket}），
     * 以给出"瓶整 8 / 罐整取"的确定语义。
     * <p>
     * 原因：AE2 原生 {@code EMPTY_ITEM} 的单次量是 {@code what.getAmountPerUnit()}
     * （本 mod 为显示正确固定为 1），而 Ren 版 {@code EssentiaContainerApi} 规定
     * {@code capacity == 8} 时必须恰好移动 8 —— 两者语义冲突。若在此真正抽取，
     * 一旦自有链路与原生链路同时生效，就会叠成"一次右键消耗 2 个容器"。
     * <p>
     * ⚠ 但<b>不能</b>改成返回 null / 让 {@link #getContainedStack} 返回 null ——
     * 那会让 AE2 落到"把容器当普通物品存进网络"分支（见 {@link #getContainedStack}）。
     * 返回 0 使 AE2 走正确的倒空分支后立即 {@code break}，不做任何破坏性动作。
     */
    @Override
    public long extract(Context context, AEssentiaKey what, long amount, Actionable mode) {
        return 0;
    }

    /** 向容器注入源质（从 ME 网络装罐）。 */
    @Override
    public long insert(Context context, AEssentiaKey what, long amount, Actionable mode) {
        ItemStack stack = context.getStack();
        if (stack.isEmpty() || amount <= 0) {
            return 0;
        }
        AspectId id = dev.tc4port.thaumcraft.api.aspect.AspectId.of(what.getId());
        int moved = EssentiaContainerApi.insert(
                context.getLevel(), stack, id,
                (int) Math.min(amount, Integer.MAX_VALUE),
                mode == Actionable.MODULATE ? EssentiaTransferMode.EXECUTE : EssentiaTransferMode.SIMULATE);
        return moved;
    }

    @Override
    public void playFillSound(Player player, AEssentiaKey what) {
        // Ren 版容器装填无独立音效钩子，静默
    }

    @Override
    public void playEmptySound(Player player, AEssentiaKey what) {
        // 静默
    }

    @Override
    @Nullable
    public GenericStack getExtractableContent(Context context) {
        return getContainedStack(context.getStack());
    }

    /** 上下文：定位被操作的 ItemStack（手持 / 背包槽位），并提供 Level。 */
    public interface Context {
        ItemStack getStack();

        net.minecraft.world.level.Level getLevel();
    }

    private record CarriedContext(Player player, AbstractContainerMenu menu) implements Context {
        @Override
        public ItemStack getStack() {
            return menu.getCarried();
        }

        @Override
        public net.minecraft.world.level.Level getLevel() {
            return player.level();
        }
    }

    private record PlayerInvContext(Player player, int slot) implements Context {
        @Override
        public ItemStack getStack() {
            return player.getInventory().getItem(slot);
        }

        @Override
        public net.minecraft.world.level.Level getLevel() {
            return player.level();
        }
    }
}
