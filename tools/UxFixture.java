package dev.stinger.split.test;
import android.os.*;
import java.util.*;

/** Emulator-only stand-in for the unstable hardware-dependent IviServer process.
 * Implements the three exact OEM transactions; carries the real OEM Parcelable.
 * It does not emulate a phone, radio, AVRCP link, or SoundHound search.
 */
public final class UxFixture extends Binder {
    private static final String NAME="com.lge.ivi.server.IviUxService";
    private static final String DESCRIPTOR="com.lge.ivi.Iviuxlistener.IIviUxManager";
    private final List<IBinder> listeners=new ArrayList<IBinder>();
    private Parcelable last;
    private void send(IBinder listener,Parcelable item)throws RemoteException {
        Parcel p=Parcel.obtain();try{
            p.writeInterfaceToken("com.lge.ivi.Iviuxlistener.IIviUxListener");p.writeInt(1);item.writeToParcel(p,0);
            listener.transact(1,p,null,IBinder.FLAG_ONEWAY);
        }finally{p.recycle();}
    }
    @Override protected synchronized boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
        if(code==IBinder.INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
        data.enforceInterface(DESCRIPTOR);
        if(code==1||code==2){
            data.readInt();IBinder listener=data.readStrongBinder();
            if(code==1){if(!listeners.contains(listener))listeners.add(listener);if(last!=null)send(listener,last);}
            else listeners.remove(listener);
            reply.writeNoException();return true;
        }
        if(code==3){
            try{
                if(data.readInt()!=0)last=(Parcelable)Class.forName("com.lge.ivi.Iviuxlistener.IviUxManagerItem").getConstructor(Parcel.class).newInstance(data);
                if(last!=null)for(Iterator<IBinder> it=listeners.iterator();it.hasNext();){IBinder listener=it.next();try{send(listener,last);}catch(RemoteException e){it.remove();}}
                reply.writeNoException();reply.writeInt(1);System.out.println("QEMU UX FIXTURE delivered to "+listeners.size()+" listeners");return true;
            }catch(Exception e){throw new RemoteException(e.toString());}
        }
        return super.onTransact(code,data,reply,flags);
    }
    public static void main(String[] args){try{
        String qemu=(String)Class.forName("android.os.SystemProperties").getMethod("get",String.class).invoke(null,"ro.kernel.qemu");
        if(!"1".equals(qemu))throw new IllegalStateException("QEMU only");
        Class<?> sm=Class.forName("android.os.ServiceManager");
        IBinder old=(IBinder)sm.getMethod("getService",String.class).invoke(null,NAME);
        if(old!=null&&old.isBinderAlive())throw new IllegalStateException("Existing OEM service: refuse replacement");
        sm.getMethod("addService",String.class,IBinder.class).invoke(null,NAME,new UxFixture());
        System.out.println("QEMU UX FIXTURE REGISTERED — NO BLUETOOTH RADIO");
        Class.forName("com.android.internal.os.BinderInternal").getMethod("joinThreadPool").invoke(null);
    }catch(Throwable t){t.printStackTrace(System.out);System.exit(1);}}
}
