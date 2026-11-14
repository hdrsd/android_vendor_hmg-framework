package dev.stinger.standby;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import java.io.*;
import java.util.UUID;

public final class PhotoStore {
    static final Object LOCK = new Object();
    public static final Uri SETTINGS = Uri.parse("content://dev.stinger.split.photos/settings");
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("photo", Context.MODE_PRIVATE); }
    static boolean validName(String name) { return name != null && name.matches("photo-[a-f0-9]{32}\\.jpg"); }
    static File current(Context c) { String n = prefs(c).getString("file", ""); return validName(n) ? new File(c.getFilesDir(), n) : null; }
    public static boolean hasPhoto(Context c) { synchronized (LOCK) { File f=current(c); return f!=null && f.isFile(); } }
    static void notifyChanged(Context c) { c.getContentResolver().notifyChange(SETTINGS, null); }
    public static void update(Context c, String key, boolean value) throws IOException {
        synchronized (LOCK) { if (!prefs(c).edit().putBoolean(key,value).commit()) throw new IOException("설정을 저장하지 못했습니다."); }
        notifyChanged(c);
    }
    public static void updateDim(Context c, int value) throws IOException {
        synchronized (LOCK) { if (!prefs(c).edit().putInt("dim",PhotoMath.dim(value)).commit()) throw new IOException("설정을 저장하지 못했습니다."); }
        notifyChanged(c);
    }
    public static void importPhoto(Context c, Uri source) throws IOException {
        File tmp=File.createTempFile("import-", ".tmp", c.getCacheDir());
        File result=new File(c.getFilesDir(),"photo-"+UUID.randomUUID().toString().replace("-", "")+".jpg");
        Bitmap bitmap=null; boolean committed=false;
        try {
            InputStream input=c.getContentResolver().openInputStream(source);
            if(input==null) throw new IOException("사진을 열 수 없습니다.");
            try { FileOutputStream output=new FileOutputStream(tmp);
                try { byte[] buffer=new byte[8192]; long total=0; int n;
                    while((n=input.read(buffer))!=-1) { total+=n; if(total>PhotoMath.MAX_INPUT_BYTES) throw new IOException("사진은 20MB 이하로 선택해 주세요."); output.write(buffer,0,n); }
                } finally { output.close(); }
            } finally { input.close(); }
            BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true; BitmapFactory.decodeFile(tmp.toString(),o);
            try { o.inSampleSize=PhotoMath.sample(o.outWidth,o.outHeight); } catch(IllegalArgumentException e) { throw new IOException(e.getMessage()); }
            o.inJustDecodeBounds=false; o.inPreferredConfig=Bitmap.Config.ARGB_8888;
            bitmap=BitmapFactory.decodeFile(tmp.toString(),o);
            if(bitmap==null) throw new IOException("사진 형식을 읽을 수 없습니다.");
            int orientation=1;
            try { orientation=new ExifInterface(tmp.toString()).getAttributeInt(ExifInterface.TAG_ORIENTATION,1); } catch(IOException ignored) {}
            Matrix matrix=orientationMatrix(orientation);
            if(!matrix.isIdentity()) { Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true); if(rotated!=bitmap) { bitmap.recycle(); bitmap=rotated; } }
            FileOutputStream output=new FileOutputStream(result);
            try { if(!bitmap.compress(Bitmap.CompressFormat.JPEG,94,output)) throw new IOException("사진 변환에 실패했습니다."); output.getFD().sync(); } finally { output.close(); }
            synchronized(LOCK) {
                String previous=prefs(c).getString("file", "");
                if(!prefs(c).edit().putString("file",result.getName()).putBoolean("enabled",true).commit()) throw new IOException("사진 설정을 저장하지 못했습니다.");
                committed=true;
                // Keep one preceding generation for readers that queried just before the commit.
                File[] files=c.getFilesDir().listFiles();
                if(files!=null) for(File f:files) if(validName(f.getName()) && !f.getName().equals(previous) && !f.equals(result)) f.delete();
            }
            notifyChanged(c);
        } catch(OutOfMemoryError e) { throw new IOException("사진이 너무 큽니다. 더 작은 사진을 선택해 주세요."); }
        finally { if(bitmap!=null) bitmap.recycle(); tmp.delete(); if(!committed) result.delete(); }
    }
    static Matrix orientationMatrix(int orientation) {
        Matrix m=new Matrix();
        switch(orientation) {
            case 2: m.setScale(-1,1); break;
            case 3: m.setRotate(180); break;
            case 4: m.setScale(1,-1); break;
            case 5: m.setRotate(90); m.postScale(-1,1); break;
            case 6: m.setRotate(90); break;
            case 7: m.setRotate(-90); m.postScale(-1,1); break;
            case 8: m.setRotate(-90); break;
            default: break;
        }
        return m;
    }
    private PhotoStore() {}
}
