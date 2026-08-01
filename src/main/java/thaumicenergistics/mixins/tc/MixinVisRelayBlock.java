package thaumicenergistics.mixins.tc;

import appeng.block.networking.CableBusBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TC4 的 {@code VisRelayBlock} 是壁挂方块：其 {@code canSurvive} 仅当附着面
 * 是坚固面（{@code isFaceSturdy}）时才返回 true。AE2 线缆总线不坚固，
 * 因此 vis 中继无法挂在线缆上——这使它无法直接放在 ThE Vis Interface（线缆部件）上方。
 *
 * 此 mixin 允许 AE2 {@link CableBusBlock} 作为有效支撑，使中继可放置在
 * Vis Interface 旁边。实际 vis 网络连接仍由 {@code VisInterfacePart.getRelay()} 决定，
 * 仅当中继真正面对 Vis Interface 部件时才建立连接。
 */
@Mixin(targets = "thaumcraft.common.blocks.VisRelayBlock", remap = false)
public abstract class MixinVisRelayBlock {

    @Shadow
    public static DirectionProperty FACING;

    @Inject(method = "canSurvive", at = @At("RETURN"), cancellable = true, remap = false)
    private void thE_allowCableSupport(BlockState state, LevelReader level, BlockPos pos,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return;
        }
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        Block supportBlock = level.getBlockState(support).getBlock();
        if (supportBlock instanceof CableBusBlock) {
            cir.setReturnValue(true);
        }
    }
}
