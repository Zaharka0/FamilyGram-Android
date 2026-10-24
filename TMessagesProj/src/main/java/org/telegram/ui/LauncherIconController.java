package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static LauncherIcon[] availableIcons(){return ApplicationLoader.applicationContext.getResources().getBoolean(R.bool.family_photo_pack_present)?LauncherIcon.values():new LauncherIcon[]{LauncherIcon.NOX};}
    public static void tryFixLauncherIconIfNeeded() {
        if(!ApplicationLoader.applicationContext.getResources().getBoolean(R.bool.family_photo_pack_present)){if(!isEnabled(LauncherIcon.NOX))setIcon(LauncherIcon.NOX);return;}
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        pm.setComponentEnabledSetting(icon.getComponentName(ctx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
        for (LauncherIcon i : LauncherIcon.values()) {
            if(i==icon)continue;
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.mipmap.family_photo_1, R.drawable.family_icon_overlay, R.string.AppIconDefault),
        VINTAGE("VintageIcon", R.mipmap.family_photo_2, R.drawable.family_icon_overlay, R.string.AppIconVintage),
        AQUA("AquaIcon", R.mipmap.family_photo_3, R.drawable.family_icon_overlay, R.string.AppIconAqua),
        PREMIUM("PremiumIcon", R.mipmap.family_photo_4, R.drawable.family_icon_overlay, R.string.AppIconPremium),
        TURBO("TurboIcon", R.mipmap.family_photo_5, R.drawable.family_icon_overlay, R.string.AppIconTurbo),
        NOX("NoxIcon", R.mipmap.family_photo_6, R.drawable.family_icon_overlay, R.string.AppIconNox);

        public String getTitle(){return org.telegram.messenger.family.FamilyText.get("Icon"+(ordinal()+1));}

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }
    }
}
