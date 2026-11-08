package thaumicenergistics.mixins.tc;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.blockentities.AuraNodeBlockEntity;
import thaumcraft.common.blockentities.WandRechargePedestalBlockEntity;
import thaumicenergistics.common.items.ThaumicCrystalTools;

/** TC4 pedestal bridge; no Thaumaturge classes or passive inventory repair. */
@Mixin(value = WandRechargePedestalBlockEntity.class, remap = false)
public abstract class MixinRechargePedestalTools {
    @Shadow private List<BlockPos> nodes;
    @Shadow private boolean draining;
    @Shadow private BlockPos drainPos;
    @Shadow private int drainColor;

    @Inject(method = "canPlaceItem", at = @At("HEAD"), cancellable = true)
    private void the$acceptTool(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot == 0 && stack.getItem() instanceof ThaumicCrystalTools.Repairable) cir.setReturnValue(true);
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void the$repairTool(CallbackInfo ci) {
        var pedestal = (WandRechargePedestalBlockEntity) (Object) this;
        var level = pedestal.getLevel();
        var tool = pedestal.heldStack();
        if (level == null || level.isClientSide() || pedestal.counter() % 5 != 0
                || !(tool.getItem() instanceof ThaumicCrystalTools.Repairable) || !tool.isDamaged() || nodes == null) return;
        int repair = Math.min(10, tool.getDamageValue());
        int cost = repair * 10; // TC4 uses hundredths: 1 Vis restores 10 durability.
        for (BlockPos pos : nodes) {
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof AuraNodeBlockEntity node) || node.isEnergized()) continue;
            for (Aspect aspect : Aspect.getPrimalAspects()) {
                // Keep one unit, just like an ordinary wand on the native pedestal.
                if (node.getVis(aspect) < cost + 1) continue;
                int paid = node.takeVis(aspect, cost, 1);
                // Both operations run on the server thread; never repair more than was paid for.
                if (paid > 0) {
                    tool.setDamageValue(tool.getDamageValue() - Math.min(repair, paid / 10));
                    draining = true;
                    drainPos = pos.immutable();
                    drainColor = aspect.getColor();
                    pedestal.setChangedAndSync();
                    return;
                }
            }
        }
    }
}
