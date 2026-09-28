package tc_energy.common.container;

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

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.item.EssencePhialItem;
import tc_energy.common.integration.appeng.AEssentiaKey;
import tc_energy.common.integration.tc.ThEEssentiaContainers;

/**
 * 源质容器 → ME 网络通用存取逻辑（服务端，Ren 版 TC4 容器 API）。
 * <p>
 * 与具体菜单解耦：调用方只需提供 storage / energySource / actionSource / player / 物品栈。
 * <ul>
 *   <li>取出（装罐）：网络有源质 → 装入手持的空罐（最多 64）或空安瓿（固定 8）。</li>
 *   <li>存入：手持/点击的罐子或安瓿 → 源质注入网络；罐子留空罐，安瓿回到空安瓿。</li>
 * </ul>
 * Ren 版要点：空安瓿与装满安瓿是同一注册物品（essence_phial），内容用 ASPECTS 组件区分；
 * {@link EssencePhialItem#forAspect} 生成装满的安瓿。
 */
public final class EssentiaFillHelper {

    private EssentiaFillHelper() {}

    /** 安瓿固定容量（Ren 版 essence_phial）。 */
    public static final int PHIAL_CAPACITY = 8;
    /** 罐子容量（Ren 版 warded_jar）。 */
    public static final int JAR_CAPACITY = 64;

    /**
     * 右键网络源质条目装罐：从网络提取源质，装入手持的源质罐子或空玻璃安瓿。
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
        int capacity = EssentiaContainerApi.capacity(carried);
        if (capacity <= 0) {
            return false;
        }
        long amountInGrid = storage.getAvailableStacks().get(key);
        if (amountInGrid <= 0) {
            return false;
        }

        // 安瓿：Ren 版一次必须整装 8（EssentiaContainerApi 转移规则：capacity==8 时 moved 必须 ==8）
        if (capacity == PHIAL_CAPACITY) {
            if (amountInGrid < PHIAL_CAPACITY || EssentiaContainerApi.contents(carried).amounts().isEmpty() == false) {
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
            ItemStack filled = filledPhialStack(aspectId);
            if (!player.getInventory().add(filled)) {
                player.drop(filled, false);
            }
            return true;
        }

        // 罐子（warded_jar，容量 64）：每次只装 1 个空罐；非空罐不能装。
        if (!ThEEssentiaContainers.isJar(carried)) {
            return false;
        }
        if (!EssentiaContainerApi.contents(carried).amounts().isEmpty()) {
            return false;
        }
        // 部分取出：网络有多少装多少（最大 64）；不足 64也能装
        long toExtract = Math.min(amountInGrid, JAR_CAPACITY);
        long extracted = storage.extract(key, toExtract, Actionable.MODULATE, actionSource);
        if (extracted <= 0) {
            return false;
        }
        AspectId aspect = tc_energy.common.integration.tc.TCReflection.toAspectId(aspectId);
        if (aspect == null) {
            storage.insert(key, extracted, Actionable.MODULATE, actionSource);
            return false;
        }
        // 生盛 1 个满罐：必须先 copy（在 shrink 之前），罐子因 shrink 消失后 copy 会拿到全局 EMPTY
        ItemStack filledJar = carried.copyWithCount(1);
        carried.shrink(1);
        int moved = EssentiaContainerApi.insert(player.level(), filledJar, aspect,
                (int) extracted, EssentiaTransferMode.EXECUTE);
        if (moved <= 0) {
            // 理论不可达（空罐+服务端线程）；回滚网络
            storage.insert(key, extracted, Actionable.MODULATE, actionSource);
            return false;
        }
        if (!player.getInventory().add(filledJar)) {
            player.drop(filledJar, false);
        }
        return true;
    }

    /**
     * 把源质容器物品中的源质注入 ME 原生存储，返回处理后的物品栈。
     * 堆叠中每个容器的源质相同，按（单含量 × 堆叠数）一次注入；全部注入成功才消耗容器。
     * <p>
     * 罐子抽干后留空罐；安瓿抽干后回到空安瓿（essence_phial，不消失）。
     *
     * @return null = 非源质容器（未处理）；原 stack = 未注入（网络满/能量不足）；
     *         空罐/空安瓿 = 全部抽干
     */
    @Nullable
    public static ItemStack extractContainerContent(MEStorage storage, IEnergySource energySource,
                                                    IActionSource actionSource, ItemStack stack) {
        if (!ThEEssentiaContainers.isEssentiaContainer(stack)) {
            return null;
        }
        int count = stack.getCount();
        if (count <= 0) {
            return stack;
        }
        var contents = EssentiaContainerApi.contents(stack);
        if (contents.amounts().isEmpty()) {
            return stack; // 空容器：无源质可存
        }

        // Ren 版容器只装一种源质
        var entry = contents.amounts().entrySet().iterator().next();
        AspectId aspect = entry.getKey();
        int perItem = entry.getValue();
        ResourceLocation aspectId = aspect.value();
        long total = (long) perItem * count;

        // 阶段1：预检 —— 网络必须能完整容纳全部源质才继续
        if (storage.insert(AEssentiaKey.of(aspectId), total, Actionable.SIMULATE, actionSource) < total) {
            return stack;
        }

        // 阶段2：执行注入（预检已保证容量；能量不足导致部分注入则【抽回】本次已注入的量）
        // ⚠ 旧代码这里写成 storage.insert(...)，会把已注入的部分再加一遍 —— 网络凭空多出一份源质。
        //   回滚语义必须用 extract：网络多出的 injected 要原样抽回，容器保持不动。
        long injected = StorageHelper.poweredInsert(energySource, storage,
                AEssentiaKey.of(aspectId), total, actionSource);
        if (injected < total) {
            if (injected > 0) {
                storage.extract(AEssentiaKey.of(aspectId), injected, Actionable.MODULATE, actionSource);
            }
            return stack;
        }

        // 全部注入成功 → 抽干每个容器（copyWithCount(1) 逐个清空组件，避免堆叠共享组件污染）
        ItemStack drained;
        if (count == 1) {
            drained = stack.copyWithCount(1);
        } else {
            drained = stack.copyWithCount(count);
        }
        drained.remove(dev.tc4port.thaumcraft.state.TCItemState.ASPECTS);
        return drained;
    }

