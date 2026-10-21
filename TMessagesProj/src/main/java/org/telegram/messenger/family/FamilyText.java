package org.telegram.messenger.family;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
public final class FamilyText {
    private FamilyText(){}
    public static String get(String key){
        int id=ApplicationLoader.applicationContext.getResources().getIdentifier("FG"+key,"string",ApplicationLoader.applicationContext.getPackageName());
        return id==0?key:LocaleController.getString(id);
    }
}
