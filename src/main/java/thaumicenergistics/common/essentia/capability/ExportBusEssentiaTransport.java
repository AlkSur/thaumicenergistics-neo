package thaumicenergistics.common.essentia.capability;

import appeng.api.config.Actionable;
import appeng.api.config.RedstoneMode;
import appeng.api.config.PowerMultiplier;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.parts.automation.ExportBusPart;
import net.minecraft.core.Direction;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.tc.AspectIds;
import thaumicenergistics.common.integration.tc.EssentiaEndpoints;
import thaumicenergistics.common.integration.tc.EssentiaConversionHelper;
import thaumicenergistics.init.ModItems;

/**
 * Sided cable-bus endpoint for pull-only TC consumers. Only advertises the export bus's
 * owned buffer, never the entire ME inventory. Resolve the part on every call so removal,
 * rotation and card changes cannot leave a cached capability pointing at an old part.
 */
public final class ExportBusEssentiaTransport implements IEssentiaTransport {
    private final CableBusBlockEntity host;
    private final Direction face;

    public ExportBusEssentiaTransport(CableBusBlockEntity host, Direction face) {
        this.host = host;
        this.face = face;
    }

    private ExportBusPart bus(Direction side) {
        if (side != face || host.isRemoved() || host.getLevel() == null || host.getLevel().isClientSide()
                || !(host.getPart(face) instanceof ExportBusPart bus)
                || !bus.getUpgrades().isInstalled(ModItems.ESSENTIA_UPGRADE_CARD.get())
                || !bus.getMainNode().isActive()) return null;
        var mode = bus.getRSMode();
        if (bus.getUpgrades().isInstalled(AEItems.REDSTONE_CARD)
                && ((mode == RedstoneMode.HIGH_SIGNAL && !host.hasRedstone())
                        || (mode == RedstoneMode.LOW_SIGNAL && host.hasRedstone()))) return null;
        // Pulse mode only exposes stock reserved by an authorized doBusWork invocation.
        return bus;
    }

    private AspectStack stock(Direction side) {
        var bus = bus(side);
        if (!(bus instanceof EssentiaTransferBuffer buffer)) return AspectStack.EMPTY;
        var pending = buffer.thE$getTransferBuffer();
        return !pending.isEmpty() && bus.getConfig().keySet().contains(AEssentiaKey.of(pending.aspectId()))
                ? pending : AspectStack.EMPTY;
    }

    /** Called only during the bus's normal, power/channel/redstone-controlled work tick. */
    public static boolean reserveForConsumer(ExportBusPart bus, AEssentiaKey key, int rate,
            net.minecraft.world.level.block.entity.BlockEntity consumer, Direction consumerSide,
            appeng.api.networking.IGrid grid, appeng.api.networking.security.IActionSource source) {
        if (!(bus instanceof EssentiaTransferBuffer buffer) || !buffer.thE$getTransferBuffer().isEmpty()) return false;
        var transport = EssentiaEndpoints.transport(consumer, consumerSide);
        var aspect = AspectIds.findAspectById(key.getId());
        if (aspect == null || transport == null || !transport.isConnectable(consumerSide)
                || !transport.canInputFrom(consumerSide) || transport.getSuctionAmount(consumerSide) <= 0) return false;
        var requested = transport.getSuctionType(consumerSide);
        if (requested != null && requested != aspect) return false;
        // Small, persisted stock replaces the intermediary jar without hoarding network resources.
        long extracted = grid.getStorageService().getInventory().extract(
                key, Math.min(rate, 8), Actionable.MODULATE, source);
        if (extracted <= 0) return false;
        buffer.thE$setTransferBuffer(new AspectStack(key.getId(), extracted));
        return true;
    }

    @Override public boolean isConnectable(Direction side) { return bus(side) != null; }
    @Override public boolean canInputFrom(Direction side) { return false; }
    @Override public boolean canOutputTo(Direction side) { return !stock(side).isEmpty(); }
    @Override public void setSuction(Aspect aspect, int amount) { }
    @Override public Aspect getSuctionType(Direction side) { return null; }
    @Override public int getSuctionAmount(Direction side) { return 0; }
    @Override public int getMinimumSuction() { return 0; }
    @Override public Aspect getEssentiaType(Direction side) {
        var stock = stock(side);
        return stock.isEmpty() ? null : AspectIds.findAspectById(stock.aspectId());
    }
    @Override public int getEssentiaAmount(Direction side) { return (int) Math.min(stock(side).amount(), Integer.MAX_VALUE); }
    @Override public int addEssentia(Aspect aspect, int amount, Direction side) { return 0; }

    @Override
    public int simulateTakeEssentia(Aspect aspect, int amount, Direction side) {
        if (aspect == null || amount <= 0) return 0;
        var bus = bus(side);
        var stock = stock(side);
        if (bus == null || stock.isEmpty() || aspect != AspectIds.findAspectById(stock.aspectId())) return 0;
        var grid = bus.getMainNode().getGrid();
        if (grid == null) return 0;
        long wanted = Math.min(amount, stock.amount());
        double power = grid.getEnergyService().extractAEPower(wanted * EssentiaConversionHelper.AE_PER_ESSENTIA,
                Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return (int) Math.min(wanted, (long) Math.floor((power + 1.0e-9) / EssentiaConversionHelper.AE_PER_ESSENTIA));
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, Direction side) {
        if (aspect == null || amount <= 0) return 0;
        var bus = bus(side);
        var stock = stock(side);
        if (bus == null || stock.isEmpty() || aspect != AspectIds.findAspectById(stock.aspectId())) return 0;
        var grid = bus.getMainNode().getGrid();
        if (grid == null) return 0;
        var buffer = (EssentiaTransferBuffer) bus;
        return (int) EssentiaEnergy.transfer(grid, Math.min(amount, stock.amount()), allowed -> {
            long remaining = stock.amount() - allowed;
            buffer.thE$setTransferBuffer(remaining == 0 ? AspectStack.EMPTY : new AspectStack(stock.aspectId(), remaining));
            return allowed;
        });
    }
}
