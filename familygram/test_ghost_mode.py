#!/usr/bin/env python3
"""Run the actual ghost settings/policy with small platform and TL test doubles."""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"


def run(*args):
    subprocess.run(args, check=True, cwd=ROOT)


def main():
    java = shutil.which("java")
    if not java:
        raise SystemExit("JDK 17 or newer is required")
    names = [
        "TL_messages_readHistory", "TL_channels_readHistory", "TL_messages_readDiscussion",
        "TL_messages_readSavedHistory", "TL_messages_readEncryptedHistory",
        "TL_messages_readMessageContents", "TL_channels_readMessageContents",
        "TL_messages_readMentions", "TL_messages_readReactions", "TL_messages_readPollVotes",
        "TL_messages_setTyping", "TL_messages_setEncryptedTyping", "TL_messages_sendMessage",
    ]
    schema = (JAVA / "org/telegram/tgnet/TLRPC.java").read_text()
    for name in names:
        assert re.search(r"class\s+" + name + r"\s+extends\s+(?:TLObject|TLMethod)\b", schema), name
    for path, name in [("tl/TL_account.java", "updateStatus"),
                       ("tl/TL_stories.java", "TL_stories_readStories"),
                       ("tl/TL_stories.java", "TL_stories_incrementStoryViews")]:
        assert re.search(r"class\s+" + name + r"\b", (JAVA / "org/telegram/tgnet" / path).read_text()), name
    assert "public boolean familygramAllowReadReceipt;" in (JAVA / "org/telegram/tgnet/TLObject.java").read_text()

    ui = (JAVA / "org/telegram/ui/NotificationsSettingsActivity.java").read_text()
    keys = set(re.findall(r"R\.string\.(FamilyGhost\w+)", ui))
    assert len(keys) == 8
    for locale in ("values", "values-ru"):
        resources = ET.parse(ROOT / "TMessagesProj/src/main/res" / locale / "strings.xml").getroot()
        for key in keys:
            matches = [s for s in resources if s.get("name") == key]
            assert len(matches) == 1 and matches[0].text, (locale, key)

    stubs = {
        "android/content/SharedPreferences.java": """package android.content;
public interface SharedPreferences {
    int getInt(String key, int fallback); Editor edit();
    interface Editor { Editor putInt(String key, int value); Editor clear(); void apply(); }
}""",
        "android/content/Context.java": """package android.content;
import java.util.HashMap;
public class Context {
    public static final int MODE_PRIVATE = 0;
    private final HashMap<String, Memory> files = new HashMap<>();
    public SharedPreferences getSharedPreferences(String name, int mode) {
        if (!files.containsKey(name)) files.put(name, new Memory());
        return files.get(name);
    }
    private static class Memory implements SharedPreferences, SharedPreferences.Editor {
        private final HashMap<String, Integer> values = new HashMap<>();
        public int getInt(String key, int fallback) { return values.getOrDefault(key, fallback); }
        public SharedPreferences.Editor edit() { return this; }
        public SharedPreferences.Editor putInt(String key, int value) { values.put(key, value); return this; }
        public SharedPreferences.Editor clear() { values.clear(); return this; }
        public void apply() {}
    }
}""",
        "org/telegram/messenger/ApplicationLoader.java": """package org.telegram.messenger;
public class ApplicationLoader { public static android.content.Context applicationContext = new android.content.Context(); }""",
        "org/telegram/messenger/UserConfig.java": """package org.telegram.messenger;
public class UserConfig { public static final int MAX_ACCOUNT_COUNT = 4; }""",
        "org/telegram/messenger/MessagesController.java": """package org.telegram.messenger;
public class MessagesController {
    public static MessagesController[] instances = {new MessagesController(), new MessagesController(), new MessagesController(), new MessagesController()};
    public int presenceChanges;
    public static MessagesController getInstance(int account) { return instances[account]; }
    public void onGhostModeSettingsChanged(boolean changed) { if (changed) presenceChanges++; }
}""",
        "org/telegram/tgnet/TLObject.java": """package org.telegram.tgnet;
public class TLObject { public boolean familygramAllowReadReceipt; }""",
        "org/telegram/tgnet/TLRPC.java": "package org.telegram.tgnet; public class TLRPC {\n" + "\n".join(
            "public static class " + n + " extends TLObject {}" for n in names) + "\n}",
        "org/telegram/tgnet/tl/TL_account.java": """package org.telegram.tgnet.tl;
public class TL_account { public static class updateStatus extends org.telegram.tgnet.TLObject { public boolean offline; } }""",
        "org/telegram/tgnet/tl/TL_stories.java": """package org.telegram.tgnet.tl;
public class TL_stories {
    public static class TL_stories_readStories extends org.telegram.tgnet.TLObject {}
    public static class TL_stories_incrementStoryViews extends org.telegram.tgnet.TLObject {}
}""",
    }
    with tempfile.TemporaryDirectory(prefix=".ghost-tests-", dir=ROOT / "familygram") as temporary:
        temp = Path(temporary)
        sources = []
        for name, content in stubs.items():
            path = temp / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
            sources.append(str(path))
        sources += [str(JAVA / "org/telegram/messenger" / (name + ".java")) for name in
                    ("GhostModeSettings", "GhostModeController", "GhostModeRequests")]
        sources += [str(p) for p in sorted((ROOT / "familygram/tests").glob("*.java"))]
        classes = temp / "classes"
        classes.mkdir()
        run(java, "com.sun.tools.javac.Main", "-encoding", "UTF-8", "-d", str(classes), *sources)
        run(java, "-cp", str(classes), "GhostModeTest")
        changed = [
            "messenger/GhostModeSettings.java", "messenger/GhostModeController.java", "messenger/GhostModeRequests.java",
            "messenger/MessagesController.java", "messenger/SecretChatHelper.java", "messenger/UserConfig.java",
            "tgnet/TLObject.java", "tgnet/ConnectionsManager.java", "ui/NotificationsSettingsActivity.java",
            "ui/Stories/StoriesController.java",
        ]
        run(java, "-cp", str(classes), "CheckJavaSyntax", *[str(JAVA / "org/telegram" / p) for p in changed])
    print("TL request classes and English/Russian UI strings checked")


if __name__ == "__main__":
    main()
