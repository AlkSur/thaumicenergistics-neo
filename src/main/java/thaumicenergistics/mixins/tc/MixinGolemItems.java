package thaumicenergistics.mixins.tc;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumcraft.common.entities.golem.ThaumcraftGolemEntity;
import thaumcraft.common.items.GolemItems;
import thaumicenergistics.common.entities.GolemBackpackHandler;

/**
 * 在 TC 傀儡打包/放置链路挂载无线背包数据通道。
 * saveGolem/loadGolem 均为白名单式读写且不含 TE 字段，SDK 无对应 API 扩展点，
 * 故 TAIL 注入补写/还原 ThEGolemBackpack 子树。
 */
@Mixin(GolemItems.class)
public abstract class MixinGolemItems {

    @Inject(method = "saveGolem", at = @At("TAIL"))
    private static void te$saveBackpackData(ItemStack stack, ThaumcraftGolemEntity golem, CallbackInfo ci) {
        GolemBackpackHandler.saveBackpackToStack(stack, golem);
    }

    @Inject(method = "loadGolem", at = @At("TAIL"))
    private static void te$restoreBackpackData(ItemStack stack, ThaumcraftGolemEntity golem, CallbackInfo ci) {
        GolemBackpackHandler.restoreBackpackFromStack(stack, golem);
    }
}
