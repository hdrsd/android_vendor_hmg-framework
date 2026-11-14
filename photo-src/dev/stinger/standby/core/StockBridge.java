package dev.stinger.standby.core;

import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;

/** Only two calls are added to OEM PowerOffScreen. No window/power/key changes. */
public final class StockBridge {
    private static final String TAG="stinger.photo.layer.v1";
    private static Object field(Object owner,String name) throws Exception {
        Field f=owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    public static void attach(Object owner) {
        try {
            ViewGroup root=(ViewGroup)field(owner,"mPowerOffScreenView");
            if(root==null || root.findViewWithTag(TAG)!=null) return;
            PhotoLayer layer=new PhotoLayer(root.getContext()); layer.setTag(TAG);
            layer.bindStock((View)field(owner,"gF"),(View)field(owner,"gG"));
            root.addView(layer,0,new ViewGroup.LayoutParams(-1,-1));
            android.util.Log.i("StingerPhoto","Attached to stock PowerOffScreen");
        } catch(Exception e) { android.util.Log.w("StingerPhoto","Attach failed; stock screen unchanged",e); }
        catch(LinkageError e) { android.util.Log.w("StingerPhoto","Attach unavailable",e); }
        catch(OutOfMemoryError e) { android.util.Log.w("StingerPhoto","Attach allocation failed"); }
    }
    public static void update(Object owner) {
        try {
            ViewGroup root=(ViewGroup)field(owner,"mPowerOffScreenView");
            if(root==null) return;
            PhotoLayer layer=(PhotoLayer)root.findViewWithTag(TAG);
            int type=((Integer)field(owner,"gI")).intValue();
            if(layer!=null) layer.stockUpdated(type==1 || type==2);
        } catch(Exception e) { android.util.Log.w("StingerPhoto","Update failed; stock clock retained",e); }
        catch(LinkageError e) { android.util.Log.w("StingerPhoto","Update unavailable",e); }
        catch(OutOfMemoryError e) { android.util.Log.w("StingerPhoto","Update allocation failed"); }
    }
    private StockBridge() {}
}
