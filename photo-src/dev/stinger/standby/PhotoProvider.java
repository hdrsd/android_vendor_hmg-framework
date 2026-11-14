package dev.stinger.standby;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.List;

/** Read-only IPC of one selected photo. Never exposes arbitrary file paths. */
public final class PhotoProvider extends ContentProvider {
    public boolean onCreate() { return true; }
    private void checkCaller() {
        int uid=Binder.getCallingUid();
        if(uid==android.os.Process.myUid()) return;
        String[] packages=getContext().getPackageManager().getPackagesForUid(uid);
        if(packages!=null) for(String p:packages) if("com.android.ivisystemui".equals(p)) return;
        throw new SecurityException("Photo access is limited to the standby screen");
    }
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort) {
        checkCaller(); if(!PhotoStore.SETTINGS.equals(uri)) throw new IllegalArgumentException("Unknown URI");
        synchronized(PhotoStore.LOCK) {
            SharedPreferences p=PhotoStore.prefs(getContext()); File f=PhotoStore.current(getContext());
            boolean available=f!=null && f.isFile();
            MatrixCursor c=new MatrixCursor(new String[]{"enabled","clock","crop","dim","image"});
            c.addRow(new Object[]{p.getBoolean("enabled",false)&&available?1:0,p.getBoolean("clock",true)?1:0,
                p.getBoolean("crop",true)?1:0,PhotoMath.dim(p.getInt("dim",15)),available?"content://dev.stinger.split.photos/photo/"+f.getName():""});
            return c;
        }
    }
    public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        checkCaller(); List<String> s=uri.getPathSegments();
        if(!"r".equals(mode) || s.size()!=2 || !"photo".equals(s.get(0)) || !PhotoStore.validName(s.get(1))) throw new FileNotFoundException("Read-only photo URI required");
        synchronized(PhotoStore.LOCK) { return ParcelFileDescriptor.open(new File(getContext().getFilesDir(),s.get(1)),ParcelFileDescriptor.MODE_READ_ONLY); }
    }
    public String getType(Uri uri) { checkCaller(); return uri.equals(PhotoStore.SETTINGS)?"vnd.android.cursor.item/vnd.stinger.standby":"image/jpeg"; }
    public Uri insert(Uri u,ContentValues v) { throw new UnsupportedOperationException("Read only"); }
    public int update(Uri u,ContentValues v,String s,String[] a) { throw new UnsupportedOperationException("Read only"); }
    public int delete(Uri u,String s,String[] a) { throw new UnsupportedOperationException("Read only"); }
}
