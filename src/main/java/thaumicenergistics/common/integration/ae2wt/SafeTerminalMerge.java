package thaumicenergistics.common.integration.ae2wt;

import java.util.Objects;
import com.mojang.datafixers.util.Unit;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
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
        var otherDefinition = WTDefinition.ofOrNull(other);
        if (otherDefinition == null || !(other.getItem() instanceof ItemWT otherItem)) {
            return ItemStack.EMPTY;
        }
        boolean universal = AE2wtlibAPI.isUniversalTerminal(other);
        if (partner.isEmpty() ? !universal : universal || !partner.equals(otherDefinition.terminalName())) {
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
            if (!empty(previous) && !Objects.equals(previous, value)) {
                // 终端偏好以已有通用终端/另一终端为准；物品、奇点和绑定等资源数据仍拒绝冲突。
                if (preference(type)) continue;
                return false;
            }
            copyComponent(target, type, value);
        }
        return true;
    }

    private static boolean preference(DataComponentType<?> type) {
        return type == DataComponents.CUSTOM_NAME || type == DataComponents.LORE
                || type == AE2wtlibComponents.PICKUP_CONFIG || type == AE2wtlibComponents.INSERT_CONFIG
                || type == AE2wtlibComponents.PICKUP_MODE || type == AE2wtlibComponents.INSERT_MODE
                || type == AE2wtlibComponents.RESTOCK || type == AE2wtlibComponents.PICK_BLOCK
                || type == AE2wtlibComponents.CRAFT_IF_MISSING;
    }

    private static boolean empty(Object value) {
        if (value == null) return true;
        if (value instanceof ItemStack stack) return stack.isEmpty();
        if (value instanceof ItemContainerContents contents) return contents.stream().allMatch(ItemStack::isEmpty);
        return false;
    }

    @SuppressWarnings("unchecked")
    private static <T> void copyComponent(ItemStack target, DataComponentType<T> type, Object value) {
        target.set(type, (T) value);
    }
}
