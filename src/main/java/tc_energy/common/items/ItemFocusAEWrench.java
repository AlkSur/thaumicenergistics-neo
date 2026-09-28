package tc_energy.common.items;

import appeng.blockentity.AEBaseBlockEntity;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.aspect.VisCost;
import dev.tc4port.thaumcraft.api.aspect.VisPaymentResult;
import dev.tc4port.thaumcraft.api.focus.FocusApi;
import dev.tc4port.thaumcraft.api.focus.FocusId;
import dev.tc4port.thaumcraft.api.focus.FocusIdentityItem;
import dev.tc4port.thaumcraft.api.focus.FocusInstallResult;
import dev.tc4port.thaumcraft.api.focus.FocusStateView;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionApi;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionContext;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionResult;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionSpec;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionTarget;
import dev.tc4port.thaumcraft.api.focus.action.FocusActivation;
import dev.tc4port.thaumcraft.api.focus.action.FocusCastAnimation;
import dev.tc4port.thaumcraft.api.item.ItemStatePlatform;
import dev.tc4port.thaumcraft.api.presentation.ActionPresentationAudience;
import dev.tc4port.thaumcraft.api.research.ResearchRequirement;
import dev.tc4port.thaumcraft.api.wand.WandCarrierItem;
import tc_energy.ThaumicEnergistics;
import dev.tc4port.thaumcraft.data.FocusData;
import dev.tc4port.thaumcraft.state.TCItemState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AE 扳手法杖焦点（Ren 版，TC4 4.2.3.5 移植版）。
 *
 * 与老版（thaumicenergistics-neo，TC4 旧 API 的 FocusItem 子类）行为对齐：
 *  - 另一手拿法杖时右键核心 → 装进法杖（FocusApi.install，等价老版 wand.installFocus）
 *  - 法杖施法（shift+右键 AE 方块）→ disassembleWithWrench 拆卸，消耗 ignis 10 + aer 10（centivis）
 *  - vis 不足提示 message.thaumcraft.wand.not_enough_vis；成功播 END_ROD 粒子光束
 *
 * Ren 版 focus 系统（tc4port）注册制：
 *  1. 物品身份：实现 FocusIdentityItem + 读写 thaumcraft:focus 组件（法杖袋/法杖识别本核心）
 *  2. 核心画像：data/tc_energy/focus_catalog/focus_aewrench.json
 *     （数据驱动注册，TC 的 FocusUpgradeCatalog 加载，"focus" 字段必须等于文件路径）
 *  3. 施法动作：registerFocusAction() 里 FocusActionApi.register，法杖施法时自动 dispatch
 */
public class ItemFocusAEWrench extends Item implements FocusIdentityItem {

