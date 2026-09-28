package tc_energy.common.parts;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import tc_energy.api.storage.AspectStack;
import tc_energy.common.integration.appeng.AEssentiaKey;
import tc_energy.common.integration.tc.TCReflection;

/**
 * 把相邻源质容器（要素罐子 / 奥术蒸馏器 / 源质管道等）包装成 {@link MEStorage}，
 * 供源质存储总线通过 {@code IStorageProvider.mountInventories} 挂载到网络。
 * <p>
 * 容器内容始终实时读取（不缓存），因此终端、子网、输入/输出总线看到的都是当前存量。
 * Ren 版对接面：相邻 {@link EssentiaTransport} 的方向插入 API（canInputFrom + addEssentia），
 * SIMULATE 如实结算（Ren 版 addEssentia 支持 SIMULATE 返回可接受量）。
 */
public class EssentiaContainerStorage implements MEStorage {

    /** 每次实时解析相邻方块实体（总线朝向方块可被替换/移除）。 */
    private final Supplier<BlockEntity> target;
    private final Component description;

    public EssentiaContainerStorage(Supplier<BlockEntity> target, Component description) {
        this.target = target;
        this.description = description;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(what instanceof AEssentiaKey key)) {
            return 0;
        }
        BlockEntity be = target.get();
        if (be == null || be.getLevel() == null) {
            return 0;
        }
        AspectId aspectId = TCReflection.toAspectId(key.getId());
        if (aspectId == null) {
            return 0;
        }
        if (!(be instanceof EssentiaTransport transport)) {
            return 0;
        }
        // Ren 版：对每个可输入面模拟/执行插入；SIMULATE 如实返回，输入总线预检不再乐观误判
        int request = (int) Math.min(amount, Integer.MAX_VALUE);
        long total = 0;
        for (var face : net.minecraft.core.Direction.values()) {
            if (total >= request) {
                break;
            }
            if (!transport.canInputFrom(face)) {
                continue;
            }
            int moved = dev.tc4port.thaumcraft.api.essentia.EssentiaApi.add(
                    be.getLevel(), transport, aspectId, request - (int) total, face,
                    mode == Actionable.MODULATE ? EssentiaTransferMode.EXECUTE : EssentiaTransferMode.SIMULATE);
            if (moved > 0) {
                total += moved;
            }
        }
        return total;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(what instanceof AEssentiaKey key)) {
            return 0;
        }
        BlockEntity be = target.get();
        if (be == null) {
            return 0;
        }
        int request = (int) Math.min(amount, Integer.MAX_VALUE);
        if (mode == Actionable.SIMULATE) {
            // 只统计、不动容器
            for (AspectStack stack : TCReflection.listEssentia(be)) {
                if (stack.aspectId().equals(key.getId())) {
                    return Math.min(stack.amount(), request);
                }
            }
            return 0;
        }
        AspectStack extracted = TCReflection.extractEssentia(be, key.getId(), request);
        return extracted == null ? 0 : extracted.amount();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        BlockEntity be = target.get();
        if (be == null) {
            return;
        }
        List<AspectStack> stacks = TCReflection.listEssentia(be);
        for (AspectStack stack : stacks) {
            if (stack.amount() <= 0) continue;
            AEssentiaKey key = AEssentiaKey.of(stack.aspectId());
            if (key != null) {
                out.add(key, stack.amount());
            }
        }
    }

    @Override
    public Component getDescription() {
        return description;
    }
}
