package dev.stinger.split;

import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.text.TextPaint;
import android.view.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class PanelView extends View {
    public interface Actions { void toggle(); void photoSettings(); }
    private final Actions actions;
    private final Paint paint=new Paint(3);
    private final TextPaint text=new TextPaint(3);
    public MediaState media=new MediaState();
    public Bitmap photo;
    public boolean photoCrop=true,photoClock=true;
    public int photoDim;
    public int page;
    public boolean expanded=true;
    private float downX,downY;
    private boolean dragged;
    private static final int WHITE=Color.rgb(238,242,249), MUTED=Color.rgb(147,161,185), ACCENT=Color.rgb(105,203,215);
    public PanelView(Context c,Actions a) { super(c); actions=a; setContentDescription("음악과 사진 패널. 위아래로 밀어 전환"); }
    private void label(Canvas c,String s,float x,float y,int size,int color) {
        text.setTypeface(Typeface.DEFAULT); text.setTextSize(size); text.setColor(color);
        c.drawText(TextUtils.ellipsize(s==null?"":s,text,Math.max(1,getWidth()-x-22),TextUtils.TruncateAt.END).toString(),x,y,text);
    }
    private void rect(Canvas c,float l,float t,float r,float b,int color) {paint.setColor(color); c.drawRect(l,t,r,b,paint);}
    private void picture(Canvas c,Bitmap b,RectF dst) {
        if(b==null||b.isRecycled())return;
        float scale=Math.max(dst.width()/b.getWidth(),dst.height()/b.getHeight());
        int w=Math.min(b.getWidth(),Math.round(dst.width()/scale)), h=Math.min(b.getHeight(),Math.round(dst.height()/scale));
        Rect src=new Rect((b.getWidth()-w)/2,(b.getHeight()-h)/2,(b.getWidth()+w)/2,(b.getHeight()+h)/2);
        paint.setColor(Color.WHITE);c.drawBitmap(b,src,dst,paint);
    }
    @Override protected void onDraw(Canvas c) {
        int w=getWidth(),h=getHeight();
        rect(c,0,0,w,h,Color.rgb(17,23,34)); rect(c,0,0,2,h,Color.rgb(51,68,87));
        if(!expanded) {label(c,"‹",14,40,28,ACCENT);return;}
        label(c,"STINGER",22,32,12,MUTED);
        label(c,page==0?"음악":"사진",22,91,16,ACCENT);
        label(c,"전체 화면  ›",w-114,91,14,WHITE);
        rect(c,22,108,w-22,109,Color.rgb(45,57,74));
        if(page==0) {
            float size=Math.min(144,w-80),left=(w-size)/2;
            RectF art=new RectF(left,120,left+size,120+size);
            rect(c,art.left,art.top,art.right,art.bottom,Color.rgb(32,45,61));
            if(media.art!=null)picture(c,media.art,art);
            else {label(c,"♪",left+size/2-15,120+size/2+9,40,MUTED);label(c,"앨범 이미지 없음",left+12,120+size-18,12,MUTED);}
            label(c,media.title.length()==0?"재생 중인 음악 없음":media.title,22,298,21,WHITE);
            label(c,media.artist.length()==0?"휴대폰에서 음악을 재생하세요":media.artist,22,326,15,MUTED);
            label(c,media.device,22,351,12,MUTED);
            rect(c,22,373,w-22,376,Color.rgb(50,61,77));
            double fraction=media.duration<=0?0:Math.min(1,media.position/(double)media.duration);
            rect(c,22,373,(float)(22+(w-44)*fraction),376,ACCENT);
            label(c,media.playing?"재생 중":"일시 정지",22,401,12,MUTED);
            label(c,media.source,22,427,11,MUTED);
        } else {
            RectF area=new RectF(18,120,w-18,h-84);
            if(photo!=null){
                RectF dest=new RectF(area);
                if(!photoCrop){float scale=Math.min(area.width()/photo.getWidth(),area.height()/photo.getHeight());float pw=photo.getWidth()*scale,ph=photo.getHeight()*scale;dest.set(area.centerX()-pw/2,area.centerY()-ph/2,area.centerX()+pw/2,area.centerY()+ph/2);}
                picture(c,photo,dest);rect(c,area.left,area.top,area.right,area.bottom,(Math.max(0,Math.min(70,photoDim))*255/100)<<24);
            } else {rect(c,area.left,area.top,area.right,area.bottom,Color.rgb(30,40,53));label(c,"사진을 선택해 주세요",35,230,17,MUTED);}
            if(photoClock){rect(c,18,h-164,w-18,h-84,0x99000000);
                label(c,new SimpleDateFormat("HH:mm",Locale.US).format(new Date()),33,h-124,34,WHITE);
                label(c,new SimpleDateFormat("yyyy.MM.dd",Locale.US).format(new Date()),35,h-102,13,WHITE);}
            label(c,"사진 선택",22,h-52,14,ACCENT);
        }
        label(c,page==0?"●  ○   위로 밀어 사진 보기":"○  ●   아래로 밀어 음악 보기",22,h-16,12,MUTED);
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();dragged=false;return true;}
        if(e.getAction()==MotionEvent.ACTION_MOVE && expanded && !dragged){
            float dy=e.getY()-downY,dx=e.getX()-downX;
            if(Math.abs(dy)>40 && Math.abs(dy)>Math.abs(dx)){page=dy<0?1:0;dragged=true;invalidate();}
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_CANCEL){dragged=false;return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){
            if(dragged){dragged=false;return true;}
            if(!expanded){actions.toggle();return true;}
            float dy=e.getY()-downY,dx=e.getX()-downX;
            if(Math.abs(dy)>40 && Math.abs(dy)>Math.abs(dx)){page=dy<0?1:0;invalidate();return true;}
            if(Math.abs(dx)<20&&Math.abs(dy)<20){
                if(e.getY()<109){actions.toggle();}
                else if(page==1 && e.getY()>getHeight()-84){actions.photoSettings();}
                performClick();
            }
            return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onGenericMotionEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_SCROLL){page=e.getAxisValue(MotionEvent.AXIS_VSCROLL)<0?1:0;invalidate();return true;}
        return super.onGenericMotionEvent(e);
    }
}
