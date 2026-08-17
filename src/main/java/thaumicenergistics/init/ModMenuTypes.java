package thaumicenergistics.init;

import appeng.api.implementations.menuobjects.IPortableTerminal;
import appeng.api.storage.ITerminalHost;
import appeng.menu.implementations.MenuTypeBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.container.*;
import thaumicenergistics.common.parts.EssentiaExportBusPart;
import thaumicenergistics.common.parts.EssentiaImportBusPart;
import thaumicenergistics.common.parts.EssentiaLevelEmitterPart;
import thaumicenergistics.common.parts.EssentiaStorageBusPart;

import java.util.function.Supplier;

public final class ModMenuTypes {
    private ModMenuTypes() {}
    public static final DeferredRegister<MenuType<?>> REGISTRY =
            DeferredRegister.create(Registries.MENU, ThaumicEnergistics.MODID);

    public static final Supplier<MenuType<ContainerEssentiaCellWorkbench>> ESSENTIA_CELL_WORKBENCH =
            REGISTRY.register("essentia_cell_workbench", () -> IMenuTypeExtension.create(ContainerEssentiaCellWorkbench::new));
    public static final Supplier<MenuType<ContainerEssentiaVibrationChamber>> EVC =
            REGISTRY.register("essentia_vibration_chamber", () -> IMenuTypeExtension.create(ContainerEssentiaVibrationChamber::new));
    public static final Supplier<MenuType<ContainerArcaneAssembler>> ARCANE_ASSEMBLER =
            REGISTRY.register("arcane_assembler", () -> IMenuTypeExtension.create(ContainerArcaneAssembler::new));
    /** 源质终端菜单：有线段（挂在线缆上的终端部件）。 */
    public static final Supplier<MenuType<EssentiaTerminalMenu>> ESSENTIA_TERMINAL =
            REGISTRY.register("essentia_terminal", () ->
                    MenuTypeBuilder.create(EssentiaTerminalMenu::new, ITerminalHost.class)
                            .buildUnregistered(ThaumicEnergistics.id("essentia_terminal")));
    /** 源质终端菜单：无线段（手持无线源质终端）。 */
    public static final Supplier<MenuType<EssentiaTerminalMenu>> WIRELESS_ESSENTIA_TERMINAL =
            REGISTRY.register("wireless_essentia_terminal", () ->
                    MenuTypeBuilder.create(EssentiaTerminalMenu::new, IPortableTerminal.class)
                            .buildUnregistered(ThaumicEnergistics.id("wireless_essentia_terminal")));
    public static final Supplier<MenuType<ContainerKnowledgeInscriber>> KNOWLEDGE_INSCRIBER =
            REGISTRY.register("knowledge_inscriber", () -> IMenuTypeExtension.create(ContainerKnowledgeInscriber::new));
    public static final Supplier<MenuType<ContainerDistillationPatternEncoder>> DISTILLATION_ENCODER =
            REGISTRY.register("distillation_encoder", () -> IMenuTypeExtension.create(ContainerDistillationPatternEncoder::new));

    public static final Supplier<MenuType<ContainerEssentiaImportBus>> ESSENTIA_IMPORT_BUS =
            REGISTRY.register("essentia_import_bus", () ->
                    MenuTypeBuilder.create(ContainerEssentiaImportBus::new, EssentiaImportBusPart.class)
                            .withMenuTitle(host -> host.getName() != null ? host.getName() : Component.empty())
                            .buildUnregistered(ThaumicEnergistics.id("essentia_import_bus")));

    public static final Supplier<MenuType<ContainerEssentiaExportBus>> ESSENTIA_EXPORT_BUS =
            REGISTRY.register("essentia_export_bus", () ->
                    MenuTypeBuilder.create(ContainerEssentiaExportBus::new, EssentiaExportBusPart.class)
                            .withMenuTitle(host -> host.getName() != null ? host.getName() : Component.empty())
                            .buildUnregistered(ThaumicEnergistics.id("essentia_export_bus")));

    public static final Supplier<MenuType<ContainerEssentiaStorageBus>> ESSENTIA_STORAGE_BUS =
            REGISTRY.register("essentia_storage_bus", () ->
                    MenuTypeBuilder.create(ContainerEssentiaStorageBus::new, EssentiaStorageBusPart.class)
                            .withMenuTitle(host -> host.getName() != null ? host.getName() : Component.empty())
                            .buildUnregistered(ThaumicEnergistics.id("essentia_storage_bus")));

    public static final Supplier<MenuType<ContainerArcaneCraftingTerminal>> ARCANE_CRAFTING_TERMINAL =
            REGISTRY.register("arcane_crafting_terminal", () ->
                    MenuTypeBuilder.create(ContainerArcaneCraftingTerminal::new, ITerminalHost.class)
                            .buildUnregistered(ThaumicEnergistics.id("arcane_crafting_terminal")));

    public static final Supplier<MenuType<ContainerEssentiaLevelEmitter>> ESSENTIA_LEVEL_EMITTER =
            REGISTRY.register("essentia_level_emitter", () ->
                    MenuTypeBuilder.create(ContainerEssentiaLevelEmitter::new, EssentiaLevelEmitterPart.class)
                            .withMenuTitle(host -> host.getName() != null ? host.getName() : Component.empty())
                            .buildUnregistered(ThaumicEnergistics.id("essentia_level_emitter")));
}
