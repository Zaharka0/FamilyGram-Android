#!/usr/bin/env python3
"""No Android runtime or API credentials: protocol/state tests, syntax and resources."""
from pathlib import Path
import os,re,subprocess,sys,tempfile,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
os.chdir(ROOT)
java=os.environ.get('JAVA','java')
with tempfile.TemporaryDirectory(prefix='familygram-tests-') as output:
    subprocess.run([java,'com.sun.tools.javac.Main','-d',output,
        'TMessagesProj/src/main/java/org/telegram/messenger/family/FamilyCore.java',
        'TMessagesProj/src/main/java/org/telegram/messenger/family/FamilyTapGuard.java',
        'TMessagesProj/src/main/java/org/telegram/messenger/family/FamilyPrivacyPolicy.java',
        'TMessagesProj/src/main/java/org/telegram/messenger/family/FamilySections.java',
        'TMessagesProj/src/main/java/org/telegram/messenger/family/FamilyStoryPolicy.java',
        'TMessagesProj/src/main/java/org/telegram/messenger/family/FamilyReleasePolicy.java',
        'familygram/tests/FamilySettingsTest.java',
        'familygram/tests/FamilyCoreTest.java','familygram/tests/CheckJavaSyntax.java'],check=True)
    subprocess.run([java,'-cp',output,'FamilyCoreTest'],check=True)
    subprocess.run([java,'-cp',output,'FamilySettingsTest'],check=True)
    modified=['SendMessagesHelper','NotificationsController','MediaController','MessageObject','MessagesController','UserConfig','FamilyController']
    files=[f'TMessagesProj/src/main/java/org/telegram/messenger/{name}.java' for name in modified]
    files += [str(p) for p in Path('TMessagesProj/src/main/java/org/telegram/messenger/family').glob('*.java')]
    files += [f'TMessagesProj/src/main/java/org/telegram/ui/{name}.java' for name in ['ChatActivity','LaunchActivity','SettingsActivity','FamilyHubActivity','FamilyWatchActivity','Components/VideoPlayer','Stories/StoriesController','Stories/StoryViewer','FamilyPluginsActivity','LauncherIconController','Cells/AppIconsSelectorCell']]
    subprocess.run([java,'-cp',output,'CheckJavaSyntax',*files],check=True)
    update_source=Path('TMessagesProj/src/main/java/org/telegram/messenger/family/FamilyUpdates.java').read_text()
    assert re.search(r'VERSION="([^"]+)"',update_source).group(1)==Path('familygram/VERSION').read_text().strip(),'Update/release version mismatch'
    en=ET.parse('TMessagesProj/src/main/res/values/familygram.xml').getroot()
    ru=ET.parse('TMessagesProj/src/main/res/values-ru/familygram.xml').getroot()
    keys=[x.attrib['name'] for x in en]
    assert len(set(keys))==len(keys),'Duplicate FamilyGram string'
    assert set(keys)=={x.attrib['name'] for x in ru},'Language keys differ'
    for name in ['TMessagesProj/src/main/AndroidManifest.xml','TMessagesProj/src/main/res/layout/family_pair_widget.xml','TMessagesProj/src/main/res/xml/family_pair_widget_info.xml']:
        ET.parse(name)
    new_files=list(Path('TMessagesProj/src/main/java/org/telegram/messenger/family').glob('*.java'))+[Path('TMessagesProj/src/main/java/org/telegram/ui/FamilyHubActivity.java'),Path('TMessagesProj/src/main/java/org/telegram/ui/FamilyWatchActivity.java')]
    schema=Path('TMessagesProj/src/main/java/org/telegram/tgnet/TLRPC.java').read_text()
    tl_classes=set(re.findall(r'\b(?:class|enum)\s+(\w+)',schema))
    for source_file in new_files:
        referenced=set(re.findall(r'TLRPC\.(\w+)',source_file.read_text()))
        assert referenced <= tl_classes,f'{source_file}: unknown Telegram types {referenced-tl_classes}'
    direct=set()
    for p in new_files:
        text=p.read_text()
        direct.update(re.findall(r'\bt\("([A-Za-z]+)"\)',text))
        direct.update(re.findall(r'FamilyText.get\("([A-Za-z]+)"\)',text))
        direct.update(re.findall(r'R.string.FG([A-Za-z]+)',text))
        direct.update(re.findall(r'new FamilyButton\([^,]+,"([A-Za-z]+)"',text))
    missing={'FG'+x for x in direct}-set(keys)
    assert not missing,f'Missing strings: {missing}'
    print(f'FamilyGram resources: {len(keys)} English/Russian strings, XML and direct references passed')

