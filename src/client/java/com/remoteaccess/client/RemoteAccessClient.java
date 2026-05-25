package com.remoteaccess.client;

import com.remoteaccess.client.config.RemoteAccessConfig;
import com.remoteaccess.client.hud.RemoteAccessHud;
import com.remoteaccess.client.nav.NavState;
import com.remoteaccess.client.workstation.WorkstationRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only Fabric entrypoint. Fabric's screen callbacks are registered per-screen from inside
 * {@link ScreenEvents#AFTER_INIT}, so we only attach the key/mouse/render hooks to screens that are
 * actually switchable (see {@link #isSwitchableScreen(Screen)}).
 */
public class RemoteAccessClient implements ClientModInitializer {

    public static final String MOD_ID = "remoteaccess";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        RemoteAccessConfig.get(); // load (or create) config eagerly

        registerAnchorTracking();
        registerScreenHooks();
        registerTick();

        LOGGER.info("[Remote Access] ready");
    }

    /**
     * Records which workstation the player just opened, so the scan can be anchored on it. This is
     * a passive observer: it never changes the interaction's outcome (always returns PASS).
     */
    private void registerAnchorTracking() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()
                    && hand == InteractionHand.MAIN_HAND
                    && !player.isShiftKeyDown()) {
                BlockPos pos = hitResult.getBlockPos();
                BlockState state = world.getBlockState(pos);
                if (WorkstationRegistry.isWorkstation(state, RemoteAccessConfig.get())) {
                    NavState.setPendingAnchor(pos);
                }
            }
            return InteractionResult.PASS;
        });
    }

    private void registerScreenHooks() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Only server-opened container screens participate (containerId 0 is the player's own
            // inventory, which we never want to treat as a workstation).
            if (!isSwitchableScreen(screen) || client.player == null) {
                NavState.deactivate();
                return;
            }

            // Begin a short retry window: UseBlockCallback can fire just after the screen inits,
            // so the anchor may not be ready on this exact frame.
            NavState.beginActivation(client.player);

            // Consume the key (so it never reaches the screen or movement) and queue the switch for
            // the next tick — switching synchronously here would briefly null the screen mid-keyPress
            // and let vanilla latch A/D into movement. See NavState#requestSwitch.
            ScreenKeyboardEvents.allowKeyPress(screen).register((scr, key, scancode, modifiers) -> {
                if (!NavState.isActive() || isTextFieldFocused(scr)) {
                    return true; // let the screen handle the key normally
                }
                RemoteAccessConfig config = RemoteAccessConfig.get();
                if (key == config.prevKeyCode()) {
                    return !NavState.requestSwitch(-1); // false == consume the key press
                }
                if (key == config.nextKeyCode()) {
                    return !NavState.requestSwitch(1);
                }
                return true;
            });

            ScreenMouseEvents.allowMouseClick(screen).register((scr, mouseX, mouseY, button) -> {
                if (!NavState.isActive() || button != 0) {
                    return true;
                }
                int side = RemoteAccessHud.iconHit(scr, mouseX, mouseY, RemoteAccessConfig.get());
                if (side == RemoteAccessHud.SIDE_PREV) {
                    return !NavState.requestSwitch(-1);
                }
                if (side == RemoteAccessHud.SIDE_NEXT) {
                    return !NavState.requestSwitch(1);
                }
                return true;
            });

            ScreenEvents.afterRender(screen).register((scr, graphics, mouseX, mouseY, tickDelta) ->
                    RemoteAccessHud.render(scr, graphics, mouseX, mouseY, RemoteAccessConfig.get()));
        });
    }

    private void registerTick() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            NavState.tick();
            if (isSwitchableScreen(client.screen) && client.player != null) {
                // Keep trying for a few ticks in case the anchor lands late.
                NavState.retryTick(client.player);
            } else if (NavState.isActive() && !NavState.inSwitch()) {
                // Left every switchable screen (or the screen closed) — forget navigation, except
                // during the brief mid-switch gap, which NavState guards.
                NavState.deactivate();
            }
        });
    }

    /**
     * A screen we can navigate from: a container screen whose menu was opened by the server
     * ({@code containerId != 0}), which excludes the player's own inventory and creative menu.
     */
    private static boolean isSwitchableScreen(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> containerScreen
                && containerScreen.getMenu().containerId != 0;
    }

    private static boolean isTextFieldFocused(Screen screen) {
        // If a text input (e.g. the anvil rename field) has focus, let it consume the key so
        // typing "a"/"d" still works.
        return screen.getFocused() instanceof EditBox;
    }
}
