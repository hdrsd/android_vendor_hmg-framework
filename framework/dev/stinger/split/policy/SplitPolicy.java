package dev.stinger.split.policy;

import android.graphics.Rect;
import android.view.WindowManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Called under the existing WMS layout lock; no Binder, disk IO or settings IO. */
public final class SplitPolicy {
    private static Field attrs, service, windows;
    private static Method visible;
    private static Field enforce, globalScale, inverseScale;
    private static final Map<Object,float[]> saved=new WeakHashMap<Object,float[]>();
    private static boolean unavailable;
    public static void constrain(Object window, Rect parent, Rect display, Rect content, Rect shown) {
        try {
            if (unavailable) return;
            if (attrs == null) {
                Class<?> cls = window.getClass();
                attrs = cls.getDeclaredField("mAttrs"); attrs.setAccessible(true);
                service = cls.getDeclaredField("mService"); service.setAccessible(true);
                windows = service.getType().getDeclaredField("mWindowMap"); windows.setAccessible(true);
                visible = Class.forName("android.view.WindowManagerPolicy$WindowState").getMethod("isVisibleLw");
                enforce=cls.getDeclaredField("mEnforceSizeCompat");enforce.setAccessible(true);
                globalScale=cls.getDeclaredField("mGlobalScale");globalScale.setAccessible(true);
                inverseScale=cls.getDeclaredField("mInvGlobalScale");inverseScale.setAccessible(true);
            }
            float[] restore=saved.remove(window);
            if(restore!=null){enforce.setBoolean(window,restore[0]!=0);globalScale.setFloat(window,restore[1]);inverseScale.setFloat(window,restore[2]);}
            WindowManager.LayoutParams current = (WindowManager.LayoutParams) attrs.get(window);
            if (current.type < 1 || current.type >= 2000 || current.packageName == null) return;
            Map<?, ?> map = (Map<?, ?>) windows.get(service.get(window));
            for (Object candidate : map.values()) {
                WindowManager.LayoutParams p = (WindowManager.LayoutParams) attrs.get(candidate);
                if (p.type != WindowManager.LayoutParams.TYPE_PHONE || !"dev.stinger.split".equals(p.packageName)) continue;
                String title = String.valueOf(p.getTitle());
                if (!title.startsWith("StingerSplit:")) continue;
                String[] parts = title.split(":", 4);
                if (parts.length < 3 || !current.packageName.equals(parts[2]) || !((Boolean)visible.invoke(candidate))) continue;
                int right = Integer.parseInt(parts[1]);
                if (right < 320 || right > 640 || parent.right <= right) return;
                // The exact package is selected by the panel. System popups/IME/phone windows stay untouched.
                if(parts.length==4 && parts[3].equals("fit") && current.type<1000 && !enforce.getBoolean(window)) {
                    float scale=right/(float)parent.right;
                    saved.put(window,new float[]{0,globalScale.getFloat(window),inverseScale.getFloat(window)});
                    enforce.setBoolean(window,true);globalScale.setFloat(window,scale);inverseScale.setFloat(window,1f/scale);
                    int height=Math.round(parent.height()*scale),top=parent.top+(parent.height()-height)/2;
                    Rect bounds=new Rect(0,top,right,top+height);
                    parent.set(bounds);display.set(bounds);content.set(bounds);shown.set(bounds);
                } else {clip(parent, right); clip(display, right); clip(content, right); clip(shown, right);}
                return;
            }
        } catch (Exception e) {
            unavailable = true; // Unsupported firmware: leave OEM frames unchanged.
        }
    }
    private static void clip(Rect r, int right) {
        r.left = Math.max(0, Math.min(r.left, right));
        r.right = Math.max(r.left, Math.min(r.right, right));
    }
}
