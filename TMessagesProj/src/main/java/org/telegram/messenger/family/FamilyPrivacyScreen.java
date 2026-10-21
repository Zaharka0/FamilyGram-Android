package org.telegram.messenger.family;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.telegram.messenger.ApplicationLoader;
import java.util.WeakHashMap;
public final class FamilyPrivacyScreen {
    private static final String TAG="familygram_private_screen";
    private static final WeakHashMap<View,Integer> ACCESS=new WeakHashMap<>();
    private static boolean addedSecure;
    private FamilyPrivacyScreen(){}
    public static boolean enabled(){return ApplicationLoader.applicationContext.getSharedPreferences("familygram_privacy",Context.MODE_PRIVATE).getBoolean("enabled",false);}
    public static void toggle(Activity a){set(a,!enabled());}
    public static void set(Activity a,boolean enabled){ApplicationLoader.applicationContext.getSharedPreferences("familygram_privacy",Context.MODE_PRIVATE).edit().putBoolean("enabled",enabled).apply();apply(a);FamilyPairWidget.updateAll(ApplicationLoader.applicationContext);}
    public static void apply(Activity activity){
        if(activity==null||activity.isFinishing())return;ViewGroup root=activity.findViewById(android.R.id.content);if(root==null)return;
        View old=root.findViewWithTag(TAG);
        if(!enabled()){
            if(old!=null){root.removeView(old);for(java.util.Map.Entry<View,Integer> e:ACCESS.entrySet())e.getKey().setImportantForAccessibility(e.getValue());ACCESS.clear();if(addedSecure)activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);addedSecure=false;}return;
        }
        if(old!=null){old.bringToFront();return;}
        addedSecure=(activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)==0;
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        for(int i=0;i<root.getChildCount();i++){View child=root.getChildAt(i);ACCESS.put(child,child.getImportantForAccessibility());child.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}
        LinearLayout cover=new LinearLayout(activity);cover.setTag(TAG);cover.setOrientation(LinearLayout.VERTICAL);cover.setGravity(Gravity.CENTER);cover.setPadding(40,40,40,40);cover.setBackgroundColor(0xff171b24);cover.setClickable(true);cover.setFocusable(true);
        TextView title=new TextView(activity);title.setText("FamilyGram");title.setTextSize(26);title.setTextColor(Color.WHITE);title.setGravity(Gravity.CENTER);cover.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView info=new TextView(activity);info.setText(FamilyText.get("ScreenHidden"));info.setTextColor(0xffb8bfce);info.setTextSize(16);info.setPadding(0,28,0,32);info.setGravity(Gravity.CENTER);cover.addView(info,new LinearLayout.LayoutParams(-1,-2));
        Button show=new Button(activity);show.setText(FamilyText.get("ShowContent"));show.setOnClickListener(v->set(activity,false));cover.addView(show,new LinearLayout.LayoutParams(-2,-2));root.addView(cover,new ViewGroup.LayoutParams(-1,-1));cover.bringToFront();cover.requestFocus();
    }
}
