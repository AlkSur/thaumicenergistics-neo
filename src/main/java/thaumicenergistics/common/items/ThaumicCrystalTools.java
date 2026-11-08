package thaumicenergistics.common.items;

import appeng.hooks.IntrinsicEnchantItem;
import appeng.items.tools.quartz.QuartzCuttingKnifeItem;
import appeng.items.tools.quartz.QuartzToolType;
import appeng.items.tools.quartz.QuartzWrenchItem;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;

/** Tool statistics and intrinsic enchantments adapted from BeiWuCN's Thaumic Energistics: CE (MIT). */
public final class ThaumicCrystalTools {
    private ThaumicCrystalTools() {}
    public static final Tier MATERIAL = new Tier() {
        public int getUses() { return 960; }
        public float getSpeed() { return 7.6F; }
        public float getAttackDamageBonus() { return 2.7F; }
        public int getEnchantmentValue() { return 14; }
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return BlockTags.INCORRECT_FOR_IRON_TOOL;
        }
        public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    };
    public interface Repairable {}
    public interface Intrinsic extends Repairable, IntrinsicEnchantItem {
        default ResourceKey<Enchantment> intrinsic() { return Enchantments.FORTUNE; }
        @Override default int getIntrinsicEnchantLevel(ItemStack stack, Holder<Enchantment> enchantment) {
            return enchantment.is(intrinsic()) ? 1 : 0;
        }
    }
    public static final class Pickaxe extends PickaxeItem implements Intrinsic {
        public Pickaxe(Properties p) { super(MATERIAL, p.attributes(PickaxeItem.createAttributes(MATERIAL, 1, -2.8F))); }
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public boolean isValidRepairItem(ItemStack stack, ItemStack material) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }
    }
    public static final class Sword extends SwordItem implements Intrinsic {
        public Sword(Properties p) { super(MATERIAL, p.attributes(SwordItem.createAttributes(MATERIAL, 3, -2.4F))); }
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public boolean isValidRepairItem(ItemStack stack, ItemStack material) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }
        @Override public ResourceKey<Enchantment> intrinsic() { return Enchantments.LOOTING; }
    }
    public static final class Axe extends AxeItem implements Intrinsic {
        public Axe(Properties p) { super(MATERIAL, p.attributes(AxeItem.createAttributes(MATERIAL, 5.2F, -3.05F))); }
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public boolean isValidRepairItem(ItemStack stack, ItemStack material) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }
    }
    public static final class Shovel extends ShovelItem implements Intrinsic {
        public Shovel(Properties p) { super(MATERIAL, p.attributes(ShovelItem.createAttributes(MATERIAL, 1.5F, -3.0F))); }
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public boolean isValidRepairItem(ItemStack stack, ItemStack material) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }
    }
    public static final class Hoe extends HoeItem implements Intrinsic {
        public Hoe(Properties p) { super(MATERIAL, p.attributes(HoeItem.createAttributes(MATERIAL, -2, -1.0F))); }
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public boolean isValidRepairItem(ItemStack stack, ItemStack material) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }
    }
    public static final class Wrench extends QuartzWrenchItem implements Repairable {
        public Wrench(Properties p) { super(p.durability(960)); }
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public boolean isValidRepairItem(ItemStack stack, ItemStack material) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }
    }
    public static final class Knife extends QuartzCuttingKnifeItem implements Repairable {
        public Knife(Properties p) { super(p.durability(960), QuartzToolType.CERTUS); }
        @Override public boolean isRepairable(ItemStack stack) { return false; }
        @Override public boolean isValidRepairItem(ItemStack stack, ItemStack material) { return false; }
        @Override public float getXpRepairRatio(ItemStack stack) { return 0; }
    }
    public static SmithingTemplateItem template() {
        return new SmithingTemplateItem(
                Component.translatable("thaumicenergistics.tools.template.applies"),
                Component.translatable("block.thaumicenergistics.mystic_crystal_block"),
                Component.translatable("item.thaumicenergistics.thaumic_upgrade_smithing_template"),
                Component.translatable("thaumicenergistics.tools.template.base"),
                Component.translatable("thaumicenergistics.tools.template.addition"),
                List.of(ResourceLocation.withDefaultNamespace("item/empty_slot_pickaxe"),
                        ResourceLocation.withDefaultNamespace("item/empty_slot_sword")),
                List.of(ResourceLocation.withDefaultNamespace("item/empty_slot_quartz")));
    }
}
