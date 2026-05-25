package com.remoteaccess.client.hud;

import com.remoteaccess.client.config.RemoteAccessConfig;
import com.remoteaccess.client.nav.NavState;
import com.remoteaccess.client.workstation.Workstation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * Draws the left ("previous") and right ("next") workstation icons on the edges of a supported
 * screen, with a subtle frame and hover tooltip. Pure rendering — no state of its own.
 */
public final class RemoteAccessHud {

    public static final int SIDE_PREV = -1;
    public static final int SIDE_NONE = 0;
    public static final int SIDE_NEXT = 1;

    private static final int FRAME_BG = 0xC0101010;
    private static final int FRAME_BORDER = 0xFF3C3C3C;
    private static final int FRAME_BORDER_HOVER = 0xFFFFFFFF;
    private static final int LETTER_COLOR = 0xFFFFFFFF;
    private static final int MARGIN = 16;

    // Slide-in (carousel) animation.
    private static final long SLIDE_MS = 220L;
    private static final int SLIDE_DISTANCE = 28;
    // Gentle continuous bob of the A/D letters above the icons.
    private static final double HOVER_PERIOD_MS = 1400.0;
    private static final float HOVER_AMPLITUDE = 1.6f;
    private static final int LETTER_GAP = 3;

    private RemoteAccessHud() {}

    public static void render(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, RemoteAccessConfig config) {
        if (!config.showIcons || !NavState.isActive()) {
            return;
        }
        Workstation prev = NavState.peek(-1);
        Workstation next = NavState.peek(1);
        if (prev == null || next == null) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int frame = config.iconSize + 4;
        int y = screen.height / 2 - frame / 2;
        int leftX = MARGIN;
        int rightX = screen.width - MARGIN - frame;

        long now = System.currentTimeMillis();
        int slide = slideOffset(config, now);

        // The two letters bob half a cycle apart so the pair feels alive rather than synchronized.
        drawIcon(graphics, font, prev, leftX, slide, y, frame, mouseX, mouseY, "A", now, 0.0);
        drawIcon(graphics, font, next, rightX, slide, y, frame, mouseX, mouseY, "D", now, Math.PI);
    }

    private static void drawIcon(GuiGraphicsExtractor graphics, Font font, Workstation ws,
                                 int restX, int slide, int y, int frame, int mouseX, int mouseY,
                                 String letter, long now, double bobPhase) {
        int x = restX + slide;
        // Hover hit-test uses the resting position so highlighting doesn't jitter mid-slide.
        boolean hover = mouseX >= restX && mouseX < restX + frame && mouseY >= y && mouseY < y + frame;

        graphics.fill(x, y, x + frame, y + frame, FRAME_BG);
        graphics.outline(x, y, frame, frame, hover ? FRAME_BORDER_HOVER : FRAME_BORDER);

        // Centre the 16x16 item render within the frame.
        int itemX = x + (frame - 16) / 2;
        int itemY = y + (frame - 16) / 2;
        graphics.item(ws.icon(), itemX, itemY);

        // Key letter centred above the icon, with a gentle vertical bob.
        double bob = Math.sin(now / HOVER_PERIOD_MS * (Math.PI * 2.0) + bobPhase) * HOVER_AMPLITUDE;
        int letterX = x + frame / 2;
        int letterY = (int) Math.round(y - font.lineHeight - LETTER_GAP + bob);
        graphics.centeredText(font, letter, letterX, letterY, LETTER_COLOR);

        if (hover) {
            graphics.setTooltipForNextFrame(ws.displayName(), mouseX, mouseY);
        }
    }

    /** Eased horizontal offset for the slide-in; 0 once the animation has finished. */
    private static int slideOffset(RemoteAccessConfig config, long now) {
        if (!config.slideAnimation) {
            return 0;
        }
        long start = NavState.slideStartMs();
        if (start <= 0L) {
            return 0;
        }
        long elapsed = now - start;
        if (elapsed < 0L || elapsed >= SLIDE_MS) {
            return 0;
        }
        double p = (double) elapsed / SLIDE_MS;
        double eased = 1.0 - Math.pow(1.0 - p, 3); // easeOutCubic
        double amount = (1.0 - eased) * NavState.lastSwitchDir() * SLIDE_DISTANCE;
        return (int) Math.round(amount);
    }

    /**
     * @return {@link #SIDE_PREV}, {@link #SIDE_NEXT}, or {@link #SIDE_NONE} for the given cursor.
     */
    public static int iconHit(Screen screen, double mouseX, double mouseY, RemoteAccessConfig config) {
        if (!config.showIcons || !NavState.isActive()) {
            return SIDE_NONE;
        }
        int frame = config.iconSize + 4;
        int y = screen.height / 2 - frame / 2;
        int leftX = MARGIN;
        int rightX = screen.width - MARGIN - frame;

        if (inBox(mouseX, mouseY, leftX, y, frame)) {
            return SIDE_PREV;
        }
        if (inBox(mouseX, mouseY, rightX, y, frame)) {
            return SIDE_NEXT;
        }
        return SIDE_NONE;
    }

    private static boolean inBox(double mx, double my, int x, int y, int size) {
        return mx >= x && mx < x + size && my >= y && my < y + size;
    }
}
