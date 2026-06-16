package com.xkmxz.attack_defense_capture_point_xkmxz.gui;

import com.xkmxz.attack_defense_capture_point_xkmxz.nodegraph.CapturePointGraphScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CapturePointManagerItem extends Item {

    public CapturePointManagerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!player.canUseGameMasterBlocks()) {
            if (level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.attack_defense_capture_point_xkmxz.op_only"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            new CapturePointGraphScreen(level).open();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
