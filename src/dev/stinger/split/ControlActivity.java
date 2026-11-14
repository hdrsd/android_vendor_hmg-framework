package dev.stinger.split;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class ControlActivity extends Activity {
    private LinearLayout choices;
    private CheckBox fit;
    private boolean supported;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setFlags(1024,1024);
        try{Class.forName("dev.stinger.split.policy.SplitPolicy");supported=true;}catch(ClassNotFoundException ignored){}
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,20,28,16);root.setBackgroundColor(Color.rgb(17,23,34));
        TextView title=new TextView(this);title.setText("STINGER  /  분할 화면");title.setTextSize(27);title.setTextColor(Color.WHITE);root.addView(title);
        TextView sub=new TextView(this);sub.setText(supported?"앱을 선택하면 오른쪽에 음악·사진 패널이 열립니다. 패널의 ‘전체 화면’으로 복귀합니다.":"이 시스템은 분할 화면을 지원하지 않습니다. 사진 선택과 미리보기는 사용할 수 있습니다.");sub.setTextSize(14);sub.setTextColor(Color.LTGRAY);root.addView(sub);
        fit=new CheckBox(this);fit.setText("순정 화면 비율 유지 · 고정 크기 화면을 잘리지 않게 축소");fit.setChecked(true);root.addView(fit);
        ScrollView scroll=new ScrollView(this);choices=new LinearLayout(this);choices.setOrientation(1);scroll.addView(choices);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bottom=new LinearLayout(this);
        Button photo=new Button(this);photo.setText("대기화면 사진 선택");photo.setOnClickListener(new View.OnClickListener(){public void onClick(View v){startActivity(new Intent(ControlActivity.this,dev.stinger.standby.SettingsActivity.class));}});bottom.addView(photo,new LinearLayout.LayoutParams(0,52,1));
        Button stop=new Button(this);stop.setText("패널 종료 · 원래 화면 복구");stop.setOnClickListener(new View.OnClickListener(){public void onClick(View v){stopService(new Intent(ControlActivity.this,PanelService.class));finish();}});bottom.addView(stop,new LinearLayout.LayoutParams(0,52,1));root.addView(bottom);
        setContentView(root);populate();
    }
    private void populate(){
        Intent main=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(main,0);
        Collections.sort(apps,new ResolveInfo.DisplayNameComparator(getPackageManager()));
        for(final ResolveInfo app:apps){
            final String pkg=app.activityInfo.packageName;
            String lower=pkg.toLowerCase(Locale.US);
            if(pkg.equals(getPackageName())||lower.contains("camera")||lower.contains("btphone")||lower.contains("engineer")||lower.contains("upgrade"))continue;
            Button button=new Button(this);button.setText(app.loadLabel(getPackageManager())+"  ›");
            button.setEnabled(supported);
            button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
                Intent service=new Intent(ControlActivity.this,PanelService.class).putExtra("target",pkg).putExtra("expanded",true).putExtra("fit",fit.isChecked());startService(service);
                Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(pkg,app.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try{startActivity(launch);}catch(RuntimeException e){stopService(service);Toast.makeText(ControlActivity.this,"앱을 열 수 없습니다",0).show();}
            }});choices.addView(button,new LinearLayout.LayoutParams(-1,52));
        }
    }
}
