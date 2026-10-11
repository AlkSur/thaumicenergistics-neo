package thaumicenergistics.common.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import thaumicenergistics.common.entities.BackpackSkins;
import thaumcraft.common.entities.golem.ThaumcraftGolemEntity;

import java.util.Map;
import java.util.WeakHashMap;
import thaumicenergistics.client.render.layer.GolemBackpackLayer;

/**
 * 客户端侧缓存：存储每个傀儡的背包连接状态和皮肤。
 *
 * 由 {@link GolemBackpackSyncPacket#handleOnClient} 写入，
 * 由 {@link GolemBackpackLayer} 读取。
 *
 * 使用 WeakHashMap 以避免实体被 GC 后内存泄漏。
 *
 * 对应 1.7.10 的 {@code WirelessClientData}，但简化为静态缓存而非挂在 handlerData 上。
 */
@OnlyIn(Dist.CLIENT)
public final class GolemBackpackClientData {

    private GolemBackpackClientData() {}

    /** 傀儡 → 连接状态 (0=无背包, 1=在范围内, 2=超出范围) */
    private static final Map<ThaumcraftGolemEntity, Integer> connectionStatusMap = new WeakHashMap<>();

    /** 傀儡 → 皮肤 */
    private static final Map<ThaumcraftGolemEntity, BackpackSkins> skinMap = new WeakHashMap<>();

    /**
     * 更新傀儡的背包数据（由网络包处理器调用）。
     */
    public static void update(ThaumcraftGolemEntity golem, int connectionStatus, int skinOrdinal) {
        if (connectionStatus == GolemBackpackSyncPacket.STATUS_NO_BACKPACK) {
            connectionStatusMap.remove(golem);
            skinMap.remove(golem);
        } else {
            connectionStatusMap.put(golem, connectionStatus);
            skinMap.put(golem, BackpackSkins.fromOrdinal(skinOrdinal));
        }
    }

    /**
     * 获取傀儡的连接状态。
     *
     * @return 0=无背包, 1=在范围内, 2=超出范围
     */
    public static int getConnectionStatus(ThaumcraftGolemEntity golem) {
        return connectionStatusMap.getOrDefault(golem, GolemBackpackSyncPacket.STATUS_NO_BACKPACK);
    }

    /**
     * 傀儡是否装备了背包且在 AE 网络范围内。
     */
    public static boolean isInRange(ThaumcraftGolemEntity golem) {
        return getConnectionStatus(golem) == GolemBackpackSyncPacket.STATUS_IN_RANGE;
    }

    /**
     * 傀儡是否装备了背包（无论是否在范围内）。
     */
    public static boolean hasBackpack(ThaumcraftGolemEntity golem) {
        return getConnectionStatus(golem) != GolemBackpackSyncPacket.STATUS_NO_BACKPACK;
    }

    /**
     * 获取傀儡的背包皮肤。
     */
    public static BackpackSkins getSkin(ThaumcraftGolemEntity golem) {
        return skinMap.getOrDefault(golem, BackpackSkins.Thaumium);
    }
}
