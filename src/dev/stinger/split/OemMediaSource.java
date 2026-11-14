package dev.stinger.split;

import android.content.*;
import android.graphics.*;
import android.os.*;
import java.io.File;
import java.lang.reflect.*;

/** Passive subscriber to the exact CK firmware UX Binder protocol. No AVRCP listener takeover. */
public final class OemMediaSource {
    public interface Listener { void changed(MediaState state); }
    private static final String MANAGER="com.lge.ivi.Iviuxlistener.IIviUxManager";
    private static final String CALLBACK="com.lge.ivi.Iviuxlistener.IIviUxListener";
    private final Context context;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HandlerThread thread=new HandlerThread("stinger-media");
    private Handler worker;
    private IBinder remote;
    private volatile boolean closed;
    private MediaState pending;
    private boolean queued;
    private MediaState previous=new MediaState();
    private long artStamp;
    public String diagnostics="not connected";
    private final Binder callback=new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if(code==IBinder.INTERFACE_TRANSACTION) { if(reply!=null) reply.writeString(CALLBACK); return true; }
            if(code!=1) return super.onTransact(code,data,reply,flags);
            data.enforceInterface(CALLBACK);
            try {
                if(data.readInt()!=0) {
                    Object item=Class.forName("com.lge.ivi.Iviuxlistener.IviUxManagerItem").getConstructor(Parcel.class).newInstance(data);
                    receive(item);
                }
            } catch(Exception e) { diagnostics=e.toString(); }
            return true; // OEM callback is one-way.
        }
    };
    private final BroadcastReceiver disconnect=new BroadcastReceiver() {
        public void onReceive(Context c, Intent i) {
            if(i.getIntExtra("android.bluetooth.profile.extra.STATE",-1)==0) submit(new MediaState());
        }
    };
    public OemMediaSource(Context c, Listener l) {
        context=c; listener=l; thread.start(); worker=new Handler(thread.getLooper());
        context.registerReceiver(disconnect,new IntentFilter("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"));
        worker.post(reconnect);
    }
    private final Runnable reconnect=new Runnable() { public void run() {
        if(closed)return;
        try {
            if(remote==null || !remote.isBinderAlive()) {
                remote=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"com.lge.ivi.server.IviUxService");
                if(remote!=null) { transaction(1); diagnostics="OEM IviUxService subscribed"; }
            }
        } catch(Exception e) { remote=null; diagnostics=e.toString(); }
        worker.postDelayed(this,3000);
    }};
    private void transaction(int code) throws RemoteException {
        Parcel d=Parcel.obtain(),r=Parcel.obtain();
        try { d.writeInterfaceToken(MANAGER); d.writeInt(11); d.writeStrongBinder(callback); if(!remote.transact(code,d,r,0))throw new RemoteException(); r.readException(); }
        finally { d.recycle(); r.recycle(); }
    }
    private Object get(Object x,String method) throws Exception { return x.getClass().getMethod(method).invoke(x); }
    private String string(Object x,String method) throws Exception {
        Object value=get(x,method); String s=value==null?"":value.toString(); return s.length()>512?s.substring(0,512):s;
    }
    private void receive(Object item) throws Exception {
        int mode=((Number)get(item,"getAppMode")).intValue();
        if(mode!=11) return;
        MediaState s=new MediaState();
        s.title=string(item,"getMusicTitle"); s.artist=string(item,"getServiceName");
        if(s.artist.length()==0)s.artist=string(item,"getMusicArtist");
        s.device=string(item,"getChannelName"); s.artPath=string(item,"getMediaFilePath");
        s.duration=Math.max(0,((Number)get(item,"getDuration")).longValue());
        s.position=Math.max(0,((Number)get(item,"getCurrentPosition")).longValue());
        s.playing=((Number)get(item,"getPlayState")).intValue()==1;
        s.received=SystemClock.elapsedRealtime(); s.source=s.device.equals("QEMU test fixture")?"QEMU 시험 데이터 · BT 미연결":"Bluetooth · 차량 수신 정보";
        submit(s);
    }
    private synchronized void submit(MediaState s) {
        if(closed)return; pending=s;
        if(!queued) { queued=true; worker.post(load); }
    }
    private final Runnable load=new Runnable() { public void run() {
        MediaState s;
        synchronized(OemMediaSource.this) { s=pending; pending=null; queued=false; }
        if(s==null || closed)return;
        File f=new File(s.artPath); long stamp=f.lastModified();
        if(s.trackKey().equals(previous.trackKey()) && s.artPath.equals(previous.artPath) && stamp==artStamp) s.art=previous.art;
        else if(s.artPath.startsWith("/") && f.isFile() && f.length()<=4*1024*1024) {
            // No URI/network fetch. The OEM service supplies a locally cached SoundHound image.
            try {
                BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true; BitmapFactory.decodeFile(f.toString(),o);
                if(o.outWidth>0 && o.outHeight>0 && o.outWidth<=8192 && o.outHeight<=8192 && (long)o.outWidth*o.outHeight<=32000000) {
                    o.inSampleSize=1; while(o.outWidth/o.inSampleSize>512 || o.outHeight/o.inSampleSize>512)o.inSampleSize*=2;
                    o.inJustDecodeBounds=false; s.art=BitmapFactory.decodeFile(f.toString(),o);
                }
            } catch(OutOfMemoryError ignored) { } catch(RuntimeException ignored) { }
        }
        previous=s; artStamp=stamp;
        final MediaState next=s;
        main.post(new Runnable(){public void run(){if(!closed)listener.changed(next);}});
    }};
    public void close() {
        closed=true; context.unregisterReceiver(disconnect);
        worker.removeCallbacksAndMessages(null);
        worker.post(new Runnable(){public void run(){try{if(remote!=null)transaction(2);}catch(Exception ignored){} finally{thread.quit();}}});
    }
}
