package sh.leaflab.goods.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import sh.leaflab.goods.TheGoods;
import sh.leaflab.goods.block.NetworkConnectorBlockEntity;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TheGoods.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkConnectorBlockEntity>> NETWORK_CONNECTOR = BLOCK_ENTITY_TYPES.register(
            "network_connector", () -> new BlockEntityType<>(
                    NetworkConnectorBlockEntity::new, TheGoods.NETWORK_CONNECTOR.get()));
}
