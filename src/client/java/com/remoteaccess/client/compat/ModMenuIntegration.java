package com.remoteaccess.client.compat;

import com.remoteaccess.client.config.RemoteAccessConfig;
import com.remoteaccess.client.config.SortMode;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;

/**
 * Mod Menu entry point. Builds the Remote Access config screen with Cloth Config when both mods are
 * present; otherwise the mod still runs and the JSON file remains the source of truth.
 * <p>
 * Cloth Config is an optional dependency, so {@link #getModConfigScreenFactory()} guards on its
 * presence and the Cloth-referencing screen builder lives in a separate method that is only ever
 * reached once we know the classes can load.
 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            // No Cloth Config: don't offer a (broken) "Configure" button.
            return parent -> null;
        }
        return ClothScreen::build;
    }

    /**
     * Isolated so referencing Cloth Config types never triggers class loading unless Cloth is
     * actually installed (see {@link #getModConfigScreenFactory()}).
     */
    private static final class ClothScreen {

        static net.minecraft.client.gui.screens.Screen build(net.minecraft.client.gui.screens.Screen parent) {
            RemoteAccessConfig config = RemoteAccessConfig.get();

            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Component.translatable("remoteaccess.config.title"));

            // Persist edits back to the live singleton + disk, and refresh the key-code cache so a
            // changed prev/next key takes effect immediately without a restart.
            builder.setSavingRunnable(() -> {
                config.clamp();
                config.invalidateKeyCache();
                config.save();
            });

            ConfigEntryBuilder entries = builder.entryBuilder();
            ConfigCategory general =
                    builder.getOrCreateCategory(Component.translatable("remoteaccess.config.category.general"));

            general.addEntry(entries
                    .startDoubleField(Component.translatable("remoteaccess.config.searchRadius"), config.searchRadius)
                    .setDefaultValue(5.0)
                    .setMin(1.0)
                    .setTooltip(Component.translatable("remoteaccess.config.searchRadius.tooltip"))
                    .setSaveConsumer(v -> config.searchRadius = v)
                    .build());

            general.addEntry(entries
                    .startDoubleField(Component.translatable("remoteaccess.config.reachLimit"), config.reachLimit)
                    .setDefaultValue(6.0)
                    .setMin(1.0)
                    .setTooltip(Component.translatable("remoteaccess.config.reachLimit.tooltip"))
                    .setSaveConsumer(v -> config.reachLimit = v)
                    .build());

            general.addEntry(entries
                    .startStrField(Component.translatable("remoteaccess.config.prevKey"), config.prevKey)
                    .setDefaultValue("A")
                    .setTooltip(Component.translatable("remoteaccess.config.prevKey.tooltip"))
                    .setSaveConsumer(v -> config.prevKey = v)
                    .build());

            general.addEntry(entries
                    .startStrField(Component.translatable("remoteaccess.config.nextKey"), config.nextKey)
                    .setDefaultValue("D")
                    .setTooltip(Component.translatable("remoteaccess.config.nextKey.tooltip"))
                    .setSaveConsumer(v -> config.nextKey = v)
                    .build());

            general.addEntry(entries
                    .startIntSlider(Component.translatable("remoteaccess.config.iconSize"), config.iconSize, 8, 48)
                    .setDefaultValue(16)
                    .setTooltip(Component.translatable("remoteaccess.config.iconSize.tooltip"))
                    .setSaveConsumer(v -> config.iconSize = v)
                    .build());

            general.addEntry(entries
                    .startEnumSelector(Component.translatable("remoteaccess.config.sortMode"), SortMode.class, config.sortMode)
                    .setDefaultValue(SortMode.ANGULAR)
                    .setTooltip(Component.translatable("remoteaccess.config.sortMode.tooltip"))
                    .setSaveConsumer(v -> config.sortMode = v)
                    .build());

            general.addEntry(entries
                    .startBooleanToggle(Component.translatable("remoteaccess.config.showSwitchMessage"), config.showSwitchMessage)
                    .setDefaultValue(true)
                    .setTooltip(Component.translatable("remoteaccess.config.showSwitchMessage.tooltip"))
                    .setSaveConsumer(v -> config.showSwitchMessage = v)
                    .build());

            general.addEntry(entries
                    .startBooleanToggle(Component.translatable("remoteaccess.config.showIcons"), config.showIcons)
                    .setDefaultValue(true)
                    .setTooltip(Component.translatable("remoteaccess.config.showIcons.tooltip"))
                    .setSaveConsumer(v -> config.showIcons = v)
                    .build());

            general.addEntry(entries
                    .startBooleanToggle(Component.translatable("remoteaccess.config.slideAnimation"), config.slideAnimation)
                    .setDefaultValue(true)
                    .setTooltip(Component.translatable("remoteaccess.config.slideAnimation.tooltip"))
                    .setSaveConsumer(v -> config.slideAnimation = v)
                    .build());

            general.addEntry(entries
                    .startBooleanToggle(Component.translatable("remoteaccess.config.playSound"), config.playSound)
                    .setDefaultValue(true)
                    .setTooltip(Component.translatable("remoteaccess.config.playSound.tooltip"))
                    .setSaveConsumer(v -> config.playSound = v)
                    .build());

            general.addEntry(entries
                    .startFloatField(Component.translatable("remoteaccess.config.soundVolume"), config.soundVolume)
                    .setDefaultValue(0.5f)
                    .setMin(0.0f)
                    .setMax(1.0f)
                    .setTooltip(Component.translatable("remoteaccess.config.soundVolume.tooltip"))
                    .setSaveConsumer(v -> config.soundVolume = v)
                    .build());

            general.addEntry(entries
                    .startStrList(Component.translatable("remoteaccess.config.blacklist"), new ArrayList<>(config.blacklist))
                    .setDefaultValue(new ArrayList<>())
                    .setTooltip(Component.translatable("remoteaccess.config.blacklist.tooltip"))
                    .setSaveConsumer(v -> config.blacklist = new ArrayList<>(v))
                    .build());

            return builder.build();
        }
    }
}