    /**
     * 本核心的 FocusId，必须与
     * data/tc_energy/focus_catalog/focus_aewrench.json 的 "focus" 字段一致。
     */
    public static final FocusId FOCUS_ID = FocusId.of(ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "focus_aewrench"));

    /** 施法成本：ignis 10 + aer 10（centivis，与 profile 的 base_cost 一致）。 */
    private static final VisCost CAST_COST = VisCost.ofCentivis(Map.of(VisChannel.IGNIS, 2, VisChannel.AER, 3));

    private static final java.util.LinkedHashMap<AspectId, Integer> COST_DISPLAY = new java.util.LinkedHashMap<>();
    static {
        COST_DISPLAY.put(AspectId.parse("ignis"), 10);
        COST_DISPLAY.put(AspectId.parse("aer"), 10);
    }

    public ItemFocusAEWrench(Properties properties) {
        super(ItemStatePlatform.withDefault(properties.stacksTo(1), TCItemState.FOCUS, FocusData.EMPTY));
        ItemStatePlatform.bindDefaults(this, properties);
    }

    // ---- FocusIdentityItem：法杖袋/法杖载体身份（读写 thaumcraft:focus 组件，同 TC 原生 FocusItem） ----

    @Override
    public FocusId focusId(ItemStack stack) {
        return FOCUS_ID;
    }

    @Override
    public FocusStateView focusState(ItemStack stack) {
        FocusData data = stack.getOrDefault(TCItemState.FOCUS, FocusData.EMPTY);
        Map<ResourceLocation, String> props = new LinkedHashMap<>();
        data.properties().forEach((key, value) -> props.put(decodePropertyKey(key), value));
        return new FocusStateView(focusId(stack), data.upgradeSlots(), props);
    }

    @Override
    public ItemStack withFocusState(ItemStack stack, FocusStateView state) {
        if (!focusId(stack).equals(state.focusId())) {
            throw new IllegalArgumentException("Focus identity cannot change");
        }
        ItemStack copy = stack.copyWithCount(1);
        Map<String, String> props = new LinkedHashMap<>();
        state.properties().forEach((key, value) -> props.put(encodePropertyKey(key), value));
        copy.set(TCItemState.FOCUS, new FocusData(state.upgradeSlots(), props));
        return copy;
    }

    /** 序列化属性 key → ResourceLocation（与 TC FocusCarrierState.propertyId 同逻辑）。 */
    private static ResourceLocation decodePropertyKey(String key) {
        ResourceLocation rl = key.indexOf(':') >= 0
                ? ResourceLocation.tryParse(key)
                : ResourceLocation.tryBuild("thaumcraft", key);
        if (rl == null) {
            throw new IllegalArgumentException("Invalid focus property key: " + key);
        }
        return rl;
    }

    /** ResourceLocation → 序列化属性 key（与 TC FocusCarrierState.serializedPropertyKey 同逻辑）。 */
    private static String encodePropertyKey(ResourceLocation key) {
        return "thaumcraft".equals(key.getNamespace()) ? key.getPath() : key.toString();
    }

    // ---- 施法动作注册（FMLCommonSetupEvent 里调用） ----

    /**
     * 注册本核心的施法动作：法杖装核心后右键触发。
     * 老版 castFromWand 语义：必须 shift；射线命中 AE 方块才拆卸；先查 vis 再扣。
     */
    public static void registerFocusAction() {
        FocusActionApi.register(
                new FocusActionSpec(
                        ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "aewrench_disassemble"),
                        new FocusActionTarget.Focus(FOCUS_ID),
                        Set.of(FocusActivation.USE_AIR, FocusActivation.USE_BLOCK),
                        ResearchRequirement.none(),
                        24,
                        0,
                        FocusCastAnimation.INSTANT,
                        ActionPresentationAudience.CONFIRMED_ALL
                ),
                ItemFocusAEWrench::handleWrenchCast
        );
    }

    /**
     * 施法处理器。dispatch 会对同一动作调两次：SIMULATE（只检查支付/记账）+ EXECUTE（真实扣减并执行），
     * 因此 SIMULATE 阶段只做支付检查，EXECUTE 阶段才执行拆卸。
     */
    private static FocusActionResult handleWrenchCast(FocusActionContext ctx, VisAction visAction) {
        ServerPlayer serverPlayer = ctx.player();
        Player player = serverPlayer;
        if (!player.isShiftKeyDown()) {
            return FocusActionResult.PASS;
        }
        HitResult hit = ctx.authoritativeHit();
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return FocusActionResult.PASS;
        }
        Level level = serverPlayer.serverLevel();
        BlockEntity be = level.getBlockEntity(blockHit.getBlockPos());
        if (!(be instanceof AEBaseBlockEntity)) {
            return FocusActionResult.PASS;
        }

        // 等价老版 consumeVisCost(cost, false) → 不足提示并失败；consumeVisCost(cost, true) → 扣减
        VisPaymentResult payment = ctx.pay(CAST_COST, visAction);
        if (!payment.affordable()) {
            player.displayClientMessage(
                    Component.translatable("message.thaumcraft.wand.not_enough_vis"), true);
            return FocusActionResult.FAILED;
        }
        if (visAction != VisAction.EXECUTE) {
            return FocusActionResult.SUCCESS;
        }

        InteractionResult result = ((AEBaseBlockEntity) be)
                .disassembleWithWrench(player, level, blockHit, ctx.wand());
        if (result.consumesAction()) {
            spawnBeamParticles(level, player, blockHit.getBlockPos());
            return FocusActionResult.SUCCESS;
        }
        return FocusActionResult.PASS;
    }

    // ---- 物品行为 ----

    /** 另一手拿法杖时右键核心 → 装进法杖（同 TC 原生 FocusItem.use）。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack other = player.getItemInHand(otherHand);
        if (other.getItem() instanceof WandCarrierItem) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                FocusInstallResult result = FocusApi.install(serverPlayer, otherHand, stack, VisAction.EXECUTE);
                if (result != FocusInstallResult.INSTALLED) {
                    return InteractionResultHolder.fail(stack);
                }
                stack.shrink(1);
                level.playSound(null, player.blockPosition(),
                        SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.PLAYERS, 0.45f, 1.2f);
                player.swing(hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        MutableComponent costLine = Component.translatable("item.Focus.cost1")
                .append(":").withStyle(ChatFormatting.GRAY);
        for (var entry : COST_DISPLAY.entrySet()) {
            AspectId aspect = entry.getKey();
            costLine = costLine.append(Component.literal(
                            " " + entry.getValue() + " vis")
                    .withStyle(style -> style.withColor(aspectColor(aspect))));
        }
        tooltip.add(costLine);
        super.appendHoverText(stack, context, tooltip, flag);
    }

    private static int aspectColor(AspectId aspect) {
        AspectDefinition def = AspectApi.get(aspect);
        return def != null ? def.color() : 0xFFFFFF;
    }

    /** 光束粒子（老版 END_ROD 沿视线 0.5 步长；供施法动作与 WrenchFocusHandler 复用）。 */
    public static void spawnBeamParticles(Level level, Player player, BlockPos target) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 end = Vec3.atCenterOf(target);
        Vec3 delta = end.subtract(start);
        double distance = delta.length();
        if (distance < 0.5) {
            return;
        }
        Vec3 step = delta.normalize().scale(0.5);
        Vec3 current = start.add(step);
        int steps = (int) (distance / 0.5);
        for (int i = 0; i < steps; i++) {
            serverLevel.sendParticles(serverPlayer, ParticleTypes.END_ROD, true,
                    current.x, current.y, current.z, 1, 0.0, 0.0, 0.0, 0.0);
            current = current.add(step);
        }
    }

    /** shift+左键旋转（供 WrenchFocusHandler 调用）。返回 true = 方块可旋转且已执行。 */
    public static boolean rotateBlock(Level level, Player player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState rotated = state.rotate(level, pos, Rotation.CLOCKWISE_90);
        if (rotated != state) {
            level.setBlock(pos, rotated, 3);
            spawnBeamParticles(level, player, pos);
            return true;
        }
        return false;
    }
}
