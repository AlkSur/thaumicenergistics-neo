package thaumicenergistics.common.container;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.common.items.JarBlockItem;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.ThEEssentiaContainers;
import thaumicenergistics.common.integration.tc.TCReflection;

/**
 * 源质容器 ↔ ME 网络通用存取逻辑（服务端）。
 * <p>
 * 与具体菜单解耦：调用方（源质终端覆写、或 ME 终端 mixin）只需提供
 * storage / energySource / actionSource / player / 物品栈，本类完成
 * 罐子与安瓿的装罐、存入网络操作。
 * <ul>
 *   <li>取出（装罐）：网络有源质 → 装入手持空罐（有多少装多少，最多 64）/ 空安瓿（固定 8）。</li>
 *   <li>存入：手持/点击的罐子或安瓿 → 源质注入网络；罐子留空罐，安瓿返回空 glass_phial（不消失）。</li>
 * </ul>
 */
public final class EssentiaFillHelper {

    private EssentiaFillHelper() {}

    /** 玻璃安瓿固定容量（TC 原版：8 源质/支）。 */
    public static final int PHIAL_CAPACITY = 8;
    /** 罐子容量（TC 原版：64 源质/罐）。 */
    public static final int JAR_CAPACITY = 64;

    /**
     * 右键网络源质条目装罐：从网络提取源质，装入手持的源质罐子或玻璃安瓿。
     *
     * @return true = 处理成功（装罐/装安瓿），false = 无法处理
     */
    public static boolean fillContainerFromGrid(MEStorage storage, IEnergySource energySource,
                                                IActionSource actionSource, Player player,
                                                ItemStack carried, ResourceLocation aspectId) {
        if (carried.isEmpty() || aspectId == null) {
            return false;
        }
        AEKey key = AEssentiaKey.of(aspectId);
        if (key == null) {
            return false;
        }
        long amountInGrid = storage.getAvailableStacks().get(key);
        if (amountInGrid <= 0) {
            return false;
        }

        // 玻璃安瓿：消耗空安瓿 → 得到装满安瓿（固定 8 源质）
        if (carried.getItem() == BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "glass_phial"))) {
            if (amountInGrid < PHIAL_CAPACITY) {
                return false;
            }
            long extracted = storage.extract(key, PHIAL_CAPACITY, Actionable.MODULATE, actionSource);
            if (extracted < PHIAL_CAPACITY) {
                if (extracted > 0) {
                    storage.insert(key, extracted, Actionable.MODULATE, actionSource);
                }
                return false;
            }
            carried.shrink(1);
            // 得到装满安瓿并放入背包（装好后自动放入背包）
            ItemStack filled = filledPhialStack(aspectId.getPath());
            if (!player.getInventory().add(filled)) {
                player.drop(filled, false);
            }
            return true;
        }

        // 罐子（JarBlockItem）：每次只装 1 个空罐（手堆叠 -1，背包得 1 满罐）。
        // 其余 IEssentiaContainerItem（天域之华等）不参与装罐。
        if (!ThEEssentiaContainers.isJar(carried)) {
            return false;
        }
        IEssentiaContainerItem container = (IEssentiaContainerItem) carried.getItem();
        AspectList existing = container.getAspects(carried);
        if (existing != null && !existing.isEmpty()) {
            return false; // 罐子非空，不能装
        }
        // 部分取出：网络有多少装多少（最多 JAR_CAPACITY 64）；不足 64 也能装，不再整组拒绝
        long toExtract = Math.min(amountInGrid, JAR_CAPACITY);
        long extracted = storage.extract(key, toExtract, Actionable.MODULATE, actionSource);
        if (extracted <= 0) {
            return false;
        }
        Aspect aspect = Aspect.getAspect(aspectId.getPath());
        if (aspect == null) {
            storage.insert(key, extracted, Actionable.MODULATE, actionSource);
            return false;
        }
        // 生成 1 个满罐：必须先 copy（在 shrink 之前），因为 count==1 的堆叠 shrink 后
        // 会变成空堆叠，ItemStack.copy() 对空堆叠直接返回全局 EMPTY 单例，setCount(1)
        // 会污染该常量产生"空气幽灵堆叠"，罐子因此消失。copyWithCount(1) 生成独立副本，
        // setAspects 只影响这一个，不影响原堆叠。
        ItemStack filledJar = carried.copyWithCount(1);
        // 手堆叠 -1
        carried.shrink(1);
        AspectList newAspects = new AspectList();
        newAspects.add(aspect, (int) extracted);
        container.setAspects(filledJar, newAspects);
        if (!player.getInventory().add(filledJar)) {
            player.drop(filledJar, false);
        }
        return true;
    }

    /**
     * 把源质容器物品中的源质注入 ME 原生存储，返回处理后的物品栈。
     * 堆叠中每个容器的源质相同（NBT 共享），按"单个量 × 堆叠数"一次注入；
     * 全部注入成功才消耗容器，否则保留原堆叠，避免源质凭空消失。
     * <p>
     * 罐子抽干后留空罐（可重复使用）；安瓿抽干后返回空 glass_phial（不消失）。
     *
     * @return null = 非源质容器（未处理，调用方应走原逻辑）；
     *         原 stack = 未注入（网络满/能量不足）或部分处理保留；
     *         空罐 = 罐子全部抽干（可重复使用）；
     *         glass_phial 空安瓿 = 安瓿全部抽干（返回空安瓿，不再消耗掉）
     */
    @Nullable
    public static ItemStack extractContainerContent(MEStorage storage, IEnergySource energySource,
                                                    IActionSource actionSource, ItemStack stack) {
        // 仅要素安瓿/要素罐子（天域之华、水晶等其他 IEssentiaContainerItem 一律不处理）
        if (!ThEEssentiaContainers.isEssentiaContainer(stack)) {
            return null;
        }
        if (!(stack.getItem() instanceof IEssentiaContainerItem container)) {
            return stack; // 空玻璃安瓿：无源质可存
        }
        int count = stack.getCount();
        if (count <= 0) {
            return stack;
        }
        AspectList aspects = container.getAspects(stack);
        if (aspects == null || aspects.isEmpty()) {
            return stack;
        }

        // 阶段1：预检（SIMULATE 无副作用）——网络必须能完整容纳全部源质才继续；
        // 任一项放不下则整个堆叠原样保留，避免"部分注入后罐子被消耗、剩余源质凭空消失"。
        Aspect[] aspectArr = aspects.getAspects();
        ResourceLocation[] ids = new ResourceLocation[aspectArr.length];
        long[] totals = new long[aspectArr.length];
        int n = 0;
        for (Aspect aspect : aspectArr) {
            if (aspect == null) {
                continue;
            }
            int perItem = aspects.getAmount(aspect);
            if (perItem <= 0) {
                continue;
            }
            ResourceLocation aspectId = TCReflection.getAspectId(aspect);
            if (aspectId == null) {
                continue;
            }
            long total = (long) perItem * count;
            if (storage.insert(AEssentiaKey.of(aspectId), total,
                    Actionable.SIMULATE, actionSource) < total) {
                return stack; // 网络容量不足：完全不注入，罐子/安瓿原样保留
            }
            ids[n] = aspectId;
            totals[n] = total;
            n++;
        }
        if (n == 0) {
            return stack;
        }

        // 阶段2：执行注入（预检已保证容量；若能量不足导致未完全注入，则回滚本次及此前
        // 已注入的量并保留罐子，保证网络数量与罐子状态一致——不丢源质也不复制源质）
        for (int i = 0; i < n; i++) {
            long injected = StorageHelper.poweredInsert(
                    energySource, storage, AEssentiaKey.of(ids[i]), totals[i], actionSource);
            if (injected < totals[i]) {
                if (injected > 0) {
                    storage.insert(AEssentiaKey.of(ids[i]), injected,
                            Actionable.MODULATE, actionSource);
                }
                for (int j = 0; j < i; j++) {
                    storage.insert(AEssentiaKey.of(ids[j]), totals[j],
                            Actionable.MODULATE, actionSource);
                }
                return stack;
            }
        }

        // 全部注入成功则消耗整个堆叠（罐子留空罐、安瓿返回空 glass_phial）
        if (stack.getItem() instanceof JarBlockItem) {
            return new ItemStack(stack.getItem());
        }
        // 安瓿：返回空安瓿（glass_phial），不直接消失
        ItemStack emptyPhial = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("thaumcraft", "glass_phial")), count);
        return emptyPhial;
    }

    /** 生成装满的安瓿（phial_of_essentia_&lt;tag&gt;），与 TC JarBlock.filledPhialStack 一致。 */
    private static ItemStack filledPhialStack(String aspectTag) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                "thaumcraft", "phial_of_essentia_" + aspectTag);
        net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getOptional(id)
                .orElse(BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("thaumcraft", "glass_phial")));
        return new ItemStack(item);
    }
}
