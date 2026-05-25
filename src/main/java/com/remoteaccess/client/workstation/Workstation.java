package com.remoteaccess.client.workstation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * An immutable snapshot of a nearby workstation, captured at scan time.
 *
 * @param pos      block position in the world
 * @param block    the block at that position when scanned
 * @param distSq   squared distance from the player's eyes (used for sorting/validation)
 * @param relAngle signed horizontal angle (radians) of the block relative to the look direction
 */
public record Workstation(BlockPos pos, Block block, double distSq, double relAngle) {

    public ItemStack icon() {
        return new ItemStack(block);
    }

    public Component displayName() {
        return block.getName();
    }
}
