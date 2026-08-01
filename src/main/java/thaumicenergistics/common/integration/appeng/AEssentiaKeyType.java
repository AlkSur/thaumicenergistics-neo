package thaumicenergistics.common.integration.appeng;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import thaumicenergistics.ThaumicEnergistics;

public final class AEssentiaKeyType extends AEKeyType {

    public static final ResourceLocation ID = ThaumicEnergistics.id("essentia");
    public static final AEssentiaKeyType INSTANCE = new AEssentiaKeyType();

    private AEssentiaKeyType() {
        super(ID, AEssentiaKey.class, Component.translatable("ae2.keytype.thaumicenergistics.essentia"));
    }

    @Override
    public MapCodec<? extends AEKey> codec() {
        return AEssentiaKey.MAP_CODEC;
    }


    public StreamCodec<RegistryFriendlyByteBuf, ? extends AEKey> streamCodec() {
        return AEssentiaKey.STREAM_CODEC;
    }

    @Override
    public AEKey readFromPacket(RegistryFriendlyByteBuf input) {
        return AEssentiaKey.fromPacket(input);
    }


    public String formatAmount(long amount, boolean large) {
        if (large) {
            if (amount >= 1000000) return String.format("%.1fM", amount / 1000000.0);
            if (amount >= 1000) return String.format("%.1fK", amount / 1000.0);
        }
        return Long.toString(amount);
    }

    @Override
    public int getAmountPerByte() {
        return 1;
    }

    @Override
    public int getAmountPerUnit() {
        return 1;
    }
}