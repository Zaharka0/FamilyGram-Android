package org.telegram.messenger.family;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import org.telegram.messenger.*;
import org.telegram.ui.LaunchActivity;
import java.io.*;
import java.util.UUID;
import java.util.function.Consumer;
public final class FamilyPhotos {
    private FamilyPhotos(){}
    public static void importPhoto(int account,Uri uri,boolean widget,Activity activity,Consumer<String> done){
        FamilyDataController data=FamilyDataController.getInstance(account);long owner=data.self;
        Utilities.globalQueue.postRunnable(()->{
            String result;Bitmap bitmap=null;
            try{
                Context c=ApplicationLoader.applicationContext;BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                try(InputStream stream=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(stream,null,bounds);}
                if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IOException("Unsupported image");
                BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1024)options.inSampleSize*=2;
                try(InputStream stream=c.getContentResolver().openInputStream(uri)){bitmap=BitmapFactory.decodeStream(stream,null,options);}
                if(bitmap==null)throw new IOException("Unable to decode image");
                int orientation=androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL;
                try(InputStream exif=c.getContentResolver().openInputStream(uri)){orientation=new androidx.exifinterface.media.ExifInterface(exif).getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,1);}catch(Exception ignored){}
                Matrix rotate=new Matrix();
                switch(orientation){case 2:rotate.setScale(-1,1);break;case 3:rotate.setRotate(180);break;case 4:rotate.setRotate(180);rotate.postScale(-1,1);break;case 5:rotate.setRotate(90);rotate.postScale(-1,1);break;case 6:rotate.setRotate(90);break;case 7:rotate.setRotate(-90);rotate.postScale(-1,1);break;case 8:rotate.setRotate(-90);break;default:break;}
                if(!rotate.isIdentity()){Bitmap fixed=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),rotate,true);if(fixed!=bitmap)bitmap.recycle();bitmap=fixed;}
                Bitmap square=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(square);int side=Math.min(bitmap.getWidth(),bitmap.getHeight());Rect src=new Rect((bitmap.getWidth()-side)/2,(bitmap.getHeight()-side)/2,(bitmap.getWidth()+side)/2,(bitmap.getHeight()+side)/2);canvas.drawBitmap(bitmap,src,new Rect(0,0,256,256),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
                File folder=new File(c.getFilesDir(),"familygram_photos_"+owner);if(!folder.exists()&&!folder.mkdirs())throw new IOException("Unable to save image");
                File file=new File(folder,widget?"widget.png":"shortcut_"+UUID.randomUUID()+".png");try(OutputStream stream=new FileOutputStream(file)){if(!square.compress(Bitmap.CompressFormat.PNG,100,stream))throw new IOException("Unable to save image");}
                if(UserConfig.getInstance(account).getClientUserId()!=owner){file.delete();square.recycle();return;}
                if(widget){data.prefs.edit().putString("widget_photo",file.getAbsolutePath()).apply();AndroidUtilities.runOnUIThread(()->{FamilyPairWidget.updateAll(c);done.accept(FamilyText.get("PhotoSaved"));});}
                else{
                    Bitmap shortcut=square;AndroidUtilities.runOnUIThread(()->{
                        if(activity.isFinishing())return;
                        if(Build.VERSION.SDK_INT>=26){ShortcutManager manager=activity.getSystemService(ShortcutManager.class);if(manager!=null&&manager.isRequestPinShortcutSupported()){
                            String id="familygram-photo-"+UUID.randomUUID();Intent launch=new Intent(activity,LaunchActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                            ShortcutInfo info=new ShortcutInfo.Builder(activity,id).setShortLabel("FamilyGram").setIcon(Icon.createWithAdaptiveBitmap(shortcut)).setIntent(launch).build();
                            boolean accepted=manager.requestPinShortcut(info,null);done.accept(FamilyText.get(accepted?"ConfirmShortcut":"ShortcutUnsupported"));return;
                        }}done.accept(FamilyText.get("ShortcutUnsupported"));
                    });
                }
            }catch(Exception e){FileLog.e(e);result=FamilyText.get("PhotoError");String error=result;AndroidUtilities.runOnUIThread(()->done.accept(error));}
            finally{if(bitmap!=null)bitmap.recycle();}
        });
    }
}
