package thaumicenergistics.common.entities;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.common.entities.golem.ThaumcraftGolemEntity;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.items.ItemGolemWirelessBackpack;
import thaumicenergistics.init.ModItems;
import appeng.api.ids.AEComponents;
import net.minecraft.nbt.CompoundTag;
import thaumcraft.common.items.GolemBellItem;
import thaumicenergistics.common.network.GolemBackpackSyncPacket;

/**
 * 傀儡无线背包处理器：装备/拆卸/Facade 皮肤更换。
 * 对应 1.7.10 的 {@code WirelessGolemHandler} 中装备/Facade 交互部分。
 * 网络同步由 {@link GolemBackpackSyncPacket} 处理。
 */
@EventBusSubscriber(modid = ThaumicEnergistics.MODID)
public final class GolemBackpackHandler {

    private static final String NBT_KEY_LINK = "ThEWifiBackpackLink";
    /** NBT 键：背包皮肤序号（int） */
    private static final String NBT_KEY_SKIN = "ThEBackpackSkin";
    /** NBT 键：Facade 物品序列化 */
    private static final String NBT_KEY_FACADE = "ThEBackpackFacade";
    /** 物品侧打包键：GolemItems.saveGolem/loadGolem Mixin 携带三键的 CUSTOM_DATA 子树 */
    public static final String STACK_DATA_KEY = "ThEGolemBackpack";
    /** 子树内键：连接目标 */
    private static final String STACK_KEY_LINK = "Link";
    /** 子树内键：皮肤序号 */
    private static final String STACK_KEY_SKIN = "Skin";
    /** 子树内键：Facade 物品 */
    private static final String STACK_KEY_FACADE = "Facade";
    /** 无背包状态已同步标记（persistentData，防每 tick 重复发包） */
    private static final String NBT_KEY_NO_BACKPACK_SYNCED = "ThEWifiNoBackpackSynced";

