package thaumicenergistics.init;

import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.blocks.BrainOfProbabilityBlock;
import thaumicenergistics.common.blocks.FumoBlock;
import thaumicenergistics.common.blocks.BrainOfProbabilityTopBlock;
import thaumicenergistics.common.blocks.OrientableEntityBlock;
import thaumicenergistics.common.blocks.ThEBaseEntityBlock;
import thaumicenergistics.common.tiles.*;
import thaumicenergistics.init.ModItems;
import net.minecraft.network.chat.Component;
import appeng.api.implementations.items.IMemoryCard;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.extensions.IPlayerExtension;
import thaumicenergistics.common.container.ContainerArcaneAssembler;
import thaumicenergistics.common.container.ContainerDistillationPatternEncoder;
import thaumicenergistics.common.container.ContainerEssentiaCellWorkbench;
import thaumicenergistics.common.container.ContainerKnowledgeInscriber;

public final class ModBlocks {
    private ModBlocks() {}
    public static final DeferredRegister.Blocks REGISTRY = DeferredRegister.createBlocks(ThaumicEnergistics.MODID);
    private static final BlockBehaviour.Properties P = BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2f).sound(SoundType.STONE);
    /** 装配器专用：noOcclusion——玻璃方块不被视为不透明，相邻方块（如冰/半透明）接触面不被剔除，透明玻璃后可正常看到后方方块。 */
    private static final BlockBehaviour.Properties PA = P.noOcclusion();

    public static final DeferredBlock<Block> ESSENTIA_PROVIDER = REGISTRY.register("essentia_provider", () -> new ThEBaseEntityBlock(P) { public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileEssentiaProvider(pos, st); } });
    public static final DeferredBlock<Block> INFUSION_PROVIDER = REGISTRY.register("infusion_provider", () -> new ThEBaseEntityBlock(P) { public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileInfusionProvider(pos, st); } });
    public static final DeferredBlock<Block> ARCANE_ASSEMBLER = REGISTRY.register("arcane_assembler", () -> new ThEBaseEntityBlock(PA) {
        public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileArcaneAssembler(pos, st); }
        @Override
        protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            // 手持 AE2 内存卡：交给装配器处理（读 P2P 频率设 vis 源 / 清空），不打开 GUI。
            // 1.21 方块 useItemOn 先于物品 useOn，返回 sidedSuccess 消费交互（也阻止内存卡自身 shift 清空）。
            if (stack.getItem() instanceof IMemoryCard memoryCard
                    && level.getBlockEntity(pos) instanceof TileArcaneAssembler tile) {
                if (!level.isClientSide) {
                    tile.onMemoryCardActivate(player, memoryCard, stack);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            if (!level.isClientSide) {
                ((IPlayerExtension) player)
                        .openMenu(state.getMenuProvider(level, pos), pos);
            }
            return ItemInteractionResult.SUCCESS;
        }
        @Override
        protected net.minecraft.world.MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
            TileArcaneAssembler tile = (TileArcaneAssembler) level.getBlockEntity(pos);
            return new SimpleMenuProvider(
                    (id, inv, p) -> new ContainerArcaneAssembler(id, inv, tile),
                    Component.translatable("block.thaumicenergistics.arcane_assembler"));
        }
    });
    public static final DeferredBlock<Block> ESSENTIA_VIBRATION_CHAMBER = REGISTRY.register("essentia_vibration_chamber", () -> new OrientableEntityBlock(P) { public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileEssentiaVibrationChamber(pos, st); } });
    public static final DeferredBlock<Block> KNOWLEDGE_INSCRIBER = REGISTRY.register("knowledge_inscriber", () -> new ThEBaseEntityBlock(P) {
        public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileKnowledgeInscriber(pos, st); }
        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(OrientableEntityBlock.FACING);
        }
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return this.defaultBlockState().setValue(OrientableEntityBlock.FACING, context.getHorizontalDirection().getOpposite());
        }
        @Override
        protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide) player.openMenu(state.getMenuProvider(level, pos));
            return ItemInteractionResult.SUCCESS;
        }
        @Override
        protected net.minecraft.world.MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
            TileKnowledgeInscriber tile = (TileKnowledgeInscriber) level.getBlockEntity(pos);
            return new SimpleMenuProvider(
                    (id, inv, p) -> new ContainerKnowledgeInscriber(id, inv, tile),
                    Component.translatable("block.thaumicenergistics.knowledge_inscriber"));
        }
    });
    public static final DeferredBlock<Block> DISTILLATION_ENCODER = REGISTRY.register("distillation_encoder", () -> new ThEBaseEntityBlock(P) {
        public BlockEntity newBlockEntity(BlockPos pos, BlockState st) { return new TileDistillationPatternEncoder(pos, st); }
        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(OrientableEntityBlock.FACING);
        }
        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return this.defaultBlockState().setValue(OrientableEntityBlock.FACING, context.getHorizontalDirection().getOpposite());
        }
        @Override
        protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide) player.openMenu(state.getMenuProvider(level, pos));
            return ItemInteractionResult.SUCCESS;
        }
        @Override
        protected net.minecraft.world.MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
            TileDistillationPatternEncoder tile = (TileDistillationPatternEncoder) level.getBlockEntity(pos);
            return new SimpleMenuProvider(
                    (id, inv, p) -> new ContainerDistillationPatternEncoder(id, inv, tile),
                    Component.translatable("block.thaumicenergistics.distillation_encoder"));
        }
    });
    public static final DeferredBlock<Block> ESSENTIA_CELL_WORKBENCH = REGISTRY.register("essentia_cell_workbench", () -> new ThEBaseEntityBlock(P) {
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TileEssentiaCellWorkbench(pos, state);
        }
        @Override
        protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (!level.isClientSide) player.openMenu(state.getMenuProvider(level, pos));
            return ItemInteractionResult.SUCCESS;
        }
        @Override
        protected net.minecraft.world.MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileEssentiaCellWorkbench workbench) {
                return new SimpleMenuProvider((id, inv, p) -> new ContainerEssentiaCellWorkbench(id, inv, workbench),
                        Component.translatable("block.thaumicenergistics.essentia_cell_workbench"));
            }
            return null;
        }
    });

    // ==================== 注魔监视器（灵气观测器） ====================

    /** 是否放入魔导手册。 */
    public static final BooleanProperty BOOK =
            BooleanProperty.create("book");
    /** 是否连接 AE 网络。 */
    public static final BooleanProperty NETWORK =
            BooleanProperty.create("network");

    /** M4：注魔结束红石脉冲激活态（单次脉冲 40 tick 后自动复位；模型不引用此属性，纯红石信号）。 */
    public static final BooleanProperty PULSE =
            BooleanProperty.create("pulse");

    /**
     * 注魔监视器：4 态（off / off+book / off+book+on / off+Jade），blockstate 用 multipart 合并模型。
     */
    public static final DeferredBlock<Block> INFUSION_MONITOR = REGISTRY.register("infusion_monitor", () -> new ThEBaseEntityBlock(P) {
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TileInfusionMonitor(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide()) return null;
            if (type == ModBlockEntities.INFUSION_MONITOR.get()) {
                return (lvl, pos, st, be) -> TileInfusionMonitor.tick(lvl, pos, st, (TileInfusionMonitor) be);
            }
            return null;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BOOK, NETWORK, FACING, PULSE);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            // 放置时始终朝向放置者
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(BOOK, false)
                    .setValue(NETWORK, false)
                    .setValue(PULSE, false);
        }

        // ===== M4：注魔结束红石脉冲（五面输出，T-M4-SIGNAL-REV） =====
        // FACING = 放置时朝向放置者（正面=贴注魔矩阵面，典型安装时朝上）；除正面外五面（4 侧面+底部）输出恒 15。

        @Override
        protected boolean isSignalSource(BlockState state) {
            return true;
        }

        @Override
        protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
            // 除正面外五面出信号（side = 信号流出面）；正面恒 0
            return state.getValue(PULSE) && side != state.getValue(FACING) ? 15 : 0;
        }

        @Override
        protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
            // 与 getSignal 一致（红石线/比较器惯例，见 LeverBlock 同款双覆写）
            return state.getValue(PULSE) && side != state.getValue(FACING) ? 15 : 0;
        }

        @Override
        protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (level.getBlockEntity(pos) instanceof TileInfusionMonitor monitor) {
                // 手持魔导手册 → 直接放入（返回 SUCCESS 拦截，避免魔导手册自身 use 打开界面）
                if (TileInfusionMonitor.isThaumonomicon(stack)) {
                    if (!level.isClientSide) {
                        monitor.placeBook(player, stack);
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
                // Shift+右键（非手册物品）→ 取回手册
                if (player.isShiftKeyDown()) {
                    if (!level.isClientSide) {
                        monitor.retrieveBook(player);
                    }
                    return ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
                // 普通右键（非手册/非Shift）→ 聊天框显示风险描述
                if (!level.isClientSide) {
                    monitor.showDescriptionInChat(player);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 空手右键（1.21 走 useWithoutItem）：Shift+空手 → 取回手册；普通空手右键 → 聊天框显示描述
        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (level.getBlockEntity(pos) instanceof TileInfusionMonitor monitor) {
                if (player.isShiftKeyDown()) {
                    if (!level.isClientSide) {
                        monitor.retrieveBook(player);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide());
                }
                if (!level.isClientSide) {
                    monitor.showDescriptionInChat(player);
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            return InteractionResult.PASS;
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            // 底座（高 0.25）+ 上部塔架（0.25~0.8）
            return Shapes.or(
                    Shapes.box(0.0, 0.0, 0.0, 1.0, 0.25, 1.0),
                    Shapes.box(0.15, 0.25, 0.15, 0.85, 0.8, 0.85));
        }
    });

    // ==================== Alkusure86 玩偶（Fumo） ====================

    /** 水平朝向（韵魔观测器共用）。 */
    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    // 姿态变体：0 = alkusure86fumo（默认坐姿），1 = a999csfumo。
    public static final BooleanProperty VARIANT =
            BooleanProperty.create("variant");

    // 玩偶专用：低硬度 → 空手可快速挖掘（装饰物）。
    private static final BlockBehaviour.Properties PD =
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.2f).sound(SoundType.WOOL);

    // Alkusure86 玩偶：纯装饰方块，2 个姿态模型 + 水平朝向。
    // 右键切换姿态；Shift+右键拿起方块到手。
    public static final DeferredBlock<Block> ALKUSURE86_FUMO = REGISTRY.register("alkusure86fumo", () -> new Block(PD) {
        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, VARIANT);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            // 默认坐姿（variant=0），面向放置者
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(VARIANT, false);
        }

        @Override
        protected ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (player.isShiftKeyDown()) {
                // Shift+右键：拿起方块到手（或背包）
                pickUp(level, pos, player);
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            // 右键：切换姿态
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(VARIANT, !state.getValue(VARIANT)), 3);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }

        // 空手右键（1.21 走 useWithoutItem）：Shift+空手拿起；右键切换姿态
        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (player.isShiftKeyDown()) {
                pickUp(level, pos, player);
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(VARIANT, !state.getValue(VARIANT)), 3);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // Shift+右键拿起：移除方块，物品进主手（空手时）或背包。
        private void pickUp(Level level, BlockPos pos, Player player) {
            if (level.isClientSide) return;
            ItemStack item = new ItemStack(ModItems.ALKUSURE86_FUMO_ITEM.get());
            if (player.getMainHandItem().isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, item);
            } else if (!player.getInventory().add(item)) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item);
            }
            level.removeBlock(pos, false);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            // 玩偶整体占位：坐姿高度约 12 格单位（0.75 格），宽约 8（0.5 格）
            return Shapes.box(0.25, 0.0, 0.25, 0.75, 0.75, 0.75);
        }

        // 纯 Block 无战利品表 → 必须覆写 getDrops，否则挖掘不掉落
        @Override
        public java.util.List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
            return java.util.List.of(new ItemStack(ModItems.ALKUSURE86_FUMO_ITEM.get()));
        }
    });

    /** 玩偶通用：拿起方块到手（空手进主手，否则进背包），并移除方块。 */
    private static void pickUpFumo(Level level, BlockPos pos, Player player, Block block) {
        if (level.isClientSide) return;
        ItemStack item = new ItemStack(block.asItem());
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, item);
        } else if (!player.getInventory().add(item)) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item);
        }
        level.removeBlock(pos, false);
    }

    // Preserve the registered IDs; both dolls now share Alkusure86's two-state interaction.
    public static final DeferredBlock<Block> MAIQILIN_FUMO = REGISTRY.register("maiqilinfumo",
            () -> new FumoBlock(PD));
    public static final DeferredBlock<Block> BEIWUCN_FUMO = REGISTRY.register("beiwucnfumo",
            () -> new FumoBlock(PD));

    // ==================== 源质无线接收器 ====================

    /** 是否已连接源质供应器（off/on 模型切换）。 */
    public static final BooleanProperty CONNECTED =
            BooleanProperty.create("connected");

    /** 接收器六向贴放（贴墙/立地/挂顶）。 */
    public static final DirectionProperty FACING_ALL =
            BlockStateProperties.FACING;

    /** 接收器专用：低硬度 → 镐子可快速挖掘。 */
    private static final BlockBehaviour.Properties PC =
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.0f).sound(SoundType.STONE);

    public static final DeferredBlock<Block> ESSENTIA_PROVIDER_CONNECTION = REGISTRY.register("essentia_provider_connection", () -> new ThEBaseEntityBlock(PC) {
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TileEssentiaProviderConnection(pos, state);
        }

        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (!level.isClientSide() && type == ModBlockEntities.ESSENTIA_PROVIDER_CONNECTION.get()) {
                return (lvl, pos, st, be) -> TileEssentiaProviderConnection.serverTick(lvl, pos, st, (TileEssentiaProviderConnection) be);
            }
            return null;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(CONNECTED, FACING_ALL);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            // 正面朝外 = 点击面的方向（贴墙/立地/挂顶）
            return this.defaultBlockState()
                    .setValue(FACING_ALL, context.getClickedFace())
                    .setValue(CONNECTED, false);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            // 薄碰撞箱（贴墙/立地/挂顶），不遮挡光线；UP=立地(底部) DOWN=挂顶(顶部)
            return switch (state.getValue(FACING_ALL)) {
                case NORTH -> Shapes.box(0.25, 0.25, 0.75, 0.75, 0.75, 1.0);
                case SOUTH -> Shapes.box(0.25, 0.25, 0.0, 0.75, 0.75, 0.25);
                case EAST -> Shapes.box(0.0, 0.25, 0.25, 0.25, 0.75, 0.75);
                case WEST -> Shapes.box(0.75, 0.25, 0.25, 1.0, 0.75, 0.75);
                case UP -> Shapes.box(0.25, 0.0, 0.25, 0.75, 0.25, 0.75);      // 立地：碰撞箱在底部
                case DOWN -> Shapes.box(0.25, 0.75, 0.25, 0.75, 1.0, 0.75);   // 挂顶：碰撞箱在顶部
            };
        }

        @Override
        public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
            return 0; // 不遮挡光线
        }
    });

    // ==================== M6：概率之脑（两格，CE gacha_box 同构） ====================

    public static final DeferredBlock<Block> BRAIN_OF_PROBABILITY =
            REGISTRY.register("brain_of_probability", () -> new BrainOfProbabilityBlock(P));
    /** 上格形态方块：无 BE、无交互、不进创造栏、无掉落。 */
    public static final DeferredBlock<Block> BRAIN_OF_PROBABILITY_TOP =
            REGISTRY.register("brain_of_probability_top", () -> new BrainOfProbabilityTopBlock(P));
}
