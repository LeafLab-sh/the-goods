package sh.leaflab.goods.block;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

import sh.leaflab.goods.menu.DepositorMenu;
import sh.leaflab.goods.registry.ModBlockEntities;

public class DepositorBlock extends Block implements EntityBlock {
    private static final MapCodec<DepositorBlock> CODEC = simpleCodec(DepositorBlock::new);

    public DepositorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    public DepositorBlock() {
        this(Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DepositorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type == ModBlockEntities.DEPOSITOR.get()) {
            return (lvl, pos, st, be) -> DepositorBlockEntity.tick(lvl, pos, st, (DepositorBlockEntity) be);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (placer instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof DepositorBlockEntity be) {
            be.setOwner(serverPlayer.getUUID());
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (level.getBlockEntity(pos) instanceof DepositorBlockEntity be) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (windowId, inventory, p) -> new DepositorMenu(windowId, inventory, be),
                        Component.translatable("block.thegoods.depositor")));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level.getBlockEntity(pos) instanceof DepositorBlockEntity be) {
            return be.getAnalogSignal();
        }
        return 0;
    }
}
