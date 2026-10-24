package org.telegram.messenger.family;
import android.animation.*;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import org.telegram.messenger.*;
/** Small, cancellable UI transitions respecting the system animator switch. */
public final class FamilyMotion {
    private FamilyMotion(){}
    public static boolean enabled(){return ApplicationLoader.applicationContext.getSharedPreferences("familygram_motion",Context.MODE_PRIVATE).getBoolean("enabled",true);}
    public static void setEnabled(boolean on){ApplicationLoader.applicationContext.getSharedPreferences("familygram_motion",Context.MODE_PRIVATE).edit().putBoolean("enabled",on).apply();}
    public static boolean allowed(){try{return enabled()&&Settings.Global.getFloat(ApplicationLoader.applicationContext.getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1)>0;}catch(Exception e){return enabled();}}
    public static void enter(View view,int index){if(!allowed())return;view.setAlpha(0);view.setTranslationY(AndroidUtilities.dp(12));view.animate().alpha(1).translationY(0).setDuration(240).setStartDelay(Math.min(index*28,180)).setInterpolator(new DecelerateInterpolator()).start();}
    public static void press(View view){view.setOnTouchListener((v,event)->{if(!allowed())return false;int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN)v.animate().scaleX(.97f).scaleY(.97f).setDuration(90).start();else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)v.animate().scaleX(1).scaleY(1).setDuration(160).start();return false;});}
    public static void hearts(View view){
        if(!allowed())return;view.post(()->{
            if(!view.isAttachedToWindow()||!allowed())return;
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(0xffff668a);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(AndroidUtilities.dp(18));
            final float[] progress={0};Drawable burst=new Drawable(){
                public void draw(Canvas canvas){float p=progress[0];paint.setAlpha((int)(255*(1-p)));for(int i=0;i<10;i++){double angle=Math.PI*2*i/10;float radius=AndroidUtilities.dp(12+55*p);canvas.drawText("♥",getBounds().centerX()+(float)Math.cos(angle)*radius,getBounds().centerY()+(float)Math.sin(angle)*radius-AndroidUtilities.dp(12*p),paint);}}
                public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
            };
            burst.setBounds(0,0,view.getWidth(),view.getHeight());view.getOverlay().add(burst);ValueAnimator animator=ValueAnimator.ofFloat(0,1);animator.setDuration(950);animator.addUpdateListener(value->{progress[0]=(float)value.getAnimatedValue();burst.invalidateSelf();});
            View.OnAttachStateChangeListener listener=new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){animator.cancel();}};view.addOnAttachStateChangeListener(listener);
            animator.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator a){view.getOverlay().remove(burst);view.removeOnAttachStateChangeListener(listener);}});animator.start();
        });
    }
}
