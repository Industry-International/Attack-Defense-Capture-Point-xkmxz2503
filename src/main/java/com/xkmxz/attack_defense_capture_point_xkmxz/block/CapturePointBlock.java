package com.xkmxz.attack_defense_capture_point_xkmxz.block;

import com.mojang.serialization.MapCodec;
import com.xkmxz.attack_defense_capture_point_xkmxz.block.entity.CapturePointBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class CapturePointBlock extends BaseEntityBlock {

    public static final MapCodec<CapturePointBlock> CODEC = simpleCodec(CapturePointBlock::new);

    public CapturePointBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CapturePointBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.canUseGameMasterBlocks()) {
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.attack_defense_capture_point_xkmxz.op_only"), true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            // 客户端：打开方块功能菜单
            if (level.getBlockEntity(pos) instanceof CapturePointBlockEntity be) {
                be.openUI(player);
            }
        }
        // 服务端/客户端都返回 SUCCESS，不阻止后续交互
        return InteractionResult.SUCCESS;
    }
}
