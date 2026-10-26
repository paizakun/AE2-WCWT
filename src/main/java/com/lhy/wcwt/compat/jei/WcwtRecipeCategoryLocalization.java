package com.lhy.wcwt.compat.jei;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

final class WcwtRecipeCategoryLocalization {
    private WcwtRecipeCategoryLocalization() {
    }

    /**
     * 优先直接从被转送的 vanilla {@link Recipe}（若存在）取其注册 {@code RecipeType} 的
     * ResourceLocation，去查 JEI 已注册的同 uid RecipeType 并取其 IRecipeCategory 的本地化标题。
     * 这是最快的路径，覆盖大多数遵循 JEI 惯例（JEI RecipeType 的 uid 与 vanilla RecipeType 的
     * 注册 id 一致）的分类。
     *
     * <p>找不到时（recipe 为 null，或该 mod 的 JEI 分类没有用同一个 uid 注册——例如 Mekanism
     * 的 Loot Fabricator、AE2 Crystal Science 的 crystal_aggregator_recipe 这类），改为扫描
     * JEI 已注册的全部分类，按 recipeClass 是否能接受 recipeBase 缩小候选范围；当多个候选都
     * 用 {@link RecipeHolder} 做 recipeClass（因而单靠类型无法区分是哪一个具体分类）时，
     * 再用 {@link IRecipeManager#createRecipeLookup} 实际取出该分类的配方列表，确认其中是否
     * 真的包含这个 recipeBase，避免命中不相关的同壳分类。
     *
     * <p>不依赖 ExtendedAE Plus 的 mapRecipeTypeToSearchKey / deriveSearchKeyFromUnknownRecipe——
     * 后者对这类配方会返回自造的缩写/内部名文本（如 "loot fab"、"crystal_aggregator_recipe"），
     * 既不是合法 ResourceLocation 也不能借助解析回推，因此不能作为查找 key。
     *
     * <p>都找不到，或 JEI 运行时不可用时返回 null，由调用方回退到原始 key。
     */
    @Nullable
    static String localize(@Nullable Recipe<?> recipe, @Nullable Object recipeBase) {
        IJeiRuntime runtime = WcwtJeiBookmarkKeys.getRuntime();
        if (runtime == null) {
            return null;
        }
        try {
            String byVanillaType = titleByVanillaRecipeType(runtime, recipe);
            if (byVanillaType != null) {
                return byVanillaType;
            }
            return titleByRecipeClassScan(runtime, recipeBase);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static String titleByVanillaRecipeType(IJeiRuntime runtime, @Nullable Recipe<?> recipe) {
        if (recipe == null) {
            return null;
        }
        ResourceLocation uid = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        if (uid == null) {
            return null;
        }
        IRecipeManager recipeManager = runtime.getRecipeManager();
        return recipeManager.getRecipeType(uid)
                .map(recipeManager::getRecipeCategory)
                .map(category -> category.getTitle().getString())
                .map(String::trim)
                .filter(title -> !title.isEmpty())
                .orElse(null);
    }

    @Nullable
    private static String titleByRecipeClassScan(IJeiRuntime runtime, @Nullable Object recipeBase) {
        if (recipeBase == null) {
            return null;
        }
        IRecipeManager recipeManager = runtime.getRecipeManager();
        return recipeManager.createRecipeCategoryLookup()
                .includeHidden()
                .get()
                .filter(category -> classMatches(category, recipeBase))
                .filter(category -> confirmMembership(recipeManager, category, recipeBase))
                .map(category -> category.getTitle().getString())
                .map(String::trim)
                .filter(title -> !title.isEmpty())
                .findFirst()
                .orElse(null);
    }

    private static boolean classMatches(IRecipeCategory<?> category, Object recipeBase) {
        return category.getRecipeType().getRecipeClass().isInstance(recipeBase);
    }

    /**
     * recipeClass 不是 {@link RecipeHolder} 的分类，类型本身已经足够精确，直接放行。
     * recipeClass 正是 RecipeHolder 的分类，多个分类会共用这同一个壳类型，必须实际取出该
     * 分类下的配方列表，确认其中真的包含 recipeBase，否则会随机命中第一个同壳分类。
     */
    private static boolean confirmMembership(IRecipeManager recipeManager, IRecipeCategory<?> category,
                                              Object recipeBase) {
        RecipeType<?> type = category.getRecipeType();
        if (type.getRecipeClass() != RecipeHolder.class) {
            return true;
        }
        return recipeManager.createRecipeLookup(type)
                .includeHidden()
                .get()
                .anyMatch(recipeBase::equals);
    }
}
