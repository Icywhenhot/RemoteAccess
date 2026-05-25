package com.remoteaccess.client.config;

/**
 * Determines the order in which nearby workstations are arranged for prev/next cycling.
 */
public enum SortMode {
    /** Sweep left-to-right relative to the player's look direction. Most intuitive for A/D. */
    ANGULAR,
    /** Closest first, ties broken by angle. Matches the brief's recommended approach. */
    DISTANCE,
    /** Deterministic block-position order (X then Y then Z). Simplest, fully stable. */
    POSITION
}
