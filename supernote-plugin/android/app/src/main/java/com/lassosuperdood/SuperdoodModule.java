package com.lassosuperdood;

import android.net.Uri;
import android.os.Bundle;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Hands the lassoed strokes to Superdood (its LassoProvider, in the app ing.ell.mybaddrawings) and writes the PNG it
 * draws where the note can read it. PluginHost runs as the system, which may call the app's provider.
 */
public class SuperdoodModule extends ReactContextBaseJavaModule {
    static final Uri PROVIDER = Uri.parse("content://ing.ell.mybaddrawings.lasso");

    SuperdoodModule(ReactApplicationContext context) { super(context); }
    @Override public String getName() { return "Superdood"; }

    /**
     * {@code dir}: the plugin's own folder (no permission needed there), where the picture goes, one file written over
     * each time; {@code method}: "draw" (the strokes with the brush) or "fill" (the highlighter fill: the inside of
     * {@code lasso}, its outline x then y, with each stroke left clear {@code clear} px each side); {@code counts}: each
     * stroke's points; {@code xy}: every point, x then y (page px); {@code travel}: 0 to 1 at each.
     */
    @ReactMethod public void draw(String method, String dir, int width, int height, ReadableArray counts, ReadableArray xy, ReadableArray travel, ReadableArray lasso, ReadableArray clear, Promise promise) {
        try {
            Bundle in = new Bundle();
            in.putInt("width", width);
            in.putInt("height", height);
            int[] c = new int[counts.size()];
            for (int i = 0; i < c.length; i++) c[i] = counts.getInt(i);
            float[] p = new float[xy.size()], t = new float[travel.size()];
            for (int i = 0; i < p.length; i++) p[i] = (float) xy.getDouble(i);
            for (int i = 0; i < t.length; i++) t[i] = (float) travel.getDouble(i);
            in.putIntArray("counts", c);
            in.putFloatArray("xy", p);
            in.putFloatArray("travel", t);
            in.putFloatArray("lasso", floats(lasso));
            in.putFloatArray("clear", floats(clear));
            Bundle out = getReactApplicationContext().getContentResolver().call(PROVIDER, method, null, in);
            if (out == null) { promise.reject("superdood", "Superdood didn't answer (is it installed?)"); return; }
            if (out.getString("error") != null) { promise.reject("superdood", out.getString("error")); return; }
            File png = new File(dir, "lasso.png");
            try (InputStream from = getReactApplicationContext().getContentResolver().openInputStream(Uri.withAppendedPath(PROVIDER, "lasso.png"));
                 FileOutputStream f = new FileOutputStream(png)) {
                byte[] buffer = new byte[65536];
                for (int n; (n = from.read(buffer)) > 0; ) f.write(buffer, 0, n);
            }
            WritableMap result = Arguments.createMap();
            result.putString("path", png.getPath());
            result.putInt("x", out.getInt("x"));
            result.putInt("y", out.getInt("y"));
            result.putInt("w", out.getInt("w"));
            result.putInt("h", out.getInt("h"));
            result.putString("brush", out.getString("brush"));
            promise.resolve(result);
        } catch (Exception e) {
            promise.reject("superdood", String.valueOf(e), e);
        }
    }

    private static float[] floats(ReadableArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = (float) a.getDouble(i);
        return f;
    }
}
