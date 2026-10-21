package org.telegram.messenger.family;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.*;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.ui.LaunchActivity;

public class FamilyPairWidget extends AppWidgetProvider {
    public static final String CONFIG="familygram_widgets";
    public static void updateAll(Context context){
        if(context==null)return;AppWidgetManager manager=AppWidgetManager.getInstance(context);int[]ids=manager.getAppWidgetIds(new ComponentName(context,FamilyPairWidget.class));for(int id:ids)update(context,manager,id);
    }
    @Override public void onUpdate(Context c,AppWidgetManager m,int[]ids){ApplicationLoader.postInitApplication();for(int id:ids)update(c,m,id);}
    @Override public void onDeleted(Context c,int[]ids){android.content.SharedPreferences.Editor e=c.getSharedPreferences(CONFIG,Context.MODE_PRIVATE).edit();for(int id:ids)e.remove("uid_"+id);e.apply();}
    public static void pin(Activity activity,int account){
        if(Build.VERSION.SDK_INT>=26){AppWidgetManager manager=AppWidgetManager.getInstance(activity);if(manager.isRequestPinAppWidgetSupported()){
            Bundle options=new Bundle();options.putLong("family_uid",UserConfig.getInstance(account).getClientUserId());manager.requestPinAppWidget(new ComponentName(activity,FamilyPairWidget.class),options,null);return;
        }}Toast.makeText(activity,FamilyText.get("WidgetManual"),Toast.LENGTH_LONG).show();
    }
    public static int account(long uid){for(int a=0;a<UserConfig.MAX_ACCOUNT_COUNT;a++)if(UserConfig.getInstance(a).isClientActivated()&&UserConfig.getInstance(a).getClientUserId()==uid)return a;return -1;}
    public static void update(Context c,AppWidgetManager manager,int id){
        ApplicationLoader.postInitApplication();RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.family_pair_widget);
        long uid=c.getSharedPreferences(CONFIG,Context.MODE_PRIVATE).getLong("uid_"+id,0);int a=account(uid);
        String title="FamilyGram",subtitle=FamilyText.get("WidgetConfigure");Intent intent=new Intent(c,FamilyWidgetConfigureActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
        view.setImageViewResource(R.id.family_widget_photo,R.mipmap.ic_launcher);
        if(a>=0&&!FamilyPrivacyScreen.enabled()){
            FamilyDataController data=FamilyDataController.getInstance(a);long partner=data.partner();
            if(partner>0){TLRPCName name=new TLRPCName(a,partner);title=name.value;subtitle=data.daysTogether()+" "+FamilyText.get("DaysTogether");
                intent=new Intent(c,LaunchActivity.class).setAction("com.tmessages.openchat.familygram."+id).putExtra("userId",partner).putExtra("currentAccount",a).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                String path=data.prefs.getString("widget_photo",null);if(path!=null){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;Bitmap bitmap=BitmapFactory.decodeFile(path,o);if(bitmap!=null)view.setImageViewBitmap(R.id.family_widget_photo,bitmap);}
            }
        }else if(FamilyPrivacyScreen.enabled()){subtitle=FamilyText.get("ScreenHidden");intent=new Intent(c,LaunchActivity.class).setAction(Intent.ACTION_MAIN);}
        view.setTextViewText(R.id.family_widget_name,title);view.setTextViewText(R.id.family_widget_days,subtitle);
        view.setOnClickPendingIntent(R.id.family_widget_root,PendingIntent.getActivity(c,id,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));manager.updateAppWidget(id,view);
    }
    private static class TLRPCName { final String value;TLRPCName(int account,long id){org.telegram.tgnet.TLRPC.User u=MessagesController.getInstance(account).getUser(id);value=u==null?FamilyText.get("Partner"):UserObject.getUserName(u);} }
}
