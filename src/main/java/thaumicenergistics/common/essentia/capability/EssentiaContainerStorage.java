package thaumicenergistics.common.essentia.capability;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.essentia.AspectContainer;
import thaumcraft.common.essentia.AspectTank;
import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.TCReflection;

/**
 * 把相邻源质容器（要素罐子 / 奥术蒸馏器 / 荒古炼金炉 / 源质管道）包装成 {@link MEStorage}，
 * 供源质存储总线通过 {@code IStorageProvider.mountInventories} 挂载到网络。
 * <p>
 * 源质「挂载存储」能力的实现载体：构造只依赖一个取相邻方块实体的 {@link Supplier}，
 * 因此可被既有存储总线与后续升级卡挂载宿主复用，不绑定到具体总线实现。
 * <p>
 * 容器内容始终实时读取（不缓存），因此终端、子网、输入/输出总线看到的都是当前存量。
 */
public class EssentiaContainerStorage implements MEStorage {

    /** 每次实时解析相邻方块实体（总线朝向方块可被替换/移除）。 */
    private final Supplier<BlockEntity> target;
    private final Component description;
    private final Supplier<net.minecraft.core.Direction> accessSide;

    public EssentiaContainerStorage(Supplier<BlockEntity> target, Component description) {
        this(target, description, () -> null);
    }

    public EssentiaContainerStorage(Supplier<BlockEntity> target, Component description,
            Supplier<net.minecraft.core.Direction> accessSide) {
        this.target = target;
        this.description = description;
        this.accessSide = accessSide;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !(what instanceof AEssentiaKey key)) {
            return 0;
        }
        BlockEntity be = target.get();
        if (be == null) {
            return 0;
        }
        Aspect aspect = TCReflection.findAspectById(key.getId());
        if (aspect == null) {
            return 0;
        }
        var side = thaumicenergistics.common.integration.tc.EssentiaEndpoints.accessSide(be, accessSide.get());
        var transport = thaumicenergistics.common.integration.tc.EssentiaEndpoints.transport(be, side);
        if (transport != null && side != null
                && (!transport.isConnectable(side) || !transport.canInputFrom(side))) return 0;
        if (be instanceof thaumcraft.api.aspects.IAspectContainer container
                && !container.doesContainerAccept(aspect)) return 0;

        // TC 内部 AspectContainer（要素罐子 / 奥术蒸馏器 等）：单方面储罐，容量可精确读取，
        // 因此 SIMULATE 能如实回答"还能装多少"。输入总线的预检依赖这一点：
        // 旧实现一律乐观返回 amount，导致容器已满时仍判定"网络装得下"，把源质从炉子里抽走。
        if (be instanceof AspectContainer aspectContainer) {
            AspectTank tank = aspectContainer.aspectTank();
            if (tank == null) {
                return 0;
            }
            int free;
            if (tank.isEmpty()) {
                free = tank.capacity();
            } else if (tank.aspect() == aspect) {
                free = tank.capacity() - tank.amount();
            } else {
                free = 0; // 单方面储罐里装的是别的方面
            }
            if (free <= 0) {
                return 0;
            }
            int request = (int) Math.min(amount, free);
            if (mode == Actionable.SIMULATE) {
                return request;
            }
            int accepted = thaumicenergistics.common.integration.tc.EssentiaEndpoints.insert(be, key.getId(), request, side);
            if (accepted > 0) {
                be.setChanged();
            }
            return accepted;
        }

        // 其他容器没有"模拟插入"API：SIMULATE 乐观返回，实际插入按容器返回值结算
        // （输入总线侧有缓冲兜底，模拟与实插不一致时源质也不会丢）
        if (mode == Actionable.SIMULATE) {
            return amount;
        }
        return thaumicenergistics.common.integration.tc.EssentiaEndpoints.insert(
                be, key.getId(), (int) Math.min(amount, Integer.MAX_VALUE), side);
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
            for (AspectStack stack : thaumicenergistics.common.integration.tc.EssentiaEndpoints.contents(be, accessSide.get())) {
                if (stack.aspectId().equals(key.getId())) {
                    return Math.min(stack.amount(), request);
                }
            }
            return 0;
        }
        AspectStack extracted = thaumicenergistics.common.integration.tc.EssentiaEndpoints.extract(
                be, key.getId(), request, accessSide.get());
        return extracted == null ? 0 : extracted.amount();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        BlockEntity be = target.get();
        if (be == null) {
            return;
        }
        List<AspectStack> stacks = thaumicenergistics.common.integration.tc.EssentiaEndpoints.contents(be, accessSide.get());
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
