package thaumicenergistics.common.parts;

import appeng.api.networking.IGrid;
import appeng.me.service.P2PService;
import appeng.parts.p2p.P2PTunnelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.items.TCFunctionalItems;
import thaumicenergistics.common.integration.tc.TCReflection;

/**
 * 法杖 vis 充能逻辑单套实现（共享核心充能循环）：
 * 有线部件 IGridTickable 经 P2P 频率定位（{@link #replenish}，行为零变化）；
 * 无线终端升级卡自动模式遍历网络全部要素中继接口（{@link #replenishAuto}，）。
 */
public final class ArcaneVisReplenish {

    /** 每 tick 每要素最大充能 cV，与原版奥术工作台充能中继（MagicWorkbenchCharger）一致（5 cV/tick = 1 vis/s）。 */
    private static final int MAX_CVIS_PER_TICK = 5;

    private ArcaneVisReplenish() {}

    /**
     * The vis interfaces a device draws from (TEU-04): the one bound by memory card when {@code freq} is set,
     * otherwise every interface on the grid that faces a relay. Only the relay-facing end of each tunnel is
     * listed, so a P2P pair is no longer counted twice (TEA2-12).
     */
    public static java.util.List<VisInterfacePart> sources(@org.jetbrains.annotations.Nullable IGrid grid, short freq) {
        if (grid == null) return java.util.List.of();
        if (freq != 0) {
            P2PService p2p = P2PService.get(grid);
            return p2p != null && p2p.getInput(freq) instanceof VisInterfacePart bound
                    ? java.util.List.of(bound) : java.util.List.of();
        }
        var result = new java.util.ArrayList<VisInterfacePart>();
        for (VisInterfacePart part : grid.getMachines(VisInterfacePart.class)) {
            // A part with no host cannot be judged by its facing; keep it (consumeVis still finds the relay end).
            if (part.getBlockEntity() == null || part.facesRelay()) result.add(part);
        }
        return result;
    }

    /** Whether any of {@link #sources} has vis behind it right now (no extraction, no chunk loading). */
    public static boolean hasAvailableSource(@org.jetbrains.annotations.Nullable IGrid grid, short freq) {
        for (VisInterfacePart source : sources(grid, freq)) {
            if (source.hasAvailableRelay()) return true;
        }
        return false;
    }

    /**
     * Checks a frequency from a memory card: it must name a vis interface. Null when it does (or when there is
     * no grid to check against), otherwise the message to show (TEU-04: any P2P frequency used to be taken).
     */
    @org.jetbrains.annotations.Nullable
    public static net.minecraft.network.chat.Component checkFrequency(@org.jetbrains.annotations.Nullable IGrid grid,
            short freq) {
        if (grid == null || freq == 0) return null;
        P2PService p2p = P2PService.get(grid);
        if (p2p == null || p2p.getInput(freq) instanceof VisInterfacePart) return null;
        return net.minecraft.network.chat.Component.translatable("thaumicenergistics.vis_source.not_vis_interface")
                .withStyle(net.minecraft.ChatFormatting.RED);
    }

    /** Avoid ticking a passive connection when the wand is absent, unsupported, or already full. */
    public static boolean needsCharge(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof TCFunctionalItems.WandCastingItem wand)) return false;
        int capacity = wand.getMaxVis(stack);
        for (Aspect primal : Aspect.getPrimalAspects()) {
            if (wand.getVis(stack, primal) < capacity) return true;
        }
        return false;
    }

    /**
     * 有线路径：按 P2P 频率定位要素中继接口后充能。
     *
     * @return 是否发生了充能（调用方据此标记保存）
     */
    public static boolean replenish(IGrid grid, short visSourceFreq, ItemStack wandStack) {
        if (grid == null || visSourceFreq == 0) {
            return false;
        }
        P2PService p2p = P2PService.get(grid);
        if (p2p == null) {
            return false;
        }
        P2PTunnelPart<?> input = p2p.getInput(visSourceFreq);
        if (!(input instanceof VisInterfacePart visSource)) {
            return false;
        }
        return chargeFrom(visSource, wandStack);
    }

    /**
     * 无线自动路径（vis 连接卡升级卡）：遍历网络内全部要素中继接口逐个尝试充能，
     * 多接口自动切换；无接口/无 vis 时静默。
     *
     * @return 是否发生了充能（调用方据此标记保存）
     */
    public static boolean replenishAuto(IGrid grid, ItemStack wandStack) {
        if (grid == null || wandStack.isEmpty()
                || !(wandStack.getItem() instanceof TCFunctionalItems.WandCastingItem)) {
            return false;
        }
        boolean changed = false;
        for (VisInterfacePart visSource : sources(grid, (short) 0)) {
            if (chargeFrom(visSource, wandStack)) {
                changed = true;
            }
        }
        return changed;
    }

    /** No wand, not a wand, or every primal already at capacity: nothing to charge. */
    public static boolean isFull(ItemStack wandStack) {
        if (wandStack.isEmpty() || !(wandStack.getItem() instanceof TCFunctionalItems.WandCastingItem wand)) {
            return true;
        }
        int maxVis = wand.getMaxVis(wandStack);
        for (Aspect primal : Aspect.getPrimalAspects()) {
            if (wand.getVis(wandStack, primal) < maxVis) return false;
        }
        return true;
    }

    /** 共享核心充能循环：从单个要素中继接口为法杖补 vis；每 tick 申请真实量（与池子每 tick 重置对齐），vis 守恒。 */
    private static boolean chargeFrom(VisInterfacePart visSource, ItemStack wandStack) {
        if (wandStack.isEmpty() || !(wandStack.getItem() instanceof TCFunctionalItems.WandCastingItem wand)) {
            return false;
        }
        boolean changed = false;
        int maxVis = wand.getMaxVis(wandStack);
        for (Aspect primal : Aspect.getPrimalAspects()) {
            int needed = maxVis - wand.getVis(wandStack, primal);
            if (needed <= 0) {
                continue;
            }
            ResourceLocation id = TCReflection.getAspectId(primal);
            if (id == null) {
                continue;
            }
            int got = visSource.consumeVis(id, Math.min(MAX_CVIS_PER_TICK, needed));
            if (got > 0) {
                wand.addRealVis(wandStack, primal, got, true);
                changed = true;
            }
        }
        return changed;
    }
}
