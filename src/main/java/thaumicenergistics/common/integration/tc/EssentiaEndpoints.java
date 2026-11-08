package thaumicenergistics.common.integration.tc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import thaumcraft.api.aspects.EssentiaCapabilities;
import thaumcraft.api.aspects.IAspectContainer;
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

    /** Local observation for provider blocking; this never grants extraction permission. */
    public static List<AspectStack> storedContents(BlockEntity be, @Nullable Direction side) {
        if (be instanceof IAspectContainer container) {
            var result = new ArrayList<AspectStack>();
            var aspects = container.getAspects();
            if (aspects != null) {
                for (var aspect : aspects.getAspects()) {
                    var id = AspectIds.getAspectId(aspect);
                    int amount = aspects.getAmount(aspect);
                    if (id != null && amount > 0) result.add(new AspectStack(id, amount));
                }
            }
            return result;
        }
        // The port's alchemical furnace has a separate, already-supported local cache.
        var legacy = TCReflection.listEssentia(be);
        if (!legacy.isEmpty()) return uniqueContents(legacy);
        side = accessSide(be, side);
        var view = transport(be, side);
        if (view == null || side == null) return List.of();
        var id = AspectIds.getAspectId(view.getEssentiaType(side));
        int amount = view.getEssentiaAmount(side);
        return id == null || amount <= 0 ? List.of() : List.of(new AspectStack(id, amount));
    }

    /** Exportable inventory for buses; consult the actual sided transfer simulation. */
    public static List<AspectStack> contents(BlockEntity be, @Nullable Direction side) {
        side = accessSide(be, side);
        var transport = transport(be, side);
        if (transport == null) return uniqueContents(TCReflection.listEssentia(be));
        if (side == null) {
            var result = new ArrayList<AspectStack>();
            for (var face : Direction.values()) result.addAll(contents(be, face));
            return uniqueContents(result);
        }
        if (!transport.isConnectable(side) || !transport.canOutputTo(side)) return List.of();
        var result = new ArrayList<AspectStack>();
        for (var stored : storedContents(be, side)) {
            var aspect = AspectIds.findAspectById(stored.aspectId());
            if (aspect == null) continue;
            int amount = Math.min((int) Math.min(stored.amount(), Integer.MAX_VALUE),
                    transport.simulateTakeEssentia(aspect, Integer.MAX_VALUE, side));
            if (amount > 0) result.add(new AspectStack(stored.aspectId(), amount));
        }
        return result;
    }

    private static List<AspectStack> uniqueContents(List<AspectStack> contents) {
        // Several faces can expose the same tank. Never sum those projections.
        var amounts = new LinkedHashMap<ResourceLocation, Long>();
        for (var stack : contents) amounts.merge(stack.aspectId(), stack.amount(), Math::max);
        return amounts.entrySet().stream().map(entry -> new AspectStack(entry.getKey(), entry.getValue())).toList();
    }

    @Nullable
    public static AspectStack extract(BlockEntity be, ResourceLocation id, int amount, @Nullable Direction side) {
        if (amount <= 0) return null;
        side = accessSide(be, side);
        var transport = transport(be, side);
        if (transport == null) return TCReflection.extractEssentia(be, id, amount);
        if (side == null) {
            for (var face : Direction.values()) {
                var extracted = extract(be, id, amount, face);
                if (extracted != null && !extracted.isEmpty()) return extracted;
            }
            return null;
        }
        var aspect = AspectIds.findAspectById(id);
        if (aspect == null || !transport.isConnectable(side) || !transport.canOutputTo(side)) return null;
        int allowed = Math.min(amount, transport.simulateTakeEssentia(aspect, amount, side));
        if (allowed <= 0) return null;
        int taken = Math.max(0, Math.min(allowed, transport.takeEssentia(aspect, allowed, side)));
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
