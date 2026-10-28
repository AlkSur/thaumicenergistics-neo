package thaumicenergistics.common.integration.appeng;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import thaumcraft.api.aspects.Aspect;

public final class AEssentiaKey extends AEKey {

    /** id → key 实例的缓存；AEssentiaKey 不可变，且 equals/hashCode 基于 id。 */
    private static final Map<ResourceLocation, AEssentiaKey> CACHE = new ConcurrentHashMap<>();

    /**
     * 规范化 id 实例：所有同 id 的 AEssentiaKey 共享同一 ResourceLocation 实例。
     * AE2 的 KeyCounter 内部按 {@code getPrimaryKey()} 的引用相等分组（Reference2ObjectMap），
     * 存储（NBT 反序列化）与 config（TCReflection 生成）的 id 若不共享实例会导致 AE2 tooltip 去重失败（同方面重复显示）。
     */
    private static final Map<ResourceLocation, ResourceLocation> ID_CACHE = new ConcurrentHashMap<>();

    public static final MapCodec<AEssentiaKey> MAP_CODEC = RecordCodecBuilder.mapCodec(
            builder -> builder.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(AEssentiaKey::getId)
            ).apply(builder, AEssentiaKey::new)
    );

    public static final Codec<AEssentiaKey> CODEC = MAP_CODEC.codec();

    public static final StreamCodec<RegistryFriendlyByteBuf, AEssentiaKey> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, AEssentiaKey::getId,
                    AEssentiaKey::new
            );

    private final ResourceLocation id;

    @Override
    protected Component computeDisplayName() {
        // 使用 TC 源质的实际显示名称（如 "Aer" / "风"），而非翻译键
        String tag = id.getPath();
        if (Aspect.aspects != null) {
            for (Aspect aspect : Aspect.aspects.values()) {
                if (aspect != null && aspect.getTag() != null && aspect.getTag().equalsIgnoreCase(tag)) {
                    return aspect.displayName();
                }
            }
        }
        // 回退：翻译键
        return Component.translatable("aspect.thaumicenergistics." + tag);
    }


    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        // AE2 的包装物品保留精确的 long 数量，可用容器取回或在终端抽空。
        if (amount > 0) drops.add(GenericStack.wrapInItemStack(this, amount));
    }

    @Override
    public boolean hasComponents() {
        return false;
    }

    public AEssentiaKey(ResourceLocation id) {
        this.id = ID_CACHE.computeIfAbsent(id, k -> k);
    }

    @Override
    public AEKeyType getType() {
        return AEssentiaKeyType.INSTANCE;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public CompoundTag toTag(HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        return (CompoundTag) CODEC.encodeStart(ops, this).getOrThrow();
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public void writeToPacket(RegistryFriendlyByteBuf data) {
        ResourceLocation.STREAM_CODEC.encode(data, id);
    }

    public static AEssentiaKey fromPacket(RegistryFriendlyByteBuf data) {
        return new AEssentiaKey(ResourceLocation.STREAM_CODEC.decode(data));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AEssentiaKey that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AEssentiaKey{" + id + "}";
    }

    @Override
    public Object getPrimaryKey(){
        return id;
    }


    public static AEssentiaKey of(ResourceLocation id) {
        return CACHE.computeIfAbsent(id, AEssentiaKey::new);
    }
}
