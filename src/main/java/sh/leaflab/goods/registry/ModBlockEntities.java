package sh.leaflab.goods.registry;

import java.util.Set;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import sh.leaflab.goods.TheGoods;
import sh.leaflab.goods.block.DepositorBlockEntity;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TheGoods.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DepositorBlockEntity>> DEPOSITOR = BLOCK_ENTITIES.register(
            "depositor", () -> new BlockEntityType<>(DepositorBlockEntity::new, Set.of(TheGoods.DEPOSITOR.get())));
}
