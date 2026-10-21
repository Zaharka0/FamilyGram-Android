package org.telegram.ui;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.messenger.family.*;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.VideoPlayer;
import java.io.File;

/** Explicit opt-in playback. Remote changes never publish another change (no echo loop). */
public final class FamilyWatchActivity extends BaseFragment implements FamilyDataController.Listener {
    private final long dialog;
    private final String session;
    private final MessageObject video;
    private final boolean host;
    private FamilyDataController data;
    private VideoPlayer player;
    private TextureView texture;
    private TextView status;
    private Button join,play;
    private SeekBar seek;
    private boolean joined,destroyed,dragging,loading,firstApply=true;
    private int lastApplied;
    private final Runnable tick=new Runnable(){public void run(){
        if(destroyed||!joined)return;
        if(player!=null&&!dragging){long duration=player.getDuration();seek.setProgress(duration>0?(int)(Math.max(0,player.getCurrentPosition())*10000/duration):0);play.setText(FamilyText.get(player.isPlaying()?"Pause":"Play"));}
        AndroidUtilities.runOnUIThread(this,500);
    }};
    public FamilyWatchActivity(int account,long dialog,String session,MessageObject video,boolean host){currentAccount=account;this.dialog=dialog;this.session=session;this.video=video;this.host=host;}
    @Override public boolean onFragmentCreate(){data=FamilyDataController.getInstance(currentAccount);data.addListener(this);data.load(dialog);return super.onFragmentCreate();}
    @Override public View createView(Context context){
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.setTitle(FamilyText.get("watch"));actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){public void onItemClick(int id){if(id==-1)finishFragment();}});
        LinearLayout root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(16,16,16,16);root.setBackgroundColor(Color.BLACK);
        texture=new TextureView(context);root.addView(texture,new LinearLayout.LayoutParams(-1,0,1));status=new TextView(context);status.setTextColor(Color.WHITE);status.setText(FamilyText.get("WatchHelp"));root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        seek=new SeekBar(context);seek.setMax(10000);seek.setEnabled(false);root.addView(seek,new LinearLayout.LayoutParams(-1,-2));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){}public void onStartTrackingTouch(SeekBar b){dragging=true;}public void onStopTrackingTouch(SeekBar b){dragging=false;if(player!=null&&player.getDuration()>0){long position=player.getDuration()*b.getProgress()/10000;player.seekTo(position);publish(player.isPlaying(),position);}}});
        join=new Button(context);join.setText(FamilyText.get("JoinWatch"));root.addView(join,new LinearLayout.LayoutParams(-1,-2));join.setOnClickListener(v->{if(!joined){joined=true;join.setEnabled(false);if(player==null)start();else{play.setEnabled(true);seek.setEnabled(true);applyRemote();AndroidUtilities.runOnUIThread(tick);}}});
        play=new Button(context);play.setText(FamilyText.get("Play"));play.setEnabled(false);root.addView(play,new LinearLayout.LayoutParams(-1,-2));play.setOnClickListener(v->{if(player==null)return;boolean playing=!player.isPlaying();if(playing)player.play();else player.pause();publish(playing,Math.max(0,player.getCurrentPosition()));});
        fragmentView=root;return root;
    }
    private void start(){
        if(destroyed||!joined)return;File file=FileLoader.getInstance(currentAccount).getPathToMessage(video.messageOwner);
        if(video.messageOwner.attachPath!=null&&!video.messageOwner.attachPath.isEmpty()){File local=new File(video.messageOwner.attachPath);if(local.isFile())file=local;}
        if(!file.isFile()){
            status.setText(FamilyText.get("DownloadingVideo"));if(!loading){loading=true;FileLoader.getInstance(currentAccount).loadFile(video.getDocument(),video,FileLoader.PRIORITY_NORMAL,0);}
            AndroidUtilities.runOnUIThread(this::start,800);return;
        }
        player=new VideoPlayer();player.setPlayWhenReady(false);player.setTextureView(texture);player.setStreamType(AudioManager.STREAM_MUSIC);
        player.setDelegate(new VideoPlayer.VideoPlayerDelegate(){
            public void onStateChanged(boolean playing,int state){if(destroyed)return;if(state==androidx.media3.exoplayer.ExoPlayer.STATE_READY){play.setEnabled(true);seek.setEnabled(true);applyRemote();}}
            public void onError(VideoPlayer p,Exception error){status.setText(FamilyText.get("VideoError")+"\n"+error.getMessage());}
            public void onVideoSizeChanged(int w,int h,int rotation,float ratio){if(w>0&&h>0){android.graphics.Matrix transform=new android.graphics.Matrix();float vw=texture.getWidth(),vh=texture.getHeight();if(vw<=0||vh<=0)return;float scale=Math.min(vw/w,vh/h);transform.setScale(w*scale/vw,h*scale/vh,vw/2,vh/2);texture.setTransform(transform);}}
            public void onRenderedFirstFrame(){}
        });
        player.preparePlayer(Uri.fromFile(file),"other");status.setText(FamilyText.get("WatchConnected"));join.setText(FamilyText.get("WatchConnected"));
        if(host&&data.card(dialog,session)==null)publish(false,0);else applyRemote();AndroidUtilities.runOnUIThread(tick);
    }
    private void publish(boolean playing,long position){
        if(!joined||destroyed)return;
        FamilyCore.Card current=data.card(dialog,session);
        String ref=current!=null?current.latest.op.get("ref"):FamilyDataController.reference(video.messageOwner,video.isOut()?data.self:dialog);
        data.send(dialog,"video",session,"🎬 "+FamilyText.get(playing?"Play":"Pause")+" · "+position/1000+" s",FamilyCore.fields("ref",ref,"message",""+video.getId(),"position",""+position,"playing",playing?"1":"0"),video);
    }
    @Override public void changed(long changed){if(changed==dialog)applyRemote();}
    private void applyRemote(){
        if(player==null||!joined||dragging||destroyed)return;FamilyCore.Card c=data.card(dialog,session);if(c==null||c.latest.id==lastApplied||player.getDuration()<=0)return;
        if(c.latest.actor==data.self&&!firstApply){lastApplied=c.latest.id;return;}firstApply=false;lastApplied=c.latest.id;
        boolean playing=c.latest.op.get("playing").equals("1");long position=c.latest.op.number("position");
        if(playing)position+=Math.max(0,org.telegram.tgnet.ConnectionsManager.getInstance(currentAccount).getCurrentTime()*1000L-c.latest.date*1000L);
        position=Math.min(player.getDuration(),Math.max(0,position));player.seekTo(position);if(playing&&position<player.getDuration())player.play();else player.pause();
        status.setText(FamilyText.get("WatchConnected"));
    }
    @Override public void onResume(){super.onResume();if(join!=null&&!joined){join.setEnabled(true);join.setText(FamilyText.get("JoinWatch"));}}
    @Override public void onPause(){if(player!=null&&joined){if(player.isPlaying())publish(false,Math.max(0,player.getCurrentPosition()));player.pause();}joined=false;if(play!=null)play.setEnabled(false);if(seek!=null)seek.setEnabled(false);AndroidUtilities.cancelRunOnUIThread(tick);super.onPause();}
    @Override public void onFragmentDestroy(){destroyed=true;data.removeListener(this);AndroidUtilities.cancelRunOnUIThread(tick);if(player!=null)player.releasePlayer(true);super.onFragmentDestroy();}
}
