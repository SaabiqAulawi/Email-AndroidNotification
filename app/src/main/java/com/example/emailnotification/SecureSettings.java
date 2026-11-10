package com.example.emailnotification;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

public final class SecureSettings {
    private static final String FILE = "secure_settings";
    private static final String PASSWORD = "imap_password";

    private SecureSettings() {}

    private static SharedPreferences preferences(Context context) {
        try {
            String keyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    FILE,
                    keyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize secure credential storage", exception);
        }
    }

    public static void putPassword(Context context, String password) {
        preferences(context).edit().putString(PASSWORD, password).apply();
    }

    public static String getPassword(Context context) {
        SharedPreferences securePreferences = preferences(context);
        String password = securePreferences.getString(PASSWORD, "");
        if (!password.isEmpty()) return password;

        SharedPreferences legacyPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        String legacyPassword = legacyPreferences.getString("password", "");
        if (!legacyPassword.isEmpty()) {
            putPassword(context, legacyPassword);
            legacyPreferences.edit().remove("password").apply();
        }
        return legacyPassword;
    }
}
