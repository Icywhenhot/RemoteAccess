package com.sideaccess.client.hud;

import com.sideaccess.client.config.SideAccessConfig;
import com.sideaccess.client.nav.NavState;
import com.sideaccess.client.workstation.Workstation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * Draws the left ("previous") and right ("next") workstation icons on the edges of a supported
 * screen, with a subtle frame and hover tooltip. Pure rendering — no state of its own.
 */
public final class SideAccessHud {

    public static final int SIDE_PREV = -1;
    public static final int SIDE_NONE = 0;
    public static final int SIDE_NEXT = 1;

    private static final int FRAME_BG = 0xC0101010;
    private static final int FRAME_BORDER = 0xFF3C3C3C;
    private static final int FRAME_BORDER_HOVER = 0xFFFFFFFF;
    private static final int MARGIN = 16;

    private SideAccessHud() {}

    public static void render(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, SideAccessConfig config) {
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

        drawIcon(graphics, font, prev, leftX, y, frame, mouseX, mouseY, "A");
        drawIcon(graphics, font, next, rightX, y, frame, mouseX, mouseY, "D");
    }

    private static void drawIcon(GuiGraphicsExtractor graphics, Font font, Workstation ws,
                                 int x, int y, int frame, int mouseX, int mouseY, String keyHint) {
        boolean hover = mouseX >= x && mouseX < x + frame && mouseY >= y && mouseY < y + frame;

        graphics.fill(x, y, x + frame, y + frame, FRAME_BG);
        int border = hover ? FRAME_BORDER_HOVER : FRAME_BORDER;
        graphics.outline(x, y, frame, frame, border);

        // Centre the 16x16 item render within the frame.
        int itemX = x + (frame - 16) / 2;
        int itemY = y + (frame - 16) / 2;
        graphics.item(ws.icon(), itemX, itemY);

        // Key hint badge in the corner.
        graphics.text(font, keyHint, x + 1, y - 1, 0xFFB0B0B0, false);

        if (hover) {
            graphics.setTooltipForNextFrame(ws.displayName(), mouseX, mouseY);
        }
    }

    /**
     * @return {@link #SIDE_PREV}, {@link #SIDE_NEXT}, or {@link #SIDE_NONE} for the given cursor.
     */
    public static int iconHit(Screen screen, double mouseX, double mouseY, SideAccessConfig config) {
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
