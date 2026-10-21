package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.atomic.AtomicReferenceArray;

public final class GhostModeController {
    private static final AtomicReferenceArray<GhostModeSettings> settings = new AtomicReferenceArray<>(UserConfig.MAX_ACCOUNT_COUNT);
    private static final GhostModeSettings defaults = new GhostModeSettings(GhostModeSettings.DEFAULT_FLAGS);

    private GhostModeController() {
    }

    private static SharedPreferences preferences(int account) {
        return ApplicationLoader.applicationContext.getSharedPreferences("familygram_ghost_" + account, Context.MODE_PRIVATE);
    }

    public static GhostModeSettings get(int account) {
        GhostModeSettings value = settings.get(account);
        if (value == null) {
            synchronized (settings) {
                value = settings.get(account);
                if (value == null) {
                    value = new GhostModeSettings(preferences(account).getInt("flags", defaults.flags));
                    settings.set(account, value);
                }
            }
        }
        return value;
    }

    public static void set(int account, int option, boolean enabled) {
        final GhostModeSettings oldSettings;
        final GhostModeSettings newSettings;
        synchronized (settings) {
            oldSettings = get(account);
            newSettings = oldSettings.with(option, enabled);
            preferences(account).edit().putInt("flags", newSettings.flags).apply();
            settings.set(account, newSettings);
        }
        MessagesController.getInstance(account).onGhostModeSettingsChanged(
                oldSettings.active(GhostModeSettings.HIDE_ONLINE) != newSettings.active(GhostModeSettings.HIDE_ONLINE));
    }

    public static void clear(int account) {
        synchronized (settings) {
            preferences(account).edit().clear().apply();
            settings.set(account, defaults);
        }
    }
}
