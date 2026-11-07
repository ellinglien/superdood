package io.github.mpdairy.monopaint;

import java.util.Arrays;
import java.util.List;

/** The Fridge ({@link Fridge}, 2026-11-06): its requests, its answers read, the cards kept; no network. */
public final class FridgeChecks {
    public static void main(String[] args) {
        requests(); size(); answers(); kept(); words();
        System.out.println("PASS: The Fridge: the send's address encodes and cuts its name and caption, shared=1 only with My Fridge; the page goes at Page up to 1600 wide, else Social, under 3 MB; answers read with the server's words as notes; cards kept and taken down by id");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static void requests() {
        check(Fridge.sendUrl("", "  ", false, true).equals("https://superdood.org/fridge/api/send"), "No name or caption: the bare address");
        check(Fridge.sendUrl("Ann & Bo", "A cat, a hat?", false, true).equals("https://superdood.org/fridge/api/send?name=Ann%20%26%20Bo&caption=A%20cat%2C%20a%20hat%3F"), "Spaces as %20, & , ? encoded");
        check(Fridge.sendUrl("Åsa", "", false, true).endsWith("?name=%C3%85sa"), "UTF-8");
        check(Fridge.sendUrl("a", "", true, true).endsWith("?name=a&shared=1") && !Fridge.sendUrl("a", "", true, false).contains("shared")
                && !Fridge.sendUrl("a", "", false, true).contains("shared"), "shared=1 only when My Fridge and The Fridge are both ticked");
        String long_ = new String(new char[50]).replace('\0', 'x');
        check(Fridge.cut(long_, Fridge.NAME_MOST).length() == 40 && Fridge.cut(" a \n b ", 40).equals("a b"), "Cut to 40, one line, trimmed");
        String emoji = new String(new char[130]).replace("\0", "😀");
        String cut = Fridge.cut(emoji, Fridge.CAPTION_MOST);
        check(cut.codePointCount(0, cut.length()) == 120 && !Character.isHighSurrogate(cut.charAt(cut.length() - 1)), "Cut by characters, never half of one");
        check(Fridge.slug("  My Bad Drawings!! ").equals("my-bad-drawings") && Fridge.slug("ab") == null && Fridge.slug("--a--") == null
                && Fridge.slug("abcdefghijklmnopqrstuvw-xyz").equals("abcdefghijklmnopqrstuvw") && Fridge.slug("Ålesund 2026").equals("lesund-2026"), "Slugs: [a-z0-9-], 3 to 24, no dash at an end");
        check(Fridge.ownBody("cat-pics", "Cat \"Pics\"").equals("{\"slug\":\"cat-pics\",\"title\":\"Cat \\\"Pics\\\"\"}"), "My Fridge's request, quotes escaped");
        Fridge.Card card = new Fridge.Card("ab12", "s3cr\\et", Fridge.SITE + "/ab12", "", true, "Harbour", 5, 20398);
        check(Fridge.deleteBody(card).equals("{\"id\":\"ab12\",\"del\":\"s3cr\\\\et\"}"), "A take-down's request");
    }

    static void size() {
        check(Fridge.size(1785, 2387) == ExportSize.SOCIAL, "The Manta's page is wider than 1600: Social");
        int[] manta = ExportSize.SOCIAL.of(1785, 2387);
        check(manta[0] <= Fridge.WIDE_MOST && manta[0] <= Fridge.WIDTH_MOST && manta[1] <= Fridge.HEIGHT_MOST, "Social fits the server");
        // An 8-bit grey PNG that deflate can't shrink at all: a byte a pixel and a filter byte a row, plus its blocks' and chunks' headers.
        check((long) manta[0] * manta[1] + manta[1] + 64 * 1024 < Fridge.BYTES_MOST, "Social is under 3 MB whatever is drawn");
        check(Fridge.size(1404, 1872) == ExportSize.PAGE && Fridge.size(1600, 2000) == ExportSize.PAGE && Fridge.size(1500, 3000) == ExportSize.SOCIAL, "Up to 1600 wide and 2700 tall: its own size");
    }

    static void answers() {
        try {
            Fridge.Card c = Fridge.sent(new Fridge.Answer(200, "{\"id\": \"k3x9\", \"del\":\"d\\u0065l\", \"url\":\"https://superdood.org/fridge/k3x9\"}"), "cat-pics", true, "Harbour", 5, 20398);
            check(c.id.equals("k3x9") && c.del.equals("del") && c.url.equals("https://superdood.org/fridge/k3x9") && c.mine.equals("cat-pics") && c.shared, "A send's answer read");
            check(c.links().equals(Arrays.asList("https://superdood.org/fridge/u/cat-pics", "https://superdood.org/fridge/k3x9")), "My Fridge's link first");
            Fridge.Card odd = Fridge.sent(new Fridge.Answer(201, "{\"url\":\"https://evil.example/x\",\"id\":\"k3x9\",\"del\":\"d\"}"), "", true, "H", 1, 0);
            check(odd.url.equals(Fridge.SITE + "/k3x9"), "A link off superdood.org's fridge never shown: the card's own");
            String[] own = Fridge.own(new Fridge.Answer(200, "{\"slug\":\"cat-pics\",\"key\":\"K\"}"));
            check(own[0].equals("cat-pics") && own[1].equals("K"), "My Fridge's answer read");
            check(Fridge.takenDown(new Fridge.Answer(200, "{}")) && Fridge.takenDown(new Fridge.Answer(404, "")), "Taken down, or already gone");
        } catch (Fridge.Failed e) { throw new AssertionError("refused: " + e.getMessage()); }
        refused(() -> Fridge.sent(new Fridge.Answer(429, "{\"error\":\"Try again later\"}"), "", true, "H", 1, 0), "Try again later", 429);
        refused(() -> Fridge.sent(new Fridge.Answer(500, "<html>"), "", true, "H", 1, 0), Fridge.FAILED, 500);
        refused(() -> Fridge.sent(new Fridge.Answer(200, "{\"id\":\"../x\",\"del\":\"d\"}"), "", true, "H", 1, 0), Fridge.FAILED, 200);
        refused(() -> Fridge.sent(new Fridge.Answer(200, "{\"id\":\"k\"}"), "", true, "H", 1, 0), Fridge.FAILED, 200);
        refused(() -> Fridge.own(new Fridge.Answer(409, "{\"error\":\"Name taken\"}")), "Name taken", 409);
        refused(() -> Fridge.own(new Fridge.Answer(200, "{\"slug\":\"Bad Slug\",\"key\":\"K\"}")), Fridge.FAILED, 200);
        refused(() -> Fridge.takenDown(new Fridge.Answer(403, "{\"error\":\"Wrong key\"}")), "Wrong key", 403);
        refused(() -> Fridge.takenDown(new Fridge.Answer(500, "")), Fridge.DOWN_FAILED, 500);
        check(Fridge.field("{\"a\":\"x\\\"y\",\"b\":\"z\"}", "b").equals("z") && Fridge.field("{\"a\":\"x\\\"y\"}", "a").equals("x\"y") && Fridge.field("{}", "a") == null, "Fields read past escaped quotes");
    }
    interface Call { Object run() throws Fridge.Failed; }
    static void refused(Call call, String note, int code) {
        try { call.run(); } catch (Fridge.Failed e) { check(e.getMessage().equals(note) && e.code == code, "Refused " + code + " says " + note + ", not " + e.getMessage()); return; }
        throw new AssertionError("Not refused: " + note);
    }

    static void kept() {
        Fridge.Card a = new Fridge.Card("a1", "d 1\n", Fridge.SITE + "/a1", "", true, "Harbour, Cats & Dogs", 5, 20398);
        Fridge.Card b = new Fridge.Card("b2", "d2", Fridge.SITE + "/b2", "cat-pics", false, "", 12, 20399);
        String kept = Fridge.added(Fridge.added("", a), b);
        List<Fridge.Card> cards = Fridge.cards(kept);
        check(cards.size() == 2 && cards.get(0).id.equals("b2") && cards.get(1).id.equals("a1"), "Newest first");
        Fridge.Card back = cards.get(1);
        check(back.del.equals("d 1\n") && back.notebook.equals("Harbour, Cats & Dogs") && back.page == 5 && back.day == 20398 && back.shared && back.mine.isEmpty(), "Every field kept as it was, spaces and line ends too");
        check(cards.get(0).mine.equals("cat-pics") && !cards.get(0).shared, "Where it went kept");
        check(back.label().equals("Harbour, Cats & Dogs, page 5, Nov 6, 2025") && cards.get(0).label().equals("page 12, Nov 7, 2025"), "The Sent list's line: " + back.label());
        List<Fridge.Card> left = Fridge.cards(Fridge.removed(kept, "a1"));
        check(left.size() == 1 && left.get(0).id.equals("b2"), "Taken down: forgotten");
        check(Fridge.cards(kept + "junk line\n" + "x y\n").size() == 2 && Fridge.cards(null).isEmpty() && Fridge.cards("").isEmpty(), "A line that can't be read left out");
    }

    static void words() {
        for (String button : new String[]{Fridge.SEND, Fridge.CANCEL, Fridge.NOT_NOW, Fridge.CLOSE, Fridge.COPY, Fridge.SENT_PAGES, Fridge.TAKE_DOWN, Fridge.MAKE, Fridge.MINE, Fridge.SHARED})
            check(button.split(" ").length <= 2 && button.matches("[A-Z][a-z]*( [A-Z][a-z]*)?"), button + ": a button, Title Case, two words at most");
        for (String note : new String[]{Fridge.FAILED, Fridge.TOO_LARGE, Fridge.TAKEN_DOWN, Fridge.DOWN_FAILED, Fridge.COPIED, SystemSettings.FRIDGE_NOTE})
            check(note.length() <= 30 && note.split(" ").length <= 4, note + ": a short note");
        for (String s : new String[]{Fridge.ABOUT, Fridge.sentWords(true, true), Fridge.OWN_ABOUT, Fridge.OWN_SHORT})
            check(!s.matches("(?i).*\\byou(r)?\\b.*") && !s.matches(".*[→↔=;].*"), s + ": no you, no symbols as grammar");
        check(Fridge.ABOUT.contains("superdood.org/fridge") && Fridge.ABOUT.contains("approves") && Fridge.ABOUT.contains("Take it down") && Fridge.ABOUT.contains("Nothing else"),
                "The first time says where, who, how it comes down and that nothing else goes");
    }
}
