package thaumicenergistics.mixins.tc;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumcraft.common.entities.golem.ThaumcraftGolemEntity;
import thaumicenergistics.common.entities.GolemBackpackHandler;

/**
 * 铃铛 shift+左键拆解（dismantle=true）只按散件返还，不含无线背包。
 * TAIL 注入在 discard 前补掉背包与 Facade 并清数据；普通回收（dismantle=false）
 * 的数据保全由 MixinGolemItems 的 saveGolem TAIL 承担，两路不重叠。
 */
@Mixin(ThaumcraftGolemEntity.class)
public abstract class MixinThaumcraftGolemEntity {

    @Inject(method = "packWithBell", at = @At("TAIL"))
    private void te$dropBackpackOnDismantle(Player player, boolean dismantle, CallbackInfo ci) {
        if (!dismantle) {
            return;
        }
        GolemBackpackHandler.dropAndClearBackpack((ThaumcraftGolemEntity) (Object) this, player);
    }
}
