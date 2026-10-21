package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

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
    private static final String KEY_GHOST_READ = "ghost_read";
    private static final String KEY_GHOST_TYPING = "ghost_typing";
    private static final String KEY_GHOST_ONLINE = "ghost_online";
    private static final String KEY_GHOST_FAMILY = "ghost_family";
    private static final String KEY_TOGETHER = "together_since";
    private static final String KEY_PARTNER = "partner_id";

    private static final Object lock = new Object();
    private static HashSet<Long> family;
    private static Boolean onlyFamilyMode;
    private static Boolean keepDeleted;
    private static LinkedHashSet<String> deletedKeys;

    private static final Pattern BRAND_PATTERN = Pattern.compile("(?<![\\w/.@#-])Telegram(?!\\w|\\.(?:org|me)\\b)");

    private FamilyController() {
    }

    /** Replaces the word "Telegram" with "FamilyGram" in texts; links and domains stay untouched. */
    public static String rebrand(String text) {
        if (text == null || text.indexOf("Telegram") < 0) {
            return text;
        }
        return BRAND_PATTERN.matcher(text).replaceAll("FamilyGram");
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

    // ---- Ghost mode -------------------------------------------------------

    private static boolean flag(String key) {
        try {
            return prefs().getBoolean(key, false);
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private static void setFlag(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    public static boolean isGhostNoRead() {
        return flag(KEY_GHOST_READ);
    }

    public static boolean isGhostNoTyping() {
        return flag(KEY_GHOST_TYPING);
    }

    public static boolean isGhostNoOnline() {
        return flag(KEY_GHOST_ONLINE);
    }

    public static boolean isGhostFamilyExempt() {
        return flag(KEY_GHOST_FAMILY);
    }

    public static void setGhostNoRead(boolean value) {
        setFlag(KEY_GHOST_READ, value);
    }

    public static void setGhostNoTyping(boolean value) {
        setFlag(KEY_GHOST_TYPING, value);
    }

    public static void setGhostNoOnline(boolean value) {
        setFlag(KEY_GHOST_ONLINE, value);
    }

    public static void setGhostFamilyExempt(boolean value) {
        setFlag(KEY_GHOST_FAMILY, value);
    }

    /** Do not tell the other person that we read the chat. */
    public static boolean shouldHideRead(long dialogId) {
        return isGhostNoRead() && !(isGhostFamilyExempt() && isFamily(dialogId));
    }

    /** Do not show "typing..." in this chat. */
    public static boolean shouldHideTyping(long dialogId) {
        return isGhostNoTyping() && !(isGhostFamilyExempt() && isFamily(dialogId));
    }

    /** True: always send "offline". With the family exception the online status is shown normally and Last seen privacy limits it to family. */
    public static boolean shouldHideOnline() {
        return shouldHideOnline(UserConfig.selectedAccount);
    }

    private static final java.util.Set<Long> activeFamilyPrivacy = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    public static boolean shouldHideOnline(int account) {
        long uid=UserConfig.getInstance(account).getClientUserId();
        return isGhostNoOnline() && (!isGhostFamilyExempt() || !activeFamilyPrivacy.contains(uid));
    }

    /** True: only family may see when we are online (server side privacy rule). */
    public static boolean wantsFamilyOnlyLastSeen() {
        return isGhostNoOnline() && isGhostFamilyExempt();
    }

    public static void sendOfflineNow() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                continue;
            }
            TL_account.updateStatus req = new TL_account.updateStatus();
            req.offline = true;
            ConnectionsManager.getInstance(a).sendRequest(req, (response, error) -> {

            });
        }
    }

    /**
     * Makes the server side "Last seen" privacy match the ghost settings:
     * "Nobody, except family" while the family exception is on, and puts the
     * complete previous rules, including user and group exceptions, back afterwards.
     */
    private static boolean syncingLastSeen;
    private static final java.util.HashMap<Integer, Long> lastSeenRequests = new java.util.HashMap<>();
    public static void syncLastSeen() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) { AndroidUtilities.runOnUIThread(FamilyController::syncLastSeen); return; }
        if (syncingLastSeen) return;
        syncingLastSeen=true;
        try { syncLastSeenInternal(); } finally { syncingLastSeen=false; }
    }
    private static void syncLastSeenInternal() {
        final boolean familyOnly = wantsFamilyOnlyLastSeen();
        ensureLoaded();
        final HashSet<Long> snapshot;
        synchronized (lock) {
            snapshot = new HashSet<>(family);
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                continue;
            }
            final int account = a;
            final long privacyUid=UserConfig.getInstance(account).getClientUserId();
            if (java.util.Objects.equals(lastSeenRequests.get(account),privacyUid)) continue;
            final String backupKey = "lastseen_backup_" + account;
            final String fullKey = "lastseen_rules_v1_" + UserConfig.getInstance(account).getClientUserId();
            final String activeKey = "family_privacy_active_" + UserConfig.getInstance(account).getClientUserId();
            final SharedPreferences p = prefs();
            final ContactsController contacts = ContactsController.getInstance(account);
            final MessagesController messages = MessagesController.getInstance(account);
            TL_account.setPrivacy req = new TL_account.setPrivacy();
            req.key = new TLRPC.TL_inputPrivacyKeyStatusTimestamp();
            if (familyOnly) {
                if (!p.contains(fullKey) && !p.contains(backupKey)) {
                    ArrayList<TLRPC.PrivacyRule> rules = contacts.getPrivacyRules(ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
                    if (rules == null) { contacts.loadPrivacySettings(); continue; }
                    try {
                        String backup=org.telegram.messenger.family.FamilyPrivacyBackup.save(rules,messages);
                        p.edit().putString(fullKey,backup).apply();
                    } catch (Exception error) { FileLog.e(error); continue; }
                }
                TLRPC.TL_inputPrivacyValueAllowUsers users = new TLRPC.TL_inputPrivacyValueAllowUsers();
                for (Long id : snapshot) {
                    if (id == null || id <= 0) {
                        continue;
                    }
                    TLRPC.User user = messages.getUser(id);
                    if (user == null) {
                        continue;
                    }
                    TLRPC.InputUser inputUser = messages.getInputUser(user);
                    if (inputUser != null && !(inputUser instanceof TLRPC.TL_inputUserEmpty)) {
                        users.users.add(inputUser);
                    }
                }
                if (!users.users.isEmpty()) {
                    req.rules.add(users);
                }
                req.rules.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
            } else {
                String full=p.getString(fullKey,null);
                if(full!=null){
                    try { req.rules.addAll(org.telegram.messenger.family.FamilyPrivacyBackup.read(full)); }
                    catch(Exception error){FileLog.e(error);continue;}
                }else{
                    // Backward compatibility with backups made by older FamilyGram builds.
                    String kind=p.getString(backupKey,null);if(kind==null)continue;
                    if("all".equals(kind))req.rules.add(new TLRPC.TL_inputPrivacyValueAllowAll());
                    else if("contacts".equals(kind))req.rules.add(new TLRPC.TL_inputPrivacyValueAllowContacts());
                    else req.rules.add(new TLRPC.TL_inputPrivacyValueDisallowAll());
                }
            }
            if(familyOnly) {
                activeFamilyPrivacy.remove(privacyUid);
                p.edit().putBoolean(activeKey,false).apply();
                TL_account.updateStatus offline=new TL_account.updateStatus();offline.offline=true;
                ConnectionsManager.getInstance(account).sendRequest(offline,(r,e)->{});
            }
            lastSeenRequests.put(account,privacyUid);
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if(java.util.Objects.equals(lastSeenRequests.get(account),privacyUid))lastSeenRequests.remove(account);
                if(UserConfig.getInstance(account).getClientUserId()!=privacyUid)return;
                if (error == null && response instanceof TL_account.privacyRules) {
                    TL_account.privacyRules result = (TL_account.privacyRules) response;
                    messages.putUsers(result.users, false);
                    messages.putChats(result.chats, false);
                    if(familyOnly)activeFamilyPrivacy.add(privacyUid);else activeFamilyPrivacy.remove(privacyUid);
                    p.edit().putBoolean(activeKey,familyOnly).apply();
                    if (!familyOnly) p.edit().remove(backupKey).remove(fullKey).apply();
                    contacts.setPrivacyRules(result.rules, ContactsController.PRIVACY_RULES_TYPE_LASTSEEN);
                    if(familyOnly!=wantsFamilyOnlyLastSeen())syncLastSeen();
                }
            }));
        }
    }

    // ---- Days together ----------------------------------------------------

    public static long getTogetherSince() {
        try {
            return prefs().getLong(KEY_TOGETHER, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public static void setTogetherSince(long millis) {
        prefs().edit().putLong(KEY_TOGETHER, millis).apply();
        org.telegram.messenger.family.FamilyDataController.legacyChanged(UserConfig.selectedAccount,millis,false);
    }

    public static long getPartnerId() {
        try {
            return prefs().getLong(KEY_PARTNER, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    public static void setPartnerId(long id) {
        prefs().edit().putLong(KEY_PARTNER, id).apply();
        org.telegram.messenger.family.FamilyDataController.legacyChanged(UserConfig.selectedAccount,id,true);
    }

    public static boolean isPartner(long id) {
        long partner = getPartnerId();
        return partner != 0 && partner == id;
    }

    /** Days since the chosen date; the first day counts as day 1. 0 when no date is set. */
    public static int getDaysTogether() {
        long since = getTogetherSince();
        if (since <= 0) {
            return 0;
        }
        Calendar start = Calendar.getInstance();
        start.setTimeInMillis(since);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long days = Math.round((today.getTimeInMillis() - start.getTimeInMillis()) / 86400000.0);
        return days < 0 ? 0 : (int) days + 1;
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
