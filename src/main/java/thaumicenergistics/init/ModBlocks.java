package thaumicenergistics.init;

import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.blocks.OrientableEntityBlock;
import thaumicenergistics.common.blocks.ThEBaseEntityBlock;
import thaumicenergistics.common.tiles.*;
import net.minecraft.network.chat.Component;

public final class ModBlocks {
    private ModBlocks() {}
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(ThaumicEnergistics.MODID);
    private static final BlockBehaviour.Properties P = BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2f).sound(SoundType.STONE);
    /** 装配器专用：noOcclusion——玻璃方块不被视为不透明，相邻方块（如冰/半透明）接触面不被剔除，透明玻璃后可正常看到后方方块。 */
    private static final BlockBehaviour.Properties PA = P.noOcclusion();

    public static final DeferredBlock<Block> ESSENTIA_PROVIDER = REGISTRY.register("essentia_provider", () -> new ThEBaseEntityBlock(P) { public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileEssentiaProvider(pos, st); } });
    public static final DeferredBlock<Block> INFUSION_PROVIDER = REGISTRY.register("infusion_provider", () -> new ThEBaseEntityBlock(P) { public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileInfusionProvider(pos, st); } });
    public static final DeferredBlock<Block> ARCANE_ASSEMBLER = REGISTRY.register("arcane_assembler", () -> new ThEBaseEntityBlock(PA) {
        public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileArcaneAssembler(pos, st); }
        @Override
        protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            // 手持 AE2 内存卡：交给装配器处理（读 P2P 频率设 vis 源 / 清空），不打开 GUI。
            // 1.21 方块 useItemOn 先于物品 useOn，返回 sidedSuccess 消费交互（也阻止内存卡自身 shift 清空）。
            if (stack.getItem() instanceof appeng.api.implementations.items.IMemoryCard memoryCard
                    && level.getBlockEntity(pos) instanceof TileArcaneAssembler tile) {
                if (!level.isClientSide) {
                    tile.onMemoryCardActivate(player, memoryCard, stack);
                }
                return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            if (!level.isClientSide) {
                ((net.neoforged.neoforge.common.extensions.IPlayerExtension) player)
                        .openMenu(state.getMenuProvider(level, pos), pos);
            }
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        @Override
        protected MenuProvider getMenuProvider(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
            TileArcaneAssembler tile = (TileArcaneAssembler) level.getBlockEntity(pos);
            return new SimpleMenuProvider(
                    (id, inv, p) -> new thaumicenergistics.common.container.ContainerArcaneAssembler(id, inv, tile),
                    Component.translatable("block.thaumicenergistics.arcane_assembler"));
        }
    });
    public static final DeferredBlock<Block> ESSENTIA_VIBRATION_CHAMBER = REGISTRY.register("essentia_vibration_chamber", () -> new OrientableEntityBlock(P) { public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileEssentiaVibrationChamber(pos, st); } });
    public static final DeferredBlock<Block> KNOWLEDGE_INSCRIBER = REGISTRY.register("knowledge_inscriber", () -> new ThEBaseEntityBlock(P) {
        public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileKnowledgeInscriber(pos, st); }
        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(OrientableEntityBlock.FACING);
        }
        @Override
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
            return this.defaultBlockState().setValue(OrientableEntityBlock.FACING, context.getHorizontalDirection().getOpposite());
        }
        @Override
        protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            if (!level.isClientSide) player.openMenu(state.getMenuProvider(level, pos));
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        @Override
        protected MenuProvider getMenuProvider(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
            TileKnowledgeInscriber tile = (TileKnowledgeInscriber) level.getBlockEntity(pos);
            return new SimpleMenuProvider(
                    (id, inv, p) -> new thaumicenergistics.common.container.ContainerKnowledgeInscriber(id, inv, tile),
                    Component.translatable("block.thaumicenergistics.knowledge_inscriber"));
        }
    });
    public static final DeferredBlock<Block> DISTILLATION_ENCODER = REGISTRY.register("distillation_encoder", () -> new ThEBaseEntityBlock(P) {
        public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileDistillationPatternEncoder(pos, st); }
        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(OrientableEntityBlock.FACING);
        }
        @Override
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
            return this.defaultBlockState().setValue(OrientableEntityBlock.FACING, context.getHorizontalDirection().getOpposite());
        }
        @Override
        protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            if (!level.isClientSide) player.openMenu(state.getMenuProvider(level, pos));
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        @Override
        protected MenuProvider getMenuProvider(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
            TileDistillationPatternEncoder tile = (TileDistillationPatternEncoder) level.getBlockEntity(pos);
            return new SimpleMenuProvider(
                    (id, inv, p) -> new thaumicenergistics.common.container.ContainerDistillationPatternEncoder(id, inv, tile),
                    Component.translatable("block.thaumicenergistics.distillation_encoder"));
        }
    });
    public static final DeferredBlock<Block> ESSENTIA_CELL_WORKBENCH = REGISTRY.register("essentia_cell_workbench", () -> new ThEBaseEntityBlock(P) {
        @Override
        public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TileEssentiaCellWorkbench(pos, state);
        }
        @Override
        protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            if (!level.isClientSide) player.openMenu(state.getMenuProvider(level, pos));
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        @Override
        protected MenuProvider getMenuProvider(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileEssentiaCellWorkbench workbench) {
                return new SimpleMenuProvider((id, inv, p) -> new thaumicenergistics.common.container.ContainerEssentiaCellWorkbench(id, inv, workbench),
                        Component.translatable("block.thaumicenergistics.essentia_cell_workbench"));
            }
            return null;
        }
    });
}
