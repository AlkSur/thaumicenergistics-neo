package tc_energy.integration.jei;

import dev.tc4port.thaumcraft.api.aspect.AspectId;

/**
 * JEI 自定义成分：持有 Ren 版 AspectId。
 * 不创建虚拟物品，不污染物品注册表；JEI 侧边栏直接显示要素图标，支持搜索和拖拽。
 */
public record AspectIngredient(AspectId aspect) {

    @Override
    public String toString() {
        return "AspectIngredient{" + aspect.serialized() + "}";
    }
}
