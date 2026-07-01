package sh.leaflab.goods.datagen;

import java.util.List;
import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import sh.leaflab.goods.TheGoods;

public class ModBlockLootSubProvider extends BlockLootSubProvider {
    public ModBlockLootSubProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.VANILLA_SET, registries);
    }

    @Override
    protected void generate() {
        dropSelf(TheGoods.TRADE_HUB.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return List.of(TheGoods.TRADE_HUB.get());
    }
}
