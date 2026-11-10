package io.github.mpdairy.monopaint;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/** Read Handwriting: a notebook's transcript written and read back, and search over it. */
public final class TranscriptChecks {
    public static void main(String[] args) throws Exception {
        Transcript t = new Transcript();
        t.put(0xABCL, Arrays.asList(
            new Transcript.Line(new float[]{10, 20, 300, 60}, new String[]{"When I was a the bed I would", "When I was a the kid I would", "When I was a thee kid I would"}),
            new Transcript.Line(new float[]{10, 80, 300, 120}, new String[]{"Café\tnoir", "", ""})));
        t.put(0xDEFL, Arrays.asList(new Transcript.Line(new float[]{0, 0, 5, 5}, new String[]{"potatoes", "", ""})));
        File f = File.createTempFile("transcript", ".txt");
        t.write(f);
        Transcript back = Transcript.read(f);
        check(back.pages().size() == 2, "two pages back");
        check(back.lines(0xABCL).get(1).guesses[0].equals("Café noir"), "a tab in a guess is a space: " + back.lines(0xABCL).get(1).guesses[0]);
        check(back.lines(0xABCL).get(0).box[3] == 60, "a line's box back");
        check(Transcript.read(new File(f.getPath() + ".none")).pages().isEmpty(), "no file, an empty transcript");

        check(Transcript.fold("  Café  NOIR ").equals("cafe noir"), "folding: " + Transcript.fold("  Café  NOIR "));
        List<Transcript.Line> kid = back.find(0xABCL, "kid");
        check(kid.size() == 1, "the second guess is searched too");
        check("When I was a the kid I would".equals(Transcript.match(kid.get(0), Transcript.words("KID"))), "a find shows the guess that matched: " + Transcript.match(kid.get(0), Transcript.words("KID")));
        check(back.find(0xABCL, "cafe").size() == 1, "accents and capitals ignored");
        check(back.find(0xABCL, "when would").size() == 1, "every word found, in any order");
        check(back.find(0xABCL, "astronaut").isEmpty(), "Nothing found");
        check(back.find(0x999L, "kid").isEmpty(), "a page not read finds nothing");

        back.keepOnly(java.util.Collections.singleton(0xDEFL));
        check(back.pages().size() == 1, "pages no longer in the notebook are dropped");

        File dir = java.nio.file.Files.createTempDirectory("transcripts").toFile();
        File a = Transcript.file(dir, "Notebooks/Journal 1");
        check(a.getParentFile().equals(dir) && !a.getName().contains("/"), "one file a notebook, named safely: " + a.getName());
        check(!Transcript.file(dir, "Notebooks/Journal 2").equals(a), "two notebooks, two files");
        check(!back.find(0xDEFL, "potatoes?").isEmpty() && t.find(0xABCL, "kid,").size() == 1, "punctuation in the query is ignored");
        check(t.find(0xABCL, "?").isEmpty(), "a query of only punctuation finds nothing");
        check(Transcript.fold("don\u2019t").equals(Transcript.fold("don't")), "curly and straight apostrophes fold alike");
        StringBuilder emoji = new StringBuilder();
        for (int i = 0; i < 63; i++) emoji.append("\uD83D\uDE00");
        File e1 = Transcript.file(dir, emoji + "\uD83D\uDE01"), e2 = Transcript.file(dir, emoji + "\uD83D\uDE02");
        check(e1.getName().getBytes("UTF-8").length <= 255, "a long name stays under the limit: " + e1.getName().length());
        check(!e1.equals(e2), "long paths differing in the last character get different files");
        StringBuilder kanji = new StringBuilder();
        for (int i = 0; i < 64; i++) kanji.append('\u65e5');
        check(Transcript.file(dir, kanji.toString()).getName().getBytes("UTF-8").length <= 255, "non-ASCII letters are encoded, so bytes stay under the limit");
        System.out.println("PASS: a notebook's transcript writes and reads back; search ignores case and accents and checks every guess");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
}
