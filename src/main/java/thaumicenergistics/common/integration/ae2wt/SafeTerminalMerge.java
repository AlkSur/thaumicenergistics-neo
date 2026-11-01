package thaumicenergistics.common.integration.ae2wt;

import java.util.Objects;
import com.mojang.datafixers.util.Unit;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import de.mari_023.ae2wtlib.api.AE2wtlibAPI;
import de.mari_023.ae2wtlib.api.AE2wtlibComponents;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.ItemWUT;

/** 在副本中完整预演合并；组件冲突或卡片不能全部安装时拒绝，绝不丢弃尾量。 */
public final class SafeTerminalMerge {
    private SafeTerminalMerge() {
    }

    public static ItemStack merge(ItemStack arcane, ItemStack other, String partner) {
        var definition = WTDefinition.ofOrNull(arcane);
        if (definition == null || !AE2WTCompat.ARCANE_TERMINAL_NAME.equals(definition.terminalName())
                || !(arcane.getItem() instanceof ItemWT arcaneItem)) {
            return ItemStack.EMPTY;
        }
        if (!(other.getItem() instanceof ItemWT otherItem)) {
            return ItemStack.EMPTY;
        }
        boolean universal = AE2wtlibAPI.isUniversalTerminal(other);
        // A blank WUT has no current definition, but is a valid destination.
        var otherDefinition = universal ? null : WTDefinition.ofOrNull(other);
        if (partner.isEmpty() ? !universal : universal || otherDefinition == null
                || !partner.equals(otherDefinition.terminalName())) {
            return ItemStack.EMPTY;
        }
        if (universal && other.has(definition.componentType())) {
            return ItemStack.EMPTY;
        }
        ItemStack result = universal ? other.copyWithCount(1) : new ItemStack(AE2wtlibAPI.getWUT());
        if (!(result.getItem() instanceof ItemWUT output)) {
            return ItemStack.EMPTY;
        }
        if (!universal && !copyComponents(other, result)) {
            return ItemStack.EMPTY;
        }
        if (!copyComponents(arcane, result)) {
            return ItemStack.EMPTY;
        }
        result.set(definition.componentType(), Unit.INSTANCE);
        if (!universal) {
            result.set(otherDefinition.componentType(), Unit.INSTANCE);
        }
        if (!result.has(AE2wtlibComponents.CURRENT_TERMINAL)) {
            result.set(AE2wtlibComponents.CURRENT_TERMINAL, universal ? definition : otherDefinition);
        }
        result.remove(AEComponents.UPGRADES);
        result.remove(AEComponents.STORED_ENERGY);
        result.remove(AEComponents.ENERGY_CAPACITY);
        var upgrades = output.getUpgrades(result);
        for (ItemStack input : new ItemStack[]{other.copy(), arcane.copy()}) {
            var item = (ItemWT) input.getItem();
            for (var card : item.getUpgrades(input)) {
                if (!card.isEmpty() && !upgrades.addItems(card.copy()).isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        output.onUpgradesChanged(result, upgrades);
        double power = arcaneItem.getAECurrentPower(arcane) + otherItem.getAECurrentPower(other);
        if (!Double.isFinite(power) || power < 0) return ItemStack.EMPTY;
        output.injectAEPower(result, power, Actionable.MODULATE);
        if (output.getAECurrentPower(result) + 1.0e-6 < power) return ItemStack.EMPTY;
        return result;
    }

    private static boolean copyComponents(ItemStack source, ItemStack target) {
        for (var entry : source.getComponentsPatch().entrySet()) {
            var type = entry.getKey();
            if (type == AEComponents.UPGRADES || type == AEComponents.STORED_ENERGY
                    || type == AEComponents.ENERGY_CAPACITY || type == AE2wtlibComponents.CURRENT_TERMINAL) {
                continue;
            }
            // 空组件不能覆盖通用终端已有的法杖、材料或奇点。
            var value = entry.getValue().orElse(null);
            if (empty(value)) {
                continue;
            }
            var previous = target.get(type);
            // Equal inventories still contain two separate sets of resources.
            // Reject instead of retaining only one copy of their contents.
            if (!empty(previous) && (value instanceof ItemContainerContents || value instanceof ItemStack
                    || type == AE2wtlibComponents.PATTERN_ENCODING_LOGIC)) {
                return false;
            }
            if (!empty(previous) && !Objects.equals(previous, value)) {
                return false;
            }
            copyComponent(target, type, value);
        }
        return true;
    }

    private static boolean empty(Object value) {
        if (value == null) return true;
        if (value instanceof ItemStack stack) return stack.isEmpty();
        if (value instanceof ItemContainerContents contents) return contents.stream().allMatch(ItemStack::isEmpty);
        if (value instanceof net.minecraft.nbt.CompoundTag tag) return tag.isEmpty();
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T> void copyComponent(ItemStack target, DataComponentType<T> type, Object value) {
        target.set(type, (T) value);
    }
}
