package org.telegram.messenger.family;
import org.telegram.messenger.*;
import java.util.*;
/** Local controls are never serialized into another person's bot keyboard. */
public final class FamilyCards {
    private FamilyCards(){}
    public static void decorate(MessageObject message){
        if(message.messageOwner==null||DialogObject.isEncryptedDialog(message.getDialogId())||message.messageOwner.fwd_from!=null)return;
        FamilyCore.Op op=FamilyCore.fromMessage(message.messageOwner.message);if(op==null)return;
        message.messageText=FamilyCore.visibleText(message.messageOwner.message);
        // State-dependent text has different offsets from the wire body. Let native plain-text parsing regenerate links.
        if(message.messageOwner.entities!=null)message.messageOwner.entities.clear();
        FamilyDataController data=FamilyDataController.getInstance(message.currentAccount);data.load(message.getDialogId());FamilyCore.Card card=data.card(message.getDialogId(),op.entity);if(card==null)return;
        String kind=card.op().kind;
        if(kind.equals("task"))message.messageText=(card.done?"☑ ":"☐ ")+card.op().get("title")+"\n"+card.op().get("list");
        else if(kind.equals("goal"))message.messageText="🎯 "+card.op().get("title")+"\n"+FamilyCore.money(card.saved)+" / "+card.op().get("target")+" "+card.op().get("currency");
        else if(kind.equals("question_commit")){
            long dialog=message.getDialogId();String question=data.questionText(op.entity);
            if(card.answers.containsKey(data.self)&&card.answers.containsKey(dialog))message.messageText=question+"\n\n"+card.answers.get(data.self)+"\n\n"+card.answers.get(dialog);
            else message.messageText=question+"\n"+FamilyText.get("WaitingAnswer");
        }else if(kind.equals("game")&&card.game!=null){FamilyCore.Game g=card.game;StringBuilder text=new StringBuilder("🎮 "+FamilyText.get(g.target==3?"NewTTT":"NewFour")+"\n");for(int y=0;y<g.height;y++){for(int x=0;x<g.width;x++){char c=g.cells[y*g.width+x];text.append(c=='.'?"·":c=='X'?"✕":"○").append(' ');}text.append('\n');}message.messageText=text.toString();}
    }
    public static boolean hidden(MessageObject message) {
        if(message==null||message.messageOwner==null||message.messageOwner.fwd_from!=null||DialogObject.isEncryptedDialog(message.getDialogId()))return false;
        return FamilyCore.hiddenInChat(FamilyCore.fromMessage(message.messageOwner.message));
    }
    public static BotInlineKeyboard.Source buttons(MessageObject message){
        if(message.messageOwner==null||message.messageOwner.fwd_from!=null||message.getId()<=0||message.messageOwner.ttl_period!=0||message.messageOwner.noforwards||DialogObject.isEncryptedDialog(message.getDialogId()))return null;
        FamilyCore.Op op=FamilyCore.fromMessage(message.messageOwner.message);
        if(op==null||!op.kind.equals("hug"))return null;
        return source(new BotInlineKeyboard.Button[]{new FamilyButton(FamilyActions.INLINE,"HugBack",0)});
    }
    private static BotInlineKeyboard.Source source(BotInlineKeyboard.Button[] buttons){return new BotInlineKeyboard.Source(){public int getRowsCount(){return 1;}public int getColumnsCount(int row){return buttons.length;}public boolean hasSeparator(int row){return false;}public BotInlineKeyboard.Button getButton(int row,int column){return buttons[column];}};}
}
