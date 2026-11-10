package io.github.mpdairy.monopaint;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

/**
 * Lasso to Superdood (the Supernote plugin, 2026-11-08): {@code call("draw")} takes a note's lassoed strokes and
 * returns them drawn with the toolbar's selected brush at its size and grey ({@link LassoRender}), as a PNG of the
 * marks' box, paper left clear; {@code call("fill")}, the highlighter fill, fills the lasso's inside with it instead,
 * the strokes left clear ({@link LassoRender#fill}). The PNG is read back from {@link #PICTURE} ({@link #openFile}).
 * Only the system (the Supernote's PluginHost runs as it), the shell (adb, for trying) and the app itself may call it.
 *
 * <p>In: {@code width}, {@code height} (the note's page, px), {@code counts} (each stroke's points), {@code xy} (every
 * point, x then y), {@code travel} (the pen's travel at each, 0 to 1); a fill's {@code lasso} (its outline, x then y)
 * and {@code clear} (px each side of each stroke left clear). Out: {@code x}, {@code y}, {@code w}, {@code h} (the box
 * on the page), {@code brush} (its name); or {@code error}.
 */
public final class LassoProvider extends ContentProvider {
    static final String DRAW = "draw", FILL = "fill", PICTURE = "lasso.png";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle in) {
        if (!(DRAW.equals(method) || FILL.equals(method)) || in == null) return null;
        allowed();
        Bundle out = new Bundle();
        try {
            Context context = getContext();
            Assets.install(context.getAssets()::open);
            SharedPreferences preferences = context.getSharedPreferences(PaintPreferences.FILE, Context.MODE_PRIVATE);
            ToolSlots slots = new ToolSlots(InkWash.store(preferences), new java.util.Random());
            // shortcut: the app's own toolbar's selection, a slot (the eraser and the rest draw with the first slot); a notebook's own toolbar and a brush picked in the plugin come later.
            int tool = InkWash.parse(preferences.getString(ToolSlots.SELECTED, null));
            if (tool >= ToolSlots.COUNT) tool = 0;
            Brush brush = slots.brush(tool);
            ToolSwatches.Look look = slots.swatches.look(InkWash.key(tool), brush);
            int[] counts = in.getIntArray("counts");
            float[] xy = in.getFloatArray("xy"), travel = in.getFloatArray("travel");
            float[][] strokes = new float[counts.length][], travels = new float[counts.length][];
            for (int i = 0, at = 0; i < counts.length; at += counts[i++]) {
                strokes[i] = java.util.Arrays.copyOfRange(xy, 2 * at, 2 * (at + counts[i]));
                travels[i] = java.util.Arrays.copyOfRange(travel, at, at + counts[i]);
            }
            long start = System.nanoTime();
            LassoRender.Marks m = FILL.equals(method)
                ? LassoRender.fill(in.getInt("width"), in.getInt("height"), in.getFloatArray("lasso"), strokes, in.getFloatArray("clear"), brush.parts, look.px, look.darkness)
                : LassoRender.draw(in.getInt("width"), in.getInt("height"), strokes, travels, brush.parts, look.px, look.darkness);
            Log.d(ProbeActivity.TAG, "Lasso to Superdood: " + method + ", " + counts.length + " strokes with " + brush.shownName() + " (" + ModularCode.encode(brush.parts) + " at " + look.px + " px, " + look.darkness + ") in " + (System.nanoTime() - start) / 1_000_000 + " ms");
            if (m == null) { out.putString("error", "Nothing drawn"); return out; }
            out.putInt("x", m.x); out.putInt("y", m.y); out.putInt("w", m.w); out.putInt("h", m.h);
            out.putString("brush", brush.shownName());
            try (java.io.FileOutputStream f = new java.io.FileOutputStream(new java.io.File(context.getCacheDir(), PICTURE))) { f.write(png(m)); }
        } catch (RuntimeException | java.io.IOException e) {
            Log.e(ProbeActivity.TAG, "Lasso to Superdood", e);
            out.putString("error", String.valueOf(e));
        }
        return out;
    }

    /** The last call's picture, read only (a file, as a page-sized wash's PNG can pass the Binder call's 1 MB). */
    @Override public android.os.ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        allowed();
        if (!("/" + PICTURE).equals(uri.getPath()) || !"r".equals(mode)) throw new java.io.FileNotFoundException(String.valueOf(uri));
        return android.os.ParcelFileDescriptor.open(new java.io.File(getContext().getCacheDir(), PICTURE), android.os.ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private static void allowed() {
        int uid = Binder.getCallingUid();
        if (uid >= Process.FIRST_APPLICATION_UID && uid != Process.myUid()) throw new SecurityException("Lasso to Superdood: not for uid " + uid);
    }

    /** The marks as a PNG: each tone its grey, paper clear. */
    private static byte[] png(LassoRender.Marks m) {
        int[] argb = new int[m.w * m.h];
        for (int i = 0; i < argb.length; i++) {
            int t = m.tones[i] & 255;
            argb[i] = t == 255 ? 0 : 0xff000000 | t << 16 | t << 8 | t;
        }
        Bitmap b = Bitmap.createBitmap(argb, m.w, m.h, Bitmap.Config.ARGB_8888);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        b.recycle();
        return bytes.toByteArray();
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
