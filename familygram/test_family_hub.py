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
        'familygram/tests/FamilyCoreTest.java','familygram/tests/CheckJavaSyntax.java'],check=True)
    subprocess.run([java,'-cp',output,'FamilyCoreTest'],check=True)
    modified=['MediaController','MessageObject','MessagesController','UserConfig','FamilyController']
    files=[f'TMessagesProj/src/main/java/org/telegram/messenger/{name}.java' for name in modified]
    files += [str(p) for p in Path('TMessagesProj/src/main/java/org/telegram/messenger/family').glob('*.java')]
    files += [f'TMessagesProj/src/main/java/org/telegram/ui/{name}.java' for name in ['ChatActivity','LaunchActivity','SettingsActivity','FamilyHubActivity','FamilyWatchActivity','Components/VideoPlayer']]
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
