package thaumicenergistics.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThaumicEnergistics;

import java.util.function.Supplier;

public final class ModCreativeTab {
    private ModCreativeTab() {}
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ThaumicEnergistics.MODID);
    public static final Supplier<CreativeModeTab> TAB = REGISTRY.register("tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.thaumicenergistics"))
            .icon(() -> new ItemStack(ModItems.ESSENTIA_CELL_64K.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.ESSENTIA_CELL_1K.get()); output.accept(ModItems.ESSENTIA_CELL_4K.get());
                output.accept(ModItems.ESSENTIA_CELL_16K.get()); output.accept(ModItems.ESSENTIA_CELL_64K.get());
                output.accept(ModItems.ESSENTIA_CELL_CREATIVE.get());
                output.accept(ModItems.STORAGE_COMPONENT_1K.get()); output.accept(ModItems.STORAGE_COMPONENT_4K.get());
                output.accept(ModItems.STORAGE_COMPONENT_16K.get()); output.accept(ModItems.STORAGE_COMPONENT_64K.get());
                output.accept(ModItems.STORAGE_CASING.get());
                output.accept(ModItems.COALESCENCE_CORE.get()); output.accept(ModItems.DIFFUSION_CORE.get());
                output.accept(ModItems.KNOWLEDGE_CORE.get());
                output.accept(ModItems.FOCUS_AEWRENCH.get()); output.accept(ModItems.GOLEM_WIFI_BACKPACK.get());
                output.accept(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
                output.accept(ModItems.VIS_CONNECTOR_CARD.get());
                output.accept(ModItems.ARCANE_CRAFTING_TERMINAL_ITEM.get()); output.accept(ModItems.ESSENTIA_LEVEL_EMITTER.get());
                output.accept(ModItems.VIS_INTERFACE.get());
                output.accept(ModItems.ESSENTIA_PROVIDER_ITEM.get()); output.accept(ModItems.INFUSION_PROVIDER_ITEM.get());
                output.accept(ModItems.ARCANE_ASSEMBLER_ITEM.get()); output.accept(ModItems.ESSENTIA_VIBRATION_CHAMBER_ITEM.get());
                output.accept(ModItems.KNOWLEDGE_INSCRIBER_ITEM.get()); output.accept(ModItems.ESSENTIA_CELL_WORKBENCH_ITEM.get());
                output.accept(ModItems.DISTILLATION_ENCODER_ITEM.get());
                output.accept(ModItems.INFUSION_MONITOR_ITEM.get());
                output.accept(ModItems.ESSENTIA_PROVIDER_CONNECTION_ITEM.get()); output.accept(ModItems.WIRELESS_CONNECTOR.get());
                output.accept(ModItems.ESSENTIA_UPGRADE_CARD.get());
                output.accept(ModItems.PROBABILITY_ACCELERATOR_CARD.get());
                output.accept(ModItems.BRAIN_OF_PROBABILITY_ITEM.get());
                // 玩偶不进创造物品栏：需 /give @p thaumicenergistics:alkusure86fumo 手动获取
                // output.accept(ModItems.ALKUSURE86_FUMO_ITEM.get());

                // 源质虚拟物品不再在创造标签页中注册（避免玩家拿到物品栏）
                // 它们通过 JEI 插件注册，仅作为 ghost ingredient 可拖拽到 AE 筛选槽
                // 如果没有 JEI，玩家可以通过拖拽源质罐子到筛选槽来设置筛选
            }).build());
}
