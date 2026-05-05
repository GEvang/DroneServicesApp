package com.example.droneservicesapp;

import android.content.Context;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatDelegate;

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

        if (BuildConfig.DEBUG) {
            com.example.droneservicesapp.data.geoawareness.GeoAwarenessDebugProbe.INSTANCE.logDummyRethymnoZones(this);
            com.example.droneservicesapp.data.geoawareness.GeoAwarenessGeometryDebugProbe.INSTANCE.logGeometryValidation(this);
            com.example.droneservicesapp.data.geoawareness.GeoAwarenessCheckerDebugProbe.INSTANCE.logCheckerValidation(this);
            com.example.droneservicesapp.data.geoawareness.LiveGeoAwarenessDebugProbe.INSTANCE.logLiveGeoValidation(this);
            com.example.droneservicesapp.data.geoawareness.GeoAwarenessHealthDebugProbe.INSTANCE.logHealthValidation(this);
            com.example.droneservicesapp.data.geoawareness.GeoZoneValidationDebugProbe.INSTANCE.logValidationProbe(this);
            com.example.droneservicesapp.data.geoawareness.GeoZoneSourceArchitectureDebugProbe.INSTANCE.logSourceArchitectureValidation(this);
        }

    }

    public void initAppLanguage(Context context) {
        LocaleUtils.initialize(context, LocaleUtils.getSelectedLanguageId());
    }

}
