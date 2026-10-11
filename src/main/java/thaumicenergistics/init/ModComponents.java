package thaumicenergistics.init;

import java.util.function.Supplier;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.registries.DeferredRegister;

import thaumicenergistics.ThaumicEnergistics;

/**
 * 物品数据组件注册：无线终端子库存的物品级持久化。
 * 对齐 AE2 {@code AEComponents.CRAFTING_INV} 范式（关菜单/存背包不丢内容）。
 */
public final class ModComponents {
    private ModComponents() {}

    public static final DeferredRegister<DataComponentType<?>> REGISTRY =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ThaumicEnergistics.MODID);

    /** 无线奥术合成终端的法杖槽内容。 */
    public static final Supplier<DataComponentType<ItemContainerContents>> WAND_INV =
            REGISTRY.register("wand_inv", () -> DataComponentType.<ItemContainerContents>builder()
                    .persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC)
                    .build());
}
