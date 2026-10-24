package org.telegram.messenger.family;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;

public final class FamilyText {
    private FamilyText() {}

    public static String get(String key) {
        Context context = ApplicationLoader.applicationContext;
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        if (locale != null) {
            Configuration configuration = new Configuration(context.getResources().getConfiguration());
            configuration.setLocale(locale);
            context = context.createConfigurationContext(configuration);
        }
        // Telegram's generated localization table does not include familygram.xml.
        // Read bundled Android resources, using the app language rather than the device language.
        Resources resources = context.getResources();
        int id = resources.getIdentifier("FG" + key, "string", context.getPackageName());
        return id == 0 ? key : resources.getString(id);
    }
}
