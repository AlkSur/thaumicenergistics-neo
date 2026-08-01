package thaumicenergistics.mixins.ae2;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigMenuInventory;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;
import thaumicenergistics.common.integration.tc.TCReflection;

/**
 * Mixin ConfigMenuInventory：在 AE2 原有的 ItemStack→AEKey 转换失败后，
 * 追加 Thaumcraft 源质容器物品的检测。
 *
 * 当玩家把装有源质的罐子（{@code thaumcraft:jar_of_essentia}）或药水
 * 拖入 AE 设备的筛选槽时，AE2 原生逻辑不认识这类物品，返回 null。
 * 此 Mixin 在原方法返回 null 后拦截，检测物品是否为
 * {@link IEssentiaContainerItem}，若则提取其中的源质转为 {@link AEssentiaKey}。
 *
 * 这使得在没有 JEI 的情况下，玩家也能通过拖拽源质罐子到筛选槽来设置源质筛选。
 */
@Mixin(ConfigMenuInventory.class)
public abstract class MixinConfigMenuInventory {

    @Shadow(remap = false)
    @Final
    private GenericStackInv inv;

    /**
     * 在 convertToSuitableStack 返回 null 时拦截，
     * 检测物品是否为 Thaumcraft 源质容器并转换。
     */
    @Inject(
            method = "convertToSuitableStack",
            at = @At("RETURN"),
            cancellable = true
    )
    private void convertEssentiaContainer(ItemStack stack,
                                          CallbackInfoReturnable<@Nullable GenericStack> cir) {
        // 如果 AE2 原生逻辑已经成功转换，不做任何事
        if (cir.getReturnValue() != null) {
            return;
        }

        // 空栈无需处理
        if (stack == null || stack.isEmpty()) {
            return;
        }

        // 检查底层 ConfigInventory 是否支持源质类型
        if (!this.inv.isSupportedType(AEssentiaKeyType.INSTANCE)) {
            return;
        }

        // 检测是否为 Thaumcraft 源质容器物品
        if (stack.getItem() instanceof IEssentiaContainerItem containerItem) {
            AspectList aspects = containerItem.getAspects(stack);
            if (aspects == null || aspects.size() == 0) {
                return;
            }

            // 取第一个源质（罐子通常只装一种）
            Aspect firstAspect = aspects.getAspects()[0];
            if (firstAspect == null) {
                return;
            }

            var aspectId = TCReflection.getAspectId(firstAspect);
            if (aspectId == null) {
                return;
            }

            AEssentiaKey key = AEssentiaKey.of(aspectId);
            long amount = aspects.getAmount(firstAspect);
            cir.setReturnValue(new GenericStack(key, amount));
        }
    }
}
