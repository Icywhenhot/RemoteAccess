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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge entrypoint for Remote Access. The whole mod is client-only ({@code dist = Dist.CLIENT}),
 * so this class never loads on a dedicated server.
 * <p>
 * Fabric registered its hooks per-screen inside {@code ScreenEvents.AFTER_INIT}. NeoForge's screen
 * events instead fire globally for every screen, so here we register the key/mouse/render handlers
 * once and gate each on "is the current screen switchable and is navigation active?".
 */
@Mod(value = RemoteAccessClient.MOD_ID, dist = Dist.CLIENT)
public final class RemoteAccessClient {

    public static final String MOD_ID = "remoteaccess";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // FML supplies the mod event bus; we only need the game bus (NeoForge.EVENT_BUS) for our events.
    public RemoteAccessClient(IEventBus modEventBus) {
        RemoteAccessConfig.get(); // load (or create) config eagerly

        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(this::onRightClickBlock);
        gameBus.addListener(this::onScreenInit);
        gameBus.addListener(this::onScreenKeyPressed);
        gameBus.addListener(this::onScreenMousePressed);
        gameBus.addListener(this::onScreenRender);
        gameBus.addListener(this::onClientTick);

        LOGGER.info("[Remote Access] ready");
    }

    /**
     * Records which workstation the player just opened, so the scan can be anchored on it. Passive
     * observer: it never changes the interaction's outcome.
     */
    private void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        Player player = event.getEntity();
        if (level.isClientSide()
                && event.getHand() == InteractionHand.MAIN_HAND
                && !player.isShiftKeyDown()) {
            BlockPos pos = event.getPos();
            BlockState state = level.getBlockState(pos);
            if (WorkstationRegistry.isWorkstation(state, RemoteAccessConfig.get())) {
                NavState.setPendingAnchor(pos);
            }
        }
    }

    private void onScreenInit(ScreenEvent.Init.Post event) {
        Minecraft client = Minecraft.getInstance();
        Screen screen = event.getScreen();
        // Only server-opened container screens participate (containerId 0 is the player's own
        // inventory, which we never treat as a workstation).
        if (!isSwitchableScreen(screen) || client.player == null) {
            NavState.deactivate();
            return;
        }
        // Begin a short retry window: the right-click anchor can land a frame after init.
        NavState.beginActivation(client.player);
    }

    private void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!NavState.isActive() || isTextFieldFocused(event.getScreen())) {
            return; // let the screen handle the key normally
        }
        RemoteAccessConfig config = RemoteAccessConfig.get();
        // NOTE (26.1.2-beta): if NeoForge moved key data behind getKeyEvent(), this becomes
        // event.getKeyEvent().key(). Verify against the actual NeoForge ScreenEvent API.
        int key = event.getKeyCode();
        if (key == config.prevKeyCode()) {
            NavState.switchTo(-1);
            event.setCanceled(true); // consume — never leaks into movement
        } else if (key == config.nextKeyCode()) {
            NavState.switchTo(1);
            event.setCanceled(true);
        }
    }

    private void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!NavState.isActive() || event.getButton() != 0) {
            return;
        }
        int side = RemoteAccessHud.iconHit(
                event.getScreen(), event.getMouseX(), event.getMouseY(), RemoteAccessConfig.get());
        if (side == RemoteAccessHud.SIDE_PREV) {
            NavState.switchTo(-1);
            event.setCanceled(true);
        } else if (side == RemoteAccessHud.SIDE_NEXT) {
            NavState.switchTo(1);
            event.setCanceled(true);
        }
    }

    private void onScreenRender(ScreenEvent.Render.Post event) {
        // 26.1.2 uses retained-mode rendering; getGuiGraphics() yields the GuiGraphicsExtractor the
        // HUD draws into. If NeoForge exposes a differently-typed/named accessor here, this is the
        // line to adjust.
        RemoteAccessHud.render(event.getScreen(), event.getGuiGraphics(),
                event.getMouseX(), event.getMouseY(), RemoteAccessConfig.get());
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        NavState.tick();
        if (isSwitchableScreen(client.screen) && client.player != null) {
            // Keep trying for a few ticks in case the anchor lands late.
            NavState.retryTick(client.player);
        } else if (NavState.isActive() && !NavState.inSwitch()) {
            // Left every switchable screen (or it closed) — forget navigation, except during the
            // brief mid-switch gap, which NavState guards.
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
