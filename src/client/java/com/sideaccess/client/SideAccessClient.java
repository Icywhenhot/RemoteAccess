package com.sideaccess.client;

import com.sideaccess.client.config.SideAccessConfig;
import com.sideaccess.client.hud.SideAccessHud;
import com.sideaccess.client.nav.NavState;
import com.sideaccess.client.workstation.WorkstationRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SideAccessClient implements ClientModInitializer {

    public static final String MOD_ID = "sideaccess";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        SideAccessConfig.get(); // load (or create) config eagerly

        registerAnchorTracking();
        registerScreenHooks();
        registerTick();

        LOGGER.info("[Side Access] ready");
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
                if (WorkstationRegistry.isWorkstation(state, SideAccessConfig.get())) {
                    NavState.setPendingAnchor(pos);
                }
            }
            return InteractionResult.PASS;
        });
    }

    private void registerScreenHooks() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Only container-backed workstation screens participate.
            if (!(screen instanceof AbstractContainerScreen<?>) || client.player == null) {
                NavState.deactivate();
                return;
            }

            NavState.activate(client.player);

            ScreenKeyboardEvents.allowKeyPress(screen).register((scr, keyEvent) -> {
                if (!NavState.isActive() || isTextFieldFocused(scr)) {
                    return true; // let the screen handle the key normally
                }
                SideAccessConfig config = SideAccessConfig.get();
                int key = keyEvent.key();
                if (key == config.prevKeyCode()) {
                    NavState.switchTo(-1);
                    return false; // consume — never leaks into movement
                }
                if (key == config.nextKeyCode()) {
                    NavState.switchTo(1);
                    return false;
                }
                return true;
            });

            ScreenMouseEvents.allowMouseClick(screen).register((scr, mouseEvent) -> {
                if (!NavState.isActive() || mouseEvent.button() != 0) {
                    return true;
                }
                int side = SideAccessHud.iconHit(scr, mouseEvent.x(), mouseEvent.y(), SideAccessConfig.get());
                if (side == SideAccessHud.SIDE_PREV) {
                    NavState.switchTo(-1);
                    return false;
                }
                if (side == SideAccessHud.SIDE_NEXT) {
                    NavState.switchTo(1);
                    return false;
                }
                return true;
            });

            // 26.1.2 uses retained-mode rendering: overlays draw in the "extract" phase.
            ScreenEvents.afterExtract(screen).register((scr, graphics, mouseX, mouseY, tickDelta) ->
                    SideAccessHud.render(scr, graphics, mouseX, mouseY, SideAccessConfig.get()));
        });
    }

    private void registerTick() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            NavState.tick();
            // Forget navigation once the player has fully left every screen (but not during the
            // brief gap mid-switch, which NavState.tick() guards).
            if (client.screen == null && NavState.isActive() && !NavState.inSwitch()) {
                NavState.deactivate();
            }
        });
    }

    private static boolean isTextFieldFocused(net.minecraft.client.gui.screens.Screen screen) {
        // If a text input (e.g. the anvil rename field) has focus, let it consume the key so
        // typing "a"/"d" still works.
        return screen.getFocused() instanceof EditBox;
    }
}
