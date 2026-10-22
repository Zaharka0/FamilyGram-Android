import org.telegram.messenger.family.FamilyCore;
import org.telegram.messenger.family.FamilyTapGuard;
import org.telegram.messenger.family.FamilyPrivacyPolicy;
import java.util.*;

public class FamilyCoreTest {
    static int checks;
    static void check(boolean test) { checks++; if(!test)throw new AssertionError("check "+checks); }
    static FamilyCore.Op op(String kind,String id,String...fields){Map<String,String> data=FamilyCore.fields(fields);data.put("clock","1");data.put("nonce",UUID.nameUUIDFromBytes((kind+id+data.toString()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());return new FamilyCore.Op(kind,id,data);}
    static FamilyCore.Entry e(int mid,long actor,FamilyCore.Op op){Map<String,String>d=new HashMap<>(op.data);d.put("clock",""+mid);d.put("nonce",new UUID(actor,mid).toString());return new FamilyCore.Entry(mid,1,actor,new FamilyCore.Op(op.kind,op.entity,d));}
    public static void main(String[]args){
        Random random=new Random(1234);
        for(int i=0;i<1500;i++){
            StringBuilder text=new StringBuilder();for(int n=0;n<random.nextInt(200);n++)text.append((char)(32+random.nextInt(5000)));
            FamilyCore.Op source=op("task","id-"+i,"title",text.toString());
            FamilyCore.Op roundTrip=FamilyCore.unpack(FamilyCore.pack(source));check(roundTrip!=null&&roundTrip.get("title").equals(text.toString()));
        }
        check(FamilyCore.fromMessage("ordinary text")==null);
        check(FamilyCore.unpack("A")==null);check(FamilyCore.unpack("#")==null);
        check(FamilyCore.unpack(FamilyCore.pack(op("hug","x"))+"A")==null);
        String wire=FamilyCore.message("Обнять",op("hug","x"));check(FamilyCore.visibleText(wire).equals("Обнять"));
        for(String amount:new String[]{"-1","0","1.001","NaN","Infinity","1e99"}){try{FamilyCore.cents(amount);check(false);}catch(IllegalArgumentException expected){check(true);}}
        check(FamilyCore.cents("12,34")==1234);check(FamilyCore.money(1234).equals("12.34"));
        Map<String,Long> shares=FamilyCore.splitBill("Pizza;10.01;Ann,Bob,Cat\nTea;1.00;Bob");
        check(shares.get("Ann")==334&&shares.get("Bob")==434&&shares.get("Cat")==333);
        for(int total=1;total<2000;total++){
            Map<String,Long> result=FamilyCore.splitBill("x;"+FamilyCore.money(total)+";A,B,C,D,E,F,G");
            long sum=0,min=Long.MAX_VALUE,max=0;for(long x:result.values()){sum+=x;min=Math.min(min,x);max=Math.max(max,x);}check(sum==total&&max-min<=1);
        }
        ArrayList<FamilyCore.Entry> log=new ArrayList<>();
        log.add(e(1,10,op("task","t","title","Milk")));log.add(e(3,20,op("task_set","t","done","0")));log.add(e(2,20,op("task_set","t","done","1")));
        check(!FamilyCore.reduce(log,10,20).get("t").done);Collections.reverse(log);check(!FamilyCore.reduce(log,10,20).get("t").done);
        log.add(e(4,30,op("task_set","t","done","1")));check(!FamilyCore.reduce(log,10,20).get("t").done);
        log.add(e(5,10,op("goal","g","title","Trip","target","10")));
        FamilyCore.Entry deposit=e(6,20,op("contribution","g","amount","0.03"));log.add(deposit);log.add(deposit);check(FamilyCore.reduce(log,10,20).get("g").saved==3);
        log.add(e(7,20,op("contribution","g","amount","-10")));check(FamilyCore.reduce(log,10,20).get("g").saved==3);
        String secret="salt\nanswer";log.add(e(8,10,op("question_commit","q:2026-10-21","hash",FamilyCore.digest(secret))));
        log.add(e(9,20,op("question_reveal","q:2026-10-21","salt","salt","answer","answer")));check(FamilyCore.reduce(log,10,20).get("q:2026-10-21").answers.isEmpty());
        log.add(e(10,10,op("question_reveal","q:2026-10-21","salt","wrong","answer","answer")));check(FamilyCore.reduce(log,10,20).get("q:2026-10-21").answers.isEmpty());
        log.add(e(11,10,op("question_reveal","q:2026-10-21","salt","salt","answer","answer")));check(FamilyCore.reduce(log,10,20).get("q:2026-10-21").answers.get(10L).equals("answer"));
        log.add(e(12,10,op("question_commit","q:2026-10-21","hash",FamilyCore.digest("new"))));check(FamilyCore.reduce(log,10,20).get("q:2026-10-21").answers.isEmpty());
        FamilyCore.Game game=new FamilyCore.Game(10,"ttt");check(!game.move(10,"0"));check(!game.join(10));check(game.join(20));check(!game.join(30));
        check(game.move(10,"0"));check(!game.move(10,"1"));check(!game.move(20,"0"));check(game.move(20,"3"));check(game.move(10,"1"));check(game.move(20,"4"));check(game.move(10,"2"));check(game.winner=='X');check(!game.move(20,"5"));
        FamilyCore.Game four=new FamilyCore.Game(10,"four");four.join(20);
        for(int i=0;i<3;i++){check(four.move(10,"0"));check(four.move(20,"1"));}check(four.move(10,"0"));check(four.winner=='X');
        ArrayList<FamilyCore.Entry> remapped=new ArrayList<>();
        for(FamilyCore.Entry entry:log)remapped.add(new FamilyCore.Entry(1000000-entry.id*17,entry.date,entry.actor,entry.op));
        check(FamilyCore.reduce(log,10,20).get("t").done==FamilyCore.reduce(remapped,10,20).get("t").done);
        check(FamilyCore.reduce(log,10,20).get("g").saved==FamilyCore.reduce(remapped,10,20).get("g").saved);
        ArrayList<FamilyCore.Entry> dates=new ArrayList<>();dates.add(e(1,10,op("event","birthday","title","Birthday","at","-946771200000","annual","1")));
        check(FamilyCore.reduce(dates,10,20).containsKey("birthday"));
        dates.add(e(2,10,op("event","invalid","title","Bad date","at","not-a-date")));check(!FamilyCore.reduce(dates,10,20).containsKey("invalid"));
        for(int i=0;i<5000;i++){StringBuilder f=new StringBuilder();for(int j=0;j<random.nextInt(50);j++)f.append((char)random.nextInt(150));FamilyCore.unpack(f.toString());checks++;}
        Set<Long> desired=new HashSet<>(Arrays.asList(10L,20L));
        check(!FamilyPrivacyPolicy.canWrite(false,desired,desired));
        check(!FamilyPrivacyPolicy.canWrite(true,desired,new HashSet<>(Arrays.asList(10L))));
        check(FamilyPrivacyPolicy.canWrite(true,desired,desired));
        check(FamilyPrivacyPolicy.matches(desired,desired,true,false));
        check(!FamilyPrivacyPolicy.matches(desired,desired,false,false));
        check(!FamilyPrivacyPolicy.matches(desired,desired,true,true));
        check(!FamilyPrivacyPolicy.matches(desired,Collections.singleton(10L),true,false));
        FamilyTapGuard guard=new FamilyTapGuard();
        check(guard.accept("hug:10",100,1500));check(!guard.accept("hug:10",101,1500));
        check(guard.accept("hug:20",101,1500));check(!guard.accept("hug:10",1599,1500));check(guard.accept("hug:10",1600,1500));
        check(guard.accept("hug:10",50,1500));
        for(int i=0;i<1000;i++)check(guard.accept("bounded:"+i,2000,1000));
        check(!FamilyCore.hiddenInChat(null));
        for(String kind:FamilyCore.KINDS)check(FamilyCore.hiddenInChat(op(kind,"x"))!=kind.equals("hug"));
        ArrayList<FamilyCore.Entry> watch=new ArrayList<>();
        watch.add(e(2,10,op("video_presence","v","joined","1")));
        watch.add(e(1,10,op("video","v","message","50","position","0","playing","0")));
        watch.add(e(3,20,op("video_presence","v","joined","1")));
        watch.add(e(4,10,op("video","v","message","50","position","1000","playing","1")));
        watch.add(e(5,20,op("video_presence","v","joined","0")));
        watch.add(e(6,30,op("video_presence","v","joined","1")));
        FamilyCore.Card video=FamilyCore.reduce(watch,10,20).get("v");
        check(video.latest.op.get("playing").equals("1"));check(video.participants.size()==2);
        check(video.participants.get(10L).op.get("joined").equals("1"));check(video.participants.get(20L).op.get("joined").equals("0"));
        Collections.reverse(watch);check(FamilyCore.reduce(watch,10,20).get("v").participants.get(20L).op.get("joined").equals("0"));
        ArrayList<FamilyCore.Entry> watchRemap=new ArrayList<>();for(FamilyCore.Entry item:watch)watchRemap.add(new FamilyCore.Entry(100000-item.id,1,item.actor,item.op));
        check(FamilyCore.reduce(watchRemap,10,20).get("v").participants.get(20L).op.get("joined").equals("0"));
        check(Arrays.equals(FamilyCore.PAIR_REACTIONS,new String[]{"❤","❤‍🔥","💋"}));
        System.out.println("FamilyGram core: "+checks+" checks passed");
    }
}
