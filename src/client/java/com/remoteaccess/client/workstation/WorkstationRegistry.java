package com.remoteaccess.client.workstation;

import com.remoteaccess.client.config.RemoteAccessConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Single source of truth for "is this block a switchable workstation?".
 * <p>
 * Detection is centralized here (not scattered through the code) so the supported set is easy to
 * extend. We deliberately use a curated set of vanilla blocks rather than a custom block tag:
 * tags are server-authoritative, and a client-only mod cannot inject a datapack into a remote
 * server, so a custom {@code TagKey} would silently fail in multiplayer. Storage containers
 * (chests, barrels, shulkers, hoppers, dispensers) are intentionally excluded — this mod is for
 * functional crafting interfaces only.
 */
public final class WorkstationRegistry {

    private static final Set<Block> WORKSTATIONS = Set.of(
            Blocks.CRAFTING_TABLE,
            Blocks.ANVIL,
            Blocks.CHIPPED_ANVIL,
            Blocks.DAMAGED_ANVIL,
            Blocks.ENCHANTING_TABLE,
            Blocks.CARTOGRAPHY_TABLE,
            Blocks.STONECUTTER,
            Blocks.SMITHING_TABLE,
            Blocks.LOOM,
            Blocks.GRINDSTONE,
            Blocks.FURNACE,
            Blocks.BLAST_FURNACE,
            Blocks.SMOKER,
            Blocks.BREWING_STAND,
            Blocks.BEACON
    );

    private WorkstationRegistry() {}

    public static boolean isWorkstation(BlockState state, RemoteAccessConfig config) {
        Block block = state.getBlock();
        if (!WORKSTATIONS.contains(block)) {
            return false;
        }
        if (!config.blacklist.isEmpty()) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            return !config.blacklist.contains(id.toString());
        }
        return true;
    }
}
