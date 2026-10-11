package thaumicenergistics.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.tiles.*;
import thaumicenergistics.common.integration.tc.VisProviderProxy;
import java.util.function.Supplier;
import thaumicenergistics.common.parts.VisInterfacePart;
import thaumicenergistics.common.tiles.BrainOfProbabilityBlockEntity;

public final class ModBlockEntities {
    private ModBlockEntities() {}
    public static final DeferredRegister<BlockEntityType<?>> REGISTRY =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ThaumicEnergistics.MODID);

    public static final Supplier<BlockEntityType<TileEssentiaProvider>> ESSENTIA_PROVIDER =
            REGISTRY.register("essentia_provider",
                    () -> BlockEntityType.Builder.of(TileEssentiaProvider::new, ModBlocks.ESSENTIA_PROVIDER.get()).build(null));

    public static final Supplier<BlockEntityType<TileInfusionProvider>> INFUSION_PROVIDER =
            REGISTRY.register("infusion_provider",
                    () -> BlockEntityType.Builder.of(TileInfusionProvider::new, ModBlocks.INFUSION_PROVIDER.get()).build(null));

    public static final Supplier<BlockEntityType<TileArcaneAssembler>> ARCANE_ASSEMBLER =
            REGISTRY.register("arcane_assembler",
                    () -> BlockEntityType.Builder.of(TileArcaneAssembler::new, ModBlocks.ARCANE_ASSEMBLER.get()).build(null));

    public static final Supplier<BlockEntityType<TileEssentiaVibrationChamber>> ESSENTIA_VIBRATION_CHAMBER =
            REGISTRY.register("essentia_vibration_chamber",
                    () -> BlockEntityType.Builder.of(TileEssentiaVibrationChamber::new, ModBlocks.ESSENTIA_VIBRATION_CHAMBER.get()).build(null));

    public static final Supplier<BlockEntityType<TileKnowledgeInscriber>> KNOWLEDGE_INSCRIBER =
            REGISTRY.register("knowledge_inscriber",
                    () -> BlockEntityType.Builder.of(TileKnowledgeInscriber::new, ModBlocks.KNOWLEDGE_INSCRIBER.get()).build(null));

    public static final Supplier<BlockEntityType<TileDistillationPatternEncoder>> DISTILLATION_ENCODER =
            REGISTRY.register("distillation_encoder",
                    () -> BlockEntityType.Builder.of(TileDistillationPatternEncoder::new, ModBlocks.DISTILLATION_ENCODER.get()).build(null));

    public static final Supplier<BlockEntityType<TileEssentiaCellWorkbench>> ESSENTIA_CELL_WORKBENCH =
            REGISTRY.register("essentia_cell_workbench",
                    () -> BlockEntityType.Builder.of(TileEssentiaCellWorkbench::new, ModBlocks.ESSENTIA_CELL_WORKBENCH.get()).build(null));

    public static final Supplier<BlockEntityType<TileInfusionMonitor>> INFUSION_MONITOR =
            REGISTRY.register("infusion_monitor",
                    () -> BlockEntityType.Builder.of(TileInfusionMonitor::new, ModBlocks.INFUSION_MONITOR.get()).build(null));

    /** M6：概率之脑（仅下格持有 BE）。 */
    public static final Supplier<BlockEntityType<BrainOfProbabilityBlockEntity>> BRAIN_OF_PROBABILITY =
            REGISTRY.register("brain_of_probability",
                    () -> BlockEntityType.Builder.of(BrainOfProbabilityBlockEntity::new,
                            ModBlocks.BRAIN_OF_PROBABILITY.get()).build(null));

    public static final Supplier<BlockEntityType<TileEssentiaProviderConnection>> ESSENTIA_PROVIDER_CONNECTION =
            REGISTRY.register("essentia_provider_connection",
                    () -> BlockEntityType.Builder.of(TileEssentiaProviderConnection::new, ModBlocks.ESSENTIA_PROVIDER_CONNECTION.get()).build(null));

    /**
     * 虚拟的 Thaumcraft vis 源，供 vis 接口的提供端使用。
     * 不作为真实方块放置；由 {@link VisInterfacePart}
     * 在运行时构造并注入到 VisNetHandler。
     */
    public static final Supplier<BlockEntityType<VisProviderProxy>> VIS_PROVIDER_PROXY =
            REGISTRY.register("vis_provider_proxy",
                    () -> BlockEntityType.Builder.of(VisProviderProxy::new).build(null));
}
