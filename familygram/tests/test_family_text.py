#!/usr/bin/env python3
"""JVM regression for the real FamilyText with a fake Android resource boundary.
Telegram's lookup deliberately returns LOC_ERR:null. This is not a device test.
"""
from pathlib import Path
import json, os, subprocess, tempfile, xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[2]
def main():
    values = {lang: {e.attrib['name']: ''.join(e.itertext()) for e in ET.parse(ROOT / f'TMessagesProj/src/main/res/{folder}/familygram.xml').getroot()} for lang, folder in [('en','values'),('ru','values-ru')]}
    keys = list(values['en'])
    literal = json.dumps
    arrays = '\n'.join('static String[] '+name+'={'+','.join(map(literal, items))+'};' for name, items in [('KEYS',keys),('EN',[values['en'][k] for k in keys]),('RU',[values['ru'][k] for k in keys])])
    sources = {
      'android/content/res/Configuration.java': '''package android.content.res;
public class Configuration {
 public java.util.Locale locale=java.util.Locale.ENGLISH;
 public Configuration(){} public Configuration(Configuration other){locale=other.locale;}
 public void setLocale(java.util.Locale value){locale=value;}
}''',
      'android/content/res/Resources.java': '''package android.content.res;
public class Resources {
 private Configuration config;
 public Resources(Configuration c){config=c;}
 public Configuration getConfiguration(){return config;}
 public int getIdentifier(String name,String type,String pkg){
  if(!type.equals("string")||!pkg.equals("com.familygram.messenger"))throw new AssertionError();
  for(int i=0;i<KEYS.length;i++)if(KEYS[i].equals(name))return i+1;return 0;
 }
 public String getString(int id){return (config.locale.getLanguage().equals("ru")?RU:EN)[id-1];}
''' + arrays + '\n}',
      'android/content/Context.java': '''package android.content;
import android.content.res.*;
public class Context {
 private Resources res;
 public Context(){this(new Configuration());} public Context(Configuration c){res=new Resources(c);}
 public Resources getResources(){return res;}
 public String getPackageName(){return "com.familygram.messenger";}
 public Context createConfigurationContext(Configuration c){return new Context(c);}
}''',
      'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;
public class ApplicationLoader {public static android.content.Context applicationContext=new android.content.Context();}''',
      'org/telegram/messenger/LocaleController.java': '''package org.telegram.messenger;
public class LocaleController {
 public static java.util.Locale language=java.util.Locale.ENGLISH;
 private static LocaleController instance=new LocaleController();
 public static LocaleController getInstance(){return instance;}
 public java.util.Locale getCurrentLocale(){return language;}
 public static String getString(int id){return "LOC_ERR:null";}
}'''
    }
    sources['org/telegram/messenger/BotInlineKeyboard.java'] = 'package org.telegram.messenger;\npublic class BotInlineKeyboard {\n public static class ButtonCustom {\n  public final int id, text, icon;\n  public ButtonCustom(int id,int text,int icon){this.id=id;this.text=text;this.icon=icon;}\n  public String getText(){return LocaleController.getString(text);}\n  public int getIconRes(){return icon;}\n }\n}'
    checks=[]
    for lang in ['ru','en','fr','ru']:
        checks.append('LocaleController.language=java.util.Locale.forLanguageTag('+literal(lang)+');')
        checks.extend('check('+literal(k[2:])+','+literal(values['ru' if lang=='ru' else 'en'][k])+');' for k in keys)
    checks += ['LocaleController.language=null;check("Title","FamilyGram");','check("MissingResource","MissingResource");']
    sources['FamilyTextTest.java']='''import org.telegram.messenger.LocaleController;
import org.telegram.messenger.family.FamilyText;
public class FamilyTextTest {
 static void check(String key,String expected){String actual=FamilyText.get(key); org.telegram.messenger.family.FamilyButton button=new org.telegram.messenger.family.FamilyButton(7401,key,42); if(button.id!=7401 || button.getIconRes()!=42 || !button.getText().equals(expected))throw new AssertionError("Button: "+key+": "+button.getText()); if(!actual.equals(expected))throw new AssertionError(key+": "+actual);}
 public static void main(String[] args){'''+'\n'.join(checks)+f'\nSystem.out.println("FamilyText: {4*len(keys)+2} resource and button checks passed");'+'}}'
    with tempfile.TemporaryDirectory(prefix='familygram-text-') as temp:
        paths=[]
        for path,content in sources.items():
            p=Path(temp)/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content);paths.append(str(p))
        java=os.environ.get('JAVA','java')
        subprocess.run([java,'com.sun.tools.javac.Main','-d',temp,*paths,str(ROOT/'TMessagesProj/src/main/java/org/telegram/messenger/family/FamilyText.java'),str(ROOT/'TMessagesProj/src/main/java/org/telegram/messenger/family/FamilyButton.java')],check=True)
        subprocess.run([java,'-cp',temp,'FamilyTextTest'],check=True)
if __name__=='__main__':main()
