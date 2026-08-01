package thaumicenergistics.common.features;

/** 功能：无线傀儡背包。启用傀儡访问 AE 网络。 */
public class FeatureGolemBackpack extends ThEFeatureBase {
    public FeatureGolemBackpack() { super("golem_backpack"); }

    @Override
    protected void doRegister() {
        // GridLinkables 注册在 commonSetup 阶段完成（需要物品已绑定）
    }
}