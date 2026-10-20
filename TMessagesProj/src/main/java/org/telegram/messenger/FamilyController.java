package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.tgnet.TLRPC;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * FamilyGram: local (device-only) list of "family" chats and the
 * "do not disturb, except family" mode. Nothing here is sent to the server.
 */
public class FamilyController {

    private static final String PREFS = "familygram";
    private static final String KEY_IDS = "family_ids";
    private static final String KEY_ONLY_MODE = "family_only_mode";
    private static final String KEY_KEEP_DELETED = "keep_deleted";
    private static final String KEY_DELETED_SET = "deleted_ids";
    private static final int MAX_DELETED = 30000;

    private static final Object lock = new Object();
    private static HashSet<Long> family;
    private static Boolean onlyFamilyMode;
    private static Boolean keepDeleted;
    private static LinkedHashSet<String> deletedKeys;

    private FamilyController() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void ensureLoaded() {
        synchronized (lock) {
            if (family != null) {
                return;
            }
            HashSet<Long> result = new HashSet<>();
            try {
                SharedPreferences p = prefs();
                String raw = p.getString(KEY_IDS, "");
                if (raw != null && raw.length() > 0) {
                    String[] parts = raw.split(",");
                    for (int a = 0; a < parts.length; a++) {
                        try {
                            result.add(Long.parseLong(parts[a].trim()));
                        } catch (NumberFormatException ignore) {
                        }
                    }
                }
                onlyFamilyMode = p.getBoolean(KEY_ONLY_MODE, false);
            } catch (Exception e) {
                FileLog.e(e);
                onlyFamilyMode = false;
            }
            family = result;
        }
    }

    private static void saveIds() {
        StringBuilder builder = new StringBuilder();
        for (Long id : family) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(id);
        }
        prefs().edit().putString(KEY_IDS, builder.toString()).apply();
    }

    public static boolean isFamily(long dialogId) {
        ensureLoaded();
        synchronized (lock) {
            return family.contains(dialogId);
        }
    }

    public static boolean hasFamily() {
        ensureLoaded();
        synchronized (lock) {
            return !family.isEmpty();
        }
    }

    public static void setFamily(long dialogId, boolean value) {
        ensureLoaded();
        synchronized (lock) {
            boolean changed = value ? family.add(dialogId) : family.remove(dialogId);
            if (changed) {
                saveIds();
            }
        }
    }

    public static boolean isOnlyFamilyMode() {
        ensureLoaded();
        synchronized (lock) {
            return onlyFamilyMode;
        }
    }

    public static void setOnlyFamilyMode(boolean value) {
        ensureLoaded();
        synchronized (lock) {
            onlyFamilyMode = value;
            prefs().edit().putBoolean(KEY_ONLY_MODE, value).apply();
        }
    }

    private static void ensureDeletedLoaded() {
        synchronized (lock) {
            if (deletedKeys != null) {
                return;
            }
            LinkedHashSet<String> result = new LinkedHashSet<>();
            try {
                SharedPreferences p = prefs();
                keepDeleted = p.getBoolean(KEY_KEEP_DELETED, true);
                String raw = p.getString(KEY_DELETED_SET, "");
                if (raw != null && raw.length() > 0) {
                    String[] parts = raw.split(";");
                    for (int a = 0; a < parts.length; a++) {
                        if (parts[a].length() > 0) {
                            result.add(parts[a]);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                keepDeleted = true;
            }
            deletedKeys = result;
        }
    }

    private static void saveDeleted() {
        StringBuilder builder = new StringBuilder();
        for (String key : deletedKeys) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(key);
        }
        prefs().edit().putString(KEY_DELETED_SET, builder.toString()).apply();
    }

    private static String deletedKey(int account, long dialogKey, int messageId) {
        return account + ":" + dialogKey + ":" + messageId;
    }

    /** Messages deleted by someone else stay in the chat, marked as deleted. */
    public static boolean isKeepDeleted() {
        ensureDeletedLoaded();
        synchronized (lock) {
            return keepDeleted;
        }
    }

    public static void setKeepDeleted(boolean value) {
        ensureDeletedLoaded();
        synchronized (lock) {
            keepDeleted = value;
            prefs().edit().putBoolean(KEY_KEEP_DELETED, value).apply();
        }
    }

    /**
     * Remember messages that the server reported as deleted but that we keep.
     * dialogKey is 0 for private chats and basic groups (their message ids are
     * unique per account) and -channelId for channels and supergroups.
     */
    public static void markDeleted(int account, long dialogKey, List<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        ensureDeletedLoaded();
        synchronized (lock) {
            for (int a = 0; a < messageIds.size(); a++) {
                deletedKeys.add(deletedKey(account, dialogKey, messageIds.get(a)));
            }
            while (deletedKeys.size() > MAX_DELETED) {
                Iterator<String> iterator = deletedKeys.iterator();
                iterator.next();
                iterator.remove();
            }
            saveDeleted();
        }
    }

    public static boolean isMarkedDeleted(int account, TLRPC.Message message) {
        if (message == null || message.id <= 0) {
            return false;
        }
        ensureDeletedLoaded();
        long channelId = message.peer_id != null ? message.peer_id.channel_id : 0;
        long dialogKey = channelId != 0 ? -channelId : 0;
        synchronized (lock) {
            return deletedKeys.contains(deletedKey(account, dialogKey, message.id));
        }
    }

    /**
     * True when the chat must stay silent because "only family" mode is on
     * and the chat is not in the family list. Telegram service notifications
     * (login codes) and Saved Messages are never silenced.
     */
    public static boolean isSilencedByFamilyMode(long dialogId) {
        if (!isOnlyFamilyMode()) {
            return false;
        }
        if (dialogId == 777000L || dialogId == UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()) {
            return false;
        }
        return !isFamily(dialogId);
    }
}
