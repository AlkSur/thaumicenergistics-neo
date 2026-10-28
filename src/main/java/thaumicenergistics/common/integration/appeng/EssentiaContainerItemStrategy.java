package thaumicenergistics.common.integration.appeng;

import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import thaumicenergistics.common.integration.tc.EssentiaItemContainers;
import thaumicenergistics.common.integration.tc.TCReflection;
import thaumicenergistics.common.integration.tc.ThEEssentiaContainers;

/** 标准 AE2 填充/抽空和幽灵槽转换共用真实容器上下文。 */
public final class EssentiaContainerItemStrategy
        implements ContainerItemStrategy<AEssentiaKey, EssentiaContainerItemStrategy.Context> {
    public record Context(Player player, @Nullable AbstractContainerMenu menu, int slot) {
        ItemStack stack() {
            return menu != null ? menu.getCarried() : player.getInventory().getItem(slot);
        }

        void replace(ItemStack replacement) {
            var source = stack();
            if (source.getCount() == 1) {
                if (menu != null) menu.setCarried(replacement);
                else player.getInventory().setItem(slot, replacement);
            } else {
                source.shrink(1);
                if (!player.getInventory().add(replacement)) player.drop(replacement, false);
            }
        }
    }

    @Override
    @Nullable
    public GenericStack getContainedStack(ItemStack stack) {
        var recovered = recoveredContent(stack);
        if (recovered != null) return recovered;
        var contents = EssentiaItemContainers.contents(stack);
        for (var aspect : contents.getAspects()) {
            var id = TCReflection.getAspectId(aspect);
            if (id != null && contents.getAmount(aspect) > 0) {
                return new GenericStack(AEssentiaKey.of(id), contents.getAmount(aspect));
            }
        }
        return null;
    }

    @Override
    @Nullable
    public Context findCarriedContext(Player player, AbstractContainerMenu menu) {
        return supported(menu.getCarried())
                ? new Context(player, menu, -1) : null;
    }

    @Override
    @Nullable
    public Context findPlayerSlotContext(Player player, int slot) {
        return slot >= 0 && slot < player.getInventory().getContainerSize()
                && supported(player.getInventory().getItem(slot))
                        ? new Context(player, null, slot) : null;
    }

    @Override
    public long extract(Context context, AEssentiaKey key, long amount, Actionable mode) {
        var recovered = recoveredContent(context.stack());
        if (recovered != null) {
            if (!recovered.what().equals(key) || amount <= 0) return 0;
            long extracted = Math.min(amount, recovered.amount());
            if (mode == Actionable.MODULATE) {
                long left = recovered.amount() - extracted;
                context.replace(left > 0 ? GenericStack.wrapInItemStack(key, left) : ItemStack.EMPTY);
            }
            return extracted;
        }
        var aspect = TCReflection.findAspectById(key.getId());
        if (aspect == null || amount <= 0) return 0;
        var contents = EssentiaItemContainers.contents(context.stack());
        int extracted = (int) Math.min(amount, contents.getAmount(aspect));
        if (extracted <= 0) return 0;
        contents.remove(aspect, extracted);
        var replacement = EssentiaItemContainers.withContents(context.stack(), contents);
        if (replacement == null) return 0;
        if (mode == Actionable.MODULATE) context.replace(replacement);
        return extracted;
    }

    @Override
    public long insert(Context context, AEssentiaKey key, long amount, Actionable mode) {
        if (!ThEEssentiaContainers.isEssentiaContainer(context.stack())) return 0;
        var aspect = TCReflection.findAspectById(key.getId());
        if (aspect == null || amount <= 0) return 0;
        var contents = EssentiaItemContainers.contents(context.stack());
        for (var existing : contents.getAspects()) {
            if (existing != aspect) return 0;
        }
        int capacity = ThEEssentiaContainers.isJar(context.stack()) ? 64 : 8;
        int inserted = (int) Math.min(amount, Math.max(0, capacity - contents.getAmount(aspect)));
        if (inserted <= 0) return 0;
        contents.add(aspect, inserted);
        var replacement = EssentiaItemContainers.withContents(context.stack(), contents);
        if (replacement == null) return 0;
        if (mode == Actionable.MODULATE) context.replace(replacement);
        return inserted;
    }

    @Override
    public void playFillSound(Player player, AEssentiaKey key) {
        player.playSound(net.minecraft.sounds.SoundEvents.BOTTLE_FILL, 1, 1);
    }

    @Override
    public void playEmptySound(Player player, AEssentiaKey key) {
        player.playSound(net.minecraft.sounds.SoundEvents.BOTTLE_EMPTY, 1, 1);
    }

    @Override
    @Nullable
    public GenericStack getExtractableContent(Context context) {
        return getContainedStack(context.stack());
    }

    private static boolean supported(ItemStack stack) {
        return ThEEssentiaContainers.isEssentiaContainer(stack) || recoveredContent(stack) != null;
    }

    @Nullable
    public static GenericStack recoveredContent(ItemStack stack) {
        var wrapped = GenericStack.unwrapItemStack(stack);
        return wrapped != null && wrapped.what() instanceof AEssentiaKey && wrapped.amount() > 0
                ? wrapped : null;
    }
}
