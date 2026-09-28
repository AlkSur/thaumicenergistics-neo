package tc_energy.common.integration.tc;

import java.util.List;
import java.util.ArrayList;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectContainerView;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSource;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import tc_energy.api.storage.AspectStack;

/**
 * ThE 与 Ren 版 TC4（dev.tc4port.thaumcraft.*）源质对接的工具层。
 * <p>
 * 对接面（旧 IAspectContainer/IEssentiaTransport/EssentiaTransport 已全部移除）：
 * <ul>
 *   <li>{@link EssentiaTransport}：方向性端点（源质管、蒸馏器、罐子等）—— take/add 走
 *       {@link EssentiaApi#take}/{@link EssentiaApi#add}（带校验，非服务端 EXECUTE 被拒）。</li>
 *   <li>{@link EssentiaSource}：直排源（罐子、水库、魔镜）—— 全有全无抽取。</li>
 *   <li>{@link AspectContainerView}：只读内容（釜、注魔矩阵等展示用途）。</li>
 * </ul>
 * 内部货币沿用 {@link ResourceLocation}（AEssentiaKey 同款），在边界处转 {@link AspectId}。
 */
public final class TCReflection {

    private TCReflection() {}

    /** ResourceLocation → AspectId（Ren 版规范型身份）。非法/未注册时返回 null。 */
    @Nullable
    public static AspectId toAspectId(@Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        AspectId aspectId;
        try {
            aspectId = AspectId.of(id);
        } catch (Exception e) {
            return null;
        }
        return AspectApi.contains(aspectId) ? aspectId : null;
    }

    /** 列出容器全部源质（非破坏性）。依次尝试 EssentiaTransport / EssentiaSource / AspectContainerView。 */
    public static List<AspectStack> listEssentia(@Nullable BlockEntity be) {
        List<AspectStack> result = new ArrayList<>();
        if (be == null) {
            return result;
        }
        if (be instanceof AspectContainerView view) {
            view.visibleAspects().amounts().forEach((id, amount) -> {
                if (amount > 0) {
                    result.add(new AspectStack(id.value(), amount));
                }
            });
            if (!result.isEmpty()) {
                return result;
            }
        }
        if (be instanceof EssentiaTransport transport) {
            for (Direction face : Direction.values()) {
                if (!transport.canOutputTo(face)) {
                    continue;
                }
                AspectId type = transport.essentiaType(face);
                int amount = transport.essentiaAmount(face);
                if (type != null && amount > 0) {
                    result.add(new AspectStack(type.value(), amount));
                }
            }
        }
        return result;
    }

    /** 非破坏性预览第一个可抽取的源质（导入总线预检）。 */
    @Nullable
    public static AspectStack peekEssentia(@Nullable BlockEntity be, int maxAmount) {
        for (AspectStack stack : listEssentia(be)) {
            if (stack.isEmpty()) {
                continue;
            }
            long amt = Math.min(stack.amount(), Math.max(0, maxAmount));
            if (amt > 0) {
                return new AspectStack(stack.aspectId(), amt);
            }
        }
        return null;
    }

    /**
     * 从容器抽取源质。only 非空时只抽指定方面。
     * <p>优先 {@link EssentiaTransport#canOutputTo} 方向抽取（走 EssentiaApi.take 校验），
     * 回退 {@link EssentiaSource} 全有全无抽取。仅在逻辑服务端生效。</p>
     */
    @Nullable
    public static AspectStack extractEssentia(@Nullable BlockEntity be, @Nullable ResourceLocation only, int maxAmount) {
        if (be == null || be.getLevel() == null || maxAmount <= 0) {
            return null;
        }
        AspectId onlyId = only == null ? null : toAspectId(only);
        if (only != null && onlyId == null) {
            return null; // 指定方面在 Ren 版注册表中不存在
        }
        var level = be.getLevel();

        // ===== 路径1：TC 管道端点（罐子/蒸馏器/离心机等）=====
        if (be instanceof EssentiaTransport transport) {
            for (Direction face : Direction.values()) {
                if (!transport.canOutputTo(face)) {
                    continue;
                }
                AspectId type = transport.essentiaType(face);
                if (type == null || (onlyId != null && !onlyId.equals(type))) {
                    continue;
                }
                int available = transport.essentiaAmount(face);
                if (available <= 0) {
                    continue;
                }
                int toExtract = (int) Math.min(available, maxAmount);
                int taken = EssentiaApi.take(level, transport, type, toExtract, face, EssentiaTransferMode.EXECUTE);
                if (taken > 0) {
                    return new AspectStack(type.value(), taken);
                }
            }
        }

        // ===== 路径2：直排源（全有全无；Ren 版罐子/水库/魔镜）=====
        if (be instanceof EssentiaSource source) {
            if (onlyId != null) {
                int taken = source.extractEssentia(onlyId, maxAmount, EssentiaTransferMode.EXECUTE);
                if (taken > 0) {
                    return new AspectStack(onlyId.value(), taken);
                }
            } else {
                for (AspectStack candidate : listEssentia(be)) {
                    AspectId id = toAspectId(candidate.aspectId());
                    if (id == null) {
                        continue;
                    }
                    int toExtract = (int) Math.min(candidate.amount(), maxAmount);
                    int taken = source.extractEssentia(id, toExtract, EssentiaTransferMode.EXECUTE);
                    if (taken > 0) {
                        return new AspectStack(id.value(), taken);
                    }
                }
            }
        }
        return null;
    }

    /** 兼容旧签名：不限方面。 */
    @Nullable
    public static AspectStack extractEssentia(@Nullable BlockEntity be, int maxAmount) {
        return extractEssentia(be, null, maxAmount);
    }

    /** 归还源质（网络已满时回滚）。 */
    public static void returnEssentia(@Nullable BlockEntity be, AspectStack stack) {
        insertEssentia(be, stack);
    }

    /**
     * 向容器插入源质，返回实际插入量。走 {@link EssentiaApi#add}（服务端校验）。
     */
    public static int insertEssentia(@Nullable BlockEntity be, AspectStack stack) {
        if (be == null || stack == null || stack.isEmpty() || be.getLevel() == null) {
            return 0;
        }
        if (!(be instanceof EssentiaTransport transport)) {
            return 0;
        }
        AspectId id = toAspectId(stack.aspectId());
        if (id == null) {
            return 0;
        }
        var level = be.getLevel();
        int toInsert = (int) Math.min(stack.amount(), Integer.MAX_VALUE);
        int added = 0;
        for (Direction face : Direction.values()) {
            if (added >= toInsert) {
                break;
            }
            if (!transport.canInputFrom(face)) {
                continue;
            }
            int moved = EssentiaApi.add(level, transport, id, toInsert - added, face, EssentiaTransferMode.EXECUTE);
            if (moved > 0) {
                added += moved;
            }
        }
        return added;
    }
}
