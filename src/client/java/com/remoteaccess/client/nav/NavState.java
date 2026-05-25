package com.remoteaccess.client.nav;

import com.remoteaccess.client.config.RemoteAccessConfig;
import com.remoteaccess.client.workstation.Workstation;
import com.remoteaccess.client.workstation.WorkstationRegistry;
import com.remoteaccess.client.workstation.WorkstationScanner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Holds the live navigation state for the currently open workstation screen and performs the
 * actual screen-to-screen switch.
 * <p>
 * <b>Why this is multiplayer-safe / not "remote access":</b> switching does not reach into the
 * world directly. It closes the current screen and replays a genuine block-use interaction via
 * {@link MultiPlayerGameMode#useItemOn}, the exact path vanilla uses when you right-click a block.
 * The server then validates reach and opens the menu itself. We additionally clamp candidates to
 * {@code reachLimit} client-side, so we only ever target blocks the player could legitimately use.
 */
public final class NavState {

    private static boolean active = false;
    private static List<Workstation> ordered = List.of();
    private static int currentIndex = 0;

    /** Block to anchor the next screen's scan on. Set by a real block-use or by a switch. */
    private static BlockPos pendingAnchor = null;

    /** Ticks during which a screen-null state should NOT clear the anchor (mid-switch window). */
    private static int switchGuard = 0;

    /** Direction (-1/+1) of the most recent switch — drives the slide direction. */
    private static int lastSwitchDir = 1;
    /** Wall-clock start of the current slide animation; 0 means "no slide running". */
    private static long slideStartMs = 0L;
    /** Set on a switch, consumed when the destination screen activates, to kick off its slide. */
    private static boolean pendingSlide = false;

    /**
     * Remaining ticks to keep retrying activation after a switchable screen opens. Covers the case
     * where {@link net.fabricmc.fabric.api.event.player.UseBlockCallback} fires slightly after the
     * screen's init, so the anchor isn't ready on the very first frame.
     */
    private static int activationTicksLeft = 0;

    private NavState() {}

    // --- anchor handling ---------------------------------------------------

    public static void setPendingAnchor(BlockPos pos) {
        pendingAnchor = pos;
    }

    public static void tick() {
        if (switchGuard > 0) {
            switchGuard--;
        }
    }

    /** True during the brief window between closing one screen and the next opening. */
    public static boolean inSwitch() {
        return switchGuard > 0;
    }

    // --- activation --------------------------------------------------------

    public static boolean isActive() {
        return active;
    }

    public static int currentIndex() {
        return currentIndex;
    }

    public static List<Workstation> ordered() {
        return ordered;
    }

    /** Direction of the last switch: +1 (next) or -1 (previous). */
    public static int lastSwitchDir() {
        return lastSwitchDir;
    }

    /** Wall-clock millis when the current slide started, or 0 if none is running. */
    public static long slideStartMs() {
        return slideStartMs;
    }

    /**
     * Begin trying to activate for a freshly opened switchable screen. Opens a short retry window
     * (see {@link #activationTicksLeft}) and attempts immediately.
     */
    public static void beginActivation(LocalPlayer player) {
        activationTicksLeft = 8;
        activate(player);
    }

    /** Per-tick retry while a switchable screen is open but we haven't activated yet. */
    public static void retryTick(LocalPlayer player) {
        if (active || activationTicksLeft <= 0) {
            return;
        }
        activationTicksLeft--;
        activate(player);
    }

    /**
     * (Re)build navigation state for the open switchable screen.
     *
     * @return true if at least two workstations are reachable and the HUD/keys should be active
     */
    public static boolean activate(LocalPlayer player) {
        RemoteAccessConfig config = RemoteAccessConfig.get();
        boolean wasSwitch = pendingSlide;
        BlockPos anchor = resolveAnchor(player, wasSwitch, config);
        pendingAnchor = null; // consume regardless of outcome to avoid stale anchors

        if (anchor == null) {
            clearActive();
            return false;
        }

        List<Workstation> list = WorkstationScanner.scan(player.level(), player, player.blockPosition(), config);
        if (list.size() < 2) {
            // Nothing else to tab to — stay invisible (but keep retrying briefly).
            clearActive();
            return false;
        }

        ordered = list;
        currentIndex = Math.max(0, indexOf(list, anchor));
        active = true;
        activationTicksLeft = 0;

        // Start the slide on the destination screen only if we arrived here via a switch.
        slideStartMs = wasSwitch ? System.currentTimeMillis() : 0L;
        pendingSlide = false;
        return true;
    }

    /**
     * Pick the block this screen belongs to. For a switch we trust the target we set ourselves; for
     * a fresh open we trust the click anchor if {@code UseBlockCallback} already fired, otherwise the
     * block under the crosshair (which is the block that was just right-clicked to open the screen).
     */
    private static BlockPos resolveAnchor(LocalPlayer player, boolean wasSwitch, RemoteAccessConfig config) {
        if (wasSwitch) {
            return isWorkstationNear(player, pendingAnchor, config) ? pendingAnchor : null;
        }
        if (isWorkstationNear(player, pendingAnchor, config)) {
            return pendingAnchor;
        }
        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            if (isWorkstationNear(player, pos, config)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isWorkstationNear(LocalPlayer player, BlockPos pos, RemoteAccessConfig config) {
        if (pos == null) {
            return false;
        }
        BlockState state = player.level().getBlockState(pos);
        if (!WorkstationRegistry.isWorkstation(state, config)) {
            return false;
        }
        double maxSq = config.searchRadius * config.searchRadius;
        Vec3 eye = player.getEyePosition();
        return eye.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= maxSq;
    }

    /** Clears the active overlay state but leaves the retry window intact. */
    private static void clearActive() {
        active = false;
        ordered = List.of();
        currentIndex = 0;
        slideStartMs = 0L;
    }

    /** Full reset, used when the player leaves every switchable screen. */
    public static boolean deactivate() {
        clearActive();
        activationTicksLeft = 0;
        pendingSlide = false;
        pendingAnchor = null;
        return false;
    }

    private static int indexOf(List<Workstation> list, BlockPos pos) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).pos().equals(pos)) {
                return i;
            }
        }
        return -1;
    }

    // --- switching ---------------------------------------------------------

    public static Workstation peek(int direction) {
        if (ordered.size() < 2) {
            return null;
        }
        int n = ordered.size();
        int idx = Math.floorMod(currentIndex + direction, n);
        return ordered.get(idx);
    }

    /** Switch to the previous (-1) or next (+1) workstation. */
    public static void switchTo(int direction) {
        if (!active || ordered.size() < 2) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null) {
            return;
        }

        int n = ordered.size();
        int targetIndex = Math.floorMod(currentIndex + direction, n);
        Workstation target = ordered.get(targetIndex);

        // Re-validate: the block may have been removed since we scanned.
        RemoteAccessConfig config = RemoteAccessConfig.get();
        BlockState state = player.level().getBlockState(target.pos());
        if (!WorkstationRegistry.isWorkstation(state, config)) {
            // Drop the stale entry and retry once in the same direction.
            ordered = WorkstationScanner.scan(player.level(), player, player.blockPosition(), config);
            if (ordered.size() < 2) {
                deactivate();
                return;
            }
            currentIndex = Math.min(currentIndex, ordered.size() - 1);
            switchTo(direction);
            return;
        }

        currentIndex = targetIndex;
        pendingAnchor = target.pos();
        switchGuard = 5;

        // Kick off the carousel feedback: remember the direction and play the swipe sound now
        // (immediately, for responsiveness — the slide itself starts when the new screen opens).
        lastSwitchDir = direction;
        pendingSlide = true;
        playSwitchSound(direction, config);

        // 1. Close the current handled screen (sends a legitimate container-close packet).
        mc.setScreen(null);

        // 2. Replay a real block interaction. Server validates reach and opens the target menu.
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResultFor(target.pos()));

        // 3. Optional subtle feedback (action-bar / overlay text).
        if (config.showSwitchMessage) {
            player.sendOverlayMessage(
                    Component.translatable("remoteaccess.switched", target.displayName()));
        }
    }

    private static BlockHitResult hitResultFor(BlockPos pos) {
        // For menu-opening blocks the hit face is irrelevant to the outcome; aim at the block
        // centre with an upward face, which is always a well-formed interaction packet.
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static void playSwitchSound(int direction, RemoteAccessConfig config) {
        if (!config.playSound) {
            return;
        }
        // Direction-aware pitch: a higher "swipe" for next, lower for previous.
        // forUI signature is (sound, pitch, volume).
        float pitch = direction > 0 ? 1.15f : 0.85f;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, pitch, config.soundVolume));
    }
}
