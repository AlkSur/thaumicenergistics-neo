package tc_energy.init;

import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.blocks.OrientableEntityBlock;
import tc_energy.common.blocks.ThEBaseEntityBlock;
import tc_energy.common.tiles.*;
import tc_energy.init.ModItems;
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
        protected net.minecraft.world.MenuProvider getMenuProvider(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
            TileArcaneAssembler tile = (TileArcaneAssembler) level.getBlockEntity(pos);
            return new SimpleMenuProvider(
                    (id, inv, p) -> new tc_energy.common.container.ContainerArcaneAssembler(id, inv, tile),
                    Component.translatable("block.tc_energy.arcane_assembler"));
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
        protected net.minecraft.world.MenuProvider getMenuProvider(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
            TileKnowledgeInscriber tile = (TileKnowledgeInscriber) level.getBlockEntity(pos);
            return new SimpleMenuProvider(
                    (id, inv, p) -> new tc_energy.common.container.ContainerKnowledgeInscriber(id, inv, tile),
                    Component.translatable("block.tc_energy.knowledge_inscriber"));
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
        protected net.minecraft.world.MenuProvider getMenuProvider(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
            TileDistillationPatternEncoder tile = (TileDistillationPatternEncoder) level.getBlockEntity(pos);
            return new SimpleMenuProvider(
                    (id, inv, p) -> new tc_energy.common.container.ContainerDistillationPatternEncoder(id, inv, tile),
                    Component.translatable("block.tc_energy.distillation_encoder"));
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
        protected net.minecraft.world.MenuProvider getMenuProvider(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TileEssentiaCellWorkbench workbench) {
                return new SimpleMenuProvider((id, inv, p) -> new tc_energy.common.container.ContainerEssentiaCellWorkbench(id, inv, workbench),
                        Component.translatable("block.tc_energy.essentia_cell_workbench"));
            }
            return null;
        }
    });

    // ==================== 注魔监视器（灵气观测器） ====================

    /** 是否放入魔导手册。 */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty BOOK =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("book");
    /** 是否连接 AE 网络。 */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty NETWORK =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("network");

    /**
     * 注魔监视器：4 态（off / off+book / off+book+on / off+Jade），blockstate 用 multipart 合并模型。
     */
    public static final DeferredBlock<Block> INFUSION_MONITOR = REGISTRY.register("infusion_monitor", () -> new ThEBaseEntityBlock(P) {
        @Override
        public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TileInfusionMonitor(pos, state);
        }

        @Override
        public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
            if (level.isClientSide()) return null;
            if (type == ModBlockEntities.INFUSION_MONITOR.get()) {
                return (lvl, pos, st, be) -> TileInfusionMonitor.tick(lvl, pos, st, (TileInfusionMonitor) be);
            }
            return null;
        }

        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(BOOK, NETWORK, FACING);
        }

        @Override
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
            // 放置时始终朝向放置者
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(BOOK, false)
                    .setValue(NETWORK, false);
        }

        @Override
        protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            if (level.getBlockEntity(pos) instanceof TileInfusionMonitor monitor) {
                // 手持魔导手册 → 直接放入（返回 SUCCESS 拦截，避免魔导手册自身 use 打开界面）
                if (TileInfusionMonitor.isThaumonomicon(stack)) {
                    if (!level.isClientSide) {
                        monitor.placeBook(player, stack);
                    }
                    return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
                // Shift+右键（非手册物品）→ 取回手册
                if (player.isShiftKeyDown()) {
                    if (!level.isClientSide) {
                        monitor.retrieveBook(player);
                    }
                    return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
                }
                // 普通右键（非手册/非Shift）→ 聊天框显示风险描述
                if (!level.isClientSide) {
                    monitor.showDescriptionInChat(player);
                }
                return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 空手右键（1.21 走 useWithoutItem）：Shift+空手 → 取回手册；普通空手右键 → 聊天框显示描述
        @Override
        protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
            if (level.getBlockEntity(pos) instanceof TileInfusionMonitor monitor) {
                if (player.isShiftKeyDown()) {
                    if (!level.isClientSide) {
                        monitor.retrieveBook(player);
                    }
                    return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
                }
                if (!level.isClientSide) {
                    monitor.showDescriptionInChat(player);
                }
                return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
            }
            return net.minecraft.world.InteractionResult.PASS;
        }

        @Override
        public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
            // 底座（高 0.25）+ 上部塔架（0.25~0.8）
            return net.minecraft.world.phys.shapes.Shapes.or(
                    net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.0, 0.0, 1.0, 0.25, 1.0),
                    net.minecraft.world.phys.shapes.Shapes.box(0.15, 0.25, 0.15, 0.85, 0.8, 0.85));
        }
    });

    // ==================== Alkusure86 玩偶（Fumo） ====================

    /** 水平朝向（韵魔观测器共用）。 */
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    // 姿态变体：0 = alkusure86fumo（默认坐姿），1 = a999csfumo。
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty VARIANT =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("variant");

    // 玩偶专用：低硬度 → 空手可快速挖掘（装饰物）。
    private static final BlockBehaviour.Properties PD =
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.2f).sound(SoundType.WOOL);

    // Alkusure86 玩偶：纯装饰方块，2 个姿态模型 + 水平朝向。
    // 右键切换姿态；Shift+右键拿起方块到手。
    public static final DeferredBlock<Block> ALKUSURE86_FUMO = REGISTRY.register("alkusure86fumo", () -> new Block(PD) {
        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, VARIANT);
        }

        @Override
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
            // 默认坐姿（variant=0），面向放置者
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(VARIANT, false);
        }

        @Override
        protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            if (player.isShiftKeyDown()) {
                // Shift+右键：拿起方块到手（或背包）
                pickUp(level, pos, player);
                return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            // 右键：切换姿态
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(VARIANT, !state.getValue(VARIANT)), 3);
            }
            return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide());
        }

        // 空手右键（1.21 走 useWithoutItem）：Shift+空手拿起；右键切换姿态
        @Override
        protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.phys.BlockHitResult hit) {
            if (player.isShiftKeyDown()) {
                pickUp(level, pos, player);
                return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
            }
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(VARIANT, !state.getValue(VARIANT)), 3);
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
        }

        // Shift+右键拿起：移除方块，物品进主手（空手时）或背包。
        private void pickUp(net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
            if (level.isClientSide) return;
            ItemStack item = new ItemStack(ModItems.ALKUSURE86_FUMO_ITEM.get());
            if (player.getMainHandItem().isEmpty()) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, item);
            } else if (!player.getInventory().add(item)) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item);
            }
            level.removeBlock(pos, false);
        }

        @Override
        public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
            // 玩偶整体占位：坐姿高度约 12 格单位（0.75 格），宽约 8（0.5 格）
            return net.minecraft.world.phys.shapes.Shapes.box(0.25, 0.0, 0.25, 0.75, 0.75, 0.75);
        }

        // 纯 Block 无战利品表 → 必须覆写 getDrops，否则挖掘不掉落
        @Override
        public java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
            return java.util.List.of(new ItemStack(ModItems.ALKUSURE86_FUMO_ITEM.get()));
        }
    });

    // ==================== 源质无线接收器 ====================

    /** 是否已连接源质供应器（off/on 模型切换）。 */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty CONNECTED =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("connected");

    /** 接收器六向贴放（贴墙/立地/挂顶）。 */
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING_ALL =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;

    /** 接收器专用：低硬度 → 镐子可快速挖掘。 */
    private static final BlockBehaviour.Properties PC =
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.0f).sound(SoundType.STONE);

    public static final DeferredBlock<Block> ESSENTIA_PROVIDER_CONNECTION = REGISTRY.register("essentia_provider_connection", () -> new ThEBaseEntityBlock(PC) {
        @Override
        public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TileEssentiaProviderConnection(pos, state);
        }

        @Override
        public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(net.minecraft.world.level.Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
            if (!level.isClientSide() && type == ModBlockEntities.ESSENTIA_PROVIDER_CONNECTION.get()) {
                return (lvl, pos, st, be) -> TileEssentiaProviderConnection.serverTick(lvl, pos, st, (TileEssentiaProviderConnection) be);
            }
            return null;
        }

        @Override
        protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(CONNECTED, FACING_ALL);
        }

        @Override
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
            // 正面朝外 = 点击面的方向（贴墙/立地/挂顶）
            return this.defaultBlockState()
                    .setValue(FACING_ALL, context.getClickedFace())
                    .setValue(CONNECTED, false);
        }

        @Override
        public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) {
            // 薄碰撞箱（贴墙/立地/挂顶），不遮挡光线；UP=立地(底部) DOWN=挂顶(顶部)
            return switch (state.getValue(FACING_ALL)) {
                case NORTH -> net.minecraft.world.phys.shapes.Shapes.box(0.25, 0.25, 0.75, 0.75, 0.75, 1.0);
                case SOUTH -> net.minecraft.world.phys.shapes.Shapes.box(0.25, 0.25, 0.0, 0.75, 0.75, 0.25);
                case EAST -> net.minecraft.world.phys.shapes.Shapes.box(0.0, 0.25, 0.25, 0.25, 0.75, 0.75);
                case WEST -> net.minecraft.world.phys.shapes.Shapes.box(0.75, 0.25, 0.25, 1.0, 0.75, 0.75);
                case UP -> net.minecraft.world.phys.shapes.Shapes.box(0.25, 0.0, 0.25, 0.75, 0.25, 0.75);      // 立地：碰撞箱在底部
                case DOWN -> net.minecraft.world.phys.shapes.Shapes.box(0.25, 0.75, 0.25, 0.75, 1.0, 0.75);   // 挂顶：碰撞箱在顶部
            };
        }

        @Override
        public int getLightBlock(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
            return 0; // 不遮挡光线
        }
    });
}
