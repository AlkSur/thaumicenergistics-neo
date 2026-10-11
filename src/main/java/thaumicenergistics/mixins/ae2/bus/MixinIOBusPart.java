package thaumicenergistics.mixins.ae2.bus;

import java.util.Collection;
import java.util.HashSet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import appeng.api.stacks.AEKeyType;
import appeng.parts.AEBasePart;
import appeng.parts.automation.IOBusPart;

import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.common.essentia.capability.EssentiaTransferBuffer;
import thaumicenergistics.common.integration.appeng.AEssentiaKeyType;

/**
 * 给 AE2 <b>原版</b>输入/输出总线提供两件事（导入/导出总线共用父类 {@code IOBusPart}，一处覆盖两条总线）：
 * <ul>
 *   <li><b>源质键类型支持</b>（T-M1-011，P2）：{@code @ModifyArg} 把宿主 config 的键类型集合扩入源质键类型，
 *       使 IO 总线的<b>原生过滤槽</b>可容纳源质键（物品/流体/源质共存一格），JEI 拖拽由此天然可用；</li>
 *   <li><b>抽取缓冲</b>（T-M1-007-⑦a）：在父类 {@code readFromNBT}/{@code writeToNBT} 末尾追加缓冲 NBT 键
 *       （{@link EssentiaTransferBuffer#NBT_KEY_ASPECT}），承载「已从容器抽出但未入网」的残留源质。</li>
 * </ul>
 * <p>
 * <b>非纯 additive 说明</b>：{@code @ModifyArg} 修改的是原版构造器传给
 * {@code ConfigInventory.Builder.supportedTypes(Collection)} 的键类型集合——<b>只增不减</b>
 * （在原有集合上追加源质键类型；已含则原样返回）。这是 T-M1-011 唯一的非纯 additive 注入；
 * 其目标 {@code IOBusPart} 构造器是本模组三总线与两条原版总线的共同父类，故会同时惠及它们（可接受）。
 * <p>
 * <b>T-M1-011 退役项</b>：原「源质过滤 NBT」（{@code ThEEssentiaFilterAspects}）与鸭子接口
 * {@code EssentiaFilterHolder} 已随本任务删除——过滤数据改由宿主<b>原生 config inventory</b> 持久化，
 * 本节不再承载过滤，只保留抽取缓冲。
 * <p>
 * 抽取缓冲必须落在 {@code IOBusPart} 层（而非子类的 Mixin）：{@code ImportBusPart} /
 * {@code ExportBusPart} 都不覆写 {@code readFromNBT}/{@code writeToNBT}，若在子类 Mixin 里注入会解析到
 * 父类方法，从而在本模组三总线实例上执行（字段却只并入子类）→ 运行期 {@code NoSuchFieldError}。
 * <p>
 * 说明：本模组自有源质三总线亦继承 {@code IOBusPart}，同样会获得该实现——但从不产生缓冲，
 * 键恒空（写入时会被移除），行为与改动前一致；且二者的键与本模组三总线自身的缓冲键
 * （{@code ThEBufferAspect}/{@code ThEBufferAmount}）不同，互不干扰。
 */
@Mixin(IOBusPart.class)
public abstract class MixinIOBusPart implements EssentiaTransferBuffer {

    @Unique
    private AspectStack thE$transferBuffer = AspectStack.EMPTY;

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
