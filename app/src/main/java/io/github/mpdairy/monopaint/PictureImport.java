package io.github.mpdairy.monopaint;

/**
 * Pictures brought onto the page (the owner, 2026-10-22: Import Image and Import PDF in the file menu;
 * docs/archive/superpowers/specs/2026-10-22-import-design.md; one Import since 2026-10-29, the owner: "can we merge the
 * pdf and image import flow? ... so it'd be just one import thing"): what both share, and the numbers each decides by.
 * <ul>
 * <li><b>Greys</b> ({@link #greys}): a picture's colours as the page holds tone, smooth (the panel's dots and the copies'
 * greys are made from it later, as from any mark), each pixel its brightness over white; paper-white is left clear, as an
 * own template's near-white is ({@link NotebookTemplate#tones}), so the page and the layers under it show through.
 * {@link #lay} puts them on the layer drawn on as ink is laid on it (transparent, darkening), one Undo step, never
 * lighter than what the layer holds there.</li>
 * <li><b>Placing an image</b> ({@link Frame}): fitted inside the page as shown and centred at first ({@link #fit}), then moved,
 * resized from its corner or pinched, its shape kept, and made Lighter or Darker and More or Less Contrast a few
 * steps ({@link #toned}), until Place or Cancel.</li>
 * <li><b>A PDF's notebook</b>: a page a stack of two layers ({@link PageStacks}), the PDF's page at the bottom, locked
 * ({@link #pdfPage}: its clock locked, as a timed page's at zero, so "Page locked" refuses marks, Undo and Merge Down
 * there), and a clear layer on it, drawn on ({@link #stacks}); so at most {@link #MOST_PDF_PAGES} of its pages, and
 * {@link #fits} keeps them inside the notebook's 200 MB.</li>
 * </ul>
 * Pure, checked on the desktop ({@code PictureImportChecks}).
 */
final class PictureImport {
    private PictureImport() {}

    /** The file menu's row and the notes (docs/TEXT-STYLE.md: two words a button, about three a note). */
    static final String IMPORT = "Import", EXPORT_NOW = "Export Now";
    static final String PLACE = "Place", CANCEL = "Cancel", LIGHTER = "Lighter", DARKER = "Darker",
            MORE_CONTRAST = "More Contrast", LESS_CONTRAST = "Less Contrast";
    /** The placing bar's step buttons in order ({@link Placing#step}): each dial's way up, then down. */
    static final String[] STEPS = {LIGHTER, DARKER, MORE_CONTRAST, LESS_CONTRAST};
    /** A PDF picked: its pages as a new notebook, or the page shown placed on the page drawn on, as a picture. */
    static final String NEW_NOTEBOOK = "New Notebook", THIS_PAGE = "This Page";
    /** Notes: a picture or PDF that doesn't read, a PDF without pages. */
    static final String UNREADABLE = "Can't read it", NO_PAGES = "No pages";
    /** The PDF's import: its progress ("Importing 3 / 12") and what it says once done when it left pages out ("First 124 pages"). */
    static String importing(int done, int of) { return "Importing " + done + " / " + of; }
    static String firstPages(int n) { return "First " + n + " pages"; }

    // The picker (the owner, 2026-10-26: "there's no way to cancel"): the app's own ({@code ImportPicker}), with Cancel

