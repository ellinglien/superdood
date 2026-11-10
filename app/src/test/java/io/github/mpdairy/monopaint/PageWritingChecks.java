package io.github.mpdairy.monopaint;

/** Read Handwriting: a page's pen strokes read from its log (docs/superpowers/specs/2026-11-09-read-handwriting-design.md). */
public final class PageWritingChecks {
    public static void main(String[] args) throws Exception {
        logRead(); noLog(); strokes();
        System.out.println("PASS: a page's stroke log reads without its pixels; its pen strokes still on the page come out, moved where the Lasso put them, erased and wide ones left out");
    }

    /** A page with one logged pen stroke, written and read back through DocumentCodec.log. */
    static void logRead() throws Exception {
        ToneDocument d = InkWashLayers.blank(400, 300);
        d.begin(0); for (int x = 10; x < 60; x++) d.setTone(x, 20, 20); check(d.finish(), "marks");
        StrokeLog.Recorder r = new StrokeLog.Recorder("ink-pen.M0abc.1", 4, 1);
        for (int i = 0; i < 10; i++) r.add(10 + i * 5, 20, .5f, Float.NaN, Float.NaN, i * 8);
        d.logStroke(r);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        DocumentCodec.write(out, d.layerSnapshot());
        DocumentCodec.PageLog read = DocumentCodec.log(out.toByteArray());
        check(read.width == 400 && read.height == 300, "the page's size: " + read.width + "x" + read.height);
        check(read.log.strokes().size() == 1, "one stroke in the log: " + read.log.strokes().size());
        check(read.log.strokes().get(0).size() == 10, "its ten samples");
    }

