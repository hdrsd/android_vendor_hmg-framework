package dev.stinger.split;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import java.io.*;

public final class SplitRuntimeTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context c=getTargetContext();
            Bitmap landscape=Bitmap.createBitmap(1200,720,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(landscape);Paint p=new Paint(3);
            p.setShader(new LinearGradient(0,0,0,720,0xff436681,0xffdca99b,Shader.TileMode.CLAMP));canvas.drawPaint(p);p.setShader(null);
            p.setColor(0xffeed6b8);canvas.drawCircle(850,210,58,p);
            Path hills=new Path();hills.moveTo(0,520);hills.lineTo(220,245);hills.lineTo(520,470);hills.lineTo(750,310);hills.lineTo(1200,600);hills.lineTo(1200,720);hills.lineTo(0,720);hills.close();p.setColor(0xff253b52);canvas.drawPath(hills,p);
            p.setColor(0xff142a3b);canvas.drawRect(0,590,1200,720,p);
            File photo=new File(c.getCacheDir(),"fixture-landscape.png");write(landscape,photo);landscape.recycle();
            dev.stinger.standby.PhotoStore.importPhoto(c,Uri.fromFile(photo));photo.delete();
            dev.stinger.standby.PhotoStore.updateDim(c,0); // Stable fixture pixels; production default remains 15%.
            if(!dev.stinger.standby.PhotoStore.hasPhoto(c))throw new AssertionError("photo import");
            Bitmap cover=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888);canvas=new Canvas(cover);
            p.setShader(new LinearGradient(0,0,512,512,0xff176b81,0xff28304f,Shader.TileMode.CLAMP));canvas.drawPaint(p);p.setShader(null);
            p.setColor(0xffe9a789);canvas.drawCircle(340,155,76,p);p.setColor(0xff142839);canvas.drawRect(0,330,512,512,p);
            p.setColor(0xff9edddf);p.setStrokeWidth(8);canvas.drawLine(60,512,300,260,p);canvas.drawLine(450,512,300,260,p);
            p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(40);p.setColor(Color.WHITE);canvas.drawText("NIGHT DRIVE",28,66,p);p.setTextSize(22);canvas.drawText("QEMU TEST ARTWORK",28,105,p);
            write(cover,new File(c.getFilesDir(),"fixture-art.png"));cover.recycle();
            // Verify the actual OEM parcel parser is present, rather than a fabricated production broadcast API.
            Class<?> item=Class.forName("com.lge.ivi.Iviuxlistener.IviUxManagerItem");Object x=item.newInstance();
            item.getMethod("setAppMode",int.class).invoke(x,11);item.getMethod("setMusicTitle",String.class).invoke(x,"Night Drive");
            Parcel parcel=Parcel.obtain();((Parcelable)x).writeToParcel(parcel,0);parcel.setDataPosition(0);Object copy=item.getConstructor(Parcel.class).newInstance(parcel);parcel.recycle();
            if(!"Night Drive".equals(item.getMethod("getMusicTitle").invoke(copy)))throw new AssertionError("OEM parcel contract");
            result.putString("stream","PASS: imported stored photo, source deletion, generated labeled art, real OEM parcel roundtrip\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable t){result.putString("stream","FAIL: "+t+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
    private void write(Bitmap b,File f)throws Exception{FileOutputStream out=new FileOutputStream(f);try{b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{out.close();}}
}
