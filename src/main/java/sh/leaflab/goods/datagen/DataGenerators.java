package sh.leaflab.goods.datagen;

import net.neoforged.neoforge.data.event.GatherDataEvent;

public class DataGenerators {
    public static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(ModModelProvider::new);
    }

    public static void gatherServerData(GatherDataEvent.Server event) {
        event.createProvider(ModRecipeProvider.Runner::new);
        event.createProvider(ModLootTableProvider::new);
    }
}
