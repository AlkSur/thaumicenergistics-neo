package tc_energy.common.integration.appeng;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import tc_energy.ThaumicEnergistics;

public final class AEssentiaKeyType extends AEKeyType {

    public static final ResourceLocation ID = ThaumicEnergistics.id("essentia");
    public static final AEssentiaKeyType INSTANCE = new AEssentiaKeyType();

    private AEssentiaKeyType() {
        super(ID, AEssentiaKey.class, Component.translatable("ae2.keytype.tc_energy.essentia"));
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
        // 显示单位换算因子（AE2 用它把网络存量按"每单位"折算显示；流体语义=1桶的mB数）。
        // 源质以 1 为单位显示（64 源质显示 64，不能返回 8 否则显示 8 / 0.875）。
        // 右键单容器存取不走 AE2 原生 EMPTY/FILL（其单次量依赖此值），
        // 由 EssentiaContainerScreenHandler + EssentiaContainerInteractC2SPacket 全接管。
        return 1;
    }
}