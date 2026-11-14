package dev.stinger.split.fixture;
import android.app.*;
import android.os.*;
import android.graphics.*;
import android.view.*;

/** Separate responsive application: validates actual WMS geometry and touch routing. Not OEM navigation. */
public final class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setFlags(1024,1024);setContentView(new Screen());}
    final class Screen extends View {
        Paint p=new Paint(3);int taps;
        Screen(){super(FixtureActivity.this);}
        void text(Canvas c,String s,int x,int y,int size,int color){p.setColor(color);p.setTextSize(size);p.setTypeface(Typeface.DEFAULT);c.drawText(s,x,y,p);}
        protected void onDraw(Canvas c){
            int w=getWidth(),h=getHeight();c.drawColor(Color.rgb(20,30,40));
            p.setStrokeWidth(1);p.setColor(Color.rgb(36,50,61));for(int i=0;i<w;i+=50)c.drawLine(i,0,i,h,p);for(int y=0;y<h;y+=50)c.drawLine(0,y,w,y,p);
            p.setColor(Color.rgb(57,81,92));p.setStrokeWidth(20);c.drawLine(-20,300,w+20,140,p);c.drawLine(w/3,-30,w*2/3,h+30,p);
            p.setColor(Color.rgb(76,158,166));p.setStrokeWidth(9);c.drawLine(w/3,350,w/2,260,p);c.drawLine(w/2,260,w-80,220,p);
            p.setColor(Color.rgb(101,221,220));c.drawCircle(w/3,350,12,p);
            text(c,"QEMU · 독립 앱 창 검사",28,44,22,Color.WHITE);
            text(c,w+" × "+h+"  /  실제 View 크기",28,77,16,Color.LTGRAY);
            p.setColor(Color.rgb(31,49,65));c.drawRect(20,h-76,w-20,h-18,p);
            text(c,"화면 터치 확인  "+taps,38,h-40,20,Color.WHITE);
            text(c,"→",w-60,h-39,23,Color.WHITE);
        }
        public boolean onTouchEvent(MotionEvent e){if(e.getAction()==1){taps++;invalidate();try{java.io.FileOutputStream out=openFileOutput("touch.txt",0);out.write((taps+":"+e.getX()+","+e.getY()).getBytes("UTF-8"));out.close();}catch(Exception ignored){}}return true;}
    }
}
