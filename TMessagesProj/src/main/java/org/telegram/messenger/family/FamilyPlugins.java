package org.telegram.messenger.family;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ActionBar.BaseFragment;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Trusted, opt-in Python extensions. No claim of Xposed or full exteraGram SDK compatibility. */
public final class FamilyPlugins {
    private static final FamilyPlugins[] INSTANCES=new FamilyPlugins[UserConfig.MAX_ACCOUNT_COUNT];
    private static final ThreadLocal<Boolean> BYPASS=new ThreadLocal<>();
    private static final Object PYTHON_LOCK=new Object();
    private static void startPython(){synchronized(PYTHON_LOCK){if(!Python.isStarted())Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));}}
    public static synchronized FamilyPlugins get(int account){
        long uid=UserConfig.getInstance(account).getClientUserId();FamilyPlugins p=INSTANCES[account];
        if(p==null||p.uid!=uid){if(p!=null)p.close(false);INSTANCES[account]=p=new FamilyPlugins(account,uid);}return p;
    }
    public static synchronized void logout(int account){FamilyPlugins p=INSTANCES[account];if(p!=null){p.close(true);INSTANCES[account]=null;}}
    public static void resume(int account){if(UserConfig.getInstance(account).isClientActivated())get(account).warm();}
    public static boolean intercept(int account,SendMessagesHelper.SendMessageParams params,Runnable continuation){
        if(Boolean.TRUE.equals(BYPASS.get())||params.retryMessageObject!=null||FamilyCore.fromMessage(params.message)!=null)return false;
        FamilyPlugins p=get(account);if(!p.enabled())return false;
        p.warm();AtomicBoolean finished=new AtomicBoolean();
        Runnable deliver=()->{if(!p.alive())return;BYPASS.set(true);try{continuation.run();}finally{BYPASS.remove();}};
        Runnable watchdog=()->{if(finished.compareAndSet(false,true)){p.prefs.edit().putBoolean("engine",false).putString("error",FamilyText.get("PluginTimeout")).apply();deliver.run();}};
        AndroidUtilities.runOnUIThread(watchdog,2500);
        p.queue.postRunnable(()->{
            boolean send=true;
            if(p.alive()&&p.enabled())for(String id:new ArrayList<>(p.loaded)){
                String originalMessage=params.message,originalCaption=params.caption;
                try{p.mark(id);if(!p.host().callAttr("send",p.uid,id,account,params).toBoolean()){send=false;break;}}
                catch(Exception error){params.message=originalMessage;params.caption=originalCaption;p.fail(id,error);}
                finally{p.unmark();}
            }
            final boolean shouldSend=send;
            AndroidUtilities.runOnUIThread(()->{AndroidUtilities.cancelRunOnUIThread(watchdog);if(finished.compareAndSet(false,true)&&shouldSend)deliver.run();});
        });return true;
    }
    public static void incoming(int account,MessageObject message){
        FamilyPlugins p=get(account);if(!p.enabled()||message.isOut()||FamilyCore.fromMessage(message.messageOwner.message)!=null)return;p.warm();
        p.queue.postRunnable(()->{if(!p.alive()||!p.enabled())return;for(String id:new ArrayList<>(p.loaded))try{p.mark(id);p.host().callAttr("receive",p.uid,id,account,message);}catch(Exception e){p.fail(id,e);}finally{p.unmark();}});
    }
    public final int account;public final long uid;private final File root;
    private final SharedPreferences prefs;private final DispatchQueue queue;
    private final Set<String> loaded=new LinkedHashSet<>();private boolean warming;private volatile boolean disposed;
    private FamilyPlugins(int account,long uid){this.account=account;this.uid=uid;Context c=ApplicationLoader.applicationContext;root=new File(c.getFilesDir(),"family_plugins_"+uid);prefs=c.getSharedPreferences("family_plugins_"+uid,Context.MODE_PRIVATE);queue=new DispatchQueue("FamilyPlugins-"+uid);}
    private boolean alive(){return !disposed&&uid>0&&UserConfig.getInstance(account).getClientUserId()==uid;}
    public boolean enabled(){return alive()&&prefs.getBoolean("engine",false);}
    public String error(){return prefs.getString("error","");}
    private PyObject host(){startPython();return Python.getInstance().getModule("family_plugin_host");}
    private File source(String id){if(!id.matches("[A-Za-z][A-Za-z0-9_-]{1,31}"))throw new IllegalArgumentException("Invalid plugin ID");return new File(root,id+".py");}
    private static String read(InputStream stream)throws IOException{
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>262144)throw new IOException("Plugin file exceeds 256 KB");out.write(b,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}
    }
    private static void write(File file,String text)throws IOException{
        File temp=new File(file.getParentFile(),file.getName()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}if(!temp.renameTo(file))throw new IOException("Unable to save plugin");
    }
    private void mark(String id){prefs.edit().putString("running",id).commit();}
    private void unmark(){prefs.edit().remove("running").apply();}
    private void fail(String id,Exception e){loaded.remove(id);prefs.edit().putBoolean("enabled_"+id,false).putString("error",id+": "+e.getClass().getSimpleName()+" · "+String.valueOf(e.getMessage()).substring(0,Math.min(800,String.valueOf(e.getMessage()).length()))).apply();try{host().callAttr("unload",uid,id);}catch(Exception ignored){}FileLog.e(e);}
    private synchronized void warm(){if(warming||!enabled())return;warming=true;queue.postRunnable(()->{
        if(!alive()||!enabled())return;String crashed=prefs.getString("running","");if(!crashed.isEmpty()){prefs.edit().putBoolean("enabled_"+crashed,false).putString("error",FamilyText.get("PluginRecovered")+": "+crashed).remove("running").apply();}
        File[] files=root.listFiles((dir,name)->name.endsWith(".py"));if(files!=null)for(File f:files){String id=f.getName().substring(0,f.getName().length()-3);if(prefs.getBoolean("enabled_"+id,false))load(id);}
    });}
    private void load(String id){if(!alive()||!enabled())return;try{PyObject runtime=host();if(!alive()||!enabled())return;mark(id);runtime.callAttr("load",uid,read(new FileInputStream(source(id))),new Bridge(this,id));if(enabled())loaded.add(id);else runtime.callAttr("unload",uid,id);}catch(Exception e){fail(id,e);}finally{unmark();}}
    private void task(Runnable action,Consumer<String> done){queue.postRunnable(()->{String error=null;try{if(!alive())throw new IllegalStateException("Account changed");action.run();}catch(Exception e){FileLog.e(e);error=String.valueOf(e.getMessage());}final String result=error;AndroidUtilities.runOnUIThread(()->{if(alive())done.accept(result);});});}
    public void engine(boolean on,Consumer<String> done){
        prefs.edit().putBoolean("engine",on).apply();synchronized(this){warming=false;}
        task(()->{for(String id:new ArrayList<>(loaded))try{host().callAttr("unload",uid,id);}catch(Exception e){FileLog.e(e);}finally{loaded.remove(id);}if(on)warm();},done);
    }
    public void example(Consumer<String> done){queue.postRunnable(()->{try{String code=read(ApplicationLoader.applicationContext.getAssets().open("family_plugins/heart_signature.plugin"));JSONObject info=new JSONObject(host().callAttr("metadata",code).toString());info.put("source",code);install(info,done);}catch(Exception e){AndroidUtilities.runOnUIThread(()->done.accept(String.valueOf(e.getMessage())));}});}
    public void inspect(Uri uri,Consumer<JSONObject> result,Consumer<String> error){queue.postRunnable(()->{try{if(!alive())return;String code=read(ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri));JSONObject info=new JSONObject(host().callAttr("metadata",code).toString());info.put("source",code);AndroidUtilities.runOnUIThread(()->{if(alive())result.accept(info);});}catch(Exception e){AndroidUtilities.runOnUIThread(()->{if(alive())error.accept(String.valueOf(e.getMessage()));});}});}
    public void install(JSONObject info,Consumer<String> done){task(()->{try{String code=info.getString("source");JSONObject checked=new JSONObject(host().callAttr("metadata",code).toString());String id=checked.getString("id");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Plugin folder unavailable");if(!source(id).isFile()&&list().length()>=50)throw new IOException("At most 50 plugins");if(loaded.contains(id))host().callAttr("unload",uid,id);loaded.remove(id);prefs.edit().putBoolean("enabled_"+id,false).apply();write(source(id),code);write(new File(root,id+".json"),checked.toString());}catch(Exception e){throw new IllegalArgumentException(e);}},done);}
    public JSONArray list(){JSONArray result=new JSONArray();File[] files=root.listFiles((dir,name)->name.endsWith(".json"));if(files==null)return result;Arrays.sort(files,Comparator.comparing(File::getName));for(File f:files)try{JSONObject p=new JSONObject(read(new FileInputStream(f)));p.put("enabled",prefs.getBoolean("enabled_"+p.getString("id"),false));result.put(p);}catch(Exception ignored){}return result;}
    public void toggle(String id,boolean on,Consumer<String> done){task(()->{if(on&&!enabled())throw new IllegalStateException(FamilyText.get("EnablePluginEngine"));prefs.edit().putBoolean("enabled_"+id,on).apply();if(on){load(id);}else{try{host().callAttr("unload",uid,id);}finally{loaded.remove(id);}}},done);}
    public void remove(String id,Consumer<String> done){task(()->{try{if(loaded.contains(id))host().callAttr("unload",uid,id);}finally{loaded.remove(id);source(id).delete();new File(root,id+".json").delete();SharedPreferences.Editor e=prefs.edit().remove("enabled_"+id);for(String key:prefs.getAll().keySet())if(key.startsWith("setting_"+id+":"))e.remove(key);e.apply();}},done);}
    public void settings(String id,Consumer<JSONArray> done,Consumer<String> error){queue.postRunnable(()->{try{if(!alive()||!loaded.contains(id))throw new IllegalStateException(FamilyText.get("EnablePluginFirst"));JSONArray rows=new JSONArray(host().callAttr("settings",uid,id).toString());AndroidUtilities.runOnUIThread(()->{if(alive())done.accept(rows);});}catch(Exception e){AndroidUtilities.runOnUIThread(()->{if(alive())error.accept(String.valueOf(e.getMessage()));});}});}
    public void setting(String id,String key,Object value){new Bridge(this,id).setSetting(key,value==null?"null":value instanceof String?JSONObject.quote((String)value):String.valueOf(value));}
    private void close(boolean erase){disposed=true;prefs.edit().putBoolean("engine",false).apply();queue.postRunnable(()->{for(String id:new ArrayList<>(loaded))try{host().callAttr("unload",uid,id);}catch(Exception ignored){}loaded.clear();if(erase){File[] files=root.listFiles();if(files!=null)for(File f:files)f.delete();root.delete();prefs.edit().clear().apply();}});}
    public static final class Bridge {
        private final FamilyPlugins owner;private final String id;
        Bridge(FamilyPlugins owner,String id){this.owner=owner;this.id=id;}
        private void check(){if(!owner.alive())throw new IllegalStateException("Account changed");}
        public String getSetting(String key){check();return owner.prefs.getString("setting_"+id+":"+key,null);}
        public void setSetting(String key,String json){check();if(key.length()>100||json.length()>16384)throw new IllegalArgumentException("Setting too long");owner.prefs.edit().putString("setting_"+id+":"+key,json).apply();}
        public void runOnUI(PyObject callback){check();owner.queue.postRunnable(()->AndroidUtilities.runOnUIThread(()->{if(!owner.alive()||!owner.enabled()||!owner.prefs.getBoolean("enabled_"+id,false))return;try{owner.host().callAttr("ui",owner.uid,id,callback);}catch(Exception e){owner.queue.postRunnable(()->owner.fail(id,e));}}));}
        public int getAccount(){check();return owner.account;}
        public long getPartner(){check();return FamilyDataController.getInstance(owner.account).partner();}
        public void log(String message){check();FileLog.d("FamilyPlugin "+id+": "+message);}
    }
}
