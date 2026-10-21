package org.telegram.messenger.family;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.telegram.messenger.*;
import org.telegram.tgnet.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Confirmed Telegram history is the shared operation log; local storage is account-owned. */
public final class FamilyDataController implements NotificationCenter.NotificationCenterDelegate {
    public interface Listener { void changed(long dialog); }
    private static final FamilyDataController[] INSTANCES = new FamilyDataController[UserConfig.MAX_ACCOUNT_COUNT];
    public static synchronized FamilyDataController getInstance(int account) {
        long uid = UserConfig.getInstance(account).getClientUserId();
        FamilyDataController c = INSTANCES[account];
        if (c == null || c.self != uid) {
            if (c != null) c.dispose(false);
            INSTANCES[account] = c = new FamilyDataController(account, uid);
        }
        return c;
    }
    public static synchronized void removeListener(int account,Listener listener) {
        if(INSTANCES[account]!=null)INSTANCES[account].removeListener(listener);
    }
    public static synchronized void legacyChanged(int account,long value,boolean partner) {
        FamilyDataController c=INSTANCES[account];
        if(c!=null&&c.alive()){
            c.prefs.edit().putLong(partner?"partner":"since",value).apply();
            c.notifyChanged(0);FamilyPairWidget.updateAll(ApplicationLoader.applicationContext);
        }
    }
    public static synchronized void logout(int account) {
        FamilyDataController c = INSTANCES[account];
        if (c != null) { c.dispose(true); INSTANCES[account] = null; }
    }
    public final int account;
    public final long self;
    public final SharedPreferences prefs;
    private final DB db;
    private final DispatchQueue queue;
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<Long, Map<String, FamilyCore.Card>> states = new ConcurrentHashMap<>();
    private final Set<Long> loaded = Collections.synchronizedSet(new HashSet<>());
    private final Map<Long, Scan> scans = new ConcurrentHashMap<>();
    private volatile boolean disposed;
    private final Object clockLock = new Object();
    private long logicalClock;
    private static final class Scan { int request, offset, count; boolean all, cancelled; }
    private FamilyDataController(int account, long self) {
        this.account = account; this.self = self;
        Context c = ApplicationLoader.applicationContext;
        prefs = c.getSharedPreferences("familygram_hub_" + self, Context.MODE_PRIVATE);
        logicalClock=prefs.getLong("lamport",0);
        db = new DB(c, self); queue = new DispatchQueue("FamilyGram-" + self);
        AndroidUtilities.runOnUIThread(() -> {
            if (!disposed) {
                NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messageReceivedByServer);
                NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.privacyRulesUpdated);
                NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.messagesDeleted);
                if(FamilyController.wantsFamilyOnlyLastSeen())FamilyController.syncLastSeen();
            }
        });
        // Migrate the old device-wide partner only into the currently selected account.
        if (!prefs.contains("initialized")) {
            SharedPreferences.Editor e = prefs.edit().putBoolean("initialized", true);
            if (account == UserConfig.selectedAccount) e.putLong("partner", FamilyController.getPartnerId()).putLong("since", FamilyController.getTogetherSince());
            e.apply();
        }
    }
    public void addListener(Listener l) { listeners.addIfAbsent(l); }
    public void removeListener(Listener l) { listeners.remove(l); }
    private boolean alive() { return !disposed && self > 0 && UserConfig.getInstance(account).getClientUserId() == self; }
    private void notifyChanged(long dialog) {
        AndroidUtilities.runOnUIThread(() -> { if (alive()) for (Listener l : listeners) l.changed(dialog); });
    }
    public long partner() { return prefs.getLong("partner", 0); }
    public void setPartner(long id) {
        prefs.edit().putLong("partner", id).apply();
        if (account == UserConfig.selectedAccount) FamilyController.setPartnerId(id);
        FamilyPairWidget.updateAll(ApplicationLoader.applicationContext);
        notifyChanged(0);
    }
    public long since() { return prefs.getLong("since", 0); }
    public void setSince(long date) {
        prefs.edit().putLong("since", date).apply();
        if (account == UserConfig.selectedAccount) FamilyController.setTogetherSince(date);
        FamilyPairWidget.updateAll(ApplicationLoader.applicationContext); notifyChanged(0);
    }
    public int daysTogether() {
        if (since() <= 0) return 0;
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance(); a.setTimeInMillis(since());
        for (Calendar c : new Calendar[]{a,b}) { c.set(Calendar.HOUR_OF_DAY, 12); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0); }
        return (int) Math.max(0, Math.round((b.getTimeInMillis()-a.getTimeInMillis())/86400000d)+1);
    }
    public void load(long dialog) {
        if (!alive() || dialog == 0 || DialogObject.isEncryptedDialog(dialog) || !loaded.add(dialog)) return;
        queue.postRunnable(() -> { if (alive()) rebuild(dialog); });
    }
    public List<FamilyCore.Card> cards(long dialog) {
        load(dialog);
        Map<String, FamilyCore.Card> state = states.get(dialog);
        return state == null ? new ArrayList<>() : new ArrayList<>(state.values());
    }
    public FamilyCore.Card card(long dialog, String id) {
        Map<String, FamilyCore.Card> s = states.get(dialog); return s == null ? null : s.get(id);
    }
    private void rebuild(long dialog) {
        ArrayList<FamilyCore.Entry> entries = new ArrayList<>();
        try (Cursor c = db.getReadableDatabase().rawQuery("SELECT mid,date,actor,wire FROM ops WHERE dialog=? ORDER BY mid", new String[]{""+dialog})) {
            while (c.moveToNext()) {
                FamilyCore.Op op = FamilyCore.unpack(c.getString(3));
                if (op != null) entries.add(new FamilyCore.Entry(c.getInt(0),c.getInt(1),c.getLong(2),op));
            }
        } catch (Exception e) { FileLog.e(e); }
        Map<String, FamilyCore.Card> state = FamilyCore.reduce(entries, self, dialog > 0 ? dialog : 0);
        states.put(dialog, state);
        notifyChanged(dialog);
        AndroidUtilities.runOnUIThread(() -> maybeReveal(dialog, state));
    }
    /** Safe to call from Telegram's incoming-message path; no sending occurs here except mutual question reveal. */
    public void ingest(MessageObject object) {
        if (object != null) ingest(object.messageOwner, true);
    }
    public void ingest(TLRPC.Message message) { ingest(message, false); }
    private void ingest(TLRPC.Message message, boolean live) {
        if (!alive() || message == null || message.id <= 0 || message.send_state != 0 ) return;
        long dialog = MessageObject.getDialogId(message);
        if (dialog == 0 || DialogObject.isEncryptedDialog(dialog)) return;
        FamilyCore.Op decoded = message.fwd_from == null ? FamilyCore.fromMessage(message.message) : null;
        final FamilyCore.Op op=decoded!=null&&decoded.number("clock")<=(message.date+300L)*1000000L?decoded:null;
        if (op == null && dialog != partner() && !loaded.contains(dialog)) return;
        if (message.ttl_period != 0 || message.media != null && message.media.ttl_seconds != 0) return;
        long actor = message.from_id != null && message.from_id.user_id != 0 ? message.from_id.user_id : message.out ? self : dialog > 0 ? dialog : 0;
        final long sender = actor;
        MessageObject object = new MessageObject(account, message, false, true);
        final int media = object.isVoice() ? 3 : object.isVideo() ? 2 : object.isPhoto() ? 1 : 0;
        final int seconds = object.isVoice() ? (int) Math.round(object.getDuration()) : 0;
        final boolean unread = message.media_unread && !message.out;
        byte[] bytes = null;
        if (media == 3 || media == 2) {
            NativeByteBuffer b = null;
            try { b = new NativeByteBuffer(message.getObjectSize()); message.serializeToStream(b); bytes = new byte[b.length()]; b.position(0); b.readBytes(bytes, true); }
            catch (Exception e) { FileLog.e(e); } finally { if (b != null) b.reuse(); }
        }
        final byte[] blob = bytes;
        final String emoji = emoji(message.message);
        queue.postRunnable(() -> {
            if (!alive()) return;
            try {
                SQLiteDatabase sql = db.getWritableDatabase();
                if (op != null && sender > 0 && (dialog < 0 || sender == self || sender == dialog)) {
                    ContentValues v = new ContentValues(); v.put("dialog",dialog);v.put("mid",message.id);v.put("date",message.date);v.put("actor",sender);v.put("wire",FamilyCore.pack(op));
                    // First confirmed representation is immutable: replaying or editing a transport message cannot double an action.
                    if (sql.insertWithOnConflict("ops",null,v,SQLiteDatabase.CONFLICT_IGNORE) != -1) {
                        synchronized(clockLock){long received=op.number("clock");if(received>logicalClock){logicalClock=received;prefs.edit().putLong("lamport",logicalClock).apply();}}
                        if (!op.get("ref").isEmpty() && message.reply_to != null && message.reply_to.reply_to_msg_id > 0) {
                            ContentValues reference = new ContentValues(); reference.put("dialog",dialog); reference.put("reference",op.get("ref")); reference.put("mid",message.reply_to.reply_to_msg_id);
                            sql.insertWithOnConflict("refs",null,reference,SQLiteDatabase.CONFLICT_REPLACE);
                        }
                        if (live) rebuild(dialog);
                        if (live && sender != self && op.kind.equals("hug") && Math.abs(ConnectionsManager.getInstance(account).getCurrentTime() - message.date) < 60)
                            AndroidUtilities.runOnUIThread(() -> FamilyEffects.incoming(account, dialog));
                    }
                }
                ContentValues v = new ContentValues();v.put("dialog",dialog);v.put("mid",message.id);v.put("date",message.date);v.put("actor",sender);
                v.put("reference",reference(message,sender));v.put("channel",message.peer_id!=null?message.peer_id.channel_id:0);v.put("media",media);v.put("seconds",seconds);v.put("unread",unread?1:0);v.put("emoji",emoji);v.put("system",op!=null || message.action!=null && !(message.action instanceof TLRPC.TL_messageActionEmpty)?1:0);
                if (blob != null) v.put("data",blob);
                sql.insertWithOnConflict("history",null,v,SQLiteDatabase.CONFLICT_REPLACE);
            } catch (Exception e) { FileLog.e(e); }
        });
    }
    @Override public void didReceivedNotification(int id, int currentAccount, Object... args) {
        if(id==NotificationCenter.messagesDeleted && args.length>2 && !Boolean.TRUE.equals(args[2]) && args[0] instanceof ArrayList && args[1] instanceof Long){
            ArrayList<Integer> ids=new ArrayList<>((ArrayList<Integer>)args[0]);long channel=(Long)args[1];
            queue.postRunnable(()->{if(!alive())return;try{
                for(int offset=0;offset<ids.size();offset+=400){
                    List<Integer> part=ids.subList(offset,Math.min(ids.size(),offset+400));StringBuilder clause=new StringBuilder("mid IN (");ArrayList<String> params=new ArrayList<>();
                    for(int n=0;n<part.size();n++){if(n>0)clause.append(',');clause.append('?');params.add(""+part.get(n));}clause.append(')');
                    if(channel!=0){clause.append(" AND dialog=?");params.add(""+(-channel));}else clause.append(" AND channel=0");
                    db.getWritableDatabase().delete("history",clause.toString(),params.toArray(new String[0]));
                }
            }catch(Exception error){FileLog.e(error);}});
        }
        if(id==NotificationCenter.privacyRulesUpdated && FamilyController.wantsFamilyOnlyLastSeen() && FamilyController.shouldHideOnline(account)) FamilyController.syncLastSeen();
        if (id == NotificationCenter.messageReceivedByServer && args.length > 6 && !Boolean.TRUE.equals(args[6]) && args[2] instanceof TLRPC.Message) ingest((TLRPC.Message) args[2], true);
    }
    public void send(long dialog, String kind, String entity, String human, Map<String,String> data) {
        send(dialog,kind,entity,human,data,null);
    }
    public void send(long dialog, String kind, String entity, String human, Map<String,String> data, MessageObject replyTo) {
        if (!alive() || dialog == 0 || DialogObject.isEncryptedDialog(dialog) || dialog == self) throw new IllegalArgumentException("Choose a normal conversation");
        Map<String,String> payload=new HashMap<>(data);
        synchronized(clockLock){logicalClock=Math.max(logicalClock+1,ConnectionsManager.getInstance(account).getCurrentTime()*1000000L);payload.put("clock",""+logicalClock);prefs.edit().putLong("lamport",logicalClock).apply();}
        payload.put("nonce",newId());
        FamilyCore.Op op = new FamilyCore.Op(kind, entity, payload);
        String text = FamilyCore.message(human, op);
        AndroidUtilities.runOnUIThread(() -> {
            if (alive()) SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(text,dialog,replyTo,null,null,false,null,null,null,true,0,0,null,false));
        });
    }
    public static String newId() { return UUID.randomUUID().toString(); }
    public void bookmark(MessageObject object) {
        if (object == null || object.getId() <= 0 || object.messageOwner.ttl_period != 0 || object.isVoiceOnce() || object.isRoundOnce() || object.messageOwner.media != null && object.messageOwner.media.ttl_seconds != 0) throw new IllegalArgumentException("This message cannot be bookmarked");
        String text = object.messageText == null ? "" : object.messageText.toString();
        if (text.length() > 600) text = text.substring(0,600);
        String reference=reference(object.messageOwner, object.isOut()?self:object.getDialogId());
        String entity = "b:" + reference.substring(0,32);
        FamilyCore.Card c = card(object.getDialogId(), entity);
        if (c != null && !c.removed) return;
        // A removed bookmark can be added again with a new entity, preserving the audit trail.
        if (c != null) entity += ":" + newId();
        send(object.getDialogId(),"bookmark",entity,"★ Наше / Ours\n"+text,FamilyCore.fields("message",""+object.getId(),"text",text,"ref",reference),object);
    }
    public void answer(long dialog, String question, String text) {
        if (dialog <= 0 || text.trim().isEmpty() || text.length()>600) throw new IllegalArgumentException("Invalid answer");
        String salt = newId(), hash = FamilyCore.digest(salt+"\n"+text);
        prefs.edit().putString("answer_"+dialog+"_"+question, salt+"\n"+text).apply();
        send(dialog,"question_commit",question,"FamilyGram · Ответ на вопрос дня сохранён / Daily answer saved",FamilyCore.fields("hash",hash));
    }
    private void maybeReveal(long dialog, Map<String, FamilyCore.Card> state) {
        if (!alive() || dialog<=0) return;
        for (Map.Entry<String, FamilyCore.Card> e : state.entrySet()) {
            FamilyCore.Card c=e.getValue();
            if (c.commitments.containsKey(self) && c.commitments.containsKey(dialog) && !c.answers.containsKey(self)) {
                String secret=prefs.getString("answer_"+dialog+"_"+e.getKey(),null);
                if (secret==null) continue;
                int pos=secret.indexOf('\n');if(pos<0)continue;
                String salt=secret.substring(0,pos), answer=secret.substring(pos+1), hash=FamilyCore.digest(secret);
                if (!hash.equals(c.commitments.get(self))) continue;
                String sentKey="reveal_"+dialog+"_"+e.getKey();
                // Pending marker is process-local so a failed/restarted send can be retried from the question UI.
                if (revealing.add(sentKey)) send(dialog,"question_reveal",e.getKey(),"FamilyGram · Ответ / Answer\n"+answer,FamilyCore.fields("salt",salt,"answer",answer));
            }
        }
    }
    private final Set<String> revealing=new HashSet<>();
    public void retryReveal(long dialog) { revealing.clear();Map<String,FamilyCore.Card>s=states.get(dialog);if(s!=null)maybeReveal(dialog,s); }
    public String questionId() { return "q:"+new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US){{setTimeZone(TimeZone.getTimeZone("UTC"));}}.format(new Date()); }
    public String questionText(String id) {
        boolean ru=LocaleController.getInstance().getCurrentLocale().getLanguage().equals("ru");
        String[] q=ru?FamilyCore.QUESTIONS_RU:FamilyCore.QUESTIONS_EN;return q[Math.floorMod(id.hashCode(),q.length)];
    }
    public boolean scanning(long dialog) { return scans.containsKey(dialog); }
    public int scanned(long dialog) { Scan s=scans.get(dialog);return s==null?prefs.getInt("scanned_"+dialog,0):s.count; }
    public boolean complete(long dialog) { return prefs.getBoolean("complete_"+dialog,false); }
    public void sync(long dialog, boolean all) {
        if (!alive() || scanning(dialog) || dialog==0 || DialogObject.isEncryptedDialog(dialog)) return;
        load(dialog); Scan scan=new Scan();scan.all=all;
        // Resume older-history indexing; refresh latest messages separately on every hub opening.
        scan.offset=all?prefs.getInt("offset_"+dialog,0):0;
        scan.count=all?prefs.getInt("scanned_"+dialog,0):0;
        scans.put(dialog,scan); next(dialog,scan);
    }
    public void cancelSync(long dialog) {
        Scan s=scans.remove(dialog);if(s!=null){s.cancelled=true;ConnectionsManager.getInstance(account).cancelRequest(s.request,true);}notifyChanged(dialog);
    }
    private void next(long dialog, Scan scan) {
        if (!alive()||scan.cancelled)return;
        TLRPC.TL_messages_getHistory req=new TLRPC.TL_messages_getHistory();req.peer=MessagesController.getInstance(account).getInputPeer(dialog);req.limit=100;req.offset_id=scan.offset;
        scan.request=ConnectionsManager.getInstance(account).sendRequest(req,(response,error)->AndroidUtilities.runOnUIThread(()->{
            if(!alive()||scan.cancelled||scans.get(dialog)!=scan)return;
            if(error!=null||!(response instanceof TLRPC.messages_Messages)){
                scans.remove(dialog);prefs.edit().putString("sync_error_"+dialog,error==null?"EMPTY_RESPONSE":error.text).apply();notifyChanged(dialog);return;
            }
            TLRPC.messages_Messages result=(TLRPC.messages_Messages)response;
            MessagesController.getInstance(account).putUsers(result.users,false);MessagesController.getInstance(account).putChats(result.chats,false);
            int oldOffset=scan.offset;
            for(TLRPC.Message m:result.messages){m.dialog_id=dialog;ingest(m);if(m.id>0&&(scan.offset==0||m.id<scan.offset))scan.offset=m.id;}
            scan.count+=result.messages.size();
            if(scan.all){
                SharedPreferences.Editor edit=prefs.edit().putInt("offset_"+dialog,scan.offset).putInt("scanned_"+dialog,scan.count).remove("sync_error_"+dialog);
                if(result.messages.isEmpty()||scan.offset==oldOffset)edit.putBoolean("complete_"+dialog,true);edit.apply();
            }
            queue.postRunnable(()->{if(alive()){rebuild(dialog);notifyChanged(dialog);}});
            if(scan.all&&!result.messages.isEmpty()&&scan.offset!=oldOffset)AndroidUtilities.runOnUIThread(()->next(dialog,scan),350);
            else{scans.remove(dialog);notifyChanged(dialog);}
        }));
    }
    public void statistics(long dialog, int year, java.util.function.Consumer<String> callback) {
        queue.postRunnable(()->{
            StringBuilder out=new StringBuilder();String where="dialog=? AND system=0";ArrayList<String> args=new ArrayList<>();args.add(""+dialog);
            if(year>0){where+=" AND strftime('%Y',date,'unixepoch')=?";args.add(""+year);}
            try {
                SQLiteDatabase sql=db.getReadableDatabase();
                try(Cursor c=sql.rawQuery("SELECT COUNT(*),SUM(media=1),SUM(media=2),SUM(media=3),SUM(seconds),MIN(date),MAX(date) FROM history WHERE "+where,args.toArray(new String[0]))){
                    if(c.moveToFirst())out.append("✉ ").append(c.getLong(0)).append("  ·  📷 ").append(c.getLong(1)).append("  ·  🎬 ").append(c.getLong(2)).append("  ·  🎙 ").append(c.getLong(3)).append("\n🎙 ").append(c.getLong(4)/60).append(" min\n");
                }
                try(Cursor c=sql.rawQuery("SELECT strftime('%Y-%m',date,'unixepoch') AS month,COUNT(*) AS n FROM history WHERE "+where+" GROUP BY month ORDER BY n DESC LIMIT 12",args.toArray(new String[0]))){while(c.moveToNext())out.append(c.getString(0)).append("  ·  ").append(c.getLong(1)).append('\n');}
                Map<String,Integer> emoji=new HashMap<>();
                try(Cursor c=sql.rawQuery("SELECT emoji FROM history WHERE "+where,args.toArray(new String[0]))){while(c.moveToNext()){String text=c.getString(0);for(int i=0;text!=null&&i<text.length();){int cp=text.codePointAt(i);String key=new String(Character.toChars(cp));emoji.put(key,emoji.containsKey(key)?emoji.get(key)+1:1);i+=Character.charCount(cp);}}}
                ArrayList<Map.Entry<String,Integer>> sorted=new ArrayList<>(emoji.entrySet());sorted.sort((a,b)->Integer.compare(b.getValue(),a.getValue()));
                for(int i=0;i<Math.min(10,sorted.size());i++)out.append(sorted.get(i).getKey()).append(" × ").append(sorted.get(i).getValue()).append("  ");
            }catch(Exception e){FileLog.e(e);out.append("Error: ").append(e.getClass().getSimpleName());}
            String result=out.toString();AndroidUtilities.runOnUIThread(()->{if(alive())callback.accept(result);});
        });
    }
    public void media(long dialog, int type, java.util.function.Consumer<ArrayList<MessageObject>> callback) {
        queue.postRunnable(()->{
            ArrayList<MessageObject> result=new ArrayList<>();
            try(Cursor c=db.getReadableDatabase().rawQuery("SELECT data FROM history WHERE dialog=? AND media=? AND data IS NOT NULL "+(type==3?"AND unread=1 ":"")+"ORDER BY mid "+(type==2?"DESC":"ASC"),new String[]{""+dialog,""+type})){
                while(c.moveToNext()){
                    NativeByteBuffer b=null;
                    try{byte[]data=c.getBlob(0);b=new NativeByteBuffer(data.length);b.writeBytes(data);b.position(0);TLRPC.Message m=TLRPC.Message.TLdeserialize(b,b.readInt32(false),false);if(m!=null){MessageObject object=new MessageObject(account,m,false,true);if(type!=3||!FamilyVoice.heard(object))result.add(object);if(result.size()>=500)break;}}
                    catch(Exception ex){FileLog.e(ex);}finally{if(b!=null)b.reuse();}
                }
            }catch(Exception e){FileLog.e(e);}
            AndroidUtilities.runOnUIThread(()->{if(alive())callback.accept(result);});
        });
    }
    public static String reference(TLRPC.Message m,long actor) {
        if(m.from_id!=null&&m.from_id.user_id>0)actor=m.from_id.user_id;
        long media=0;
        if(m.media!=null){if(m.media.document!=null)media=m.media.document.id;else if(m.media.photo!=null)media=m.media.photo.id;}
        return FamilyCore.digest(m.id+":"+m.date+":"+actor+":"+media+":"+(m.message==null?"":m.message));
    }
    public void resolve(long dialog,String reference,java.util.function.IntConsumer callback){
        queue.postRunnable(()->{
            int id=0;
            for(String table:new String[]{"refs","history"}){
                try(Cursor c=db.getReadableDatabase().rawQuery("SELECT mid FROM "+table+" WHERE dialog=? AND reference=? LIMIT 1",new String[]{""+dialog,reference})){if(c.moveToFirst()){id=c.getInt(0);break;}}
            }
            final int result=id;AndroidUtilities.runOnUIThread(()->{if(alive())callback.accept(result);});
        });
    }
    public void message(long dialog,int id,java.util.function.Consumer<MessageObject> callback){
        if(dialog<=0||id<=0){callback.accept(null);return;}
        TLRPC.TL_messages_getMessages req=new TLRPC.TL_messages_getMessages();
        TLRPC.TL_inputMessageID input=new TLRPC.TL_inputMessageID();input.id=id;req.id.add(input);
        ConnectionsManager.getInstance(account).sendRequest(req,(response,error)->AndroidUtilities.runOnUIThread(()->{
            if(!alive())return;
            if(error==null&&response instanceof TLRPC.messages_Messages){
                TLRPC.messages_Messages result=(TLRPC.messages_Messages)response;
                MessagesController.getInstance(account).putUsers(result.users,false);MessagesController.getInstance(account).putChats(result.chats,false);
                if(!result.messages.isEmpty()){TLRPC.Message m=result.messages.get(0);m.dialog_id=dialog;ingest(m);callback.accept(new MessageObject(account,m,false,true));return;}
            }
            callback.accept(null);
        }));
    }
    private static String emoji(String s){
        StringBuilder out=new StringBuilder();if(s==null)return "";
        for(int i=0;i<s.length();){int cp=s.codePointAt(i);if(cp>=0x1F300&&cp<=0x1FAFF||cp>=0x2600&&cp<=0x27BF)out.appendCodePoint(cp);i+=Character.charCount(cp);}return out.toString();
    }
    private void dispose(boolean erase){
        disposed=true;for(Long dialog:new ArrayList<>(scans.keySet()))cancelSync(dialog);
        AndroidUtilities.runOnUIThread(()->{
            NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.messageReceivedByServer);
            NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.privacyRulesUpdated);
            NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.messagesDeleted);
        });listeners.clear();
        queue.postRunnable(()->{db.close();if(erase){ApplicationLoader.applicationContext.deleteDatabase(db.getDatabaseName());prefs.edit().clear().apply();
            java.io.File folder=new java.io.File(ApplicationLoader.applicationContext.getFilesDir(),"familygram_photos_"+self);
            java.io.File[] files=folder.listFiles();if(files!=null)for(java.io.File file:files)file.delete();folder.delete();
            AndroidUtilities.runOnUIThread(()->FamilyPairWidget.updateAll(ApplicationLoader.applicationContext));
        }});
    }
    private static final class DB extends SQLiteOpenHelper {
        DB(Context c,long uid){super(c,"familygram_hub_"+uid+".db",null,1);}
        @Override public void onCreate(SQLiteDatabase db){
            db.execSQL("CREATE TABLE ops(dialog INTEGER NOT NULL,mid INTEGER NOT NULL,date INTEGER NOT NULL,actor INTEGER NOT NULL,wire TEXT NOT NULL,PRIMARY KEY(dialog,mid))");
            db.execSQL("CREATE TABLE refs(dialog INTEGER NOT NULL,reference TEXT NOT NULL,mid INTEGER NOT NULL,PRIMARY KEY(dialog,reference))");
            db.execSQL("CREATE TABLE history(dialog INTEGER NOT NULL,mid INTEGER NOT NULL,date INTEGER NOT NULL,actor INTEGER,reference TEXT,channel INTEGER,media INTEGER,seconds INTEGER,unread INTEGER,emoji TEXT,system INTEGER,data BLOB,PRIMARY KEY(dialog,mid))");
        }
        @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
    }
}
