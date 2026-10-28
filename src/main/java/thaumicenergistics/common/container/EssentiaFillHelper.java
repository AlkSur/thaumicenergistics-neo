package thaumicenergistics.common.container;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import thaumcraft.api.aspects.AspectList;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.EssentiaItemContainers;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.common.integration.tc.ThEEssentiaContainers;

/** 服务端真实容器存取；实际转移多少就更新多少，不依赖网络反向回滚。 */
public final class EssentiaFillHelper {
    public static final int PHIAL_CAPACITY = 8;
    public static final int JAR_CAPACITY = 64;

    private EssentiaFillHelper() {
    }

    public static boolean fillContainerFromGrid(MEStorage storage, IEnergySource energySource,
            IActionSource actionSource, Player player, ItemStack source, ResourceLocation aspectId) {
        var aspect = TCReflection.findAspectById(aspectId);
        if (source.isEmpty() || aspect == null || !ThEEssentiaContainers.isEssentiaContainer(source)
                || !EssentiaItemContainers.contents(source).isEmpty()) {
            return false;
        }
        int capacity = ThEEssentiaContainers.isJar(source) ? JAR_CAPACITY : PHIAL_CAPACITY;
        var key = AEssentiaKey.of(aspectId);
        long available = StorageHelper.poweredExtraction(
                energySource, storage, key, capacity, actionSource, Actionable.SIMULATE);
        if (available <= 0 || (capacity == PHIAL_CAPACITY && available < capacity)) {
            return false;
        }
        // Keep the actual source item (including a void jar) and components across network callbacks.
        ItemStack template = source.copyWithCount(1);
        // 固定安瓿不能修改内容，先验证通用安瓿能够保存实际转移量。
        var planned = new AspectList().add(aspect, (int) available);
        if (EssentiaItemContainers.withContents(template, planned) == null) {
            return false;
        }
        long extracted = StorageHelper.poweredExtraction(
                energySource, storage, key, available, actionSource);
        if (extracted <= 0) {
            return false;
        }
        var filled = EssentiaItemContainers.withContents(template,
                new AspectList().add(aspect, (int) extracted));
        if (filled == null) {
            throw new IllegalStateException("Validated essentia container cannot retain extracted contents");
        }
        source.shrink(1);
        give(player, filled);
        return true;
    }

    public static ItemStack depositContainerContent(MEStorage storage, IEnergySource energySource,
            IActionSource actionSource, Player player, ItemStack source) {
        while (!source.isEmpty()) {
            var remaining = EssentiaItemContainers.contents(source);
            if (remaining.isEmpty() || EssentiaItemContainers.withContents(source, remaining) == null) {
                break;
            }
            boolean moved = false;
            for (var aspect : remaining.getAspects()) {
                var id = TCReflection.getAspectId(aspect);
                if (id == null) {
                    continue;
                }
                int offered = remaining.getAmount(aspect);
                long injected = StorageHelper.poweredInsert(
                        energySource, storage, AEssentiaKey.of(id), offered, actionSource);
                if (injected > 0) {
                    remaining.remove(aspect, (int) injected);
                    moved = true;
                }
            }
            if (!moved) {
                break;
            }
            var replacement = EssentiaItemContainers.withContents(source, remaining);
            if (replacement == null) {
                throw new IllegalStateException("Validated essentia container cannot retain remaining contents");
            }
            if (source.getCount() == 1) {
                return replacement;
            }
            // 拆出内容不同的一个容器，其余堆叠保持原组件和数量。
            source.shrink(1);
            give(player, replacement);
            if (!remaining.isEmpty()) {
                break;
            }
        }
        return source;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
