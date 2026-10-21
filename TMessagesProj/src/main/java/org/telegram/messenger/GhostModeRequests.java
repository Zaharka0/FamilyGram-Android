package org.telegram.messenger;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

/** Checks the final outgoing request, including requests from background components. */
public final class GhostModeRequests {
    private GhostModeRequests() {
    }

    public static boolean shouldSuppress(GhostModeSettings settings, TLObject request) {
        if (settings.active(GhostModeSettings.HIDE_ONLINE)
                && request instanceof TL_account.updateStatus) {
            return !((TL_account.updateStatus) request).offline;
        }
        if (settings.active(GhostModeSettings.HIDE_TYPING)
                && (request instanceof TLRPC.TL_messages_setTyping
                || request instanceof TLRPC.TL_messages_setEncryptedTyping)) {
            return true;
        }
        if (settings.active(GhostModeSettings.HIDE_STORIES)
                && (request instanceof TL_stories.TL_stories_readStories
                || request instanceof TL_stories.TL_stories_incrementStoryViews)) {
            return true;
        }
        return settings.active(GhostModeSettings.HIDE_READ)
                && !request.familygramAllowReadReceipt
                && (request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_channels_readHistory
                || request instanceof TLRPC.TL_messages_readDiscussion
                || request instanceof TLRPC.TL_messages_readSavedHistory
                || request instanceof TLRPC.TL_messages_readEncryptedHistory
                || request instanceof TLRPC.TL_messages_readMessageContents
                || request instanceof TLRPC.TL_channels_readMessageContents
                || request instanceof TLRPC.TL_messages_readMentions
                || request instanceof TLRPC.TL_messages_readReactions
                || request instanceof TLRPC.TL_messages_readPollVotes);
    }
}
