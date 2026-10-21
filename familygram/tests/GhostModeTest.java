import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.GhostModeController;
import org.telegram.messenger.GhostModeRequests;
import org.telegram.messenger.GhostModeSettings;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class GhostModeTest {
    private static int checks;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static final TLObject[] reads = {
        new TLRPC.TL_messages_readHistory(), new TLRPC.TL_channels_readHistory(),
        new TLRPC.TL_messages_readDiscussion(), new TLRPC.TL_messages_readSavedHistory(),
        new TLRPC.TL_messages_readEncryptedHistory(), new TLRPC.TL_messages_readMessageContents(),
        new TLRPC.TL_channels_readMessageContents(), new TLRPC.TL_messages_readMentions(),
        new TLRPC.TL_messages_readReactions(), new TLRPC.TL_messages_readPollVotes()
    };

    public static void main(String[] args) throws Exception {
        GhostModeSettings defaults = new GhostModeSettings(GhostModeSettings.DEFAULT_FLAGS);
        check(!defaults.selected(GhostModeSettings.ENABLED), "off by default");
        check(!defaults.restoreOffline(), "automatic offline is opt-in");

        TL_account.updateStatus online = new TL_account.updateStatus();
        TL_account.updateStatus offline = new TL_account.updateStatus();
        offline.offline = true;
        TLObject[] stories = {new TL_stories.TL_stories_readStories(), new TL_stories.TL_stories_incrementStoryViews()};
        TLObject[] typing = {new TLRPC.TL_messages_setTyping(), new TLRPC.TL_messages_setEncryptedTyping()};
        for (int flags = 0; flags < 64; flags++) {
            GhostModeSettings settings = new GhostModeSettings(flags);
            boolean enabled = (flags & GhostModeSettings.ENABLED) != 0;
            for (TLObject read : reads) {
                read.familygramAllowReadReceipt = false;
                check(GhostModeRequests.shouldSuppress(settings, read) == (enabled && (flags & GhostModeSettings.HIDE_READ) != 0), "read flags=" + flags);
                read.familygramAllowReadReceipt = true;
                check(!GhostModeRequests.shouldSuppress(settings, read), "timer receipt flags=" + flags);
            }
            for (TLObject story : stories) {
                story.familygramAllowReadReceipt = true;
                check(GhostModeRequests.shouldSuppress(settings, story) == (enabled && (flags & GhostModeSettings.HIDE_STORIES) != 0), "story independently protected flags=" + flags);
            }
            for (TLObject action : typing) {
                action.familygramAllowReadReceipt = true;
                check(GhostModeRequests.shouldSuppress(settings, action) == (enabled && (flags & GhostModeSettings.HIDE_TYPING) != 0), "typing independently protected flags=" + flags);
            }
            check(GhostModeRequests.shouldSuppress(settings, online) == (enabled && (flags & GhostModeSettings.HIDE_ONLINE) != 0), "online flags=" + flags);
            check(!GhostModeRequests.shouldSuppress(settings, offline), "offline must always pass");
            check(!GhostModeRequests.shouldSuppress(settings, new TLRPC.TL_messages_sendMessage()), "ordinary sending must pass");
            check(!GhostModeRequests.shouldSuppress(settings, new TLObject()), "unrelated requests must pass");
            check(settings.restoreOffline() == (enabled && (flags & GhostModeSettings.HIDE_ONLINE) != 0 && (flags & GhostModeSettings.RESTORE_OFFLINE) != 0), "offline dependency");
        }

        GhostModeController.clear(0);
        GhostModeController.clear(1);
        GhostModeController.set(0, GhostModeSettings.ENABLED, true);
        check(GhostModeController.get(0).active(GhostModeSettings.HIDE_READ), "account zero active");
        check(!GhostModeController.get(1).active(GhostModeSettings.HIDE_READ), "account one unaffected");
        check(MessagesController.instances[0].presenceChanges == 1, "presence transition notified");
        GhostModeController.set(0, GhostModeSettings.HIDE_TYPING, false);
        GhostModeController.set(0, GhostModeSettings.ENABLED, false);
        GhostModeController.set(0, GhostModeSettings.ENABLED, true);
        check(!GhostModeController.get(0).active(GhostModeSettings.HIDE_TYPING), "master preserves individual choices");

        Field field = GhostModeController.class.getDeclaredField("settings");
        field.setAccessible(true);
        ((AtomicReferenceArray<?>) field.get(null)).set(0, null);
        check(GhostModeController.get(0).active(GhostModeSettings.HIDE_READ), "master persisted across controller reload");
        check(!GhostModeController.get(0).selected(GhostModeSettings.HIDE_TYPING), "individual choice persisted");
        GhostModeController.set(1, GhostModeSettings.ENABLED, true);
        GhostModeController.clear(0);
        ((AtomicReferenceArray<?>) field.get(null)).set(0, null);
        check(!GhostModeController.get(0).selected(GhostModeSettings.ENABLED), "logout removes persisted mode");
        check(GhostModeController.get(0).selected(GhostModeSettings.HIDE_TYPING), "new login gets defaults");
        check(GhostModeController.get(1).active(GhostModeSettings.HIDE_READ), "logout does not affect other account");
        System.out.println("Ghost mode: " + checks + " checks passed");
    }
}
