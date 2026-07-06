package sh.leaflab.goods.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.world.item.Items;

import sh.leaflab.goods.TheGoods;

public class ModRecipeProvider extends RecipeProvider {
    public ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        // Placeholder, unbalanced recipe (see docs/spec.md): a hollow 3x3 ring of sticks -> 1 Trade Hub.
        shaped(RecipeCategory.MISC, TheGoods.TRADE_HUB_ITEM.get())
                .pattern("SSS")
                .pattern("S S")
                .pattern("SSS")
                .define('S', Items.STICK)
                .unlockedBy(getHasName(Items.STICK), has(Items.STICK))
                .save(output);

        // Depositor: a chest surrounded by iron ingots in a U shape (like a hopper without the center).
        shaped(RecipeCategory.MISC, TheGoods.DEPOSITOR_ITEM.get())
                .pattern("III")
                .pattern("ICI")
                .pattern(" I ")
                .define('I', Items.IRON_INGOT)
                .define('C', Items.CHEST)
                .unlockedBy(getHasName(Items.CHEST), has(Items.CHEST))
                .save(output);
    }

    public static class Runner extends RecipeProvider.Runner {
        public Runner(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
            super(output, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new ModRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "The Goods Recipes";
        }
    }
}