    /** The picker's top folders, under the storage root: the Supernote file manager's folders, then Pictures and Download. */
    static final String[] FOLDERS = {"Document", "EXPORT", "MyStyle", "Note", "SCREENSHOT", "INBOX", "Pictures", "Download"};
    /** The files the picker lists, by their ending (any case): pictures and PDFs together. */
    static final String[] IMPORTABLE = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".pdf"};
    /**
     * Whether {@code bytes} begin as a file named {@code name} ({@link #IMPORTABLE}) does: a PDF's "%PDF-", a PNG's, a
     * JPEG's, a WebP's, a GIF's or a BMP's signature. A file from elsewhere (Share on Wi-Fi's upload) is checked before
     * any reader sees it.
     */
    static boolean importable(String name, byte[] bytes) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        String[][] signs = {{".pdf", "%PDF-"}, {".png", "\u0089PNG\r\n\u001a\n"}, {".jpg", "ÿØÿ"}, {".jpeg", "ÿØÿ"},
                {".webp", "RIFF"}, {".gif", "GIF8"}, {".bmp", "BM"}};
        for (String[] s : signs) if (n.endsWith(s[0])) {
            if (bytes.length < Math.max(12, s[1].length())) return false;
            for (int i = 0; i < s[1].length(); i++) if ((bytes[i] & 255) != s[1].charAt(i)) return false;
            return !s[0].equals(".webp") || bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
        }
        return false;
    }
    /** Whether a picked file is a PDF, by its type ({@code null} when unknown) or else its name's ending. */
    static boolean pdf(String type, String name) {
        return "application/pdf".equals(type) || name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
    }
    /** The picker's top folder's name (the storage root, its folders {@link #FOLDERS}), and its note in a folder with nothing to import. */
    static final String STORAGE = "Storage", NOTHING_FOUND = "Nothing found";
    /**
     * What the picker shows in {@code folder} (the owner, 2026-10-29: "more like supernote file explorer"): its folders A
     * to Z, then its pictures and PDFs newest first, hidden ones skipped; at {@code root}, only {@link #FOLDERS} that are there.
     */
    static java.util.List<java.io.File> entries(java.io.File root, java.io.File folder) {
        java.util.List<java.io.File> folders = new java.util.ArrayList<>(), files = new java.util.ArrayList<>();
        if (folder.equals(root)) {
            for (String name : FOLDERS) { java.io.File top = new java.io.File(root, name); if (top.isDirectory()) folders.add(top); }
            return folders;
        }
        java.io.File[] all = folder.listFiles();
        if (all == null) return folders;
        for (java.io.File file : all) {
            String name = file.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.startsWith(".")) continue;
            if (file.isDirectory()) { folders.add(file); continue; }
            for (String ending : IMPORTABLE) if (name.endsWith(ending)) { files.add(file); break; }
        }
        folders.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        // Each file's time read once: a file written while sorting can't upset the sort.
        java.util.Map<java.io.File, Long> changed = new java.util.HashMap<>();
        for (java.io.File file : files) changed.put(file, file.lastModified());
        files.sort((a, b) -> { int by = Long.compare(changed.get(b), changed.get(a)); return by != 0 ? by : a.getName().compareTo(b.getName()); });
        folders.addAll(files);
        return folders;
    }
    /** {@code folder} from {@code root}, "Document/Scribulor" ("" for the root): what the picker remembers, its crumbs. */
    static String path(java.io.File root, java.io.File folder) {
        return root.toPath().relativize(folder.toPath()).toString().replace(java.io.File.separatorChar, '/');
    }
    /** Where the picker opens: {@code last} ({@link #path}), or the nearest folder above it still there; the root for none, or one outside it. */
    static java.io.File start(java.io.File root, String last) {
        if (last == null || last.isEmpty() || last.startsWith("/") || java.util.Arrays.asList(last.split("/")).contains("..")) return root;
        java.io.File at = new java.io.File(root, last);
        while (at != null && !at.equals(root) && !at.isDirectory()) at = at.getParentFile();
        return at == null ? root : at;
    }

    // Greys

    /** Brightness this light and over is paper: left clear ({@link NotebookTemplate}'s own templates' near-white). */
    static final int PAPER = 248;
    /** Pixel {@code argb}'s brightness over white paper, 0 (black) to 255: its luma, its see-through share white. */
    static int grey(int argb) {
        int a = argb >>> 24;
        int luma = (((argb >> 16) & 255) * 299 + ((argb >> 8) & 255) * 587 + (argb & 255) * 114) / 1000;
        return 255 - (255 - luma) * a / 255;
    }
    /** {@code argb} as the greys {@link #lay} lays, in place: each pixel its {@link #grey}, −1 (clear) where it is paper. */
    static int[] greys(int[] argb) { return greys(argb, 0, 0); }
    /** As {@link #greys(int[])}, each grey {@link #toned} {@code shade} and {@code contrast} steps; what is paper is still decided by the picture's own grey. */
    static int[] greys(int[] argb, int shade, int contrast) {
        for (int i = 0; i < argb.length; i++) { int g = grey(argb[i]); argb[i] = g >= PAPER ? -1 : toned(g, shade, contrast); }
        return argb;
    }
    /** A picture being placed is made Lighter or Darker (the owner, 2026-11-01), and More or Less Contrast (2026-11-02), at most this many steps each way. */
    static final int SHADES = 3;
    /** What one step leaves: Lighter of a grey's ink (black to 76), Darker of its light (128 to 90). */
    static final double SHADE = .7;
    /** Grey {@code g} {@code shade} steps Lighter (more than 0) or Darker (less): its ink, or its light, times {@link #SHADE} a step; Lighter keeps white, Darker black. */
    static int shaded(int g, int shade) {
        if (shade == 0) return g;
        double f = Math.pow(SHADE, Math.abs(shade));
        return shade > 0 ? 255 - (int) Math.round((255 - g) * f) : (int) Math.round(g * f);
    }
    /** The middle grey that More and Less Contrast spread the greys from and draw them to. */
    static final int MIDDLE = 128;
    /**
     * Grey {@code g} {@code contrast} steps More (more than 0) or Less (less) Contrast, its distance from {@link #MIDDLE}
     * divided or multiplied by {@link #SHADE} a step (held between black and white), then {@link #shaded} {@code shade}
     * steps: contrast first, so Lighter still keeps white and Darker black.
     */
    static int toned(int g, int shade, int contrast) {
        if (contrast != 0) g = Math.max(0, Math.min(255, MIDDLE + (int) Math.round((g - MIDDLE) * Math.pow(SHADE, -contrast))));
        return shaded(g, shade);
    }
    /**
     * Lays {@code greys} ({@link #greys}: {@code w} a row, −1 clear) at {@code x, y} on the layer of {@code page} drawn on,
     * as one Undo step: a pixel takes its grey as ink does ({@link ToneDocument#setTones}), only where that is darker than
     * the layer there, so on a layer with marks the picture darkens them and never lightens them. Throws as a stroke would
     * on a hidden layer.
     */
    static void lay(ToneDocument page, int x, int y, int w, int h, int[] greys) { lay(page, x, y, w, h, greys, false); }
    /**
     * As {@link #lay}, {@code joined}: one Undo step with the step before it ({@link ToneDocument#finishWithPrevious},
     * so that step must have changed something); whether it changed anything.
     */
    static boolean lay(ToneDocument page, int x, int y, int w, int h, int[] greys, boolean joined) { return lay(page, x, y, w, h, greys, joined, false); }
    /**
     * As {@link #lay(ToneDocument, int, int, int, int, int[], boolean)}, {@code lighter}: the page made to show each grey,
     * lighter too (a live page's change that lightens, {@code LiveChange#set}: the eraser, an Undo, Clear Page; and a live
     * page's own Undo). Only where a grey is lighter than the page shows: the marks of the layer drawn on erased there, base
     * and planes, and where that leaves it still too dark, every other shown layer's too (2026-11-06, three layers: a
     * change made on one layer leaves the others'); then the grey laid on the layer drawn on, white (254 and up) leaving
     * the paper. Where it is darker it goes on as ink ({@link #inked}), the other layers left as they are. Before
     * 2026-11-02 every pixel of a set change was erased, so a guest's smudge that lightened one pixel flattened the
     * owner's layers, kinds and tones under all of it.
     */
    static boolean lay(ToneDocument page, int x, int y, int w, int h, int[] greys, boolean joined, boolean lighter) { return lay(page, x, y, w, h, greys, joined, lighter, false); }
    /**
     * As above, {@code exact} (a page's own live Undo, which must show each grey as it was): a darker grey erased and laid
     * too, not inked, which a plane over the layer's base (the paint's own) would hide; a grey the page shows left as it is.
     */
    static boolean lay(ToneDocument page, int x, int y, int w, int h, int[] greys, boolean joined, boolean lighter, boolean exact) { return lay(page, x, y, w, h, greys, joined, lighter, exact, 0); }
    /**
     * As above, the ink laid as a mark of {@code kinds} ({@code ToneDocument.KIND_} bits; 0: of none): on its kind's
     * plane, as the stroke that made it lay on the sender's, so Show Kinds fades it here too (a live change's {@code kinds}).
     */
    static boolean lay(ToneDocument page, int x, int y, int w, int h, int[] greys, boolean joined, boolean lighter, boolean exact, int kinds) {
        int[] shown = null;
        byte[] erased = null;
        // A lighter change is another screen's (or a live page's own Undo): the page as every screen shares it, each layer Full.
        if (lighter) { shown = liveTones(page, x, y, w, h); erased = new byte[w * h]; }
        boolean lightens = false;
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++) {
            int i = row * w + col, px = x + col, py = y + row;
            if (greys[i] < 0) continue;
            if (px < 0 || py < 0 || px >= page.width || py >= page.height) { greys[i] = -1; continue; }
            int g = greys[i] >= 254 ? 255 : greys[i];  // 254 is white as a change sends it
            if (lighter && (g > shown[i] || exact && g < shown[i])) { erased[i] = 1; lightens = true; continue; }
            int layer = page.layerTone(px, py, kinds);
            if (lighter) greys[i] = greys[i] >= 254 ? -1 : inked(greys[i], shown[i], layer);
            if (greys[i] >= layer) greys[i] = -1;
        }
        boolean changed = false, link = joined;
        if (lightens) {
            int active = page.activeLayer(), pair = InkWashLayers.current(page);
            Object mark = page.lastStep();
            try {
                // The layer drawn on first (a change made on one layer: its Lasso, an Undo of its stroke), the others' marks
                // kept; then, where the page must still get lighter (the eraser takes every layer), every other layer too.
                changed = erase(page, x, y, w, h, erased, pair, true, link);
                int[] now = liveTones(page, x, y, w, h);
                byte[] rest = new byte[w * h];
                boolean more = false;
                for (int i = 0; i < rest.length; i++) if (erased[i] != 0 && (greys[i] >= 254 ? 255 : greys[i]) > now[i]) { rest[i] = 1; more = true; }
                if (more && erase(page, x, y, w, h, rest, pair, false, link || changed)) { changed = true; now = liveTones(page, x, y, w, h); }
                link |= changed;
                // Vector Ink's lines cut there too, as the eraser cuts them: else the zoomed view drew them where they were erased, and the next eraser near them drew their pixels back.
                if (VectorInk.cut(page, x, y, w, h, erased, rest, pair)) { changed = link = true; page.linkSince(mark); }
                // The grey laid on the layer drawn on, now blank there, as ink over what the others show.
                for (int i = 0; i < rest.length; i++) if (erased[i] != 0) { int g = greys[i] >= 254 ? -1 : inked(greys[i], now[i], 255); greys[i] = g >= 255 ? -1 : g; }
            } finally { page.selectLayer(active); }
        }
        page.begin(1, kinds);
        try { page.setTones(x, y, w, h, greys); }
        finally { changed |= link ? page.finishWithPrevious() : page.finish(); }
        return changed;
    }
    /** The halves of pair {@code pair} ({@code own}) or of every other pair, shown ones, erased at {@code erased}, each plane an Undo step joined to the one before when {@code link}; whether any changed. */
    private static boolean erase(ToneDocument page, int x, int y, int w, int h, byte[] erased, int pair, boolean own, boolean link) {
        boolean changed = false;
        for (int layer = 0; layer < page.layerCount(); layer++) {
            if (!page.layerVisible(layer) || (InkWashLayers.pair(layer) == pair) != own) continue;
            page.selectLayer(layer);
            for (int k = -1; k < ToneDocument.PLANES; k++) {
                if (!page.beginPlane(k, false)) continue;
                page.eraseKinds(ToneDocument.KIND_ALL);
                try { page.eraseTones(x, y, w, h, erased); } finally { page.eraseKinds(0); }
                boolean c = link ? page.finishWithPrevious() : page.finish();
                changed |= c; link |= c;
            }
        }
        return changed;
    }
    /** {@link ToneDocument#liveTones} over the rect, cut to the page; white off it. */
    static int[] liveTones(ToneDocument page, int x, int y, int w, int h) {
        int[] out = new int[w * h];
        java.util.Arrays.fill(out, 255);
        int x0 = Math.max(0, x), y0 = Math.max(0, y), x1 = Math.min(page.width, x + w), y1 = Math.min(page.height, y + h);
        if (x0 < x1 && y0 < y1) page.liveTones(x0, y0, x1 - x0, y1 - y0, out, (y0 - y) * w + x0 - x, w);
        return out;
    }
    /**
     * The grey the layer drawn on (tone {@code layer} there) takes so that the page, showing {@code shown}, shows
     * {@code g}: its tone scaled by how much darker the page must get, as black ink darkens what lies under it; −1 where
     * {@code g} is no darker. Laid as it came, a grey darkened again whatever the other layers held there (a browser's
     * stroke over a wash came out darker on the Manta). Shortcut: exact for black ink over another layer's marks, near
     * for grey ink on this layer; upgrade with live pages proper (layers of their own).
     */
    static int inked(int g, int shown, int layer) {
        if (g < 0 || g >= shown) return -1;
        return shown == 0 ? g : Math.min(255, (layer * g + shown / 2) / shown);
    }

    /**
     * Place: the picture {@link #lay laid} on the layer drawn on, as any mark, so the eraser takes it and what was drawn
     * there before alike, and Undo takes it away (the owner, 2026-10-26; before, it went on a new layer above, drawn on,
     * which left the marks under it out of the eraser's reach).
     */
    static void place(DrawingBook book, int x, int y, int w, int h, int[] greys) { place(book.current(), x, y, w, h, greys); }
    /**
     * Place on {@code page}, the Manta's and the browser's: {@code greys} {@link #lay laid}, logged for Record and Replay
     * ({@link StrokeLog.Picture}, on the layer drawn on); whether it changed the page.
     */
    static boolean place(ToneDocument page, int x, int y, int w, int h, int[] greys) {
        StrokeLog.Picture logged = StrokeLog.Picture.of(x, y, w, h, page.activeLayer(), greys);
        boolean changed = lay(page, x, y, w, h, greys, false);
        page.logMade(StrokeLog.Entry.picture(0, logged), changed);
        return changed;
    }

    // Placing an image

    /** {left, top, width, height} of a {@code w × h} picture fitted inside a {@code pageW × pageH} page, centred, its shape kept. */
    static int[] fit(int w, int h, int pageW, int pageH) {
        if (w <= 0 || h <= 0) throw new IllegalArgumentException("Empty picture");
        double scale = Math.min((double) pageW / w, (double) pageH / h);
        int fw = Math.max(1, (int) Math.round(w * scale)), fh = Math.max(1, (int) Math.round(h * scale));
        return new int[]{(pageW - fw) / 2, (pageH - fh) / 2, fw, fh};
    }

    /**
     * Where a picture being placed lies on the page, page px: its top left, width and height, its shape always the
     * picture's. Immutable. It stays at least {@link #KEPT} px on the page, between {@link #SMALLEST} px across its
     * shorter side and {@link #LARGEST} times the page.
     */
    static final class Frame {
        /** The least of it left on the page, px each way (about 5 mm), so it can't be lost off the edge. */
        static final float KEPT = 64;
        /** Its shorter side's least, px (about 4 mm). */
        static final float SMALLEST = 48;
        /** Its longer side's most, times the page's longer side. */
        static final float LARGEST = 3;
        /** The corner handle's side, px, and how far from the corner a pen or finger still takes it. */
        static final float HANDLE = 56, REACH = 96;

        final float x, y, w, h;
        final int pageW, pageH;

        Frame(float x, float y, float w, float h, int pageW, int pageH) { this.x = x; this.y = y; this.w = w; this.h = h; this.pageW = pageW; this.pageH = pageH; }
        /** A {@code w × h} picture fitted inside the page, centred ({@link #fit}). */
        static Frame fitted(int w, int h, int pageW, int pageH) { return fitted(w, h, new int[]{0, 0, pageW, pageH}, pageW, pageH); }
        /**
         * A {@code w × h} picture fitted inside the part of the page {@code shown} ({left, top, right, bottom}: the page
         * may be larger than the screen at 100%, under the bars), {@link #HANDLE} in from its edges so the handle shows,
         * centred there; the whole page when none of it is shown.
         */
        static Frame fitted(int w, int h, int[] shown, int pageW, int pageH) {
            int inset = Math.round(HANDLE);
            int l = Math.max(0, shown[0]) + inset, t = Math.max(0, shown[1]) + inset, r = Math.min(pageW, shown[2]) - inset, b = Math.min(pageH, shown[3]) - inset;
            if (r - l < SMALLEST || b - t < SMALLEST) { l = 0; t = 0; r = pageW; b = pageH; }
            int[] f = fit(w, h, r - l, b - t);
            return new Frame(l + f[0], t + f[1], f[2], f[3], pageW, pageH);
        }
        /** Moved by {@code dx, dy}, kept {@link #KEPT} on the page. */
        Frame moved(float dx, float dy) { return new Frame(x + dx, y + dy, w, h, pageW, pageH).kept(); }
        /** Its corner dragged to {@code cornerX, cornerY}, its top left staying: as wide as reaches the corner either way, its shape kept. */
        Frame cornerTo(float cornerX, float cornerY) {
            float across = Math.max(cornerX - x, (cornerY - y) * w / h);
            return sized(across).kept();
        }
        /** Scaled by {@code factor} about page point {@code cx, cy} (a pinch: the point between the fingers stays put), then moved by {@code dx, dy}. */
        Frame scaled(float factor, float cx, float cy, float dx, float dy) {
            Frame s = sized(w * factor);
            float f = s.w / w;
            return new Frame(cx - (cx - x) * f + dx, cy - (cy - y) * f + dy, s.w, s.h, pageW, pageH).kept();
        }
        /** As wide as {@code across} (height following), held between {@link #SMALLEST} and {@link #LARGEST}; its top left staying. */
        private Frame sized(float across) {
            float aspect = w / h;
            float least = aspect >= 1 ? SMALLEST * aspect : SMALLEST, most = Math.max(pageW, pageH) * LARGEST * Math.min(1, aspect);
            float nw = Math.max(least, Math.min(most, across));
            return new Frame(x, y, nw, nw / aspect, pageW, pageH);
        }
        /** Moved, if it must be, to leave {@link #KEPT} px of it on the page each way (all of it, when it is smaller). */
        private Frame kept() {
            float kx = Math.min(KEPT, w), ky = Math.min(KEPT, h);
            float nx = Math.max(kx - w, Math.min(pageW - kx, x)), ny = Math.max(ky - h, Math.min(pageH - ky, y));
            return nx == x && ny == y ? this : new Frame(nx, ny, w, h, pageW, pageH);
        }
        /** Whether page point {@code px, py} takes the corner handle (its lower right). */
        boolean onHandle(float px, float py) { return Math.hypot(px - (x + w), py - (y + h)) <= REACH; }
        /** {left, top, right, bottom} rounded, the whole of it (perhaps past the page). */
        int[] rect() { return new int[]{Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h)}; }
        /** {@link #rect} with the handle and its outline, so a move puts back all it drew. */
        int[] drawn() {
            int[] r = rect();
            int out = Math.round(HANDLE / 2) + 4;
            return new int[]{r[0] - 4, r[1] - 4, r[2] + out, r[3] + out};
        }
        /** The handle's square, centred on its lower right, {left, top, right, bottom}. */
        int[] handle() {
            int[] r = rect();
            int half = Math.round(HANDLE / 2);
            return new int[]{r[2] - half, r[3] - half, r[2] + half, r[3] + half};
        }
    }

    /** The placing bar along the page's foot ({@code PicturePlacer}): its height and its buttons', dp. */
    static final int BAR = 64, BUTTON = 44;

    /**
     * A picture being placed as the pen and fingers move it ({@code PicturePlacer}'s, the browser's alike), page px: a drag
     * moves it, a drag from the handle resizes it, a second pointer makes it a pinch, its shape always kept. One gesture
     * at a time; once a pinch's pointer lifts, nothing moves it until every pointer has.
     */
    static final class Placing {
        Frame frame;
        private enum Gesture { NONE, MOVE, CORNER, PINCH, LIFTING }
        private Gesture gesture = Gesture.NONE;
        /** The frame as the gesture found it, and where its pointers landed: the first's (the handle's offset), the pinch's middle and spread. */
        private Frame start;
        private float downX, downY, midX, midY, spread;

        /** Its dials' steps, {@link #SHADES} at most each way: Lighter (more than 0) or Darker, More or Less Contrast; its greys {@link #toned} so as it shows and is laid. */
        private final int[] dials = new int[STEPS.length / 2];

        Placing(Frame frame) { this.frame = frame; }
        int shade() { return dials[0]; }
        int contrast() { return dials[1]; }
        /** Whether button {@code step} of {@link #STEPS} has a step left. */
        boolean canStep(int step) { return Math.abs(dials[step / 2] + by(step)) <= SHADES; }
        /** Button {@code step} of {@link #STEPS}: its dial a step up or down, if one is left; whether it took it. */
        boolean step(int step) {
            if (!canStep(step)) return false;
            dials[step / 2] += by(step);
            return true;
        }
        private static int by(int step) { return step % 2 == 0 ? 1 : -1; }
        /** Whether a pointer is moving it. */
        boolean dragging() { return gesture != Gesture.NONE && gesture != Gesture.LIFTING; }
        /** Whether a pinch moves it: the second pointer counts. */
        boolean pinching() { return gesture == Gesture.PINCH; }
        /** The first pointer down at {@code x, y}: from the handle it resizes, anywhere else it moves. */
        void down(float x, float y) {
            start = frame; downX = x; downY = y;
            gesture = frame.onHandle(x, y) ? Gesture.CORNER : Gesture.MOVE;
            if (gesture == Gesture.CORNER) { downX -= frame.x + frame.w; downY -= frame.y + frame.h; }
        }
        /** A second pointer down: a pinch from {@code a} and {@code b} (the first's and its), if one pointer alone moves it; whether it is. */
        boolean pinch(float ax, float ay, float bx, float by) {
            if (gesture != Gesture.MOVE && gesture != Gesture.CORNER) return false;
            start = frame; gesture = Gesture.PINCH;
            midX = (ax + bx) / 2; midY = (ay + by) / 2; spread = Math.max(1, (float) Math.hypot(ax - bx, ay - by));
            return true;
        }
        /** The pointers at {@code a} (the first) and {@code b} (the second, for a pinch): the frame follows; whether it changed. */
        boolean moved(float ax, float ay, float bx, float by) {
            Frame next = frame;
            if (gesture == Gesture.MOVE) next = start.moved(ax - downX, ay - downY);
            else if (gesture == Gesture.CORNER) next = start.cornerTo(ax + downX, ay + downY);
            else if (gesture == Gesture.PINCH) {
                float mx = (ax + bx) / 2, my = (ay + by) / 2, d = (float) Math.hypot(ax - bx, ay - by);
                next = start.scaled(d / spread, midX, midY, mx - midX, my - midY);
            }
            if (next == frame) return false;
            frame = next;
            return true;
        }
        /** Nothing moves it until every pointer lifts (a pinch's pointer lifted, or a pointer lost). */
        void lifting() { gesture = Gesture.LIFTING; }
        /** Every pointer up. */
        void up() { gesture = Gesture.NONE; }
    }

    // A PDF's notebook

    /** The most PDF pages a notebook holds: two layers a page in its {@link DrawingBook#MAX_PAGES} (124 of 250), one to spare for a page the + adds. */
    static final int MOST_PDF_PAGES = (DrawingBook.MAX_PAGES - 1) / 2;
    /** How many of a PDF's {@code count} pages are imported. */
    static int pdfPages(int count) { return Math.max(0, Math.min(count, MOST_PDF_PAGES)); }
    /** Room left in the 200 MB ({@link DrawingBook#MAX_PACKED_BYTES}) for the files' own records and a page the + adds. */
    static final long SPARE = 1 << 20;
    /** Whether a page pair of {@code pair} packed bytes fits after {@code packed} bytes already taken. */
    static boolean fits(long packed, long pair) { return packed + pair + SPARE <= DrawingBook.MAX_PACKED_BYTES; }
    /** Tracing paper for {@code pdfPages} pages of two layers each: every other page stacked on the one below ({@link TracingPaper#LINKED}). */
    static TracingPaper stacks(int pdfPages) {
        int[] steps = new int[2 * pdfPages];
        for (int i = 1; i < steps.length; i += 2) steps[i] = TracingPaper.LINKED;
        return new TracingPaper(TracingPaper.PLAIN, -1, steps);
    }
    /** The page a PDF's notebook opens on: its first page's clear layer. */
    static final int OPENS_ON = 1;
    /**
     * A PDF's page as its notebook's bottom layer: {@code greys} ({@link #greys}, {@code width} a row) laid on a blank
     * page and locked, with no Undo history: no marks, no erasing, no Undo there.
     */
    static ToneDocument pdfPage(int width, int height, int[] greys) {
        ToneDocument drawn = new ToneDocument(width, height);
        lay(drawn, 0, 0, width, height, greys);
        ToneDocument.Snapshot laid = drawn.layerSnapshot();
        ToneDocument page = new ToneDocument(laid);
        laid.release();
        page.placeClock(PageClock.NONE.lock(0));
        return page;
    }
    /**
     * Writes a PDF's notebook to {@code out}, as a notebook's file ({@link BookCodec}): {@code pdf}, its PDF pages packed
     * ({@link #pdfPage}), each with {@code clear} (a blank page packed, {@link BlankPages#made}) stacked on it
     * ({@link #stacks}), opening on the first clear layer, in covers of {@code design}, made {@code day} (an epoch day).
     * The pages may lie in a file ({@link PackedPage#in}): they are copied from it as they are.
     */
    static void writeBook(java.io.OutputStream out, int width, int height, java.util.List<PackedPage> pdf, PackedPage clear, CoverDesign design, long day) throws java.io.IOException {
        if (pdf.isEmpty() || pdf.size() > MOST_PDF_PAGES) throw new java.io.IOException("Invalid page list");
        java.util.List<PackedPage> pages = new java.util.ArrayList<>();
        for (PackedPage p : pdf) { pages.add(p); pages.add(clear); }
        BookOptions.Covers covers = new BookOptions.Covers();
        covers.design = design; covers.options = NotebookOptions.NEW; covers.tracing = stacks(pdf.size()); covers.lastDay = day;
        java.util.List<byte[]> pictures = new java.util.ArrayList<>();
        for (int i = 0; i < pages.size(); i++) pictures.add(null);
        BookCodec.write(out, width, height, pages, OPENS_ON, new int[pages.size()], -1, pictures, covers, new long[2]);
    }
}
