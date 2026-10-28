package thaumicenergistics.common.integration.appeng;

import java.util.function.Supplier;
import appeng.helpers.externalstorage.GenericStackInv;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;


import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;

import thaumcraft.api.aspects.Aspect;

import thaumicenergistics.api.storage.AspectStack;
import thaumicenergistics.common.integration.appeng.AEssentiaKey;
import thaumicenergistics.common.integration.appeng.IEssentiaInterfaceLogic;
import thaumicenergistics.common.integration.tc.AspectIds;
import thaumicenergistics.init.ModItems;


/** ME 接口的源质业务状态；Mixin 仅负责转发宿主字段和生命周期。 */
public final class EssentiaInterfaceState implements IEssentiaInterfaceLogic {
    private final Supplier<MEStorage> networkStorage;
    private final Supplier<GenericStackInv> storage;
    private final Supplier<IUpgradeInventory> upgrades;
    private final Supplier<IActionSource> actionSource;
    private final Supplier<IGridNode> node;
    private AspectStack thE$essentiaBuffer = AspectStack.EMPTY;

    public EssentiaInterfaceState(Supplier<MEStorage> networkStorage, Supplier<GenericStackInv> storage,
            Supplier<IUpgradeInventory> upgrades, Supplier<IActionSource> actionSource, Supplier<IGridNode> node) {
        this.networkStorage = networkStorage;
        this.storage = storage;
        this.upgrades = upgrades;
        this.actionSource = actionSource;
        this.node = node;
    }

    private boolean thE$hasEssentiaCard() {
        return upgrades.get().isInstalled(ModItems.ESSENTIA_UPGRADE_CARD.get());
    }

    public boolean hasBuffered() {
        return !thE$essentiaBuffer.isEmpty();
    }

    public void save(CompoundTag tag) {
        tag.remove("ThEEssentiaInterfaceBufferAspect");
        tag.remove("ThEEssentiaInterfaceBufferAmount");
        if (hasBuffered()) {
            tag.putString("ThEEssentiaInterfaceBufferAspect", thE$essentiaBuffer.aspectId().toString());
            tag.putLong("ThEEssentiaInterfaceBufferAmount", thE$essentiaBuffer.amount());
        }
    }

    public void load(CompoundTag tag) {
        clear();
        var id = ResourceLocation.tryParse(tag.getString("ThEEssentiaInterfaceBufferAspect"));
        long amount = tag.getLong("ThEEssentiaInterfaceBufferAmount");
        if (id != null && amount > 0) thE$essentiaBuffer = new AspectStack(id, amount);
    }

    public void clear() {
        thE$essentiaBuffer = AspectStack.EMPTY;
    }

    public void addBufferedDrops(List<ItemStack> drops) {
        if (hasBuffered()) {
            var key = AEssentiaKey.of(thE$essentiaBuffer.aspectId());
            drops.add(GenericStack.wrapInItemStack(key, thE$essentiaBuffer.amount()));
        }
    }
    @Override
    public boolean thE$essentiaActive() {
        // 联网判据用 networkStorage（InterfaceLogic 自身即以它作「是否挂网」的判断，见 getInventory()）
        return thE$hasEssentiaCard() && networkStorage.get() != null;
    }

    @Override
    public int thE$simulateAcceptEssentia(Aspect aspect, int amount) {
        if (!thE$essentiaActive() || aspect == null || amount <= 0) {
            return 0;
        }
        AEssentiaKey key = thE$essentiaKey(aspect);
        if (key == null) {
            return 0;
        }
        // 模拟只检查容量，不搬动缓冲中的资源。
        if (!this.thE$essentiaBuffer.isEmpty() && !this.thE$essentiaBuffer.aspectId().equals(key.getId())) {
            return 0;
        }
        int want = Math.min(amount, thE$transportLimit());
        long room = networkStorage.get().insert(key, want, Actionable.SIMULATE, actionSource.get());
        return (int) Math.min(room, want);
    }

    @Override
    public int thE$acceptEssentia(Aspect aspect, int amount) {
        if (!thE$essentiaActive() || aspect == null || amount <= 0) {
            return 0;
        }
        AEssentiaKey key = thE$essentiaKey(aspect);
        if (key == null) {
            return 0;
        }
        // 缓冲被异类占用 → 先冲；仍占用则拒收（源质留在外部，绝不丢弃）
        if (!this.thE$essentiaBuffer.isEmpty() && !this.thE$essentiaBuffer.aspectId().equals(key.getId())) {
            thE$flushEssentiaBuffer();
            if (!this.thE$essentiaBuffer.isEmpty() && !this.thE$essentiaBuffer.aspectId().equals(key.getId())) {
                return 0;
            }
        }
        int want = Math.min(amount, thE$transportLimit());
        long room = networkStorage.get().insert(key, want, Actionable.SIMULATE, actionSource.get());
        if (room <= 0) {
            return 0;
        }
        int accepted = (int) Math.min(room, want);
        long existing = this.thE$essentiaBuffer.isEmpty() ? 0 : this.thE$essentiaBuffer.amount();
        this.thE$essentiaBuffer = new AspectStack(key.getId(), existing + accepted);
        thE$saveHost();
        // 尽力立即注入（残留留缓冲，交 tick 循环下轮冲）
        thE$flushEssentiaBuffer();
        return accepted;
    }

