package dev.stinger.split;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.*;

public final class PanelService extends Service implements PanelView.Actions {
    public static PanelService active; // Instrumentation can observe the real service without an exported control receiver.
    public PanelView panel;
    public String target="";
    private WindowManager windows;
    private WindowManager.LayoutParams params;
    private OemMediaSource source;
    private final Handler main=new Handler();
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private boolean expanded=true,attached,closed,loading,fit;
    private String photoKey="";
    private final Runnable monitor=new Runnable(){public void run(){
        if(closed)return;
        boolean show=false;
        try {
            List<ActivityManager.RunningTaskInfo> tasks=((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getRunningTasks(1);
            show=!tasks.isEmpty() && tasks.get(0).topActivity!=null && target.equals(tasks.get(0).topActivity.getPackageName());
        }catch(RuntimeException ignored){}
        // Unknown/phone/camera/another app => remove the panel; removal restores the main window automatically.
        if(show && !attached){try{windows.addView(panel,params);attached=true;}catch(RuntimeException ignored){}}
        else if(!show && attached){windows.removeView(panel);attached=false;}
        loadPhoto(); main.postDelayed(this,400);
    }};
    private final Runnable clockTick=new Runnable(){public void run(){if(closed)return;if(panel.page==1)panel.invalidate();main.postDelayed(this,60000-System.currentTimeMillis()%60000);}};
    @Override public void onCreate(){
        super.onCreate();active=this;windows=(WindowManager)getSystemService(WINDOW_SERVICE);
        panel=new PanelView(this,this);
        params=new WindowManager.LayoutParams(272,480,WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        params.gravity=Gravity.TOP|Gravity.RIGHT;
        params.packageName=getPackageName();
        source=new OemMediaSource(this,new OemMediaSource.Listener(){public void changed(MediaState m){panel.media=m;panel.invalidate();}});
        PendingIntent launch=PendingIntent.getActivity(this,0,new Intent(this,ControlActivity.class),0);
        Notification n=new Notification.Builder(this).setContentTitle("Stinger 분할 화면").setContentText("음악 · 사진 패널 사용 중")
                .setSmallIcon(android.R.drawable.ic_media_play).setContentIntent(launch).build();
        startForeground(1701,n);
        main.post(clockTick);
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        if(i!=null && "stop".equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
        if(i!=null && i.hasExtra("target"))target=i.getStringExtra("target");
        if(target==null||!target.matches("[A-Za-z0-9_.]+")){stopSelf();return START_NOT_STICKY;}
        if(i!=null&&i.hasExtra("expanded"))expanded=i.getBooleanExtra("expanded",true);
        if(i!=null&&i.hasExtra("fit"))fit=i.getBooleanExtra("fit",false);
        update();main.removeCallbacks(monitor);main.post(monitor);
        return START_NOT_STICKY;
    }
    private void update(){
        panel.expanded=expanded;
        params.width=expanded?272:44;params.height=expanded?480:64;params.y=expanded?0:208;
        params.setTitle(expanded?"StingerSplit:528:"+target+(fit?":fit":""):"StingerSplitHandle");
        if(attached)windows.updateViewLayout(panel,params);
        panel.invalidate();
    }
    public void toggle(){expanded=!expanded;update();}
    public void photoSettings(){startActivity(new Intent(this,dev.stinger.standby.SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
    private void loadPhoto(){
        SharedPreferences prefs=getSharedPreferences("photo",0);
        int dim=prefs.getInt("dim",15);boolean crop=prefs.getBoolean("crop",true),clock=prefs.getBoolean("clock",true);
        if(panel.photoDim!=dim||panel.photoCrop!=crop||panel.photoClock!=clock){panel.photoDim=dim;panel.photoCrop=crop;panel.photoClock=clock;panel.invalidate();}
        String key=prefs.getBoolean("enabled",false)?prefs.getString("file",""):"";
        if(key.equals(photoKey)||loading)return;
        if(!key.matches("photo-[a-f0-9]{32}\\.jpg")){photoKey=key;panel.photo=null;panel.invalidate();return;}
        loading=true;final String selected=key;
        io.execute(new Runnable(){public void run(){
            Bitmap result=null;
            try{BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;result=BitmapFactory.decodeFile(new File(getFilesDir(),selected).toString(),o);}catch(OutOfMemoryError ignored){}
            final Bitmap image=result;
            main.post(new Runnable(){public void run(){loading=false;if(closed)return;photoKey=selected;panel.photo=image;panel.invalidate();}});
        }});
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){
        closed=true;main.removeCallbacksAndMessages(null);if(attached){windows.removeView(panel);attached=false;}
        source.close();io.shutdownNow();active=null;super.onDestroy();
    }
}
