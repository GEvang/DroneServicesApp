package com.example.droneservicesapp;

import android.content.Context;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.droneservicesapp.data.diagnostics.DiagnosticLog;

import com.example.droneservicesapp.core.util.LocaleUtils;

import org.osmdroid.config.Configuration;

public class Application extends android.app.Application {

    private static Application applicationInstance;

    public static synchronized Application getInstance() {
        return applicationInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        DiagnosticLog.initialize(this);
        DiagnosticLog.installCrashHandler();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        applicationInstance = this;

        // osmdroid init (required)
        Configuration.getInstance().load(
                this,
                PreferenceManager.getDefaultSharedPreferences(this)
        );
        Configuration.getInstance().setUserAgentValue(getPackageName());

        // Limit osmdroid tile cache size
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(1024L * 1024L * 1024L); // 1 GB
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(800L * 1024L * 1024L); // trim back to 800 MB

    }

    @Override
    public void onLowMemory() {
        DiagnosticLog.event("app", "low_memory", "WARN");
        super.onLowMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        DiagnosticLog.event("app", "memory_trim", "WARN", java.util.Collections.singletonMap("level", level));
        super.onTrimMemory(level);
    }

    public void initAppLanguage(Context context) {
        LocaleUtils.initialize(context, LocaleUtils.getSelectedLanguageId());
    }

}
