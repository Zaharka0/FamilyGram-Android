package org.telegram.ui;
import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.telegram.messenger.*;
import org.telegram.messenger.family.*;
import org.telegram.ui.ActionBar.*;
import java.util.function.Consumer;

/** Explicit local-file installation; new or replaced plugin code starts disabled. */
public final class FamilyPluginsActivity extends BaseFragment {
    private static final int IMPORT=7461;private FamilyPlugins manager;private LinearLayout body;
    public FamilyPluginsActivity(int account){currentAccount=account;}
    private String t(String key){return FamilyText.get(key);}
    @Override public boolean onFragmentCreate(){manager=FamilyPlugins.get(currentAccount);return super.onFragmentCreate();}
    @Override public View createView(Context context){
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setTitle(t("Plugins"));actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){public void onItemClick(int id){if(id==-1)finishFragment();}});
        ScrollView scroll=new ScrollView(context);scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);int p=AndroidUtilities.dp(16);body.setPadding(p,p,p,p);scroll.addView(body);fragmentView=scroll;render();return scroll;
    }
    private void text(LinearLayout parent,String value,boolean title){TextView view=new TextView(getParentActivity());view.setText(value);view.setTextSize(title?17:14);view.setTextColor(Theme.getColor(title?Theme.key_windowBackgroundWhiteBlackText:Theme.key_windowBackgroundWhiteGrayText));view.setPadding(0,8,0,8);parent.addView(view,new LinearLayout.LayoutParams(-1,-2));}
    private void button(LinearLayout parent,String value,Runnable action){TextView v=new TextView(getParentActivity());v.setText(value);v.setTextSize(16);v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));v.setPadding(0,16,0,16);v.setMinHeight(AndroidUtilities.dp(48));v.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));v.setOnClickListener(view->{if(!view.isEnabled())return;view.setEnabled(false);try{action.run();}finally{view.postDelayed(()->view.setEnabled(true),800);}});parent.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    private void result(String error){if(getParentActivity()==null)return;if(error!=null)Toast.makeText(getParentActivity(),error,Toast.LENGTH_LONG).show();render();}
    private void render(){if(body==null||getParentActivity()==null)return;body.removeAllViews();text(body,t("PluginsInfo"),false);text(body,t("PluginCompatibility"),false);
        Switch engine=new Switch(getParentActivity());engine.setText(t("EnablePluginEngine"));engine.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));engine.setChecked(manager.enabled());body.addView(engine,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(56)));
        engine.setOnClickListener(v->{engine.setChecked(manager.enabled());if(manager.enabled())manager.engine(false,this::result);else showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(t("EnablePluginEngine")).setMessage(t("PluginTrust")).setNegativeButton(t("Cancel"),null).setPositiveButton(t("Enable"),(d,w)->manager.engine(true,this::result)).create());});
        button(body,t("ImportPlugin"),()->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("*/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,IMPORT);});
        button(body,t("InstallExamplePlugin"),()->manager.example(this::result));
        if(!manager.error().isEmpty())text(body,manager.error(),false);
        JSONArray plugins=manager.list();if(plugins.length()==0)text(body,t("NoPlugins"),false);
        for(int i=0;i<plugins.length();i++){JSONObject plugin=plugins.optJSONObject(i);if(plugin==null)continue;String id=plugin.optString("id");
            LinearLayout card=new LinearLayout(getParentActivity());card.setOrientation(LinearLayout.VERTICAL);card.setPadding(16,12,16,12);card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14),Theme.getColor(Theme.key_windowBackgroundWhite)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=12;body.addView(card,lp);
            text(card,plugin.optString("name")+" · "+plugin.optString("version"),true);text(card,plugin.optString("author")+"\n"+plugin.optString("description"),false);
            Switch active=new Switch(getParentActivity());active.setText(t("PluginEnabled"));active.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));active.setChecked(plugin.optBoolean("enabled"));active.setEnabled(manager.enabled());card.addView(active,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(48)));active.setOnClickListener(v->{active.setEnabled(false);manager.toggle(id,active.isChecked(),this::result);});
            button(card,t("settings"),()->manager.settings(id,rows->settings(id,rows),this::result));
            button(card,t("RemovePlugin"),()->showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(t("RemovePlugin")).setMessage(plugin.optString("name")).setNegativeButton(t("Cancel"),null).setPositiveButton(t("Remove"),(d,w)->manager.remove(id,this::result)).create()));
        }
    }
    private void settings(String id,JSONArray rows){
        LinearLayout content=new LinearLayout(getParentActivity());content.setPadding(24,12,24,12);content.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;String key=row.optString("key"),label=row.optString("text"),type=row.optString("type");
            if(type.equals("switch")){Switch v=new Switch(getParentActivity());v.setText(label);v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));v.setChecked(row.optBoolean("value"));v.setOnCheckedChangeListener((view,on)->manager.setting(id,key,on));content.addView(v,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(48)));}
            else if(type.equals("input")){text(content,label,false);EditText v=new EditText(getParentActivity());v.setText(row.optString("value"));v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));content.addView(v,new LinearLayout.LayoutParams(-1,-2));v.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){manager.setting(id,key,s.toString());}public void afterTextChanged(android.text.Editable e){}});}
            else text(content,label,type.equals("header"));if(!row.optString("description").isEmpty())text(content,row.optString("description"),false);
        }
        ScrollView wrap=new ScrollView(getParentActivity());wrap.addView(content);showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(t("settings")).setView(wrap).setNegativeButton(t("Close"),null).create());
    }
    @Override public void onActivityResultFragment(int request,int result,Intent data){super.onActivityResultFragment(request,result,data);if(request!=IMPORT||result!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        manager.inspect(data.getData(),info->{if(getParentActivity()==null)return;showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(info.optString("name")+" · "+info.optString("version")).setMessage(info.optString("description")+"\n\n"+t("PluginTrust")).setNegativeButton(t("Cancel"),null).setPositiveButton(t("Install"),(d,w)->manager.install(info,this::result)).create());},this::result);
    }
    @Override public void onResume(){super.onResume();render();}
}
