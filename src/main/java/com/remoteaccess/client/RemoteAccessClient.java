package com.remoteaccess.client;

import com.remoteaccess.client.config.RemoteAccessConfig;
import com.remoteaccess.client.hud.RemoteAccessHud;
import com.remoteaccess.client.nav.NavState;
import com.remoteaccess.client.workstation.WorkstationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only NeoForge entrypoint. Unlike Fabric's per-screen callbacks, NeoForge's screen events
 * fire globally, so every handler is registered once on {@link NeoForge#EVENT_BUS} and gates on
 * {@link #isSwitchableScreen(Screen)} plus {@link NavState#isActive()}.
 */
@Mod(value = RemoteAccessClient.MOD_ID, dist = Dist.CLIENT)
public final class RemoteAccessClient {

    public static final String MOD_ID = "remoteaccess";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public RemoteAccessClient() {
        RemoteAccessConfig.get(); // load (or create) config eagerly
        NeoForge.EVENT_BUS.register(this);
        LOGGER.info("[Remote Access] ready");
    }

    /**
     * Records which workstation the player just opened, so the scan can be anchored on it. Passive
     * observer: it never cancels the interaction.
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()
                && event.getHand() == InteractionHand.MAIN_HAND
                && !event.getEntity().isShiftKeyDown()) {
            BlockPos pos = event.getPos();
            BlockState state = event.getLevel().getBlockState(pos);
            if (WorkstationRegistry.isWorkstation(state, RemoteAccessConfig.get())) {
                NavState.setPendingAnchor(pos);
            }
        }
    }

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        Minecraft client = Minecraft.getInstance();
        // Only server-opened container screens participate (containerId 0 is the player's own
        // inventory, which we never want to treat as a workstation).
        if (!isSwitchableScreen(event.getScreen()) || client.player == null) {
            NavState.deactivate();
            return;
        }
        // Begin a short retry window: the right-click anchor can land just after the screen inits,
        // so it may not be ready on this exact frame.
        NavState.beginActivation(client.player);
    }

    @SubscribeEvent
    public void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!isSwitchableScreen(screen) || !NavState.isActive() || isTextFieldFocused(screen)) {
            return;
        }
        RemoteAccessConfig config = RemoteAccessConfig.get();
        int key = event.getKeyCode();
        if (key == config.prevKeyCode()) {
            NavState.switchTo(-1);
            event.setCanceled(true); // consume — never leaks into movement
        } else if (key == config.nextKeyCode()) {
            NavState.switchTo(1);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!isSwitchableScreen(screen) || !NavState.isActive() || event.getButton() != 0) {
            return;
        }
        int side = RemoteAccessHud.iconHit(screen, event.getMouseX(), event.getMouseY(), RemoteAccessConfig.get());
        if (side == RemoteAccessHud.SIDE_PREV) {
            NavState.switchTo(-1);
            event.setCanceled(true);
        } else if (side == RemoteAccessHud.SIDE_NEXT) {
            NavState.switchTo(1);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!isSwitchableScreen(screen)) {
            return;
        }
        RemoteAccessHud.render(screen, event.getGuiGraphics(), event.getMouseX(), event.getMouseY(),
                RemoteAccessConfig.get());
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        NavState.tick();
        if (isSwitchableScreen(client.screen) && client.player != null) {
            // Keep trying for a few ticks in case the anchor lands late.
            NavState.retryTick(client.player);
        } else if (NavState.isActive() && !NavState.inSwitch()) {
            // Left every switchable screen (or the screen closed) — forget navigation, except
            // during the brief mid-switch gap, which NavState guards.
            NavState.deactivate();
        }
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
