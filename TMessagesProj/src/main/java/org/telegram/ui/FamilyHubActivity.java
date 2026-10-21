package org.telegram.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.family.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.MediaActivity;
import java.text.*;
import java.util.*;
import java.util.function.Consumer;

/** Native entry point for the family workspace; no WebView or additional login. */
public class FamilyHubActivity extends BaseFragment implements FamilyDataController.Listener {
    private FamilyDataController data;
    private long dialog;
    private String page="home", query="";
    private LinearLayout body;
    private ScrollView scroll;
    private EditText search;
    private boolean refreshed;
    private ArrayList<MessageObject> voiceQueue;
    private final ArrayList<String> billDraft = new ArrayList<>();
    private int visibleLimit=80;
    private static final int PICK_WIDGET_PHOTO=7401, PICK_SHORTCUT_PHOTO=7402;
    public FamilyHubActivity(Bundle args) {
        super(args);
        if(args!=null){currentAccount=args.getInt("account",UserConfig.selectedAccount);dialog=args.getLong("dialog_id",0);page=args.getString("page","home");}
    }
    public static FamilyHubActivity create(int account,long dialog,String page) {
        Bundle b=new Bundle();b.putInt("account",account);b.putLong("dialog_id",dialog);b.putString("page",page);return new FamilyHubActivity(b);
    }
    private String t(String key){return FamilyText.get(key);}
    private int dp(float n){return AndroidUtilities.dp(n);}
    @Override public boolean onFragmentCreate(){
        data=FamilyDataController.getInstance(currentAccount);if(dialog==0)dialog=data.partner();data.addListener(this);data.load(dialog);loadBillDraft();return super.onFragmentCreate();
    }
    @Override public void onFragmentDestroy(){data.removeListener(this);super.onFragmentDestroy();}
    @Override public View createView(Context context){
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setTitle(t("Title"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1){if(!page.equals("home"))open("home");else finishFragment();}}});
        LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        search=new EditText(context);search.setSingleLine(true);search.setTextSize(16);search.setHint(t("Search"));search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));search.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));search.setPadding(dp(20),dp(10),dp(20),dp(10));
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString().trim().toLowerCase(Locale.ROOT);render();}public void afterTextChanged(Editable e){}});
        root.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));
        scroll=new ScrollView(context);scroll.setFillViewport(true);body=new LinearLayout(context);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),dp(8),dp(12),dp(24));scroll.addView(body,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));fragmentView=root;render();return root;
    }
    @Override public void onResume(){super.onResume();render();if(!refreshed&&dialog!=0){refreshed=true;data.sync(dialog,false);}}
    @Override public boolean onBackPressed(boolean invoked){if(!page.equals("home")){open("home");return false;}return true;}
    @Override public void changed(long changed){if(changed==0||changed==dialog){loadBillDraft();render();}}
    private void open(String name){page=name;visibleLimit=80;query="";if(search!=null)search.setText("");render();if(scroll!=null)scroll.smoothScrollTo(0,0);}
    private void render(){
        if(body==null||getParentActivity()==null)return;int y=scroll.getScrollY();body.removeAllViews();
        actionBar.setTitle(page.equals("home")?t("Title"):t(page));
        if(dialog!=0)row(name(dialog),t("ChooseChat"),()->chooseChat(false));
        if(page.equals("settings")){settings();restoreScroll(y);return;}
        if(page.equals("icons")){icons();restoreScroll(y);return;}
        if(page.equals("widget")){widget();restoreScroll(y);return;}
        if(page.equals("home")){
            row(t("settings"),t("SettingsInfo"),()->open("settings"));
            row(t("icons"),t("IconsInfo"),()->open("icons"));row(t("widget"),t("WidgetInfo"),()->open("widget"));
            if(dialog==0){row(t("ChooseChat"),t("ChooseChatInfo"),()->chooseChat(false));return;}
            String[] sections={"tasks","calendar","album","ours","hug","question","goals","watch","voice","games","bill","stats"};
            for(String section:sections)row(t(section),t(section+"Info"),()->open(section));
            row(t("QuickReaction"),t("QuickReactionInfo"),()->FamilyActions.reactToLatest(currentAccount,dialog,this));
            syncRows();restoreScroll(y);return;
        }
        if(dialog==0){row(t("ChooseChat"),t("ChooseChatInfo"),()->chooseChat(false));return;}
        if(page.equals("tasks"))tasks();else if(page.equals("calendar"))calendar();else if(page.equals("album"))album();
        else if(page.equals("ours"))ours();else if(page.equals("hug"))hugs();else if(page.equals("question"))question();
        else if(page.equals("goals"))goals();else if(page.equals("watch"))watch();else if(page.equals("voice"))voice();
        else if(page.equals("games"))games();else if(page.equals("bill"))bills();else if(page.equals("stats"))statistics();
        restoreScroll(y);
    }
    private void restoreScroll(int y){scroll.post(()->scroll.scrollTo(0,y));}
    private boolean matches(String text){return query.isEmpty()||text.toLowerCase(Locale.ROOT).contains(query);}
    private LinearLayout card(){
        LinearLayout v=new LinearLayout(getParentActivity());v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(16),dp(12),dp(16),dp(12));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));bg.setCornerRadius(dp(14));v.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(9);body.addView(v,lp);return v;
    }
    private TextView text(LinearLayout parent,String value,boolean title){
        TextView v=new TextView(getParentActivity());v.setText(value);v.setTextSize(title?17:14);v.setTextColor(Theme.getColor(title?Theme.key_windowBackgroundWhiteBlackText:Theme.key_windowBackgroundWhiteGrayText));
        if(title)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);v.setPadding(0,dp(4),0,dp(4));parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;
    }
    private void button(LinearLayout parent,String label,Runnable action){
        TextView b=new TextView(getParentActivity());b.setText(label);b.setTextSize(15);b.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(0,dp(12),0,dp(12));b.setMinHeight(dp(48));b.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));b.setFocusable(true);b.setOnClickListener(v->run(action));parent.addView(b,new LinearLayout.LayoutParams(-1,-2));
    }
    private void row(String title,String description,Runnable action){
        if(!matches(title+" "+description))return;LinearLayout c=card();text(c,title,true);if(!description.isEmpty())text(c,description,false);if(action!=null){c.setBackground(Theme.createRoundRectDrawable(dp(14),Theme.getColor(Theme.key_windowBackgroundWhite)));c.setOnClickListener(v->run(action));c.setFocusable(true);c.setContentDescription(title+". "+description);c.setMinimumHeight(dp(64));}
    }
    private void info(String message){if(matches(message))text(card(),message,false);}
    private void run(Runnable action){try{action.run();}catch(Exception e){toast(t("Invalid")+"\n"+e.getMessage());FileLog.e(e);}}
    private void toast(String value){if(getParentActivity()!=null)Toast.makeText(getParentActivity(),value,Toast.LENGTH_LONG).show();}
    private String name(long id){
        if(id==data.self)return t("You");if(id>0){TLRPC.User u=getMessagesController().getUser(id);return u==null?""+id:UserObject.getUserName(u);}TLRPC.Chat c=getMessagesController().getChat(-id);return c==null?""+id:c.title;
    }
    private void chooseChat(boolean partner){
        ArrayList<Long> ids=new ArrayList<>();ArrayList<String> names=new ArrayList<>();
        for(TLRPC.Dialog d:getMessagesController().getAllDialogs()){
            if(d.id==0||d.id==data.self||DialogObject.isEncryptedDialog(d.id)||d.id>0&&UserObject.isBot(getMessagesController().getUser(d.id)))continue;
            if(partner&&d.id<0)continue;if(d.id<0){TLRPC.Chat c=getMessagesController().getChat(-d.id);if(c==null||ChatObject.isChannel(c)&&!c.megagroup)continue;}
            ids.add(d.id);names.add(name(d.id));if(ids.size()>=500)break;
        }
        if(ids.isEmpty()){toast(t("NoChats"));return;}
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(partner?t("Partner"):t("ChooseChat")).setItems(names.toArray(new String[0]),(d,which)->{
            dialog=ids.get(which);voiceQueue=null;loadBillDraft();if(partner)data.setPartner(dialog);refreshed=false;data.load(dialog);data.sync(dialog,false);render();
        }).setNegativeButton(t("Cancel"),null).create());
    }
    private void form(String title,String[] keys,String[] defaults,Consumer<String[]> submit){form(title,keys,defaults,submit,t("Save"));}
    private void form(String title,String[] keys,String[] defaults,Consumer<String[]> submit,String positiveLabel){
        LinearLayout content=new LinearLayout(getParentActivity());content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(4),dp(20),dp(4));
        EditText[] fields=new EditText[keys.length];
        for(int i=0;i<keys.length;i++){
            if(keys[i].equals("Annual")){
                EditText hidden=new EditText(getParentActivity());fields[i]=hidden;hidden.setText(defaults!=null&&i<defaults.length?defaults[i]:"0");
                CheckBox check=new CheckBox(getParentActivity());check.setText(t("Annual"));check.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));check.setChecked(hidden.getText().toString().equals("1"));check.setOnCheckedChangeListener((v,on)->hidden.setText(on?"1":"0"));content.addView(check,new LinearLayout.LayoutParams(-1,dp(56)));continue;
            }
            text(content,t(keys[i]),false);EditText field=new EditText(getParentActivity());fields[i]=field;field.setTextSize(16);field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));field.setHint(t(keys[i]+"Hint"));field.setText(defaults!=null&&i<defaults.length?defaults[i]:"");field.setMaxLines(keys[i].equals("Items")?8:4);
            if(keys[i].equals("Amount")||keys[i].equals("Target"))field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
            if(keys[i].equals("When")){field.setFocusable(false);field.setOnClickListener(v->dateTime(field));}
            content.addView(field,new LinearLayout.LayoutParams(-1,-2));
        }
        ScrollView wrap=new ScrollView(getParentActivity());wrap.addView(content);
        AlertDialog dlg=new AlertDialog.Builder(getParentActivity()).setTitle(title).setView(wrap).setNegativeButton(t("Cancel"),null).setPositiveButton(positiveLabel,null).create();showDialog(dlg);
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String[] values=new String[fields.length];for(int i=0;i<fields.length;i++)values[i]=fields[i].getText().toString().trim();submit.accept(values);dlg.dismiss();}catch(Exception e){toast(t("Invalid")+"\n"+e.getMessage());}});
    }
    private void dateTime(EditText field){
        Calendar c=Calendar.getInstance();new DatePickerDialog(getParentActivity(),(picker,y,m,d)->new TimePickerDialog(getParentActivity(),(clock,h,min)->{c.set(y,m,d,h,min,0);c.set(Calendar.MILLISECOND,0);field.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(c.getTime()));},c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show(),c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
    private String required(String s,int max){if(s.isEmpty()||s.length()>max)throw new IllegalArgumentException(t("Required")+" ("+max+")");return s;}
    private List<FamilyCore.Card> cards(String kind){
        ArrayList<FamilyCore.Card> cards=new ArrayList<>();for(FamilyCore.Card c:data.cards(dialog))if(c.op().kind.equals(kind)&&!c.removed)cards.add(c);Collections.reverse(cards);return cards;
    }
    private void send(String kind,String entity,String human,String...fields){data.send(dialog,kind,entity,human,FamilyCore.fields(fields));}
    private void tasks(){
        row(t("AddTask"),t("AddTaskInfo"),()->form(t("AddTask"),new String[]{"TaskTitle","ListName"},new String[]{"",t("Shopping")},v->{required(v[0],200);required(v[1],80);send("task",FamilyDataController.newId(),"☐ "+v[0]+" · "+v[1],"title",v[0],"list",v[1]);}));
        int count=0;for(FamilyCore.Card c:cards("task")){
            if(!matches(c.op().get("title")+" "+c.op().get("list")))continue;if(count++>=visibleLimit)break;
            LinearLayout box=card();text(box,(c.done?"☑ ":"☐ ")+c.op().get("title"),true);text(box,c.op().get("list")+" · "+name(c.latest.actor),false);
            button(box,c.done?t("UndoDone"):t("MarkBought"),()->send("task_set",c.op().entity,(c.done?"☐ ":"☑ ")+c.op().get("title"),"done",c.done?"0":"1"));
        }more(count);
    }
    public void eventFromMessage(MessageObject source){
        String initial=source==null?"":source.messageText==null?"":source.messageText.toString();if(initial.length()>200)initial=initial.substring(0,200);
        form(t("AddEvent"),new String[]{"EventTitle","When","Annual"},new String[]{initial,new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date()),"0"},v->{
            required(v[0],200);SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);format.setLenient(false);ParsePosition p=new ParsePosition(0);Date date=format.parse(v[1],p);if(date==null||p.getIndex()!=v[1].length()||date.getTime() < -5364662400000L||date.getTime()>32503680000000L)throw new IllegalArgumentException(t("DateFormat"));
            if(!v[2].equals("0")&&!v[2].equals("1"))throw new IllegalArgumentException(t("AnnualHint"));
            Map<String,String> f=FamilyCore.fields("title",v[0],"at",""+date.getTime(),"annual",v[2]);if(source!=null){f.put("ref",FamilyDataController.reference(source.messageOwner,source.isOut()?data.self:dialog));f.put("message",""+source.getId());}
            data.send(dialog,"event",FamilyDataController.newId(),"📅 "+v[0]+"\n"+v[1],f,source);
        });
    }
    private long occurrence(FamilyCore.Card card){
        long at=card.op().number("at");if(card.op().get("annual").equals("1")){Calendar c=Calendar.getInstance();c.setTimeInMillis(at);int month=c.get(Calendar.MONTH),day=c.get(Calendar.DAY_OF_MONTH);Calendar now=Calendar.getInstance();c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.YEAR,now.get(Calendar.YEAR));c.set(Calendar.MONTH,month);c.set(Calendar.DAY_OF_MONTH,Math.min(day,c.getActualMaximum(Calendar.DAY_OF_MONTH)));if(c.getTimeInMillis()<System.currentTimeMillis()){c.set(Calendar.DAY_OF_MONTH,1);c.add(Calendar.YEAR,1);c.set(Calendar.MONTH,month);c.set(Calendar.DAY_OF_MONTH,Math.min(day,c.getActualMaximum(Calendar.DAY_OF_MONTH)));}at=c.getTimeInMillis();}return at;
    }
    private void calendar(){
        row(t("AddEvent"),t("calendarInfo"),()->eventFromMessage(null));List<FamilyCore.Card> list=cards("event");list.sort(Comparator.comparingLong(this::occurrence));int count=0;
        for(FamilyCore.Card c:list){if(!matches(c.op().get("title")))continue;if(count++>=visibleLimit)break;LinearLayout box=card();text(box,c.op().get("title"),true);text(box,DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(occurrence(c)))+(c.op().get("annual").equals("1")?" · "+t("Yearly"):""),false);if(!c.op().get("ref").isEmpty())button(box,t("OpenMessage"),()->openReference(c.op().get("ref")));if(c.origin.actor==data.self)button(box,t("CancelEvent"),()->send("event_cancel",c.op().entity,"📅 × "+c.op().get("title")));}more(count);
    }
    private void album(){
        long peer=data.partner();if(peer==0){row(t("Partner"),t("ChoosePartnerInfo"),()->chooseChat(true));return;}
        row(t("OpenAlbum"),name(peer)+" · "+t("AlbumDateInfo"),()->{Bundle b=new Bundle();b.putLong("dialog_id",peer);b.putInt("start_from",0);MediaActivity media=new MediaActivity(b,null);media.setCurrentAccount(currentAccount);presentFragment(media);});
    }
    private void ours(){
        info(t("OursHelp"));int count=0;for(FamilyCore.Card c:cards("bookmark")){if(!matches(c.op().get("text")))continue;if(count++>=visibleLimit)break;LinearLayout box=card();text(box,"★ "+c.op().get("text"),true);text(box,name(c.origin.actor),false);button(box,t("OpenMessage"),()->openReference(c.op().get("ref")));if(c.origin.actor==data.self)button(box,t("RemoveBookmark"),()->send("bookmark_remove",c.op().entity,"★ × "+c.op().get("text")));}more(count);
    }
    private void openReference(String reference){data.resolve(dialog,reference,id->{if(id<=0){toast(t("LoadHistoryFirst"));return;}openChat(dialog,id);});}
    private void openChat(long peer,int mid){Bundle b=new Bundle();if(peer>0)b.putLong("user_id",peer);else b.putLong("chat_id",-peer);if(mid>0)b.putInt("message_id",mid);ChatActivity chat=new ChatActivity(b);chat.setCurrentAccount(currentAccount);presentFragment(chat);}
    private boolean pair(){if(dialog<=0||dialog!=data.partner()){row(t("Partner"),t("PairOnly"),()->chooseChat(true));return false;}return true;}
    private void hugs(){
        if(!pair())return;row(t("SendHug"),t("hugInfo"),()->{send("hug",FamilyDataController.newId(),"🤗 "+t("SendingHug"));FamilyEffects.show(this,null);});
        int count=0;for(FamilyCore.Card c:cards("hug")){if(count++>=20)break;row("🤗 "+name(c.origin.actor),DateFormat.getDateTimeInstance().format(new Date(c.origin.date*1000L)),()->{send("hug",FamilyDataController.newId(),"🤗 "+t("SendingHug"));FamilyEffects.show(this,null);});}
    }
    private void question(){
        if(!pair())return;String id=data.questionId();LinearLayout box=card();text(box,data.questionText(id),true);text(box,t("QuestionHelp"),false);FamilyCore.Card q=data.card(dialog,id);
        if(q!=null&&q.answers.containsKey(data.self)&&q.answers.containsKey(dialog)){text(box,name(data.self)+": "+q.answers.get(data.self),false);text(box,name(dialog)+": "+q.answers.get(dialog),false);}
        else{String status=q==null?t("NoAnswers"):q.commitments.containsKey(data.self)?t("WaitingAnswer"):t("PartnerAnswered");text(box,status,false);button(box,t("Answer"),()->form(t("Answer"),new String[]{"AnswerText"},null,v->data.answer(dialog,id,required(v[0],600))));button(box,t("RetryReveal"),()->data.retryReveal(dialog));}
        for(FamilyCore.Card c:cards("question_commit")){if(c.op().entity.equals(id)||!c.answers.containsKey(data.self)||!c.answers.containsKey(dialog))continue;LinearLayout old=card();text(old,c.op().entity.substring(2)+" · "+data.questionText(c.op().entity),true);text(old,name(data.self)+": "+c.answers.get(data.self)+"\n"+name(dialog)+": "+c.answers.get(dialog),false);}
    }
    private void goals(){
        row(t("AddGoal"),t("goalsInfo"),()->form(t("AddGoal"),new String[]{"GoalTitle","Target","Currency"},new String[]{"","","RUB"},v->{required(v[0],200);FamilyCore.cents(v[1]);if(!v[2].matches("[A-Za-z]{3}"))throw new IllegalArgumentException(t("CurrencyHint"));send("goal",FamilyDataController.newId(),"🎯 "+v[0]+" · "+v[1]+" "+v[2].toUpperCase(Locale.ROOT),"title",v[0],"target",v[1],"currency",v[2].toUpperCase(Locale.ROOT));}));
        for(FamilyCore.Card c:cards("goal")){
            if(!matches(c.op().get("title")))continue;LinearLayout box=card();text(box,c.op().get("title"),true);text(box,FamilyCore.money(c.saved)+" / "+FamilyCore.money(FamilyCore.cents(c.op().get("target")))+" "+c.op().get("currency"),false);
            ProgressBar bar=new ProgressBar(getParentActivity(),null,android.R.attr.progressBarStyleHorizontal);bar.setMax(10000);bar.setProgress((int)Math.min(10000,c.saved/(double)FamilyCore.cents(c.op().get("target"))*10000));box.addView(bar,new LinearLayout.LayoutParams(-1,dp(8)));
            button(box,t("Contribute"),()->form(t("Contribute"),new String[]{"Amount","Note"},null,v->{FamilyCore.cents(v[0]);if(v[1].length()>300)throw new IllegalArgumentException(t("TooLong"));send("contribution",c.op().entity,"🎯 +"+v[0]+" "+c.op().get("currency")+" · "+c.op().get("title")+"\n"+v[1],"amount",v[0],"note",v[1]);}));
        }info(t("ManualMoney"));
    }
    private void watch(){
        if(!pair())return;info(t("WatchHelp"));row(t("ChooseVideo"),t("LoadHistoryFirst"),()->data.media(dialog,2,list->{if(list.isEmpty()){toast(t("NoMedia"));return;}String[] labels=new String[list.size()];for(int i=0;i<list.size();i++)labels[i]=DateFormat.getDateTimeInstance().format(new Date(list.get(i).messageOwner.date*1000L))+" · "+(int)list.get(i).getDuration()+" s";showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(t("ChooseVideo")).setItems(labels,(d,n)->presentFragment(new FamilyWatchActivity(currentAccount,dialog,FamilyDataController.newId(),list.get(n),true))).setNegativeButton(t("Cancel"),null).create());}));
        for(FamilyCore.Card c:cards("video"))row(t("WatchSession"),DateFormat.getDateTimeInstance().format(new Date(c.latest.date*1000L)),()->data.resolve(dialog,c.latest.op.get("ref"),id->{if(id==0){toast(t("LoadHistoryFirst"));return;}data.message(dialog,id,m->{if(m==null||!m.isVideo()){toast(t("NoMedia"));return;}presentFragment(new FamilyWatchActivity(currentAccount,dialog,c.op().entity,m,false));});}));
    }
    private void voice(){
        toggle(t("SkipSilence"),t("SkipSilenceInfo"),FamilyVoice.skipSilence(currentAccount),()->{FamilyVoice.setSkipSilence(currentAccount,!FamilyVoice.skipSilence(currentAccount));render();});
        info(t("VoiceHelp"));row(t("LoadVoice"),t("VoiceQueueInfo"),()->data.media(dialog,3,list->{
            voiceQueue=new ArrayList<>();for(MessageObject m:list)if(!m.isOut()&&m.isContentUnread()&&!FamilyVoice.heard(m))voiceQueue.add(m);
            if(voiceQueue.isEmpty())toast(t("NoVoice"));render();
        }));
        if(voiceQueue!=null&&!voiceQueue.isEmpty()){
            ArrayList<MessageObject> unread=new ArrayList<>(voiceQueue);LinearLayout box=card();text(box,t("Queue")+": "+unread.size(),true);
            button(box,t("PlayQueue"),()->{MessageObject first=unread.get(0);MediaController player=MediaController.getInstance();if(player.playMessage(first))player.setVoiceMessagesPlaylist(unread,true);});
            for(int i=0;i<Math.min(100,unread.size());i++){MessageObject m=unread.get(i);button(box,DateFormat.getDateTimeInstance().format(new Date(m.messageOwner.date*1000L))+" · "+(int)m.getDuration()+" s",()->{if(MediaController.getInstance().playMessage(m))MediaController.getInstance().setVoiceMessagesPlaylist(new ArrayList<>(unread.subList(unread.indexOf(m),unread.size())),true);});}
        }
    }
    private void games(){
        if(dialog<=0){info(t("PairGame"));return;}
        row(t("NewTTT"),t("GameHelp"),()->send("game",FamilyDataController.newId(),"🎮 "+t("NewTTT"),"mode","ttt"));
        row(t("NewFour"),t("FourHelp"),()->send("game",FamilyDataController.newId(),"🎮 "+t("NewFour"),"mode","four"));
        int count=0;for(FamilyCore.Card c:cards("game")){
            if(count++>=visibleLimit)break;FamilyCore.Game g=c.game;if(g==null)continue;LinearLayout box=card();text(box,t(g.target==4?"NewFour":"NewTTT"),true);
            String status=g.second==0?t("WaitingPlayer"):g.winner!=0?t("Winner")+" "+name(g.winner=='X'?g.first:g.second):g.moves==g.cells.length?t("Draw"):t("Turn")+" "+name(g.turn());text(box,status,false);
            if(g.second==0&&g.first!=data.self)button(box,t("JoinGame"),()->send("join",c.op().entity,"🎮 "+t("JoinGame")));
            for(int y=0;y<g.height;y++){
                LinearLayout line=new LinearLayout(getParentActivity());line.setOrientation(LinearLayout.HORIZONTAL);box.addView(line,new LinearLayout.LayoutParams(-1,-2));
                for(int x=0;x<g.width;x++){int cell=y*g.width+x, move=g.target==4?x:cell;TextView b=new TextView(getParentActivity());b.setGravity(Gravity.CENTER);b.setText(g.cells[cell]=='.'?"·":g.cells[cell]=='X'?"✕":"○");b.setTextSize(g.target==4?22:30);b.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));b.setContentDescription((y+1)+", "+(x+1)+": "+g.cells[cell]);b.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));line.addView(b,new LinearLayout.LayoutParams(0,dp(g.target==4?44:64),1));b.setEnabled(g.turn()==data.self&&g.winner==0);b.setOnClickListener(v->run(()->send("move",c.op().entity,"🎮 "+t("GameMove")+" "+(move+1),"cell",""+move)));}
            }
        }more(count);
    }
    private void loadBillDraft(){billDraft.clear();String stored=data.prefs.getString("bill_draft_"+dialog,"");if(!stored.isEmpty())billDraft.addAll(Arrays.asList(stored.split("\n")));}
    private void saveBillDraft(){data.prefs.edit().putString("bill_draft_"+dialog,android.text.TextUtils.join("\n",billDraft)).apply();}
    private void bills(){
        row(t("AddBillItem"),t("BillBuilder"),()->form(t("AddBillItem"),new String[]{"ItemName","Amount","Participants"},null,v->{
            required(v[0],100);required(v[2],500);if(v[0].contains(";")||v[0].contains("\n")||v[2].contains(";")||v[2].contains("\n"))throw new IllegalArgumentException(t("ParticipantsHint"));
            String line=v[0]+";"+FamilyCore.money(FamilyCore.cents(v[1]))+";"+v[2];ArrayList<String> next=new ArrayList<>(billDraft);next.add(line);FamilyCore.splitBill(android.text.TextUtils.join("\n",next));billDraft.add(line);saveBillDraft();render();
        },t("Add")));
        if(!billDraft.isEmpty()){
            LinearLayout draft=card();text(draft,t("BillDraft"),true);
            for(int i=0;i<billDraft.size();i++){final int index=i;String[] item=billDraft.get(i).split(";",-1);text(draft,item[0]+" · "+item[1]+" · "+item[2],false);button(draft,t("RemoveItem"),()->{billDraft.remove(index);saveBillDraft();render();});}
            String encoded=android.text.TextUtils.join("\n",billDraft);for(Map.Entry<String,Long> share:FamilyCore.splitBill(encoded).entrySet())text(draft,share.getKey()+": "+FamilyCore.money(share.getValue()),true);
            button(draft,t("SendBill"),()->form(t("SendBill"),new String[]{"Currency"},new String[]{"RUB"},v->{
                if(!v[0].matches("[A-Za-z]{3}"))throw new IllegalArgumentException(t("CurrencyHint"));String currency=v[0].toUpperCase(Locale.ROOT);Map<String,Long> shares=FamilyCore.splitBill(encoded);StringBuilder human=new StringBuilder("🧾 "+t("SplitBill")+"\n");for(Map.Entry<String,Long> e:shares.entrySet())human.append(e.getKey()).append(": ").append(FamilyCore.money(e.getValue())).append(' ').append(currency).append('\n');
                send("bill",FamilyDataController.newId(),human.toString(),"items",encoded,"currency",currency);billDraft.clear();saveBillDraft();render();
            },t("SendBill")));
        }
        for(FamilyCore.Card c:cards("bill")){LinearLayout box=card();text(box,t("SplitBill"),true);for(String line:c.op().get("items").split("\n")){if(line.trim().isEmpty())continue;String[] item=line.split(";",-1);text(box,item[0]+" · "+item[1]+" "+c.op().get("currency")+" · "+item[2],false);}for(Map.Entry<String,Long> e:FamilyCore.splitBill(c.op().get("items")).entrySet())text(box,e.getKey()+": "+FamilyCore.money(e.getValue())+" "+c.op().get("currency"),true);}
    }
    private void statistics(){
        syncRows();row(t("AllStats"),t("StatsCoverage"),()->showStats(0));int year=Calendar.getInstance().get(Calendar.YEAR);row(t("YearCard")+" "+year,t("YearCardInfo"),()->showStats(year));row(t("YearCard")+" "+(year-1),t("YearCardInfo"),()->showStats(year-1));
    }
    private void showStats(int year){
        data.statistics(dialog,year,result->{if(getParentActivity()==null)return;String title=(year==0?t("AllStats"):t("YearCard")+" "+year)+" · "+name(dialog);String coverage=data.complete(dialog)?t("HistoryComplete"):t("HistoryPartial");showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(title).setMessage(coverage+"\n\n"+result).setNegativeButton(t("Close"),null).setPositiveButton(t("SendToChat"),(d,w)->getSendMessagesHelper().sendMessage(SendMessagesHelper.SendMessageParams.of("FamilyGram · "+title+"\n"+coverage+"\n"+result,dialog,null,null,null,false,null,null,null,true,0,0,null,false))).create());});
    }
    private void syncRows(){
        String status=(data.complete(dialog)?t("HistoryComplete"):t("HistoryPartial"))+" · "+data.scanned(dialog);
        String error=data.prefs.getString("sync_error_"+dialog,"");if(!error.isEmpty())status+="\n"+error;
        if(data.scanning(dialog))row(t("Syncing"),status,()->data.cancelSync(dialog));
        else{row(t("Refresh"),t("RefreshInfo"),()->data.sync(dialog,false));row(t("LoadAllHistory"),status,()->data.sync(dialog,true));}
    }
    private void more(int count){if(count>visibleLimit)row(t("More"),"",()->{visibleLimit+=80;render();});}
    private void toggle(String title,String description,boolean checked,Runnable action){row((checked?"✓ ":"○ ")+title,description,action);}
    private void settings(){
        row(t("Partner"),data.partner()==0?t("ChoosePartnerInfo"):name(data.partner()),()->chooseChat(true));
        row(t("TogetherSince"),data.since()==0?t("NotSet"):DateFormat.getDateInstance().format(new Date(data.since())),()->{
            Calendar c=Calendar.getInstance();if(data.since()>0)c.setTimeInMillis(data.since());DatePickerDialog picker=new DatePickerDialog(getParentActivity(),(v,y,m,d)->{Calendar date=Calendar.getInstance();date.set(y,m,d,12,0,0);date.set(Calendar.MILLISECOND,0);data.setSince(date.getTimeInMillis());render();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH));picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.setButton(android.content.DialogInterface.BUTTON_NEUTRAL,t("Reset"),(d,w)->{data.setSince(0);render();});picker.show();
        });
        toggle(t("PrivateScreen"),t("PrivateScreenInfo"),FamilyPrivacyScreen.enabled(),()->FamilyPrivacyScreen.toggle(getParentActivity()));
        toggle(t("SkipSilence"),t("SkipSilenceInfo"),FamilyVoice.skipSilence(currentAccount),()->{FamilyVoice.setSkipSilence(currentAccount,!FamilyVoice.skipSilence(currentAccount));render();});
        row(t("icons"),t("IconsInfo"),()->open("icons"));row(t("widget"),t("WidgetInfo"),()->open("widget"));
        info(t("LegacySettings"));
        toggle(t("FamilyOnly"),t("FamilyOnlyInfo"),FamilyController.isOnlyFamilyMode(),()->{FamilyController.setOnlyFamilyMode(!FamilyController.isOnlyFamilyMode());render();});
        toggle(t("KeepDeleted"),t("KeepDeletedInfo"),FamilyController.isKeepDeleted(),()->{FamilyController.setKeepDeleted(!FamilyController.isKeepDeleted());render();});
        toggle(t("GhostRead"),t("GhostReadInfo"),FamilyController.isGhostNoRead(),()->{FamilyController.setGhostNoRead(!FamilyController.isGhostNoRead());render();});
        toggle(t("GhostTyping"),t("GhostTypingInfo"),FamilyController.isGhostNoTyping(),()->{FamilyController.setGhostNoTyping(!FamilyController.isGhostNoTyping());render();});
        toggle(t("GhostOnline"),t("GhostOnlineInfo"),FamilyController.isGhostNoOnline(),()->{FamilyController.setGhostNoOnline(!FamilyController.isGhostNoOnline());if(FamilyController.shouldHideOnline())FamilyController.sendOfflineNow();FamilyController.syncLastSeen();render();});
        toggle(t("GhostFamily"),t("GhostFamilyInfo"),FamilyController.isGhostFamilyExempt(),()->{FamilyController.setGhostFamilyExempt(!FamilyController.isGhostFamilyExempt());if(FamilyController.shouldHideOnline(currentAccount))FamilyController.sendOfflineNow();FamilyController.syncLastSeen();render();});
        row(t("Updates"),t("UpdatesInfo"),()->FamilyUpdates.check(this,false));
        toggle(t("UpdateChecks"),t("UpdateChecksInfo"),data.prefs.getBoolean("updates",false),()->{data.prefs.edit().putBoolean("updates",!data.prefs.getBoolean("updates",false)).apply();render();});
    }
    private void icons(){
        info(t("IconPhotoInfo"));row(t("AddPhotoShortcut"),t("ShortcutInfo"),()->pickPhoto(PICK_SHORTCUT_PHOTO));
        for(LauncherIconController.LauncherIcon icon:LauncherIconController.LauncherIcon.values())row((LauncherIconController.isEnabled(icon)?"✓ ":"")+LocaleController.getString(icon.title),t("BuiltInIcon"),()->{LauncherIconController.setIcon(icon);render();});
    }
    private void widget(){
        if(data.partner()==0){row(t("Partner"),t("ChoosePartnerInfo"),()->chooseChat(true));return;}
        info(name(data.partner())+" · "+data.daysTogether()+" "+t("DaysTogether"));row(t("ChoosePhoto"),t("WidgetPhotoInfo"),()->pickPhoto(PICK_WIDGET_PHOTO));
        row(t("AddWidget"),t("WidgetInfo"),()->FamilyPairWidget.pin(getParentActivity(),currentAccount));
    }
    private void pickPhoto(int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,request);}
    @Override public void onActivityResultFragment(int request,int result,Intent intent){super.onActivityResultFragment(request,result,intent);if(result!=Activity.RESULT_OK||intent==null||intent.getData()==null)return;if(request==PICK_WIDGET_PHOTO||request==PICK_SHORTCUT_PHOTO)FamilyPhotos.importPhoto(currentAccount,intent.getData(),request==PICK_WIDGET_PHOTO,getParentActivity(),message->{toast(message);render();});}
}
