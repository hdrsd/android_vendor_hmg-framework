package dev.stinger.standby;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import dev.stinger.standby.core.PhotoLayer;
import java.io.*;
import java.util.*;

public final class SettingsActivity extends Activity {
    private final Handler main=new Handler();
    private boolean busy, building;
    private TextView status, empty;
    private Switch enabled, clock, crop;
    private Button choose;
    private PhotoLayer preview;
    private static final int PICK=71;
    @Override public void onCreate(Bundle state) { super.onCreate(state); getWindow().setFlags(1024,1024); build(); }
    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density*n+0.5f); }
    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private void build() {
        building=true;
        LinearLayout root=column(); root.setBackgroundColor(0xff10151c); root.setPadding(dp(24),dp(12),dp(24),dp(10));
        LinearLayout header=row(); TextView title=text("사진 대기화면",26,Color.WHITE); title.setTypeface(null,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button close=new Button(this); close.setText("닫기"); close.setOnClickListener(new View.OnClickListener(){public void onClick(View v){finish();}}); header.addView(close,new LinearLayout.LayoutParams(dp(88),dp(46))); root.addView(header);
        LinearLayout content=row();
        LinearLayout left=column(); left.setPadding(0,dp(12),dp(24),0);
        FrameLayout frame=new FrameLayout(this); frame.setBackgroundColor(0xff080b10);
        empty=text("좋아하는 사진을 대기화면에 담아보세요",18,0xff9baabd); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(18),0,dp(18),0);
        frame.addView(empty,new FrameLayout.LayoutParams(-1,-1)); preview=new PhotoLayer(this); frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        frame.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showPreview();}});
        left.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        status=text("",14,0xff9baabd); left.addView(status,new LinearLayout.LayoutParams(-1,dp(35)));
        LinearLayout actions=row(); choose=new Button(this); choose.setText("사진 선택"); choose.setOnClickListener(new View.OnClickListener(){public void onClick(View v){chooseSource();}});
        actions.addView(choose,new LinearLayout.LayoutParams(0,dp(51),1));
        Button full=new Button(this); full.setText("전체 화면 보기"); full.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showPreview();}}); actions.addView(full,new LinearLayout.LayoutParams(0,dp(51),1));
        left.addView(actions); content.addView(left,new LinearLayout.LayoutParams(0,-1,1));
        ScrollView scroll=new ScrollView(this); LinearLayout options=column(); options.setPadding(0,dp(8),0,0);
        enabled=toggle(options,"사진 사용","enabled",false);
        clock=toggle(options,"시계 함께 표시","clock",true);
        crop=toggle(options,"화면 가득 채우기","crop",true);
        TextView fitHelp=text("끄면 사진 전체가 보이도록 맞춥니다.",13,0xff9baabd); options.addView(fitHelp,new LinearLayout.LayoutParams(-1,dp(40)));
        final TextView dimLabel=text("어둡기  "+PhotoStore.prefs(this).getInt("dim",15)+"%",16,Color.WHITE); options.addView(dimLabel,new LinearLayout.LayoutParams(-1,dp(38)));
        SeekBar dim=new SeekBar(this); dim.setMax(70); dim.setProgress(PhotoStore.prefs(this).getInt("dim",15)); dim.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s) {}
            public void onProgressChanged(SeekBar s,int n,boolean user){dimLabel.setText("어둡기  "+n+"%");}
            public void onStopTrackingTouch(SeekBar s){try{PhotoStore.updateDim(SettingsActivity.this,s.getProgress());}catch(IOException e){error(e);}}
        }); options.addView(dim,new LinearLayout.LayoutParams(-1,dp(44)));
        TextView help=text("선택한 사진을 오른쪽 패널에 표시합니다.\n사진은 기기에 복사되어 USB를 빼도 유지됩니다.",13,0xff9baabd); help.setPadding(0,dp(10),0,0); options.addView(help,new LinearLayout.LayoutParams(-1,dp(82)));
        scroll.addView(options); content.addView(scroll,new LinearLayout.LayoutParams(dp(257),-1));
        root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        TextView foot=text("사진 사용을 끄면 패널의 사진이 숨겨집니다.",13,0xff7f90a5); root.addView(foot,new LinearLayout.LayoutParams(-1,dp(32)));
        setContentView(root); building=false; refreshStatus();
    }
    private Switch toggle(LinearLayout parent,String label,final String key,boolean def) {
        final Switch s=new Switch(this); s.setText(label); s.setTextColor(Color.WHITE); s.setTextSize(17); s.setChecked(PhotoStore.prefs(this).getBoolean(key,def));
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){public void onCheckedChanged(CompoundButton b,boolean checked){
            if(building) return;
            if("enabled".equals(key)&&checked&&!PhotoStore.hasPhoto(SettingsActivity.this)){building=true;s.setChecked(false);building=false;chooseSource();return;}
            try{PhotoStore.update(SettingsActivity.this,key,checked);refreshStatus();}catch(IOException e){error(e);}
        }}); parent.addView(s,new LinearLayout.LayoutParams(-1,dp(51))); return s;
    }
    private void refreshStatus() {
        boolean has=PhotoStore.hasPhoto(this), on=PhotoStore.prefs(this).getBoolean("enabled",false);
        status.setText(busy?"사진을 가져오는 중…":has?(on?"사진 대기화면 사용 중":"사진 보관 중 · 표시 꺼짐"):"JPG · PNG · WebP · BMP / 최대 20MB");
        empty.setText(has?"사진 사용을 켜면 미리보기가 표시됩니다.":"좋아하는 사진을 대기화면에 담아보세요");
        building=true; enabled.setChecked(on&&has); building=false; choose.setEnabled(!busy);
    }
    private void showPreview() {
        if(!PhotoStore.hasPhoto(this)||!PhotoStore.prefs(this).getBoolean("enabled",false)){Toast.makeText(this,"사진을 선택하고 사진 사용을 켜 주세요.",Toast.LENGTH_SHORT).show();return;}
        startActivity(new Intent(this,PreviewActivity.class));
    }
    private void chooseSource() {
        if(busy) return;
        final String[] names={"USB 1","USB 2","SD 카드","내부 저장소","사진 앱에서 선택"};
        new AlertDialog.Builder(this).setTitle("사진 가져오기").setItems(names,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int i){
            if(i==4){try{Intent pick=new Intent(Intent.ACTION_GET_CONTENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(Intent.createChooser(pick,"사진 선택"),PICK);}catch(ActivityNotFoundException e){Toast.makeText(SettingsActivity.this,"사진 앱이 없습니다. USB 또는 SD 카드를 선택해 주세요.",Toast.LENGTH_LONG).show();}return;}
            String[] paths={"/storage/usb0","/storage/usb1","/storage/sdcard1"};File base=i==3?Environment.getExternalStorageDirectory():new File(paths[i]);browse(base,base);
        }}).setNegativeButton("취소",null).show();
    }
    private static boolean photoFile(File f) {String s=f.getName().toLowerCase(Locale.US);return s.endsWith(".jpg")||s.endsWith(".jpeg")||s.endsWith(".png")||s.endsWith(".webp")||s.endsWith(".bmp");}
    private void browse(final File base,final File dir) {
        new Thread(new Runnable(){public void run(){
            final ArrayList<File> files=new ArrayList<File>(); File[] all=dir.listFiles();
            if(all!=null) for(File f:all) if(!f.isHidden()&&(f.isDirectory()||photoFile(f))) {try{if(f.getCanonicalPath().startsWith(base.getCanonicalPath()+File.separator)||f.equals(base))files.add(f);}catch(IOException ignored){}}
            Collections.sort(files,new Comparator<File>(){public int compare(File a,File b){if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());}});
            final boolean parent=!base.equals(dir); final String[] labels=new String[files.size()+(parent?1:0)];if(parent)labels[0]="‹ 상위 폴더";
            for(int i=0;i<files.size();i++)labels[i+(parent?1:0)]=(files.get(i).isDirectory()?"[폴더] ":"")+files.get(i).getName();
            main.post(new Runnable(){public void run(){if(isFinishing())return;if(labels.length==0){Toast.makeText(SettingsActivity.this,"사진을 찾지 못했습니다. 저장장치 연결을 확인해 주세요.",Toast.LENGTH_LONG).show();return;}
                new AlertDialog.Builder(SettingsActivity.this).setTitle(dir.getPath()).setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int index){if(parent&&index==0){browse(base,dir.getParentFile());return;}File f=files.get(index-(parent?1:0));if(f.isDirectory())browse(base,f);else importPhoto(Uri.fromFile(f));}}).setNegativeButton("닫기",null).show();
            }});
        }},"photo-browser").start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {super.onActivityResult(request,result,data);if(request==PICK&&result==RESULT_OK&&data!=null&&data.getData()!=null)importPhoto(data.getData());}
    private void importPhoto(final Uri uri) {
        if(busy)return;busy=true;refreshStatus();
        new Thread(new Runnable(){public void run(){String failure=null;try{PhotoStore.importPhoto(SettingsActivity.this,uri);}catch(Exception e){failure=e.getMessage();}final String message=failure;
            main.post(new Runnable(){public void run(){busy=false;if(isFinishing())return;refreshStatus();if(message!=null)Toast.makeText(SettingsActivity.this,message,Toast.LENGTH_LONG).show();else Toast.makeText(SettingsActivity.this,"사진을 저장했습니다.",Toast.LENGTH_SHORT).show();}});
        }},"photo-import").start();
    }
    private void error(Exception e){Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();}
}
