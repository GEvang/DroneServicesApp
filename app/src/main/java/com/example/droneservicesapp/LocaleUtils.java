package com.example.droneservicesapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.StringDef;
import androidx.preference.PreferenceManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;
import java.util.Objects;

public class LocaleUtils {

    public static final String ENGLISH = "default";
    public static final String GREEK = "el";


    public static void initialize(Context context, @LocaleDef String defaultLanguage) {
        setLocale(context, defaultLanguage);
    }

    public static void setLocale(Context context, @LocaleDef String language) {
        updateResources(context, language);
    }

    private static void updateResources(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        context.createConfigurationContext(configuration);
        configuration.locale = locale;
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());

    }

    private static SharedPreferences getDefaultSharedPreference() {
        if (PreferenceManager.getDefaultSharedPreferences(Application.getInstance().getApplicationContext()) != null)
            return PreferenceManager.getDefaultSharedPreferences(Application.getInstance().getApplicationContext());
        else
            return null;
    }

    public static String getSelectedLanguageId() {
        return Objects.requireNonNull(getDefaultSharedPreference())
                .getString(Application.getInstance().getApplicationContext().getString(R.string.language_pref), "en");
    }

    public static void setSelectedLanguageId(String id) {
        final SharedPreferences prefs = getDefaultSharedPreference();
        assert prefs != null;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Application.getInstance().getApplicationContext().getString(R.string.language_pref), id);
        editor.apply();
    }

    @Retention(RetentionPolicy.SOURCE)
    @StringDef({ENGLISH, GREEK})
    public @interface LocaleDef {
        String[] SUPPORTED_LOCALES = {ENGLISH, GREEK};
    }
}
