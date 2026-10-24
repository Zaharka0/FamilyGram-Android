package org.telegram.messenger.family;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.LaunchActivity;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Only this repository's public, non-prerelease GitHub Releases are update sources. */
public final class FamilyUpdates {
    public static final String VERSION="1.2.0";
    private static final String REPO="Zaharka0/FamilyGram-Android";
    private static boolean checking;
    private FamilyUpdates(){}
    public static void onResume(){
        if(FamilyPrivacyScreen.enabled())return;BaseFragment f=LaunchActivity.getSafeLastFragment();if(f==null)return;FamilyDataController data=FamilyDataController.getInstance(f.getCurrentAccount());FamilyPlugins.resume(f.getCurrentAccount());
        if(data.prefs.getBoolean("updates",false)&&System.currentTimeMillis()-data.prefs.getLong("update_check",0)>86400000L)check(f,true);
    }
    public static void check(BaseFragment fragment,boolean quiet){
        if(checking||fragment.getParentActivity()==null)return;checking=true;FamilyDataController data=FamilyDataController.getInstance(fragment.getCurrentAccount());data.prefs.edit().putLong("update_check",System.currentTimeMillis()).apply();
        Utilities.globalQueue.postRunnable(()->{
            String title=FamilyText.get("Updates"),message=null,url=null,tag=null;HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL("https://api.github.com/repos/"+REPO+"/releases?per_page=20").openConnection();connection.setConnectTimeout(12000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept","application/vnd.github+json");connection.setRequestProperty("User-Agent","FamilyGram/"+VERSION);
                if(connection.getResponseCode()==404)message=FamilyText.get("NoRelease");
                else if(connection.getResponseCode()!=200)throw new IOException("HTTP "+connection.getResponseCode());
                else{
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream stream=connection.getInputStream()){byte[]buffer=new byte[4096];int n;while((n=stream.read(buffer))!=-1){if(bytes.size()+n>524288)throw new IOException("Response too large");bytes.write(buffer,0,n);}}
                    JSONArray releases=new JSONArray(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
                    JSONObject release=null;
                    for(int i=0;i<releases.length();i++){
                        JSONObject candidate=releases.getJSONObject(i);String candidateTag=candidate.optString("tag_name");
                        if(candidate.optBoolean("draft")||candidate.optBoolean("prerelease")||!FamilyReleasePolicy.valid(candidateTag))continue;
                        if(release==null||compare(candidateTag,release.getString("tag_name"))>0)release=candidate;
                    }
                    if(release==null)message=FamilyText.get("NoRelease");
                    else{
                        tag=release.getString("tag_name");
                        android.content.pm.PackageInfo installed=ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(),0);
                        long code=android.os.Build.VERSION.SDK_INT>=28?installed.getLongVersionCode():installed.versionCode;
                        if(!FamilyReleasePolicy.newer(tag,VERSION,code))message=FamilyText.get("UpToDate")+" · "+VERSION+" ("+code+")";
                        else{
                            JSONArray assets=release.optJSONArray("assets");
                            if(assets!=null)for(int i=0;i<assets.length();i++){JSONObject asset=assets.getJSONObject(i);String candidate=asset.optString("browser_download_url");if(asset.optString("name").endsWith(".apk")&&asset.optLong("size")>0&&trusted(candidate)){url=candidate;break;}}
                            if(url==null)message=FamilyText.get("NoApk");else{title="FamilyGram · "+tag;String notes=release.optString("body","");message=notes.substring(0,Math.min(6000,notes.length()));}
                        }
                    }
                }
            }catch(Exception error){FileLog.e(error);message=FamilyText.get("UpdateError");}finally{if(connection!=null)connection.disconnect();}
            final String dialogTitle=title,body=message,download=url,version=tag;
            AndroidUtilities.runOnUIThread(()->{
                checking=false;if(fragment.getParentActivity()==null||fragment.isPaused()||FamilyPrivacyScreen.enabled())return;
                if(quiet&&(download==null||version.equals(data.prefs.getString("update_notified",""))))return;
                AlertDialog.Builder dialog=new AlertDialog.Builder(fragment.getParentActivity()).setTitle(dialogTitle).setMessage(body).setNegativeButton(FamilyText.get("Close"),null);
                if(download!=null){data.prefs.edit().putString("update_notified",version).apply();dialog.setPositiveButton(FamilyText.get("DownloadApk"),(d,w)->Browser.openUrl(fragment.getParentActivity(),download));}
                if(download==null&&!quiet)dialog.setNeutralButton(FamilyText.get("OpenReleases"),(d,w)->Browser.openUrl(fragment.getParentActivity(),"https://github.com/"+REPO+"/releases"));
                fragment.showDialog(dialog.create());
            });
        });
    }
    static boolean trusted(String url){return FamilyReleasePolicy.trusted(url);}
    public static int compare(String a,String b){return FamilyReleasePolicy.compare(a,b);}
}
