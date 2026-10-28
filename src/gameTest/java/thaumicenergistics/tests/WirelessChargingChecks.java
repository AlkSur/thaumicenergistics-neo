package thaumicenergistics.tests;

import java.lang.reflect.Proxy;
import java.util.List;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.ILinkStatus;
import appeng.api.upgrades.Upgrades;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.locator.ItemMenuHostLocator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.items.TCFunctionalItems;
import thaumicenergistics.common.items.WirelessArcaneCraftingTerminalItem;
import thaumicenergistics.common.items.WirelessArcaneCraftingTerminalMenuHost;
import thaumicenergistics.common.parts.VisInterfacePart;
import thaumicenergistics.common.parts.WirelessArcaneChargingHandler;
import thaumicenergistics.init.ModComponents;
import thaumicenergistics.init.ModItems;

/** 隔离无线链路之外的世界搭建，验证真实玩家 tick、法杖组件及卡片门控。 */
final class WirelessChargingChecks {
    private static final net.neoforged.neoforge.registries.DeferredRegister.Items ITEMS =
            net.neoforged.neoforge.registries.DeferredRegister.createItems("thaumicenergistics_tests");
    private static final net.neoforged.neoforge.registries.DeferredItem<TestTerminal> TERMINAL =
            ITEMS.registerItem("charging_fixture", TestTerminal::new);
    private static IGridNode chargingNode;
    private static boolean connected;

    static void register(net.neoforged.bus.api.IEventBus bus) { ITEMS.register(bus); }

    static void emptyTerminals(GameTestHelper helper) {
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "EmptyTerminalTest"),
                net.minecraft.server.level.ClientInformation.createDefault());
        var item = TERMINAL.get();
        Upgrades.add(ModItems.VIS_CONNECTOR_CARD.get(), item, 1);
        // Vanilla AE2 terminals also pass through the background charging handler.
        player.getInventory().setItem(1, appeng.core.definitions.AEItems.WIRELESS_TERMINAL.stack());
        player.getInventory().setItem(2, appeng.core.definitions.AEItems.WIRELESS_CRAFTING_TERMINAL.stack());
        for (boolean withCard : new boolean[]{false, true}) {
            var terminal = new ItemStack(item);
            if (withCard) item.getUpgrades(terminal).addItems(new ItemStack(ModItems.VIS_CONNECTOR_CARD.get()));
            player.getInventory().setItem(0, terminal);
            WirelessArcaneChargingHandler.onPlayerTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(!terminal.has(ModComponents.WAND_INV.get()),
                    "A missing wand component must be skipped without changing the item");
            terminal.set(ModComponents.WAND_INV.get(), ItemContainerContents.EMPTY);
            WirelessArcaneChargingHandler.onPlayerTick(new PlayerTickEvent.Post(player));
            // Removing the last wand trims the serialized component to zero slots.
            terminal.set(ModComponents.WAND_INV.get(), ItemContainerContents.fromItems(List.of(ItemStack.EMPTY)));
            var restored = ItemStack.parse(helper.getLevel().registryAccess(),
                    terminal.save(helper.getLevel().registryAccess())).orElseThrow();
            player.getInventory().setItem(0, restored);
            WirelessArcaneChargingHandler.onPlayerTick(new PlayerTickEvent.Post(player));
            helper.assertTrue(restored.getOrDefault(ModComponents.WAND_INV.get(), ItemContainerContents.EMPTY)
                    .equals(ItemContainerContents.EMPTY), "An empty wand component must survive reload and tick");
        }
        player.discard();
    }

    private static class TestTerminal extends WirelessArcaneCraftingTerminalItem {
        TestTerminal(Item.Properties properties) { super(() -> 10000, properties); }

        @Override
        public WirelessTerminalMenuHost<?> getMenuHost(Player holder, ItemMenuHostLocator locator, BlockHitResult hit) {
            return new WirelessArcaneCraftingTerminalMenuHost(this, holder, locator, (p, menu) -> { }) {
                @Override public IGridNode getActionableNode() { return chargingNode; }
                @Override public ILinkStatus getLinkStatus() {
                    return connected ? ILinkStatus.ofConnected() : ILinkStatus.ofDisconnected();
                }
            };
        }
    }

    static void run(GameTestHelper helper) {
        // 不触发登录同步；无协议握手的 GameTest 客户端无法接收 TC 研究数据负载。
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "WirelessChargeTest"),
                net.minecraft.server.level.ClientInformation.createDefault());
        connected = true;
        var source = new VisInterfacePart(ModItems.VIS_INTERFACE.get()) {
            @Override
            public int consumeVis(net.minecraft.resources.ResourceLocation aspect, int amount) {
                return amount;
            }
        };
        var grid = (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getMachines")) return java.util.Set.of(source);
                    throw new UnsupportedOperationException(method.getName());
                });
        var node = (IGridNode) Proxy.newProxyInstance(IGridNode.class.getClassLoader(), new Class<?>[]{IGridNode.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getGrid" -> grid;
                    case "isActive" -> true;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        chargingNode = node;
        var terminalItem = TERMINAL.get();
        Upgrades.add(ModItems.VIS_CONNECTOR_CARD.get(), terminalItem, 1);
        var terminal = new ItemStack(terminalItem);
        var wandItem = BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof TCFunctionalItems.WandCastingItem).findFirst().orElseThrow();
        var wand = new ItemStack(wandItem);
        var casting = (TCFunctionalItems.WandCastingItem) wandItem;
        terminal.set(ModComponents.WAND_INV.get(), ItemContainerContents.fromItems(List.of(wand)));
        player.getInventory().setItem(0, terminal);
        // 不打开终端菜单，验证后台 tick 对保存的法杖组件生效。
        WirelessArcaneChargingHandler.onPlayerTick(new PlayerTickEvent.Post(player));
        helper.assertTrue(vis(terminal, casting) == 0, "No Vis card must mean no passive charge");
        helper.assertTrue(terminalItem.getUpgrades(terminal).addItems(new ItemStack(ModItems.VIS_CONNECTOR_CARD.get())).isEmpty(),
                "Vis card must install");
        WirelessArcaneChargingHandler.onPlayerTick(new PlayerTickEvent.Post(player));
        helper.assertTrue(vis(terminal, casting) == 5, "Closed terminal charges and persists five centivis per aspect");
        connected = false;
        WirelessArcaneChargingHandler.onPlayerTick(new PlayerTickEvent.Post(player));
        helper.assertTrue(vis(terminal, casting) == 5, "Disconnected terminal must stop passive charging");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        player.discard();
        chargingNode = null;
    }

    private static int vis(ItemStack terminal, TCFunctionalItems.WandCastingItem casting) {
        return casting.getVis(terminal.get(ModComponents.WAND_INV.get()).getStackInSlot(0), Aspect.AIR);
    }
}
