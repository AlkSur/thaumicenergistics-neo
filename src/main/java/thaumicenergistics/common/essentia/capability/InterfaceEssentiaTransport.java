package thaumicenergistics.common.essentia.capability;

import net.minecraft.core.Direction;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumicenergistics.common.integration.appeng.IEssentiaInterfaceLogic;

/**
 * TC endpoint of an ME interface's essentia logic. The interface block gets it by mixin; the cable-mounted
 * interface part, which takes the same essentia card, gets it through the cable bus capability (TEM-05).
 */
public final class InterfaceEssentiaTransport implements IEssentiaTransport {
    private final IEssentiaInterfaceLogic logic;

    public InterfaceEssentiaTransport(IEssentiaInterfaceLogic logic) {
        this.logic = logic;
    }

    @Override public boolean isConnectable(Direction side) { return true; }
    @Override public boolean canInputFrom(Direction side) { return logic.thE$essentiaActive(); }
    @Override public boolean canOutputTo(Direction side) { return logic.thE$essentiaActive(); }
    @Override public void setSuction(Aspect aspect, int amount) { }
    @Override public Aspect getSuctionType(Direction side) { return null; }
    @Override public int getSuctionAmount(Direction side) { return 0; }
    @Override public Aspect getEssentiaType(Direction side) { return logic.thE$stockAspect(); }

    @Override
    public int getEssentiaAmount(Direction side) {
        Aspect aspect = logic.thE$stockAspect();
        return aspect == null ? 0 : logic.thE$stockAmount(aspect);
    }

    @Override
    public int takeEssentia(Aspect aspect, int amount, Direction side) {
        return logic.thE$essentiaActive() ? logic.thE$takeFromStock(aspect, amount) : 0;
    }

    @Override
    public int addEssentia(Aspect aspect, int amount, Direction side) {
        return logic.thE$essentiaActive() ? logic.thE$acceptEssentia(aspect, amount) : 0;
    }

    @Override
    public int simulateTakeEssentia(Aspect aspect, int amount, Direction side) {
        return logic.thE$essentiaActive() ? logic.thE$simulateTakeFromStock(aspect, amount) : 0;
    }

    @Override
    public int simulateAddEssentia(Aspect aspect, int amount, Direction side) {
        return logic.thE$essentiaActive() ? logic.thE$simulateAcceptEssentia(aspect, amount) : 0;
    }

    @Override public int getMinimumSuction() { return 0; }
}
