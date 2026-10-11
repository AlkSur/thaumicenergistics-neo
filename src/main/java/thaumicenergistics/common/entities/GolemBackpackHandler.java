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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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

        GlobalPos linkedPos = getBackpackLink(golem);
        if (linkedPos == null) {
            return;
        }

        ItemStack backpackStack = new ItemStack(ModItems.GOLEM_WIFI_BACKPACK.get());
        backpackStack.set(AEComponents.WIRELESS_LINK_TARGET, linkedPos);
        golem.spawnAtLocation(backpackStack);

        // 丢弃旧 Facade（如果有）
        ItemStack oldFacade = getFacadeItem(golem);
        if (!oldFacade.isEmpty() && !player.isCreative()) {
            golem.spawnAtLocation(oldFacade);
        }

        clearBackpackLink(golem);
        clearBackpackSkin(golem);
        clearFacadeItem(golem);
        golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 0.5F, 1.0F);
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
