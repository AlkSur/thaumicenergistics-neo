package thaumicenergistics.common.integration.appeng;

/** Marks an existing interface inventory without replacing its storage semantics. */
public interface InterfaceEssentiaInventory {
    long MAX_ESSENTIA_PER_SLOT = 256;

    /** Must not enumerate slots: an inventory may be a live network proxy. */
    void thE$markInterfaceInventory();
}