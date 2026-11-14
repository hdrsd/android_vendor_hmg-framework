package dev.stinger.split.test;
import android.os.*;
/** QEMU-only root diagnostic: sends the real OEM UX parcel contract, no production test broadcast. */
public final class InjectMedia {
    public static void main(String[] args){
        try { deliver(args); } catch(Throwable t) { t.printStackTrace(System.out);System.exit(1); }
    }
    private static void deliver(String[] args)throws Exception{
        Class<?> cls=Class.forName("com.lge.ivi.Iviuxlistener.IviUxManagerItem");Object item=cls.newInstance();
        cls.getMethod("setAppMode",int.class).invoke(item,11);
        cls.getMethod("setMusicTitle",String.class).invoke(item,args[0]);
        cls.getMethod("setServiceName",String.class).invoke(item,args[1]);
        cls.getMethod("setChannelName",String.class).invoke(item,"QEMU test fixture");
        cls.getMethod("setMediaFilePath",String.class).invoke(item,args.length>2?args[2]:"");
        cls.getMethod("setDuration",long.class).invoke(item,240000L);
        cls.getMethod("setCurrentPosition",long.class).invoke(item,87000L);
        cls.getMethod("setPlayState",int.class).invoke(item,1);
        IBinder service=(IBinder)Class.forName("android.os.ServiceManager").getMethod("getService",String.class).invoke(null,"com.lge.ivi.server.IviUxService");
        Parcel d=Parcel.obtain(),r=Parcel.obtain();d.writeInterfaceToken("com.lge.ivi.Iviuxlistener.IIviUxManager");d.writeInt(1);((Parcelable)item).writeToParcel(d,0);
        if(!service.transact(3,d,r,0))throw new Exception("Delivery rejected");r.readException();System.out.println("OEM_MEDIA_DELIVERED "+r.readInt());d.recycle();r.recycle();System.exit(0);
    }
}
