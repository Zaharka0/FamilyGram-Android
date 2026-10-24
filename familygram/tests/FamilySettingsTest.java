import java.util.*;
import org.telegram.messenger.family.*;
public final class FamilySettingsTest {
 static int count;
 static void check(boolean value){count++;if(!value)throw new AssertionError("settings "+count);}
 public static void main(String[]args){
  check(FamilySections.order("").equals(FamilySections.ALL));
  check(FamilySections.order("games,invalid,games,tasks").size()==12);
  check(FamilySections.order("games,invalid,games,tasks").get(0).equals("games"));
  List<String> visible=FamilySections.visible("games,tasks,hug","tasks","hug");
  check(visible.get(0).equals("hug"));check(visible.get(1).equals("games"));check(!visible.contains("tasks"));
  check(FamilySections.visible("",String.join(",",FamilySections.ALL),"").isEmpty());
  Random random=new Random(19);
  for(int i=0;i<500;i++){
   ArrayList<String> shuffled=new ArrayList<>(FamilySections.ALL);Collections.shuffle(shuffled,random);
   String csv=String.join(",",shuffled);check(FamilySections.order(csv).equals(shuffled));
   List<String> pins=FamilySections.visible(csv,"",shuffled.get(5)+","+shuffled.get(2));
   check(pins.get(0).equals(shuffled.get(2))&&pins.get(1).equals(shuffled.get(5)));
  }
  check(!FamilyStoryPolicy.exempt(0,0,0,false));
  for(long peer:new long[]{1,2,3,-4})for(boolean family:new boolean[]{false,true}){
   boolean exempt=FamilyStoryPolicy.exempt(peer,1,2,family);
   check(exempt==(peer==1||peer==2||family));
   check(FamilyStoryPolicy.hideView(true,exempt)==!exempt);
   check(!FamilyStoryPolicy.hideView(false,exempt));
   check(FamilyStoryPolicy.show(true,exempt)==exempt);
   check(FamilyStoryPolicy.show(false,exempt));
  }
  check(FamilyReleasePolicy.compare("familygram-1.1.0-b90129","v1.0.0")>0);
  check(FamilyReleasePolicy.compare("1.1.0-b90129","1.1.0-b90119")>0);
  check(FamilyReleasePolicy.newer("familygram-1.1.0-b90129","1.1.0",90119));
  check(!FamilyReleasePolicy.newer("familygram-1.1.0-b90129","1.1.0",90129));
  check(!FamilyReleasePolicy.newer("familygram-1.0.0-b999999","1.1.0",90129));
  check(FamilyReleasePolicy.newer("1.2.0","1.1.0",90129));
  for(String tag:new String[]{"latest","nightly","1.0","x1.2.3","1.2.3-rc1","1.2.3-b-1"})check(!FamilyReleasePolicy.valid(tag));
  String root="https://github.com/Zaharka0/FamilyGram-Android/releases/download/";
  check(FamilyReleasePolicy.trusted(root+"familygram-1.1.0-b90129/app.apk"));
  for(String url:new String[]{root+"../app.apk",root+"%2e%2e/app.apk",root+"v1/file.zip",root+"v1/app.apk?x=1",root.replace("https:","http:")+"v1/app.apk",root.replace("github.com","github.com.evil.test")+"v1/app.apk",root.replace("github.com","user@github.com")+"v1/app.apk",root.replace("FamilyGram-Android","AnotherRepo")+"v1/app.apk"})check(!FamilyReleasePolicy.trusted(url));
  System.out.println("FamilyGram settings: "+count+" checks passed");
 }
}
