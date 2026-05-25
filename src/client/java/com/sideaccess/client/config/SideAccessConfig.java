package com.sideaccess.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plain-data config, serialized to {@code config/sideaccess.json}. Loaded once at startup.
 * Keybinds are stored as GLFW key codes (see {@link org.lwjgl.glfw.GLFW}).
 */
public final class SideAccessConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("sideaccess");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Cubic scan radius around the player, in blocks. */
    public double searchRadius = 5.0;

    /**
     * Hard cap on switching distance, in blocks. A target further than this is skipped even if it
     * falls inside {@link #searchRadius}. Keeps switching within legitimate interaction reach so
     * the server accepts the interaction packet. Vanilla reach is ~4.5 blocks.
     */
    public double reachLimit = 6.0;

    /**
     * Key for "previous workstation". Human-readable name, e.g. {@code "A"}, {@code "LEFT"},
     * {@code "SPACE"}, {@code "LEFT_BRACKET"} (matches GLFW key names without the {@code GLFW_KEY_}
     * prefix; single letters/digits also work directly).
     */
    public String prevKey = "A";

    /** Key for "next workstation". See {@link #prevKey} for the accepted format. */
    public String nextKey = "D";

    /** Frame size of the on-screen navigation icons, in pixels. */
    public int iconSize = 16;

    /** Ordering strategy for the prev/next cycle. */
    public SortMode sortMode = SortMode.ANGULAR;

    /** Show a brief action-bar message naming the workstation switched to. */
    public boolean showSwitchMessage = true;

    /** Draw the left/right navigation icons inside supported screens. */
    public boolean showIcons = true;

    /** Block IDs (e.g. {@code "minecraft:beacon"}) to never treat as a workstation. */
    public List<String> blacklist = new ArrayList<>();

    private transient Integer prevKeyCode;
    private transient Integer nextKeyCode;

    private static SideAccessConfig instance;

    /** Resolved GLFW key code for {@link #prevKey} (cached). */
    public int prevKeyCode() {
        if (prevKeyCode == null) {
            prevKeyCode = resolveKey(prevKey, GLFW.GLFW_KEY_A);
        }
        return prevKeyCode;
    }

    /** Resolved GLFW key code for {@link #nextKey} (cached). */
    public int nextKeyCode() {
        if (nextKeyCode == null) {
            nextKeyCode = resolveKey(nextKey, GLFW.GLFW_KEY_D);
        }
        return nextKeyCode;
    }

    /**
     * Maps a human-readable key name to its GLFW key code. Accepts single letters/digits directly
     * (their ASCII value equals the GLFW code) and GLFW key names without the {@code GLFW_KEY_}
     * prefix (e.g. {@code "LEFT"}, {@code "SPACE"}, {@code "LEFT_BRACKET"}).
     */
    private static int resolveKey(String name, int fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        String key = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (key.length() == 1) {
            char c = key.charAt(0);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                return c;
            }
        }
        try {
            return GLFW.class.getField("GLFW_KEY_" + key).getInt(null);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[Side Access] Unknown key name '{}', falling back to default", name);
            return fallback;
        }
    }

    public static SideAccessConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("sideaccess.json");
    }

    private static SideAccessConfig load() {
        Path path = path();
        if (Files.exists(path)) {
            try {
                SideAccessConfig cfg = GSON.fromJson(Files.readString(path), SideAccessConfig.class);
                if (cfg != null) {
                    cfg.sanitize();
                    return cfg;
                }
            } catch (IOException | JsonSyntaxException e) {
                LOGGER.warn("[Side Access] Could not read config, using defaults", e);
            }
        }
        SideAccessConfig cfg = new SideAccessConfig();
        cfg.save();
        return cfg;
    }

    private void sanitize() {
        if (blacklist == null) blacklist = new ArrayList<>();
        if (sortMode == null) sortMode = SortMode.ANGULAR;
        if (searchRadius < 1) searchRadius = 1;
        if (reachLimit < 1) reachLimit = 1;
        if (iconSize < 8) iconSize = 8;
    }

    public void save() {
        try {
            Files.createDirectories(path().getParent());
            Files.writeString(path(), GSON.toJson(this));
        } catch (IOException e) {
            LOGGER.warn("[Side Access] Could not write config", e);
        }
    }
}
