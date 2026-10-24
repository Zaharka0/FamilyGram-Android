package org.telegram.messenger.family;
import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import org.telegram.messenger.*;
public class FamilyWidgetConfigureActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);setResult(RESULT_CANCELED);ApplicationLoader.postInitApplication();
        if(FamilyPrivacyScreen.enabled()){finish();return;}
        int id=getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);AppWidgetManager manager=AppWidgetManager.getInstance(this);AppWidgetProviderInfo info=manager.getAppWidgetInfo(id);
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID||info==null||!info.provider.equals(new ComponentName(this,FamilyPairWidget.class))){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,48,32,32);root.setGravity(Gravity.CENTER);setContentView(root);
        TextView title=new TextView(this);title.setText(FamilyText.get("WidgetConfigure"));title.setTextSize(22);root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        int count=0;for(int a=0;a<UserConfig.MAX_ACCOUNT_COUNT;a++){
            if(!UserConfig.getInstance(a).isClientActivated())continue;FamilyDataController data=FamilyDataController.getInstance(a);if(data.partner()<=0)continue;count++;
            org.telegram.tgnet.TLRPC.User self=UserConfig.getInstance(a).getCurrentUser();Button b=new Button(this);b.setText((self==null?"FamilyGram":UserObject.getUserName(self))+" · "+data.daysTogether()+" "+FamilyText.get("DaysTogether"));root.addView(b,new LinearLayout.LayoutParams(-1,-2));long uid=data.self;
            b.setOnClickListener(v->{getSharedPreferences(FamilyPairWidget.CONFIG,MODE_PRIVATE).edit().putLong("uid_"+id,uid).apply();FamilyPairWidget.update(this,manager,id);setResult(RESULT_OK,new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));finish();});
        }
        if(count==0){TextView message=new TextView(this);message.setText(FamilyText.get("ChoosePartnerInfo"));root.addView(message,new LinearLayout.LayoutParams(-1,-2));}
    }
}