    @Override
    public Aspect thE$stockAspect() {
        if (!thE$essentiaActive()) {
            return null;
        }
        // TC 传输面是单要素契约；遍历实际库存大小以覆盖扩展接口。
        for (int i = 0; i < storage.get().size(); i++) {
            if (storage.get().getKey(i) instanceof AEssentiaKey key) {
                return AspectIds.findAspectById(key.getId());
            }
        }
        return null;
    }

    @Override
    public int thE$stockAmount(Aspect aspect) {
        if (!thE$essentiaActive() || aspect == null) {
            return 0;
        }
        AEssentiaKey key = thE$essentiaKey(aspect);
        if (key == null) {
            return 0;
        }
        long sum = 0;
        for (int i = 0; i < storage.get().size(); i++) {
            if (storage.get().getKey(i) instanceof AEssentiaKey k && k.equals(key)) {
                sum += storage.get().getAmount(i);
            }
        }
        return (int) Math.min(sum, Integer.MAX_VALUE);
    }

    @Override
    public int thE$simulateTakeFromStock(Aspect aspect, int amount) {
        if (!thE$essentiaActive() || aspect == null || amount <= 0) {
            return 0;
        }
        AEssentiaKey key = thE$essentiaKey(aspect);
        if (key == null) {
            return 0;
        }
        long canTake = storage.get().extract(key, amount, Actionable.SIMULATE, actionSource.get());
        return (int) Math.min(canTake, amount);
    }

    @Override
    public int thE$takeFromStock(Aspect aspect, int amount) {
        if (!thE$essentiaActive() || aspect == null || amount <= 0) {
            return 0;
        }
        AEssentiaKey key = thE$essentiaKey(aspect);
        if (key == null) {
            return 0;
        }
        // 仅从缓存格（stock 槽）取出——禁止直取网络
        long taken = storage.get().extract(key, amount, Actionable.MODULATE, actionSource.get());
        return (int) taken;
    }

    /** 把缓冲里的源质注入网络（成功即落盘 + 按实际入网量 double 记账）。@return 本次注入量 */
    public long thE$flushEssentiaBuffer() {
        if (this.thE$essentiaBuffer.isEmpty() || networkStorage.get() == null) {
            return 0;
        }
        AEssentiaKey key = AEssentiaKey.of(this.thE$essentiaBuffer.aspectId());
        if (key == null) {
            this.thE$essentiaBuffer = AspectStack.EMPTY;
            return 0;
        }
        var grid = thE$grid();
        if (grid == null) return 0;
        long injected = thaumicenergistics.common.essentia.capability.EssentiaEnergy.transfer(
                grid, this.thE$essentiaBuffer.amount(), allowed -> networkStorage.get().insert(
                        key, allowed, Actionable.MODULATE, actionSource.get()));
        if (injected <= 0) {
            return 0;
        }
        long left = this.thE$essentiaBuffer.amount() - injected;
        this.thE$essentiaBuffer = left > 0
                ? new AspectStack(this.thE$essentiaBuffer.aspectId(), left)
                : AspectStack.EMPTY;
        thE$saveHost();
        return injected;
    }

    /** 通知宿主保存（缓冲为新增字段，需显式落盘）。经节点 owner 取得宿主，不新增 @Shadow。 */
    private void thE$saveHost() {
        IGridNode node = thE$actionableNode();
        Object owner = node != null ? node.getOwner() : null;
        if (owner instanceof InterfaceLogicHost host) {
            host.saveChanges();
        }
    }

    /** 本逻辑所在网格（未挂网 → null）。经公开 getActionableNode() 取得，避开父类陷阱。 */
    private IGrid thE$grid() {
        IGridNode node = thE$actionableNode();
        return node != null ? node.getGrid() : null;
    }

    private IGridNode thE$actionableNode() {
        return node.get();
    }

    /** 单次搬运上限 = 8 × 每 tick 操作次数（仅计 AE2 加速卡，与供应器同口径）。 */
    private int thE$transportLimit() {
        int installed = upgrades.get().getInstalledUpgrades(AEItems.SPEED_CARD);
        int ops = switch (installed) {
            default -> 1;
            case 1 -> 8;
            case 2 -> 32;
            case 3 -> 64;
            case 4 -> 96;
        };
        return 8 * Math.max(1, ops);
    }

    /** Aspect → AEssentiaKey（转换为空 → null）。 */
    private AEssentiaKey thE$essentiaKey(Aspect aspect) {
        if (aspect == null) {
            return null;
        }
        ResourceLocation id = AspectIds.getAspectId(aspect);
        return id == null ? null : AEssentiaKey.of(id);
    }
}
