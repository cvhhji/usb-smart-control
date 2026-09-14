package com.usb.smartcontrol;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.Set;

final class Config {
    static final String GROUP = "config";
    static final String AUTO_ADB = "auto_adb";
    static final String AUTO_MTP = "auto_mtp";
    static final String MTP_UNLOCK_ONLY = "mtp_unlock_only";
    static final String GAME_MODE = "game_mode";
    static final String GAME_PACKAGES = "game_packages";

    static final String MODE_ORIENTATION = "orientation";
    static final String MODE_FOREGROUND = "foreground";
    static final String MODE_DISABLED = "disabled";

    private Config() {
    }

    static boolean autoAdb(SharedPreferences preferences) {
        return preferences == null || preferences.getBoolean(AUTO_ADB, true);
    }

    static boolean autoMtp(SharedPreferences preferences) {
        return preferences == null || preferences.getBoolean(AUTO_MTP, true);
    }

    static boolean mtpUnlockOnly(SharedPreferences preferences) {
        return preferences == null || preferences.getBoolean(MTP_UNLOCK_ONLY, true);
    }

    static String gameMode(SharedPreferences preferences) {
        return preferences == null
                ? MODE_ORIENTATION
                : preferences.getString(GAME_MODE, MODE_ORIENTATION);
    }

    static Set<String> gamePackages(SharedPreferences preferences) {
        return preferences == null
                ? Collections.emptySet()
                : preferences.getStringSet(GAME_PACKAGES, Collections.emptySet());
    }
}
