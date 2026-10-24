package org.telegram.messenger.family;
import android.os.SystemClock;
import org.telegram.messenger.*;
public final class FamilyVoice {
    private static long lastSave;
    private FamilyVoice(){}
    public static boolean skipSilence(int account){return FamilyDataController.getInstance(account).prefs.getBoolean("skip_silence",false);}
    public static void setSkipSilence(int account,boolean enabled){FamilyDataController.getInstance(account).prefs.edit().putBoolean("skip_silence",enabled).apply();MediaController.getInstance().applyFamilySilence();}
    private static String key(MessageObject m){return "voice_"+m.getDialogId()+"_"+m.getId();}
    private static boolean eligible(MessageObject m){return m!=null&&m.isVoice()&&m.getId()>0&&!m.isVoiceOnce()&&m.messageOwner.ttl_period==0&&(m.messageOwner.media==null||m.messageOwner.media.ttl_seconds==0)&&!DialogObject.isEncryptedDialog(m.getDialogId());}
    public static boolean heard(MessageObject m){return eligible(m)&&FamilyDataController.getInstance(m.currentAccount).prefs.getBoolean(key(m)+"_heard",false);}
    public static void save(MessageObject m,boolean force){
        if(!eligible(m))return;long now=SystemClock.elapsedRealtime();if(!force&&now-lastSave<4000)return;lastSave=now;
        android.content.SharedPreferences.Editor e=FamilyDataController.getInstance(m.currentAccount).prefs.edit();
        if(m.audioProgress>=0.98f)e.remove(key(m)).putBoolean(key(m)+"_heard",true);else if(m.audioProgress>0)e.putFloat(key(m),m.audioProgress);e.apply();
    }
    private static boolean samePlaying(MessageObject m){MessageObject p=MediaController.getInstance().getPlayingMessageObject();return p!=null&&p.currentAccount==m.currentAccount&&p.getDialogId()==m.getDialogId()&&p.getId()==m.getId();}
    public static void restore(MessageObject m){
        if(!eligible(m)||m.audioProgress>0||m.forceSeekTo>0||samePlaying(m))return;
        float value=FamilyDataController.getInstance(m.currentAccount).prefs.getFloat(key(m),0);if(value>0&&value<0.98f){m.audioProgress=value;m.forceSeekTo=value;}
    }
}
