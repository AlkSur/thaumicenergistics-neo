package thaumicenergistics.mixins.ae2.bus;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.stacks.AEKeyType;
import appeng.parts.AEBasePart;
import appeng.parts.automation.IOBusPart;
import appeng.parts.automation.UpgradeablePart;
import appeng.api.parts.IPartItem;
import appeng.api.stacks.GenericStack;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;

import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.common.essentia.capability.EssentiaTransferBuffer;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;

/**
 * 在导入/导出总线共用父类追加源质过滤类型和持久化余量缓冲。
 * 余量由宿主拥有，拆除时保存为可回收物品；原版升级卡和 NBT 保持不变。
 */
@Mixin(IOBusPart.class)
public abstract class MixinIOBusPart extends UpgradeablePart implements EssentiaTransferBuffer {

    protected MixinIOBusPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Unique
    private AspectStack thE$transferBuffer = AspectStack.EMPTY;

    // IOBusPart 未声明这两个方法，覆写并调用父类，避免向继承方法注入导致字段作用域错误。
    @Override
    public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        super.addAdditionalDrops(drops, wrenched);
        if (!thE$transferBuffer.isEmpty()) {
            drops.add(GenericStack.wrapInItemStack(AEssentiaKey.of(thE$transferBuffer.aspectId()),
                    thE$transferBuffer.amount()));
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        thE$transferBuffer = AspectStack.EMPTY;
    }

    /**
     * 把源质键类型补进原版 IO 总线 config 的支持类型集合（只增不减）。
     * <p>
     * 目标调用：{@code IOBusPart.<init>} 内
     * {@code ConfigInventory.configTypes(63).supportedTypes(supportedKeyTypes)}（IOBusPart.java:96-97），
     * 字节码为 {@code ConfigInventory$Builder.supportedTypes(Ljava/util/Collection;)}。
     * 源集合来自 {@code StackWorldBehaviors.withImportStrategy()/withExportStrategy()}（仅 item/fluid），
     * 本模组未给源质注册搬运策略，故须在此追加 {@link AEssentiaKeyType#INSTANCE}。
     */
    @ModifyArg(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lappeng/util/ConfigInventory$Builder;supportedTypes(Ljava/util/Collection;)Lappeng/util/ConfigInventory$Builder;"),
            index = 0)
    private Collection<AEKeyType> thE$addEssentiaKeyType(Collection<AEKeyType> supportedTypes) {
        if (supportedTypes.contains(AEssentiaKeyType.INSTANCE)) {
            return supportedTypes;
        }
        Collection<AEKeyType> extended = new HashSet<>(supportedTypes);
        extended.add(AEssentiaKeyType.INSTANCE);
        return extended;
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void thE$readEssentiaBuffer(CompoundTag extra, HolderLookup.Provider registries, CallbackInfo ci) {
        this.thE$transferBuffer = thE$readBuffer(extra);
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void thE$writeEssentiaBuffer(CompoundTag extra, HolderLookup.Provider registries, CallbackInfo ci) {
        thE$writeBuffer(extra, this.thE$transferBuffer);
    }

    @Override
    public AspectStack thE$getTransferBuffer() {
        return this.thE$transferBuffer;
    }

    @Override
    public void thE$setTransferBuffer(AspectStack buffer) {
        this.thE$transferBuffer = buffer == null ? AspectStack.EMPTY : buffer;
        ((AEBasePart) (Object) this).getHost().markForSave();
    }

    @Unique
    private AspectStack thE$readBuffer(CompoundTag extra) {
        if (extra.getLong(EssentiaTransferBuffer.NBT_KEY_AMOUNT) <= 0) {
            return AspectStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(extra.getString(EssentiaTransferBuffer.NBT_KEY_ASPECT));
        return id == null
                ? AspectStack.EMPTY
                : new AspectStack(id, extra.getLong(EssentiaTransferBuffer.NBT_KEY_AMOUNT));
    }

    @Unique
    private void thE$writeBuffer(CompoundTag extra, AspectStack buffer) {
        if (buffer == null || buffer.isEmpty()) {
            extra.remove(EssentiaTransferBuffer.NBT_KEY_ASPECT);
            extra.remove(EssentiaTransferBuffer.NBT_KEY_AMOUNT);
            return;
        }
        extra.putString(EssentiaTransferBuffer.NBT_KEY_ASPECT, buffer.aspectId().toString());
        extra.putLong(EssentiaTransferBuffer.NBT_KEY_AMOUNT, buffer.amount());
    }
}