    /**
     * 傀儡 UUID → 解码后的 home 位置缓存，避免每 tick 重复 GlobalPos.CODEC.parse NBT 解码。
     * 使用 WeakHashMap：golem 实体被 GC 后键自动回收，无泄漏；set/clear 时同步失效。
     */
    private static final Map<UUID, GlobalPos> LINK_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private GolemBackpackHandler() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ThaumcraftGolemEntity golem)) {
            return;
        }

        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());

        if (held.isEmpty()) {
            return;
        }

        // 1) 手持无线背包物品 → 装备背包
        if (held.getItem() == ModItems.GOLEM_WIFI_BACKPACK.get()) {
            if (event.getLevel().isClientSide()) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            handleEquipBackpack(golem, player, held, event);
            return;
        }

        // 2) 手持 Golem 铃铛 + 蹲下 → 拆卸背包
        if (held.getItem() instanceof GolemBellItem) {
            if (event.getLevel().isClientSide()) {
                return;
            }
            handleDismantleBackpack(golem, player, event);
            return;
        }

        // 3) 手持 AE2 Facade → 更换皮肤
        if (hasBackpack(golem)) {
            BackpackSkins skin = FacadeToSkinMapping.getSkinFromFacade(held);
            if (skin != null) {
                if (event.getLevel().isClientSide()) {
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                    return;
                }
                handleApplyFacade(golem, player, held, skin, event);
            }
        }
    }

    private static void handleEquipBackpack(ThaumcraftGolemEntity golem, Player player, ItemStack held,
                                            PlayerInteractEvent.EntityInteract event) {
        if (!golem.isOwnedBy(player)) {
            return;
        }

        if (hasBackpack(golem)) {
            return;
        }

        ItemGolemWirelessBackpack backpackItem = (ItemGolemWirelessBackpack) held.getItem();
        GlobalPos linkedPos = backpackItem.getLinkedPosition(held);
        if (linkedPos == null) {
            return;
        }

        setBackpackLink(golem, linkedPos);
        // 默认皮肤为 Thaumium
        setBackpackSkin(golem, BackpackSkins.Thaumium);
        golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 0.5F, 1.0F);

        if (!player.isCreative()) {
            held.shrink(1);
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static void handleDismantleBackpack(ThaumcraftGolemEntity golem, Player player,
                                                PlayerInteractEvent.EntityInteract event) {
        if (!player.isShiftKeyDown()) {
            return;
        }
        if (!golem.isOwnedBy(player)) {
            return;
        }

        if (!hasBackpack(golem)) {
            return;
        }

        dropAndClearBackpack(golem, player);

        // 卸载成功：终止铃铛后续绑定逻辑，防止在同一交互中误触发
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /**
     * 掉落背包与 Facade 附件并清除实体侧数据（服务端）。
     * 供铃铛拆卸事件与 Mixin packWithBell dismantle 分支共用；同步 STATUS_NO_BACKPACK
     * 使所有追踪玩家立即停止渲染背包。
     */
    public static void dropAndClearBackpack(ThaumcraftGolemEntity golem, Player player) {
        GlobalPos linkedPos = getBackpackLink(golem);
        if (linkedPos != null) {
            ItemStack backpackStack = new ItemStack(ModItems.GOLEM_WIFI_BACKPACK.get());
            backpackStack.set(AEComponents.WIRELESS_LINK_TARGET, linkedPos);
            golem.spawnAtLocation(backpackStack);
        }

        ItemStack oldFacade = getFacadeItem(golem);
        if (!oldFacade.isEmpty() && !player.isCreative()) {
            golem.spawnAtLocation(oldFacade);
        }

        clearBackpackLink(golem);
        clearBackpackSkin(golem);
        clearFacadeItem(golem);
        golem.getPersistentData().putBoolean(NBT_KEY_NO_BACKPACK_SYNCED, true);
        golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 0.5F, 1.0F);

        GolemBackpackSyncPacket packet = new GolemBackpackSyncPacket(
                golem.getId(), GolemBackpackSyncPacket.STATUS_NO_BACKPACK, 0);
        PacketDistributor.sendToPlayersTrackingEntity(golem, packet);
    }

    /**
     * 回收打包：将三键收拢进傀儡物品 CUSTOM_DATA 的 ThEGolemBackpack 子树。
     * 由 MixinGolemItems 在 saveGolem TAIL 调用；TC 白名单不覆盖 TE 字段，
     * 故在 TC 写完 ThaumcraftGolem 后补写本模组数据。
     */
    public static void saveBackpackToStack(ItemStack stack, ThaumcraftGolemEntity golem) {
        var entityData = golem.getPersistentData();
        if (!entityData.contains(NBT_KEY_LINK) && !entityData.contains(NBT_KEY_SKIN)
                && !entityData.contains(NBT_KEY_FACADE)) {
            return;
        }
        CompoundTag payload = new CompoundTag();
        if (entityData.contains(NBT_KEY_LINK)) {
            payload.put(STACK_KEY_LINK, entityData.get(NBT_KEY_LINK).copy());
        }
        if (entityData.contains(NBT_KEY_SKIN)) {
            payload.putInt(STACK_KEY_SKIN, entityData.getInt(NBT_KEY_SKIN));
        }
        if (entityData.contains(NBT_KEY_FACADE)) {
            payload.put(STACK_KEY_FACADE, entityData.get(NBT_KEY_FACADE).copy());
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                root -> root.put(STACK_DATA_KEY, payload));
    }

    /**
     * 放置恢复：从傀儡物品 ThEGolemBackpack 子树还原三键并失效解码缓存。
     * 由 MixinGolemItems 在 loadGolem TAIL 调用（PlacerItem 放置链）。
     */
    public static void restoreBackpackFromStack(ItemStack stack, ThaumcraftGolemEntity golem) {
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!root.contains(STACK_DATA_KEY, CompoundTag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag payload = root.getCompound(STACK_DATA_KEY);
        var entityData = golem.getPersistentData();
        if (payload.contains(STACK_KEY_LINK)) {
            entityData.put(NBT_KEY_LINK, payload.getCompound(STACK_KEY_LINK).copy());
        }
        if (payload.contains(STACK_KEY_SKIN)) {
            entityData.putInt(NBT_KEY_SKIN, payload.getInt(STACK_KEY_SKIN));
        }
        if (payload.contains(STACK_KEY_FACADE)) {
            entityData.put(NBT_KEY_FACADE, payload.getCompound(STACK_KEY_FACADE).copy());
        }
        LINK_CACHE.remove(golem.getUUID());
    }

    /**
     * 处理 Facade 皮肤更换。
     * 对应 1.7.10 的 {@code WirelessGolemHandler.customInteraction} 中的 Facade 分支。
     */
    private static void handleApplyFacade(ThaumcraftGolemEntity golem, Player player, ItemStack held,
                                          BackpackSkins skin, PlayerInteractEvent.EntityInteract event) {
        if (!golem.isOwnedBy(player)) {
            return;
        }

        ItemStack oldFacade = getFacadeItem(golem);
        if (!oldFacade.isEmpty() && !player.isCreative()) {
            golem.spawnAtLocation(oldFacade);
        }

        ItemStack facadeCopy = held.copy();
        facadeCopy.setCount(1);
        setFacadeItem(golem, facadeCopy);

        setBackpackSkin(golem, skin);

        if (!player.isCreative()) {
            held.shrink(1);
        }

        // 播放音效（对应 1.7.10 的 thaumcraft:cameraticks）
        golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 0.5F, 1.0F);

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    // ========== NBT 操作方法 ==========

    public static boolean hasBackpack(ThaumcraftGolemEntity golem) {
        return golem.getPersistentData().contains(NBT_KEY_LINK);
    }

    public static GlobalPos getBackpackLink(ThaumcraftGolemEntity golem) {
        var tag = golem.getPersistentData();
        if (!tag.contains(NBT_KEY_LINK)) {
            LINK_CACHE.remove(golem.getUUID());
            return null;
        }
        GlobalPos cached = LINK_CACHE.get(golem.getUUID());
        if (cached != null) {
            return cached;
        }
        var linkTag = tag.getCompound(NBT_KEY_LINK);
        GlobalPos decoded = GlobalPos.CODEC.parse(NbtOps.INSTANCE, linkTag).result().orElse(null);
        if (decoded != null) {
            LINK_CACHE.put(golem.getUUID(), decoded);
        }
        return decoded;
    }

    public static void setBackpackLink(ThaumcraftGolemEntity golem, GlobalPos pos) {
        var tag = golem.getPersistentData();
        GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos)
                .result().ifPresent(encoded -> tag.put(NBT_KEY_LINK, encoded));
        LINK_CACHE.put(golem.getUUID(), pos);
    }

    public static void clearBackpackLink(ThaumcraftGolemEntity golem) {
        golem.getPersistentData().remove(NBT_KEY_LINK);
        LINK_CACHE.remove(golem.getUUID());
    }

    public static BackpackSkins getBackpackSkin(ThaumcraftGolemEntity golem) {
        int ordinal = golem.getPersistentData().getInt(NBT_KEY_SKIN);
        return BackpackSkins.fromOrdinal(ordinal);
    }

    public static void setBackpackSkin(ThaumcraftGolemEntity golem, BackpackSkins skin) {
        golem.getPersistentData().putInt(NBT_KEY_SKIN, skin.ordinal());
    }

    /**
     * 清除背包皮肤 NBT。
     */
    public static void clearBackpackSkin(ThaumcraftGolemEntity golem) {
        golem.getPersistentData().remove(NBT_KEY_SKIN);
    }

    /**
     * 获取保存的 Facade 物品。
     */
    public static ItemStack getFacadeItem(ThaumcraftGolemEntity golem) {
        var tag = golem.getPersistentData();
        if (!tag.contains(NBT_KEY_FACADE)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parse(golem.level().registryAccess(), tag.getCompound(NBT_KEY_FACADE))
                .orElse(ItemStack.EMPTY);
    }

    /**
     * 保存 Facade 物品到 NBT。
     */
    public static void setFacadeItem(ThaumcraftGolemEntity golem, ItemStack facade) {
        var encoded = (CompoundTag) facade.save(golem.level().registryAccess());
        golem.getPersistentData().put(NBT_KEY_FACADE, encoded);
    }

    /**
     * 清除 Facade NBT。
     */
    public static void clearFacadeItem(ThaumcraftGolemEntity golem) {
        golem.getPersistentData().remove(NBT_KEY_FACADE);
    }
}
