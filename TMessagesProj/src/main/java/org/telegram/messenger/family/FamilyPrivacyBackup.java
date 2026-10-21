package org.telegram.messenger.family;
import android.util.Base64;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.*;
import java.util.*;
/** Preserve complete last-seen rules, including exceptions, before the family override. */
public final class FamilyPrivacyBackup {
 private FamilyPrivacyBackup(){}
 public static String save(List<TLRPC.PrivacyRule> rules,MessagesController messages){
  if(rules==null||rules.isEmpty())throw new IllegalArgumentException("Privacy rules not loaded");
  ArrayList<TLRPC.InputPrivacyRule> converted=new ArrayList<>();
  for(TLRPC.PrivacyRule rule:rules){
   TLRPC.InputPrivacyRule out;
   if(rule instanceof TLRPC.TL_privacyValueAllowAll)out=new TLRPC.TL_inputPrivacyValueAllowAll();
   else if(rule instanceof TLRPC.TL_privacyValueDisallowAll)out=new TLRPC.TL_inputPrivacyValueDisallowAll();
   else if(rule instanceof TLRPC.TL_privacyValueAllowContacts)out=new TLRPC.TL_inputPrivacyValueAllowContacts();
   else if(rule instanceof TLRPC.TL_privacyValueDisallowContacts)out=new TLRPC.TL_inputPrivacyValueDisallowContacts();
   else if(rule instanceof TLRPC.TL_privacyValueAllowCloseFriends)out=new TLRPC.TL_inputPrivacyValueAllowCloseFriends();
   else if(rule instanceof TLRPC.TL_privacyValueAllowPremium)out=new TLRPC.TL_inputPrivacyValueAllowPremium();
   else if(rule instanceof TLRPC.TL_privacyValueAllowBots)out=new TLRPC.TL_inputPrivacyValueAllowBots();
   else if(rule instanceof TLRPC.TL_privacyValueDisallowBots)out=new TLRPC.TL_inputPrivacyValueDisallowBots();
   else if(rule instanceof TLRPC.TL_privacyValueAllowChatParticipants){TLRPC.TL_inputPrivacyValueAllowChatParticipants r=new TLRPC.TL_inputPrivacyValueAllowChatParticipants();r.chats.addAll(rule.chats);out=r;}
   else if(rule instanceof TLRPC.TL_privacyValueDisallowChatParticipants){TLRPC.TL_inputPrivacyValueDisallowChatParticipants r=new TLRPC.TL_inputPrivacyValueDisallowChatParticipants();r.chats.addAll(rule.chats);out=r;}
   else if(rule instanceof TLRPC.TL_privacyValueAllowUsers){TLRPC.TL_inputPrivacyValueAllowUsers r=new TLRPC.TL_inputPrivacyValueAllowUsers();r.users.addAll(users(rule.users,messages));out=r;}
   else if(rule instanceof TLRPC.TL_privacyValueDisallowUsers){TLRPC.TL_inputPrivacyValueDisallowUsers r=new TLRPC.TL_inputPrivacyValueDisallowUsers();r.users.addAll(users(rule.users,messages));out=r;}
   else throw new IllegalArgumentException("Unsupported privacy rule");
   converted.add(out);
  }
  SerializedData data=new SerializedData();try{data.writeInt32(converted.size());for(TLRPC.InputPrivacyRule rule:converted)rule.serializeToStream(data);return Base64.encodeToString(data.toByteArray(),Base64.NO_WRAP);}finally{data.cleanup();}
 }
 private static ArrayList<TLRPC.InputUser> users(List<Long> ids,MessagesController messages){
  ArrayList<TLRPC.InputUser> result=new ArrayList<>();for(long id:ids){TLRPC.User u=messages.getUser(id);if(u==null)throw new IllegalArgumentException("Missing exception user");TLRPC.InputUser input=messages.getInputUser(u);if(input==null||input instanceof TLRPC.TL_inputUserEmpty)throw new IllegalArgumentException("Invalid exception user");result.add(input);}return result;
 }
 public static ArrayList<TLRPC.InputPrivacyRule> read(String value){
  if(value==null||value.length()>350000)throw new IllegalArgumentException("Invalid privacy backup");
  SerializedData data=new SerializedData(Base64.decode(value,Base64.NO_WRAP));try{int count=data.readInt32(true);if(count<1||count>1000)throw new IllegalArgumentException("Invalid privacy backup");ArrayList<TLRPC.InputPrivacyRule> result=new ArrayList<>();for(int i=0;i<count;i++){TLRPC.InputPrivacyRule rule=TLRPC.InputPrivacyRule.TLdeserialize(data,data.readInt32(true),true);if(rule==null)throw new IllegalArgumentException("Unsupported privacy backup");result.add(rule);}return result;}finally{data.cleanup();}
 }
}
