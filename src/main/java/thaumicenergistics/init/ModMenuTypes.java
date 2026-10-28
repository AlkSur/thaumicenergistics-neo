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
import thaumicenergistics.common.parts.EssentiaLevelEmitterPart;

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
    public static final Supplier<MenuType<ContainerKnowledgeInscriber>> KNOWLEDGE_INSCRIBER =
            REGISTRY.register("knowledge_inscriber", () -> IMenuTypeExtension.create(ContainerKnowledgeInscriber::new));
    public static final Supplier<MenuType<ContainerDistillationPatternEncoder>> DISTILLATION_ENCODER =
            REGISTRY.register("distillation_encoder", () -> IMenuTypeExtension.create(ContainerDistillationPatternEncoder::new));

    public static final Supplier<MenuType<ContainerArcaneCraftingTerminal>> ARCANE_CRAFTING_TERMINAL =
            REGISTRY.register("arcane_crafting_terminal", () ->
                    MenuTypeBuilder.create(ContainerArcaneCraftingTerminal::new, ITerminalHost.class)
                            .buildUnregistered(ThaumicEnergistics.id("arcane_crafting_terminal")));
    /**
     * 无线奥术合成终端菜单：复用有线菜单类，仅换 host 类型（手持无线终端，同 wireless_essentia_terminal 先例）。
     * <p>
     * COMPAT-PLAN：菜单类型对象提前构造（AE2WT 官方 WCTMenu.TYPE 同范式——类加载期
     * buildUnregistered），WTDefinition 与注册表引用同一实例，避免通用终端注册回调
     * （RegisterEvent(ITEM) 时 MENU 注册表尚未注册）拿到 null。
     * 工厂显式传入无线菜单类型：客户端按 menu.getType() 选屏，若沿用三参构造会落回
     * 有线类型，打开无通用终端切换按钮的有线屏幕。
     */
    public static final MenuType<ContainerArcaneCraftingTerminal> WIRELESS_ARCANE_CRAFTING_TERMINAL_TYPE =
            MenuTypeBuilder.create(
                    (id, inventory, host) -> new ContainerArcaneCraftingTerminal(
                            ModMenuTypes.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(), id, inventory, host, true),
                    IPortableTerminal.class)
                    .buildUnregistered(ThaumicEnergistics.id("wireless_arcane_crafting_terminal"));
    public static final Supplier<MenuType<ContainerArcaneCraftingTerminal>> WIRELESS_ARCANE_CRAFTING_TERMINAL =
            REGISTRY.register("wireless_arcane_crafting_terminal", () -> WIRELESS_ARCANE_CRAFTING_TERMINAL_TYPE);

    public static final Supplier<MenuType<ContainerEssentiaLevelEmitter>> ESSENTIA_LEVEL_EMITTER =
            REGISTRY.register("essentia_level_emitter", () ->
                    MenuTypeBuilder.create(ContainerEssentiaLevelEmitter::new, EssentiaLevelEmitterPart.class)
                            .withMenuTitle(host -> host.getName() != null ? host.getName() : Component.empty())
                            .buildUnregistered(ThaumicEnergistics.id("essentia_level_emitter")));
}
