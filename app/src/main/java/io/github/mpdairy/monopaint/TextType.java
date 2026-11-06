package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Text boxes' letters on the tablet ({@link TextBoxes.Type}): each line drawn where {@link TextBoxes#lines} lays it, in
 * iA Writer Duo ({@link Theme#textFace}), antialiased, as coverage; the last {@link #KEPT} kept, so the page, its copies
 * and Make Ink draw a box once.
 */
final class TextType implements TextBoxes.Type {
    /** How many boxes' letters are kept (a page-wide box of Medium text is about half a megabyte). */
    static final int KEPT = 24;
    private final Map<TextBoxes.Box, byte[]> kept = new LinkedHashMap<TextBoxes.Box, byte[]>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<TextBoxes.Box, byte[]> eldest) { return size() > KEPT; }
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    @Override public synchronized byte[] letters(TextBoxes.Box b) {
        byte[] a = kept.get(b);
        if (a != null) return a;
        int[] r = TextBoxes.bounds(b);
        int w = r[2] - r[0], h = r[3] - r[1];
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(bitmap);
        paint.setTypeface(Theme.textFace(b.bold)); paint.setTextSize(b.em());
        for (TextBoxes.Line line : TextBoxes.lines(b)) c.drawText(line.text, line.x - r[0], line.baseline - r[1], paint);
        // ALPHA_8 rows may be padded: copied out row by row.
        ByteBuffer buffer = ByteBuffer.allocate(bitmap.getRowBytes() * h);
        bitmap.copyPixelsToBuffer(buffer);
        bitmap.recycle();
        a = new byte[w * h];
        for (int y = 0; y < h; y++) System.arraycopy(buffer.array(), y * buffer.capacity() / h, a, y * w, w);
        kept.put(b, a);
        return a;
    }
}
