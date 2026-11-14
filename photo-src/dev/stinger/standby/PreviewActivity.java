package dev.stinger.standby;
import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.FrameLayout;
import android.widget.TextView;
import dev.stinger.standby.core.PhotoLayer;
public final class PreviewActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setFlags(1024,1024);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff000000);
        TextView fallback=new TextView(this);fallback.setText("사진이 없습니다. 터치하면 설정으로 돌아갑니다.");fallback.setTextColor(0xffbbbbbb);fallback.setGravity(Gravity.CENTER);root.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        root.addView(new PhotoLayer(this),new FrameLayout.LayoutParams(-1,-1));
        root.setOnClickListener(new View.OnClickListener(){public void onClick(View v){finish();}});setContentView(root);
        android.widget.Toast.makeText(this,"화면을 터치하면 설정으로 돌아갑니다.",android.widget.Toast.LENGTH_SHORT).show();
    }
}