subprocess.run([sys.executable, 'familygram/tests/test_family_text.py'], check=True)

# FamilyText reads bundled Android resources; APK locale filtering must retain Russian.
for module in ['TMessagesProj_App', 'TMessagesProj_AppHuawei', 'TMessagesProj_AppHockeyApp', 'TMessagesProj_AppStandalone', 'TMessagesProj_AppTests']:
    gradle = (ROOT / module / 'build.gradle').read_text()
    locale_filter = re.search(r'localeFilters\s*\+=\s*\[([^]]+)\]', gradle)
    assert locale_filter and 'ru' in re.findall(r'[\"\']([^\"\']+)[\"\']', locale_filter.group(1)), f'{module}: Russian strings excluded from APK'
print('APK locale filters: Russian retained in all five app modules')

story_source=(ROOT/'TMessagesProj/src/main/java/org/telegram/ui/Stories/StoriesController.java').read_text()
for start,end,send in [('public boolean markStoryAsRead(TL_stories.PeerStories','public int getMaxStoriesReadId','new TL_stories.TL_stories_readStories'),('protected boolean markAsRead(int storyId)','protected boolean canLoad()','new TL_stories.TL_stories_incrementStoryViews')]:
    method=story_source.split(start,1)[1].split(end,1)[0]
    assert method.index('shouldHideStoryView')<method.index(send),'Story receipt bypasses ghost guard'
workflow=(ROOT/'.github/workflows/android.yml').read_text()
assert 'default: true\n      abis:' in workflow,'APK release publishing must default on'
assert 'TAG="familygram-$VERSION-b$APK_CODE"' in workflow,'Updater release/build tag mismatch'
assert 'APP_VERSION_CODE=$BUILD_CODE' in workflow,'Update rebuild must increment installed APK code'
print('Story receipt entry points and release workflow wiring passed')

subprocess.run([sys.executable, 'familygram/tests/test_plugins.py'], check=True)
# Alias names survive updates, while all six icon files and previews are distinct.
manifest=ET.parse(ROOT/'TMessagesProj/src/main/AndroidManifest.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
aliases={node.attrib[ns+'name']:node for node in manifest.find('application').findall('activity-alias')}
for index,key in enumerate(['DefaultIcon','VintageIcon','AquaIcon','PremiumIcon','TurboIcon','NoxIcon'],1):
    assert aliases['org.telegram.messenger.'+key].attrib[ns+'icon']=='@mipmap/family_icon_'+str(index)
    ET.parse(ROOT/f'TMessagesProj/src/main/res/mipmap-anydpi-v26/family_icon_{index}.xml')
    assert (ROOT/f'TMessagesProj/src/main/res/mipmap-nodpi/family_icon_{index}.jpg').is_file()
import hashlib
photo_pack=ET.parse(ROOT/'TMessagesProj/src/main/res/values/family_photo_pack.xml').getroot().find('bool').text=='true'
hashes={hashlib.sha256((ROOT/f'TMessagesProj/src/main/res/mipmap-nodpi/family_icon_{i}.jpg').read_bytes()).hexdigest() for i in range(1,7)}
assert len(hashes)==(6 if photo_pack else 1), 'Private photo pack or public author-icon resources mismatch'
print('Six launcher alias slots, private/public icon packaging and plugin lifecycle tests passed')
