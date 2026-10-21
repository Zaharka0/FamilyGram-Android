package org.telegram.messenger.family;
import android.view.*;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.ui.*;
import org.telegram.ui.ActionBar.*;
public final class FamilyEffects {
    private FamilyEffects(){}
    public static void incoming(int account,long dialog){
        if(!LaunchActivity.isResumed||FamilyPrivacyScreen.enabled())return;BaseFragment f=LaunchActivity.getSafeLastFragment();
        if(f==null||f.getCurrentAccount()!=account)return;
        if(f instanceof ChatActivity&&((ChatActivity)f).getDialogId()!=dialog)return;
        if(!(f instanceof ChatActivity)&&!(f instanceof FamilyHubActivity))return;
        show(f,()->FamilyDataController.getInstance(account).send(dialog,"hug",FamilyDataController.newId(),"🤗 "+FamilyText.get("SendingHug"),FamilyCore.fields()));
    }
    public static void show(BaseFragment f,Runnable reply){
        if(f.getParentActivity()==null)return;LinearLayout box=new LinearLayout(f.getParentActivity());box.setPadding(24,24,24,24);box.setGravity(Gravity.CENTER);box.setOrientation(LinearLayout.VERTICAL);
        TextView emoji=new TextView(f.getParentActivity());emoji.setText("🤗");emoji.setTextSize(96);emoji.setGravity(Gravity.CENTER);box.addView(emoji,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(150)));
        AlertDialog.Builder b=new AlertDialog.Builder(f.getParentActivity()).setTitle(FamilyText.get(reply==null?"HugSent":"HugReceived")).setView(box).setNegativeButton(FamilyText.get("Close"),null);
        if(reply!=null)b.setPositiveButton(FamilyText.get("HugBack"),(d,w)->reply.run());f.showDialog(b.create());
        emoji.setScaleX(0.25f);emoji.setScaleY(0.25f);emoji.setAlpha(0);emoji.animate().scaleX(1).scaleY(1).alpha(1).setDuration(600).setInterpolator(new android.view.animation.OvershootInterpolator()).start();emoji.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }
}
