package org.telegram.messenger.family;
import android.os.Bundle;
import android.widget.Toast;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.*;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import java.util.*;
public final class FamilyActions {
    public static final int INLINE=7401, HUB=7402, OURS=7403, EVENT=7404, REACT=7405, WATCH=7406, PRIVATE=7407;
    private FamilyActions(){}
    private static final FamilyTapGuard reactionTaps=new FamilyTapGuard();
    public static boolean hasPairReactions(int account,long dialog) {
        return dialog>0&&dialog==FamilyDataController.getInstance(account).partner();
    }
    public static void inline(BaseFragment fragment,MessageObject message){
        FamilyCore.Op op=FamilyCore.fromMessage(message.messageOwner.message);if(op==null)return;
        FamilyDataController data=FamilyDataController.getInstance(message.currentAccount);long dialog=message.getDialogId();
        FamilyCore.Card card=data.card(dialog,op.entity);
        if((op.kind.equals("task")||op.kind.equals("task_set"))&&card!=null){data.send(dialog,"task_set",op.entity,(card.done?"☐ ":"☑ ")+card.op().get("title"),FamilyCore.fields("done",card.done?"0":"1"));return;}
        if(op.kind.equals("hug")){data.send(dialog,"hug",FamilyDataController.newId(),"🤗 "+FamilyText.get("SendingHug"),FamilyCore.fields());FamilyEffects.show(fragment,null);return;}
        String page=op.kind.startsWith("question")?"question":op.kind.equals("goal")||op.kind.equals("contribution")?"goals":op.kind.equals("video")?"watch":op.kind.equals("game")||op.kind.equals("join")||op.kind.equals("move")?"games":op.kind.equals("bill")?"bill":op.kind.startsWith("bookmark")?"ours":op.kind.startsWith("event")?"calendar":"tasks";
        fragment.presentFragment(FamilyHubActivity.create(message.currentAccount,dialog,page));
    }
    public static void reactToLatest(int account,long dialog,BaseFragment f){
        ArrayList<MessageObject> list=MessagesController.getInstance(account).dialogMessage.get(dialog);MessageObject chosen=null;
        if(list!=null)for(MessageObject m:list)if(m.getId()>0&&!m.isOut()&&m.messageOwner.action==null&&(chosen==null||m.getId()>chosen.getId()))chosen=m;
        if(chosen!=null){randomReaction(chosen,f);return;}
        Toast.makeText(f.getParentActivity(),FamilyText.get("SelectMessageReaction"),Toast.LENGTH_LONG).show();
        Bundle b=new Bundle();b.putLong("user_id",dialog);if(dialog>0){ChatActivity chat=new ChatActivity(b);chat.setCurrentAccount(account);f.presentFragment(chat);}
    }
    public static void randomReaction(MessageObject message,BaseFragment fragment){
        if(message.getId()<=0||message.getDialogId()!=FamilyDataController.getInstance(message.currentAccount).partner())return;
        if(!message.canSetReaction()||!reactionTaps.accept(message.currentAccount+":"+message.getDialogId()+":"+message.getId(),android.os.SystemClock.elapsedRealtime(),800))return;
        ArrayList<String> available=new ArrayList<>();Map<String,TLRPC.TL_availableReaction> reactions=MediaDataController.getInstance(message.currentAccount).getReactionsMap();
        for(String emoji:FamilyCore.PAIR_REACTIONS){TLRPC.TL_availableReaction r=reactions.get(emoji);if(r!=null&&!r.inactive&&!r.premium&&!available.contains(r.reaction))available.add(r.reaction);}
        if(available.isEmpty()){Toast.makeText(fragment.getParentActivity(),FamilyText.get("ReactionsUnavailable"),Toast.LENGTH_LONG).show();return;}
        String emoji=available.get(new java.security.SecureRandom().nextInt(available.size()));ReactionsLayoutInBubble.VisibleReaction reaction=ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(emoji);
        ArrayList<ReactionsLayoutInBubble.VisibleReaction> selected=new ArrayList<>(message.getChoosenReactions());selected.clear();selected.add(reaction);
        SendMessagesHelper.getInstance(message.currentAccount).sendReaction(message,selected,reaction,false,true,fragment,()->{});
        if(fragment.getFragmentView()!=null)fragment.getFragmentView().performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
    }
}
