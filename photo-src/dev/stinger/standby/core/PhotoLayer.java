package dev.stinger.standby.core;

import android.content.*;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import dev.stinger.standby.PhotoMath;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** A non-interactive child of the original standby window, not a new overlay. */
public final class PhotoLayer extends View {
    private static final Uri SETTINGS=Uri.parse("content://dev.stinger.split.photos/settings");
    private static final ExecutorService IO=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private View[] stock=new View[0];
    private int[] saved=new int[0];
    private boolean overridden, attached, busy, again, watching, eligible=true;
    private int epoch;
    private Bitmap photo;
    private boolean clock=true, crop=true;
    private int dim=15;
    private final ContentObserver observer=new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { refresh(); }
    };
    private final Runnable tick=new Runnable() {
        public void run() { if(attached && getWindowVisibility()==VISIBLE) { invalidate(); main.postDelayed(this,1000); } }
    };
    public PhotoLayer(Context c) {
        super(c); setClickable(false); setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    public void bindStock(View... views) {
        stock=views; saved=new int[views.length];
        for(int i=0;i<views.length;i++) saved[i]=views[i].getVisibility();
    }
    public void stockUpdated(boolean allow) {
        eligible=allow;
        // Called immediately AFTER the OEM method has selected its clock.
        for(int i=0;i<stock.length;i++) saved[i]=stock[i].getVisibility();
        overridden=false; applyStock(); refresh();
    }
    public boolean isPhotoActive() { return eligible && photo!=null; }
    private void applyStock() {
        if(isPhotoActive()) {
            if(!overridden) for(int i=0;i<stock.length;i++) saved[i]=stock[i].getVisibility();
            for(View v:stock) v.setVisibility(GONE);
            overridden=true;
        } else restoreStock();
        invalidate();
    }
    private void restoreStock() {
        if(overridden) for(int i=0;i<stock.length;i++) stock[i].setVisibility(saved[i]);
        overridden=false;
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); attached=true; epoch++;
        try { getContext().getContentResolver().registerContentObserver(SETTINGS,false,observer); watching=true; }
        catch(RuntimeException ignored) {}
        refresh(); main.removeCallbacks(tick); main.post(tick);
    }
    @Override protected void onDetachedFromWindow() {
        attached=false; epoch++; main.removeCallbacks(tick);
        if(watching) { try { getContext().getContentResolver().unregisterContentObserver(observer); } catch(RuntimeException ignored) {} watching=false; }
        photo=null; restoreStock(); super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if(main==null) return; // View may call this during construction.
        main.removeCallbacks(tick);
        if(attached && visibility==VISIBLE) { refresh(); main.post(tick); }
        else if(visibility!=VISIBLE) { epoch++; photo=null; restoreStock(); }
    }
    public void refresh() {
        if(!attached || getWindowVisibility()!=VISIBLE) return;
        if(busy) { again=true; return; }
        busy=true; final int requestEpoch=epoch; final ContentResolver resolver=getContext().getContentResolver();
        IO.execute(new Runnable() { public void run() {
            Bitmap image=null; boolean showClock=true, fill=true; int darkness=15;
            Cursor c=null;
            try {
                c=resolver.query(SETTINGS,null,null,null,null);
                if(c!=null && c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow("enabled"))==1) {
                    showClock=c.getInt(c.getColumnIndexOrThrow("clock"))==1;
                    fill=c.getInt(c.getColumnIndexOrThrow("crop"))==1;
                    darkness=PhotoMath.dim(c.getInt(c.getColumnIndexOrThrow("dim")));
                    Uri uri=Uri.parse(c.getString(c.getColumnIndexOrThrow("image")));
                    if(!"content".equals(uri.getScheme()) || !"dev.stinger.split.photos".equals(uri.getAuthority())) throw new IllegalArgumentException("Invalid photo URI");
                    BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true;
                    InputStream stream=resolver.openInputStream(uri);
                    if(stream!=null) { try { BitmapFactory.decodeStream(stream,null,o); } finally { stream.close(); } }
                    o.inSampleSize=PhotoMath.sample(o.outWidth,o.outHeight); o.inJustDecodeBounds=false; o.inPreferredConfig=Bitmap.Config.RGB_565;
                    stream=resolver.openInputStream(uri);
                    if(stream!=null) { try { image=BitmapFactory.decodeStream(stream,null,o); } finally { stream.close(); } }
                }
            } catch(Exception e) { android.util.Log.w("StingerPhoto","Photo unavailable; retaining stock screen: "+e.getClass().getSimpleName()); }
            catch(OutOfMemoryError e) { image=null; android.util.Log.w("StingerPhoto","Photo allocation failed; retaining stock screen"); }
            finally { if(c!=null) c.close(); }
            final Bitmap decoded=image; final boolean newClock=showClock, newCrop=fill; final int newDim=darkness;
            main.post(new Runnable() { public void run() {
                busy=false;
                if(attached && epoch==requestEpoch && getWindowVisibility()==VISIBLE) {
                    photo=decoded; clock=newClock; crop=newCrop; dim=newDim; applyStock();
                }
                if(again) { again=false; refresh(); }
            }});
        }});
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); Bitmap b=photo;
        if(!eligible || b==null || getWidth()<=0 || getHeight()<=0) return;
        canvas.drawColor(Color.BLACK);
        float[] r=PhotoMath.destination(b.getWidth(),b.getHeight(),getWidth(),getHeight(),crop);
        paint.clearShadowLayer(); paint.setColor(Color.WHITE); paint.setAlpha(255);
        canvas.drawBitmap(b,null,new RectF(r[0],r[1],r[2],r[3]),paint);
        canvas.drawColor(Color.argb(dim*255/100,0,0,0));
        if(clock) {
            float scale=Math.min(getWidth()/800f,getHeight()/480f);
            paint.setColor(Color.WHITE); paint.setShadowLayer(4*scale,0,2*scale,0xcc000000);
            paint.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL)); paint.setTextSize(62*scale);
            String pattern=android.text.format.DateFormat.is24HourFormat(getContext())?"HH:mm":"h:mm a";
            // OEM light clock font has no Korean glyphs; use numeric date and
            // Latin AM/PM so both the stock font set and AOSP render completely.
            Date now=new Date(); canvas.drawText(new SimpleDateFormat(pattern,Locale.US).format(now),36*scale,getHeight()-70*scale,paint);
            paint.setTextSize(22*scale);
            canvas.drawText(new SimpleDateFormat("yyyy.MM.dd",Locale.US).format(now),39*scale,getHeight()-33*scale,paint);
            paint.clearShadowLayer();
        }
    }
}