    /** 生成装满的安瓿（Ren 版：同一 essence_phial 物品 + ASPECTS 组件 8 源质）。 */
    private static ItemStack filledPhialStack(ResourceLocation aspectId) {
        AspectId aspect = tc_energy.common.integration.tc.TCReflection.toAspectId(aspectId);
        net.minecraft.world.item.Item phial = dev.tc4port.thaumcraft.api.ThaumcraftContent.item("essence_phial");
        if (aspect == null) {
            return new ItemStack(phial);
        }
        return EssencePhialItem.forAspect(phial, aspect);
    }

    /**
     * 生成装入了指定数量源质的容器（网络取出的统一出口）。
     * <ul>
     *   <li>瓶（essence_phial，capacity=8）：Ren 版转移规则要求"整 8"，因此 amount 必须为 8。</li>
     *   <li>罐（warded_jar，capacity=64）：<b>允许不满</b>（网络有多少装多少）。</li>
     * </ul>
     *
     * @param capacity 容器容量（用 {@link EssentiaContainerApi#capacity(ItemStack)} 取，8=瓶 / 64=罐）
     * @param amount   实际要装入的源质数量（1..capacity；瓶必须等于 8）
     * @return 装好的容器；无法生成时返回 {@link ItemStack#EMPTY}
     */
    public static ItemStack filledContainer(ResourceLocation aspectId, int capacity, int amount) {
        AspectId aspect = tc_energy.common.integration.tc.TCReflection.toAspectId(aspectId);
        if (aspect == null || capacity <= 0 || amount <= 0 || amount > capacity) {
            return ItemStack.EMPTY;
        }
        // 瓶：Ren 版 EssentiaContainerApi 规定 capacity==8 时必须恰好 8，不接受部分
        if (capacity == PHIAL_CAPACITY) {
            return amount == PHIAL_CAPACITY ? filledPhialStack(aspectId) : ItemStack.EMPTY;
        }
        // 罐：允许部分（1..64）
        net.minecraft.world.item.Item jarItem = dev.tc4port.thaumcraft.api.ThaumcraftContent.item("warded_jar");
        ItemStack jar = new ItemStack(jarItem);
        // EssentiaContainerApi.transfer 要求 ServerLevel + 主线程（Objects.requireNonNull(level)），传入当前服务端世界
        var level = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() != null
                ? net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().overworld() : null;
        if (level == null) {
            return ItemStack.EMPTY;
        }
        int moved = dev.tc4port.thaumcraft.api.essentia.EssentiaContainerApi.insert(
                level, jar, aspect, amount, dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode.EXECUTE);
        return moved > 0 ? jar : ItemStack.EMPTY;
    }
}
