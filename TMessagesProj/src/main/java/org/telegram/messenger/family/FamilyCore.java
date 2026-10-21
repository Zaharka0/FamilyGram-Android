package org.telegram.messenger.family;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Platform-independent, bounded wire format and deterministic shared state. */
public final class FamilyCore {
    public static final String MARKER = "\n\nfg1:";
    private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
    public static final Set<String> KINDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "task", "task_set", "event", "event_cancel", "bookmark", "bookmark_remove", "hug",
        "question_commit", "question_reveal", "goal", "contribution", "video", "game", "join", "move",
        "bill", "reaction")));
    private FamilyCore() {}

    public static final class Op {
        public final String kind, entity;
        public final Map<String, String> data;
        public Op(String kind, String entity, Map<String, String> data) {
            if (!KINDS.contains(kind) || entity == null || !entity.matches("[A-Za-z0-9_:.\\-]{1,100}") || data.size() > 20)
                throw new IllegalArgumentException("Invalid operation");
            TreeMap<String, String> copy = new TreeMap<>();
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (!e.getKey().matches("[a-z_]{1,32}") || e.getValue() == null || e.getValue().length() > 1200)
                    throw new IllegalArgumentException("Invalid field");
                copy.put(e.getKey(), e.getValue());
            }
            if (copy.containsKey("clock") || copy.containsKey("nonce")) {
                try {
                    long clock = Long.parseLong(copy.get("clock"));
                    if (clock <= 0 || clock > 9_000_000_000_000_000L || !copy.get("nonce").matches("[a-f0-9\\-]{36}")) throw new IllegalArgumentException("Invalid logical clock");
                } catch (Exception e) { throw new IllegalArgumentException("Invalid operation identity"); }
            }
            this.kind = kind; this.entity = entity; this.data = Collections.unmodifiableMap(copy);
        }
        public String get(String key) { return data.containsKey(key) ? data.get(key) : ""; }
        public long number(String key) { try { return Long.parseLong(get(key)); } catch (Exception e) { return 0; } }
    }
    public static final class Entry {
        public final int id, date;
        public final long actor;
        public final Op op;
        public Entry(int id, int date, long actor, Op op) { this.id = id; this.date = date; this.actor = actor; this.op = op; }
    }
    public static Map<String, String> fields(String... values) {
        if (values.length % 2 != 0) throw new IllegalArgumentException("Pairs required");
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) map.put(values[i], values[i + 1]);
        return map;
    }
    public static String pack(Op op) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(1); out.writeUTF(op.kind); out.writeUTF(op.entity); out.writeByte(op.data.size());
            for (Map.Entry<String, String> e : op.data.entrySet()) { out.writeUTF(e.getKey()); out.writeUTF(e.getValue()); }
            out.flush();
            byte[] raw = bytes.toByteArray();
            if (raw.length > 2400) throw new IllegalArgumentException("Card is too large");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < raw.length; i += 3) {
                int n = (raw[i] & 255) << 16;
                if (i + 1 < raw.length) n |= (raw[i + 1] & 255) << 8;
                if (i + 2 < raw.length) n |= raw[i + 2] & 255;
                result.append(B64.charAt(n >>> 18)).append(B64.charAt((n >>> 12) & 63));
                if (i + 1 < raw.length) result.append(B64.charAt((n >>> 6) & 63));
                if (i + 2 < raw.length) result.append(B64.charAt(n & 63));
            }
            return result.toString();
        } catch (IOException e) { throw new IllegalArgumentException(e); }
    }
    public static Op unpack(String value) {
        if (value == null || value.isEmpty() || value.length() > 3200 || value.length() % 4 == 1) return null;
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream(); int n = 0, bits = 0;
            for (int i = 0; i < value.length(); i++) {
                int x = B64.indexOf(value.charAt(i)); if (x < 0) return null;
                n = (n << 6) | x; bits += 6;
                if (bits >= 8) { bits -= 8; raw.write((n >>> bits) & 255); }
            }
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw.toByteArray()));
            if (in.readUnsignedByte() != 1) return null;
            String kind = in.readUTF(), entity = in.readUTF(); int count = in.readUnsignedByte();
            if (count > 20) return null;
            Map<String, String> data = new HashMap<>();
            for (int i = 0; i < count; i++) if (data.put(in.readUTF(), in.readUTF()) != null) return null;
            if (in.available() != 0) return null;
            Op op = new Op(kind, entity, data);
            return op.data.containsKey("nonce") && op.data.containsKey("clock") && pack(op).equals(value) ? op : null;
        } catch (Exception e) { return null; }
    }
    public static Op fromMessage(String message) {
        if (message == null || message.length() > 4096) return null;
        int pos = message.lastIndexOf(MARKER);
        return pos >= 0 ? unpack(message.substring(pos + MARKER.length())) : null;
    }
    public static String visibleText(String message) {
        return fromMessage(message) == null ? message : message.substring(0, message.lastIndexOf(MARKER));
    }
    public static String message(String human, Op op) {
        String result = human + MARKER + pack(op);
        if (result.length() > 4096) throw new IllegalArgumentException("Message too long");
        return result;
    }
    public static long cents(String value) {
        try {
            BigDecimal n = new BigDecimal(value.trim().replace(',', '.'));
            long amount = n.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
            if (amount <= 0 || amount > 100_000_000_000_000L) throw new IllegalArgumentException("Amount out of range");
            return amount;
        } catch (Exception e) { throw new IllegalArgumentException("Use a positive amount with at most two decimal places"); }
    }
    public static String money(long cents) { return BigDecimal.valueOf(cents, 2).toPlainString(); }
    /** Each line: description; amount; comma-separated participants. Remainders are allocated exactly. */
    public static Map<String, Long> splitBill(String lines) {
        TreeMap<String, Long> total = new TreeMap<>();
        if (lines == null || lines.length() > 1200) throw new IllegalArgumentException("Bill too long");
        for (String line : lines.split("\n")) {
            if (line.trim().isEmpty()) continue;
            String[] p = line.split(";", -1);
            if (p.length != 3 || p[0].trim().isEmpty()) throw new IllegalArgumentException("description; amount; names");
            long amount = cents(p[1]);
            TreeSet<String> names = new TreeSet<>();
            for (String name : p[2].split(",", -1)) {
                name = name.trim();
                if (name.isEmpty() || name.length() > 40 || !names.add(name)) throw new IllegalArgumentException("Invalid participant");
            }
            if (names.isEmpty() || names.size() > 20) throw new IllegalArgumentException("1–20 participants");
            long each = amount / names.size(), remainder = amount % names.size();
            for (String name : names) total.put(name, Math.addExact(total.containsKey(name) ? total.get(name) : 0, each + (remainder-- > 0 ? 1 : 0)));
        }
        if (total.isEmpty() || total.size() > 20) throw new IllegalArgumentException("Empty bill or too many participants");
        return total;
    }
    public static String digest(String text) {
        try {
            StringBuilder out = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))) out.append(String.format(Locale.US, "%02x", b & 255));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static final String[] QUESTIONS_RU = {
        "Какое наше воспоминание ты хотел(а) бы пережить снова?", "О какой совместной поездке ты мечтаешь?",
        "Что сегодня помогло тебе улыбнуться?", "Как я могу поддержать тебя на этой неделе?",
        "Какое наше маленькое приключение тебе особенно дорого?", "Чему ты хотел(а) бы научиться вместе?",
        "Как выглядел бы наш идеальный выходной?", "Какой мой поступок ты запомнил(а) с теплом?",
        "Какую семейную традицию нам стоит придумать?", "Какая песня напоминает тебе о нас?",
        "Какое блюдо приготовим вместе?", "Чего тебе сейчас хочется больше: объятий, разговора или отдыха?",
        "Что ты хотел(а) бы попробовать впервые?", "Какое место из детства ты хочешь мне показать?"
    };
    public static final String[] QUESTIONS_EN = {
        "Which memory of us would you like to relive?", "Which trip would you love to take together?",
        "What made you smile today?", "How can I support you this week?", "Which little adventure do you treasure?",
        "What would you like us to learn together?", "What would our perfect weekend look like?",
        "Which kind thing I did do you remember?", "What family tradition should we start?", "Which song reminds you of us?",
        "What should we cook together?", "Would you like a hug, a conversation or some rest?",
        "What would you like to try for the first time?", "Which childhood place would you like to show me?"
    };

    public static final class Card {
        public final Entry origin;
        public Entry latest;
        public boolean done, removed;
        public long saved;
        public final Map<Long, String> commitments = new TreeMap<>(), answers = new TreeMap<>();
        public Game game;
        Card(Entry origin) { this.origin = this.latest = origin; }
        public Op op() { return origin.op; }
    }
    /** Replay in confirmed Telegram message-ID order, independent of arrival order. */
    public static Map<String, Card> reduce(Collection<Entry> input, long self, long peer) {
        TreeMap<Integer, Entry> ordered = new TreeMap<>();
        for (Entry e : input) if (e.id > 0 && e.actor > 0 && e.op != null && (peer <= 0 || e.actor == self || e.actor == peer)) ordered.put(e.id, e);
        LinkedHashMap<String, Card> cards = new LinkedHashMap<>();
        ArrayList<Entry> replay = new ArrayList<>(ordered.values());
        replay.sort((a,b) -> {
            long ac=a.op.number("clock"),bc=b.op.number("clock");
            int result=Long.compare(ac>0?ac:a.id,bc>0?bc:b.id);
            if(result==0)result=a.op.get("nonce").compareTo(b.op.get("nonce"));
            if(result==0)result=Long.compare(a.actor,b.actor);
            return result;
        });
        Set<String> identities=new HashSet<>();
        for (Entry e : replay) {
            if(!e.op.get("nonce").isEmpty()&&!identities.add(e.actor+":"+e.op.get("nonce")))continue;
            Op op = e.op; Card card = cards.get(op.entity);
            try {
                switch (op.kind) {
                    case "task":
                    case "event":
                    case "bookmark":
                    case "hug":
                    case "goal":
                    case "game":
                    case "bill":
                    case "reaction":
                        if (card != null) break;
                        if (op.kind.equals("goal")) cents(op.get("target"));
                        if (op.kind.equals("bill")) splitBill(op.get("items"));
                        if (op.kind.equals("event") && (op.number("at") <= 0 || op.number("at") > 32503680000000L)) break;
                        if ((op.kind.equals("task") || op.kind.equals("event") || op.kind.equals("goal")) && op.get("title").trim().isEmpty()) break;
                        card = new Card(e);
                        if (op.kind.equals("game")) card.game = new Game(e.actor, op.get("mode"));
                        cards.put(op.entity, card); break;
                    case "task_set":
                        if (card != null && card.op().kind.equals("task") && (op.get("done").equals("1") || op.get("done").equals("0"))) { card.done = op.get("done").equals("1"); card.latest = e; } break;
                    case "event_cancel":
                    case "bookmark_remove":
                        if (card != null && card.origin.actor == e.actor && card.op().kind.equals(op.kind.equals("event_cancel") ? "event" : "bookmark")) { card.removed = true; card.latest = e; } break;
                    case "contribution":
                        if (card != null && card.op().kind.equals("goal")) { card.saved = Math.addExact(card.saved, cents(op.get("amount"))); card.latest = e; } break;
                    case "question_commit":
                        if (!op.entity.startsWith("q:") || !op.get("hash").matches("[a-f0-9]{64}")) break;
                        if (card == null) { card = new Card(e); cards.put(op.entity, card); }
                        if (!card.op().kind.equals("question_commit")) break;
                        if (!op.get("hash").equals(card.commitments.get(e.actor))) card.answers.remove(e.actor);
                        card.commitments.put(e.actor, op.get("hash")); card.latest = e; break;
                    case "question_reveal":
                        if (card != null && card.op().kind.equals("question_commit") && digest(op.get("salt") + "\n" + op.get("answer")).equals(card.commitments.get(e.actor))) {
                            card.answers.put(e.actor, op.get("answer")); card.latest = e;
                        } break;
                    case "video":
                        if (op.number("message") <= 0 || op.number("position") < 0 || op.number("position") > 86400000L) break;
                        if (card == null) { card = new Card(e); cards.put(op.entity, card); }
                        if (card.op().kind.equals("video")) card.latest = e; break;
                    case "join":
                        if (card != null && card.game != null && card.game.join(e.actor)) card.latest = e; break;
                    case "move":
                        if (card != null && card.game != null && card.game.move(e.actor, op.get("cell"))) card.latest = e; break;
                    default: break;
                }
            } catch (IllegalArgumentException | ArithmeticException ignored) { /* Invalid remote operation cannot corrupt the timeline. */ }
        }
        return cards;
    }
    /** Resumable tic-tac-toe and four-in-a-row; turn ownership is derived from sender IDs. */
    public static final class Game {
        public final long first;
        public long second;
        public final int width, height, target;
        public final char[] cells;
        public char winner;
        public int moves;
        public Game(long first, String mode) {
            if (!mode.equals("ttt") && !mode.equals("four")) throw new IllegalArgumentException("Unknown game");
            this.first = first; width = mode.equals("four") ? 7 : 3; height = mode.equals("four") ? 6 : 3;
            target = mode.equals("four") ? 4 : 3; cells = new char[width * height]; Arrays.fill(cells, '.');
        }
        public boolean join(long actor) { if (actor == first || second != 0 || actor <= 0) return false; second = actor; return true; }
        public long turn() { return second == 0 || winner != 0 || moves == cells.length ? 0 : moves % 2 == 0 ? first : second; }
        public boolean move(long actor, String cell) {
            if (turn() != actor || actor <= 0) return false;
            int pos; try { pos = Integer.parseInt(cell); } catch (Exception e) { return false; }
            if (target == 4) {
                if (pos < 0 || pos >= width) return false;
                int col = pos; pos = -1;
                for (int row = height - 1; row >= 0; row--) if (cells[row * width + col] == '.') { pos = row * width + col; break; }
            }
            if (pos < 0 || pos >= cells.length || cells[pos] != '.') return false;
            char mark = actor == first ? 'X' : 'O'; cells[pos] = mark; moves++;
            int x = pos % width, y = pos / width;
            for (int[] d : new int[][]{{1,0},{0,1},{1,1},{1,-1}}) {
                int count = 1;
                for (int sign : new int[]{-1,1}) for (int step = 1; step < target; step++) {
                    int xx = x + d[0] * sign * step, yy = y + d[1] * sign * step;
                    if (xx < 0 || xx >= width || yy < 0 || yy >= height || cells[yy * width + xx] != mark) break;
                    count++;
                }
                if (count >= target) winner = mark;
            }
            return true;
        }
    }
}
