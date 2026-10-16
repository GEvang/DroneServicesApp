package com.example.droneservicesapp;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.droneservicesapp.data.diagnostics.DiagnosticLog;

import com.example.droneservicesapp.core.util.LocaleUtils;

import com.example.droneservicesapp.ui.map.osmdroid.OsmdroidMapEngine;

public class Application extends android.app.Application {

    private static Application applicationInstance;

    public static synchronized Application getInstance() {
        return applicationInstance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        applicationInstance = this;
        LocaleUtils.initialize(this, LocaleUtils.getSelectedLanguageId());
        DiagnosticLog.initialize(this);
        DiagnosticLog.installCrashHandler();
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // Map engine (user agent, 1 GB tile cache trimmed back to 800 MB).
        OsmdroidMapEngine.configure(this);

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
