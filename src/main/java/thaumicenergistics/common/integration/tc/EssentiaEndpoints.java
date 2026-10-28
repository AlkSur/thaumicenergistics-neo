package thaumicenergistics.common.integration.tc;

import java.util.List;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.EssentiaCapabilities;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.blockentities.JarBlockEntity;
import thaumicenergistics.api.storage.AspectStack;

/** TC 能力优先，固定版本的炼金炉等遗留容器由兼容回退处理。 */
public final class EssentiaEndpoints {
    private EssentiaEndpoints() {
    }

    /** ME accesses the jar's contents from any face; other machines retain their sided ports. */
    @Nullable
    public static Direction accessSide(BlockEntity be, @Nullable Direction side) {
        return be instanceof JarBlockEntity && side != null ? Direction.UP : side;
    }

    @Nullable
    public static IEssentiaTransport transport(BlockEntity be, @Nullable Direction side) {
        return be == null || be.getLevel() == null ? null
                : EssentiaCapabilities.find(be.getLevel(), be.getBlockPos(), side);
    }

    public static List<AspectStack> contents(BlockEntity be, @Nullable Direction side) {
        side = accessSide(be, side);
        var transport = transport(be, side);
        if (transport == null || side == null) return TCReflection.listEssentia(be);
        if (!transport.isConnectable(side) || !transport.canOutputTo(side)) return List.of();
        var id = AspectIds.getAspectId(transport.getEssentiaType(side));
        int amount = transport.getEssentiaAmount(side);
        return id == null || amount <= 0 ? List.of() : List.of(new AspectStack(id, amount));
    }

    @Nullable
    public static AspectStack extract(BlockEntity be, ResourceLocation id, int amount, @Nullable Direction side) {
        side = accessSide(be, side);
        var transport = transport(be, side);
        if (transport == null || side == null) return TCReflection.extractEssentia(be, id, amount);
        var aspect = AspectIds.findAspectById(id);
        if (aspect == null || !transport.isConnectable(side) || !transport.canOutputTo(side)) return null;
        int taken = Math.max(0, Math.min(amount, transport.takeEssentia(aspect, amount, side)));
        if (taken > 0) {
            be.setChanged();
            return new AspectStack(id, taken);
        }
        return null;
    }

    public static int insert(BlockEntity be, ResourceLocation id, int amount, @Nullable Direction side) {
        if (amount <= 0 || (be instanceof JarBlockEntity jar
                && !jar.doesContainerAccept(AspectIds.findAspectById(id)))) return 0;
        side = accessSide(be, side);
        var transport = transport(be, side);
        if (transport == null || side == null) {
            return TCReflection.insertEssentia(be, new AspectStack(id, amount));
        }
        var aspect = AspectIds.findAspectById(id);
        if (aspect == null || !transport.isConnectable(side) || !transport.canInputFrom(side)) return 0;
        int inserted = Math.max(0, Math.min(amount, transport.addEssentia(aspect, amount, side)));
        if (inserted > 0) be.setChanged();
        return inserted;
    }
}
