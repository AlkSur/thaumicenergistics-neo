package thaumicenergistics.common.features;

import appeng.api.features.P2PTunnelAttunement;
import thaumicenergistics.init.ModItems;

public class FeatureVisRelayInterface extends ThEFeatureBase {
    public FeatureVisRelayInterface() { super("vis_interface"); }

    @Override
    protected void doRegister() {
        P2PTunnelAttunement.registerAttunementTag(ModItems.VIS_INTERFACE.get());
    }

    @Override
    protected void doPostInit() {
    }
}
