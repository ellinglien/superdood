package io.github.mpdairy.monopaint;

public final class PromptsChecks {
    public static void main(String[] args) {
        band(); words(); keys(); adding(); sending();
        System.out.println("PASS: Prompts: a prompt page's band is a template (prompt:<slug>|<number>|<word>|<week>), its word and its number and week lettered across the page's top in ink as guides, no box, nothing below it; never offered as a choice");
    }
    static void band() {
        NotebookTemplate t = NotebookTemplate.of("prompt:lantern|45|lantern|Nov 10–16");
        check(t.prompt() && t.label.equals("lantern") && !t.own() && !t.random(), "A prompt key is a prompt template, named by its word");
        check(!NotebookTemplate.choices().contains(t) && !java.util.Arrays.asList(NotebookTemplate.values()).contains(t), "Never offered as a choice");
        check(!NotebookTemplate.of("prompt:").prompt() && NotebookTemplate.of("prompt:") == NotebookTemplate.BLANK, "A broken key is Blank");
        java.util.List<String> lettered = new java.util.ArrayList<>();
        NotebookCovers.Lettering was = NotebookCovers.lettering;
        NotebookCovers.lettering = (tones, w, h, label) -> { for (NotebookCovers.Words words : label.words) lettered.add(words.text); tones[label.y * w + label.x] = 0; };
        try {
            int w = 1404, h = 1872;
            byte[] g = t.shared(w, h);
            check(g != null && g.length == w * h, "It draws guides");
            int ink = 0, below = 0, bandBottom = Math.round(h * .02f) + Math.round(h * .055f) + 8;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if ((g[y * w + x] & 255) != NotebookTemplate.NONE) { if (y < bandBottom) ink++; else below++; }
            check(ink > 0 && below == 0, "Its band is at the top, and nothing below it");
            check(lettered.equals(java.util.Arrays.asList("lantern", "#inktober52 no. 45 · Nov 10–16")), "Its word, then its number and week, lettered: " + lettered);
        } finally { NotebookCovers.lettering = was; }
        check(NotebookTemplate.of("prompt:kite||kite|Oct 27–Nov 2").prompt(), "A word without a number is a prompt too");
        DrawingBook book = new DrawingBook(new ToneDocument(64, 64));
        book.setPageTemplate(0, NotebookTemplate.of("prompt:lantern|45|lantern|Nov 10–16"));
        book.setPageTemplate(0, NotebookTemplate.LINED);
        check(book.template(0).prompt(), "A prompt page keeps its band: no other template replaces it");
    }
    static final String JSON = "{\"words\":[{\"word\":\"lantern\",\"number\":45,\"slug\":\"lantern\",\"starts\":1762745400000,\"ends\":null},"
            + "{\"word\":\"Ça \\\"va\\\"|ok\",\"number\":null,\"slug\":\"ca-va-ok\",\"starts\":1762140600000,\"ends\":1762745400000}]}";
    static void words() {
        java.util.List<Prompts.Word> ws = Prompts.words(JSON);
        check(ws.size() == 2, "Both open words read");
        Prompts.Word a = ws.get(0), b = ws.get(1);
        check(a.slug.equals("lantern") && a.word.equals("lantern") && a.number == 45 && a.starts == 1762745400000L && a.ends == 0, "The current word");
        check(b.word.equals("Ça \"va\"|ok") && b.number == -1 && b.ends == 1762745400000L, "Escapes, no number, an end");
        check(Prompts.words("").isEmpty() && Prompts.words("{\"words\":[]}").isEmpty() && Prompts.words("<html>").isEmpty(), "Nothing, or not JSON: no words");
        check(Prompts.words("{\"words\":[{\"word\":\"x\",\"slug\":\"BAD SLUG\",\"starts\":1}]}").isEmpty(), "A slug that isn't one is skipped");
    }
    static void keys() {
        Prompts.Word a = Prompts.words(JSON).get(0), b = Prompts.words(JSON).get(1);
        check(Prompts.week(1762745400000L, 0).equals("Nov 10–16"), "The week, St. John's time: " + Prompts.week(1762745400000L, 0));
        check(Prompts.week(1762140600000L, 1762745400000L).equals("Nov 3–9"), "The week until the next word: " + Prompts.week(1762140600000L, 1762745400000L));
        check(Prompts.key(a).equals("prompt:lantern|45|lantern|Nov 10–16"), "A page's key: " + Prompts.key(a));
        check(Prompts.key(b).equals("prompt:ca-va-ok||Ça \"va\" ok|Nov 3–9"), "A | in the word becomes a space: " + Prompts.key(b));
        check(NotebookTemplate.of(Prompts.key(b)).prompt(), "The key reads back as a band");
        check("lantern".equals(Prompts.slug("=" + Prompts.key(a))) && "lantern".equals(Prompts.slug(Prompts.key(a))), "A page's word, from its own template key");
        check(Prompts.slug(null) == null && Prompts.slug("=lined") == null && Prompts.slug("lined") == null, "Not a prompt page: no word");
    }
    static void adding() { try { added(); } catch (java.io.IOException e) { throw new AssertionError(e); } }
    static void added() throws java.io.IOException {
        java.util.List<Prompts.Word> open = Prompts.words(JSON);
        java.util.List<String> keys = java.util.Arrays.asList(null, "=lined");
        java.util.List<Prompts.Word> add = Prompts.toAdd(keys, open);
        check(add.size() == 2 && add.get(0).slug.equals("ca-va-ok") && add.get(1).slug.equals("lantern"), "Both, oldest first");
        keys = java.util.Arrays.asList("=" + Prompts.key(open.get(1)), null);
        add = Prompts.toAdd(keys, open);
        check(add.size() == 1 && add.get(0).slug.equals("lantern"), "Only the word with no page");
        keys = java.util.Arrays.asList("=" + Prompts.key(open.get(0)), "=" + Prompts.key(open.get(1)));
        check(Prompts.toAdd(keys, open).isEmpty(), "Never twice");
        check(Prompts.open("lantern", open) && !Prompts.open("kite", open) && !Prompts.open(null, open), "Open words take entries; others are closed");
        DrawingBook book = DrawingBook.fresh(new ToneDocument(64, 64));
        check(Prompts.append(book, open) && book.count() == 3 && book.index() == 2, "A page for each word at the end, the newest drawn on");
        check(!book.template(0).prompt() && "ca-va-ok".equals(Prompts.slug(book.pageTemplates().get(1))) && "lantern".equals(Prompts.slug(book.pageTemplates().get(2))), "The first page stays blank; each added page has its word, oldest first");
        check(Prompts.append(book, open) && book.count() == 3 && Prompts.toAdd(book.pageTemplates(), open).isEmpty(), "Added once");
        DrawingBook full = DrawingBook.fresh(new ToneDocument(8, 8));
        while (full.count() < DrawingBook.MAX_PAGES - 1) full.addPage();
        check(!Prompts.append(full, open) && full.count() == DrawingBook.MAX_PAGES, "A full notebook takes what fits, and says so");
        check(DrawingFiles.validName(Prompts.NOTEBOOK) && DrawingNames.fits(Prompts.NOTEBOOK), "The notebook's name is a file name that fits the shelf");
    }
    static void sending() {
        java.util.List<Prompts.Word> open = Prompts.words(JSON);
        String lantern = "=" + Prompts.key(open.get(0)), kite = "=prompt:kite||kite|Oct 27–Nov 2";
        check(lantern.equals(Prompts.wordKey(true, true, lantern)), "A prompt page of the Prompts notebook, on, posts under its word");
        check(Prompts.wordKey(false, true, lantern) == null && Prompts.wordKey(true, false, lantern) == null && Prompts.wordKey(true, true, "=lined") == null && Prompts.wordKey(true, true, null) == null,
                "Outside the Prompts notebook, while off, or off a prompt page: a post with no word");
        check(Prompts.sendRow(null, open) == null && Prompts.sendRow(null, null) == null, "The row live on any other page (2026-11-13)");
        check(Prompts.sendRow(lantern, open) == null && Prompts.sendRow(kite, null) == null, "Live on an open word's page, and before any fetch (the server answers)");
        check(Prompts.CLOSED.equals(Prompts.sendRow(kite, open)), "Greyed on a closed word's page");
        // The current word (Nov 10–16) takes pages to the end of the next week; the one before to a week after its own.
        check(Prompts.about("=" + Prompts.key(open.get(0)), open).equals("no. 45 · open till Nov 23"), Prompts.about("=" + Prompts.key(open.get(0)), open));
        check(Prompts.about(Prompts.key(open.get(1)), open).equals("open till Nov 16"), Prompts.about(Prompts.key(open.get(1)), open));
        check(Prompts.about(Prompts.key(open.get(0)), null).equals("no. 45") && Prompts.about("=lined", open).isEmpty(), "Unknown parts left out");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
