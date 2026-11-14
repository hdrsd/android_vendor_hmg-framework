package dev.stinger.standby;

/** Android-independent bounds/geometry policy, also used by the runtime. */
public final class PhotoMath {
    public static final long MAX_INPUT_BYTES = 20L * 1024 * 1024;
    public static int sample(int width, int height) {
        if (width <= 0 || height <= 0 || width > 32768 || height > 32768
                || (long)width * height > 80000000L) throw new IllegalArgumentException("지원하지 않는 사진 크기입니다.");
        int sample = 1;
        while ((width + sample - 1) / sample > 1600 || (height + sample - 1) / sample > 1600
                || (long)((width + sample - 1) / sample) * ((height + sample - 1) / sample) > 1536000L) sample *= 2;
        return sample;
    }
    public static float[] destination(int iw, int ih, int vw, int vh, boolean crop) {
        if (iw <= 0 || ih <= 0 || vw <= 0 || vh <= 0) throw new IllegalArgumentException("Invalid size");
        float sx = (float)vw / iw, sy = (float)vh / ih;
        float scale = crop ? Math.max(sx, sy) : Math.min(sx, sy);
        float w = iw * scale, h = ih * scale;
        return new float[]{(vw-w)/2, (vh-h)/2, (vw+w)/2, (vh+h)/2};
    }
    public static int dim(int value) { return Math.max(0, Math.min(70, value)); }
    private PhotoMath() {}
}
