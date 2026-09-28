package tc_energy.integration.jei;

import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

/**
 * AE2-JEI-Integration 兼容注册的隔离类。
 * 仅被 {@link ThEJeiPlugin} 通过反射调用（{@code Class.forName} + {@code getMethod("register").invoke}），
 * 本类（及其引用的 ae2jei 库类）只在库存在时才被类加载器解析。
 * 因此只装 JEI 时不会因引用缺失而 NoClassDefFoundError，双装时拖拽转换完整生效。
 */
public final class Ae2JeiCompat {

    private Ae2JeiCompat() {
    }

    /** 注册源质转换器到 AE2-JEI-Integration（支持从 JEI 拖拽源质到 AE2 筛选槽）。 */
    public static void register() {
        IngredientConverters.register(new EssentiaIngredientConverter());
    }
}
