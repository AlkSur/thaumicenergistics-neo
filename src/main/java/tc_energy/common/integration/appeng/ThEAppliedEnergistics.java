package tc_energy.common.integration.appeng;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
public final class ThEAppliedEnergistics {
    private static final Logger LOG = LoggerFactory.getLogger("ThE-AE2");
    private ThEAppliedEnergistics() {}
    public static void init() {
        // AE2 官方容器物品策略：让 AE2 原生识别源质安瓿/罐子
        // （替代旧 MixinConfigMenuInventory / MixinMEStorageScreen / MixinWCWTScreen）
        EssentiaContainerItemStrategy.register();
        LOG.info("ThaumicEnergistics AE2 integration initialized (essentia container strategy registered).");
    }
}