    /** A page with no strokes reads an empty log; a page too short to have a header is an IOException. */
    static void noLog() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        DocumentCodec.write(out, InkWashLayers.blank(400, 300).layerSnapshot());
        check(DocumentCodec.log(out.toByteArray()).log.strokes().isEmpty(), "a blank page has no strokes");
        try { DocumentCodec.log(new byte[8]); } catch (java.io.IOException expected) { return; }
        check(false, "a short page should throw IOException");
    }

    /** Two pen strokes, a wash and a finger stroke; erasing; a Lasso move, turn and delete. */
    static void strokes() {
        StrokeLog.Entry pen1 = penEntry(StrokeLog.PEN, 4, new float[]{100, 100, 140, 100});
        StrokeLog.Entry pen2 = penEntry(StrokeLog.PEN, 4, new float[]{100, 200, 140, 200});
        StrokeLog.Entry wash = penEntry(StrokeLog.PEN, 60, new float[]{50, 50, 300, 50});
        StrokeLog.Entry finger = penEntry(StrokeLog.FINGER, 4, new float[]{10, 10, 30, 10});
        java.util.List<StrokeLog.Entry> entries = java.util.Arrays.asList(pen1, pen2, wash, finger);
        java.util.List<PageWriting.Stroke> s = PageWriting.strokes(entries, null);
        check(s.size() == 2, "the two pen strokes, not the wash or the finger: " + s.size());
        check(s.get(0).x[0] == 100 && s.get(0).y[0] == 100, "the first stroke where it was drawn");
        check(s.get(1).t[0] > s.get(0).t[s.get(0).t.length - 1], "times run on from stroke to stroke");

        PageWriting.Tones tones = (x, y) -> y > 150 ? 255 : 0;
        check(PageWriting.strokes(entries, tones).size() == 1, "an erased stroke is left out");

        // The log's PLACE is back entries after its LIFT; the stroke's box centre is (120, 100).
        StrokeLog.Entry lift = liftEntry(new float[]{90, 90, 150, 90, 150, 110, 90, 110});
        StrokeLog.Entry place = placeEntry(1, true, true, 120, 200, 1, 0);
        java.util.List<PageWriting.Stroke> moved = PageWriting.strokes(java.util.Arrays.asList(pen1, lift, place), null);
        check(moved.size() == 1, "the moved stroke stays one stroke");
        check(Math.abs(moved.get(0).y[0] - 200) < 1 && Math.abs(moved.get(0).x[0] - 100) < 1, "moved to its new place: " + moved.get(0).x[0] + "," + moved.get(0).y[0]);

        StrokeLog.Entry cut = placeEntry(1, true, false, 0, 0, 1, 0);
        check(PageWriting.strokes(java.util.Arrays.asList(pen1, lift, cut), null).isEmpty(), "a lifted piece deleted takes its stroke");

        // angle is radians, as VectorLines.posed's cos/sin.
        StrokeLog.Entry turn = placeEntry(1, true, true, 120, 100, 1, Math.PI / 2);
        PageWriting.Stroke t = PageWriting.strokes(java.util.Arrays.asList(pen1, lift, turn), null).get(0);
        check(Math.abs(t.x[0] - t.x[t.size() - 1]) < 1, "a quarter turn stands the stroke upright");

        // A turn's position and direction: first point (100,100) is -20,0 from the box centre (120,100); y down, +90 deg turns it to (120,80).
        check(Math.abs(t.x[0] - 120) < 1 && Math.abs(t.y[0] - 80) < 1, "a quarter turn puts the first point above the centre: " + t.x[0] + "," + t.y[0]);

        // Poses start from the original lift: moved at scale 2, then copied at scale 2 to (300,300).
        StrokeLog.Entry big = placeEntry(1, true, true, 120, 100, 2, 0);
        StrokeLog.Entry copy = placeEntry(2, false, true, 300, 300, 2, 0);
        java.util.List<PageWriting.Stroke> two = PageWriting.strokes(java.util.Arrays.asList(pen1, lift, big, copy), null);
        check(two.size() == 2, "the moved stroke and its copy: " + two.size());
        check(Math.abs(two.get(0).x[0] - 80) < 1 && Math.abs(two.get(0).x[1] - 160) < 1, "the moved one stays where the first place put it");
        check(Math.abs(two.get(1).x[0] - 260) < 1 && Math.abs(two.get(1).x[1] - 340) < 1, "the copy spans 260..340: " + two.get(1).x[0] + ".." + two.get(1).x[1]);

        // Order: a moved first stroke is still first.
        java.util.List<PageWriting.Stroke> ord = PageWriting.strokes(java.util.Arrays.asList(pen1, pen2, lift, place), null);
        check(ord.size() == 2 && Math.abs(ord.get(0).y[0] - 200) < 1 && Math.abs(ord.get(1).y[0] - 200) < 1 && ord.get(0).x[0] == 100, "writing order kept");
        check(ord.get(0).t[0] < ord.get(1).t[0], "times keep the order");

        // A place whose lift isn't in the log is ignored.
        check(PageWriting.strokes(java.util.Arrays.asList(pen1, place), null).size() == 1 && PageWriting.strokes(java.util.Arrays.asList(pen1, place), null).get(0).y[0] == 100, "a place with no lift is ignored");

        // onPaper: paper 230 reads 255, ink 100 stays dark.
        PageWriting.Tones shaded = PageWriting.onPaper((x, y) -> x < 50 ? 230 : 100, 230);
        check(shaded.tone(10, 0) == 255 && shaded.tone(60, 0) == 125 && shaded.tone(60, 0) < PageWriting.PAPER_MARGIN, "onPaper lifts paper to 255 and leaves ink dark");
    }

    static StrokeLog.Entry penEntry(int kind, double px, float[] xy) {
        StrokeLog.Recorder r = new StrokeLog.Recorder(kind, kind == StrokeLog.PEN ? "ink-pen.M0abc.1" : null, null, px, 1, 0);
        for (int i = 0; i < xy.length / 2; i++) r.add(xy[2 * i], xy[2 * i + 1], .5f, Float.NaN, Float.NaN, i * 8);
        return r.done(1);
    }
    static StrokeLog.Entry liftEntry(float[] loop) { return StrokeLog.Entry.lift(2, StrokeLog.Entry.halfPixels(loop, loop.length / 2), loop.length / 2); }
    static StrokeLog.Entry placeEntry(int back, boolean cut, boolean drop, float cx, float cy, float scale, double angle) {
        return new StrokeLog.Entry(StrokeLog.PLACE, 3, null, null, 0, 0, 0, 0, new int[0], new int[0], new int[0], new byte[0], new byte[0], new byte[0],
                new StrokeLog.Place(back, drop, cut, cx, cy, scale, angle));
    }

    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
