package thaumicenergistics.integration.jei;

import thaumcraft.api.aspects.Aspect;

/**
 * JEI 自定义成分：持有 Thaumcraft Aspect 实例。
 * 不创建虚拟物品，不污染物品注册表。
 * JEI 侧边栏直接显示要素图标，支持搜索和拖拽。
 */
public record AspectIngredient(Aspect aspect) {

    @Override
    public String toString() {
        return "AspectIngredient{" + aspect.getTag() + "}";
    }
}
