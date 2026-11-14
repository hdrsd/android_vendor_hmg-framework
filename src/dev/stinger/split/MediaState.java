package dev.stinger.split;

import android.graphics.Bitmap;
public final class MediaState {
    public String title="", artist="", album="", device="", artPath="", source="Bluetooth 연결 대기";
    public long duration, position, received;
    public boolean playing;
    public Bitmap art;
    public String trackKey() { return title+"\n"+artist+"\n"+album; }
}
