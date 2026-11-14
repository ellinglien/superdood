package io.github.mpdairy.monopaint;

import java.util.List;

/** The Fridge ({@link Fridge}, 2026-11-06): its requests, its answers read, the cards kept; no network. */
public final class FridgeChecks {
    public static void main(String[] args) {
        requests(); size(); answers(); kept(); words(); prompts(); named(); replaced();
        System.out.println("PASS: The Fridge: the send's address names its word, encodes and cuts its name and caption; confirm's email and answers read; the page goes at Page up to 1600 wide, else Social, under 3 MB; answers read with the server's words as notes; cards kept and taken down by id; a name required, the tablet's, read from api/confirmed and set by api/name; a page sent again updates its card (replace=<id>), sent as new when that card is gone");
    }
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    static void requests() {
        check(Fridge.sendUrl("", "", "  ", null).equals("https://superdood.org/fridge/api/send"), "No word, name or caption: the bare address");
        check(Fridge.sendUrl("kite", "Ann & Bo", "A cat, a hat?", null).equals("https://superdood.org/fridge/api/send?word=kite&name=Ann%20%26%20Bo&caption=A%20cat%2C%20a%20hat%3F"), "Spaces as %20, & , ? encoded");
        check(Fridge.sendUrl("kite", "Åsa", "", null).endsWith("?word=kite&name=%C3%85sa"), "UTF-8");
        String long_ = new String(new char[50]).replace('\0', 'x');
        check(Fridge.cut(long_, Fridge.NAME_MOST).length() == 40 && Fridge.cut(" a \n b ", 40).equals("a b"), "Cut to 40, one line, trimmed");
        String emoji = new String(new char[130]).replace("\0", "😀");
        String cut = Fridge.cut(emoji, Fridge.CAPTION_MOST);
        check(cut.codePointCount(0, cut.length()) == 120 && !Character.isHighSurrogate(cut.charAt(cut.length() - 1)), "Cut by characters, never half of one");
        Fridge.Card card = new Fridge.Card("ab12", "s3cr\\et", Fridge.SITE + "/ab12", "Harbour", 5, 20398);
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
            Fridge.Card c = Fridge.sent(new Fridge.Answer(200, "{\"id\": \"k3x9\", \"del\":\"d\\u0065l\", \"url\":\"https://superdood.org/fridge/k3x9\"}"), "kite", "Harbour", 5, 20398);
            check(c.id.equals("k3x9") && c.del.equals("del") && c.url.equals("https://superdood.org/fridge/k3x9"), "A send's answer read");
            Fridge.Card odd = Fridge.sent(new Fridge.Answer(201, "{\"url\":\"https://evil.example/x\",\"id\":\"k3x9\",\"del\":\"d\"}"), "kite", "H", 1, 0);
            check(odd.url.equals(Fridge.SITE + "/k3x9"), "A link off superdood.org's fridge never shown: the card's own");
            check(Fridge.takenDown(new Fridge.Answer(200, "{}")) && Fridge.takenDown(new Fridge.Answer(404, "")), "Taken down, or already gone");
        } catch (Fridge.Failed e) { throw new AssertionError("refused: " + e.getMessage()); }
        refused(() -> Fridge.sent(new Fridge.Answer(429, "{\"error\":\"Try again later\"}"), "kite", "H", 1, 0), "Try again later", 429);
        refused(() -> Fridge.sent(new Fridge.Answer(500, "<html>"), "kite", "H", 1, 0), Fridge.FAILED, 500);
        refused(() -> Fridge.sent(new Fridge.Answer(200, "{\"id\":\"../x\",\"del\":\"d\"}"), "kite", "H", 1, 0), Fridge.FAILED, 200);
        refused(() -> Fridge.sent(new Fridge.Answer(200, "{\"id\":\"k\"}"), "kite", "H", 1, 0), Fridge.FAILED, 200);
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
        Fridge.Card a = new Fridge.Card("a1", "d 1\n", Fridge.SITE + "/a1", "Harbour, Cats & Dogs", 5, 20398);
        Fridge.Card b = new Fridge.Card("b2", "d2", Fridge.SITE + "/b2", "", 12, 20399);
        String kept = Fridge.added(Fridge.added("", a), b);
        List<Fridge.Card> cards = Fridge.cards(kept);
        check(cards.size() == 2 && cards.get(0).id.equals("b2") && cards.get(1).id.equals("a1"), "Newest first");
        Fridge.Card back = cards.get(1);
        check(back.del.equals("d 1\n") && back.notebook.equals("Harbour, Cats & Dogs") && back.page == 5 && back.day == 20398, "Every field kept as it was, spaces and line ends too");
        List<Fridge.Card> old = Fridge.cards("a1 d1 https%3A%2F%2Fsuperdood.org%2Ffridge%2Fa1 cat-pics 1 Harbour 5 20398\n");
        check(old.size() == 1 && old.get(0).notebook.equals("Harbour") && old.get(0).page == 5 && old.get(0).url.equals(Fridge.SITE + "/a1"), "A line 1.2.5 kept (My Fridge's slug and the door's tick) still read");
        check(back.label().equals("Harbour, Cats & Dogs, page 5, Nov 6, 2025") && cards.get(0).label().equals("page 12, Nov 7, 2025"), "The Sent list's line: " + back.label());
        List<Fridge.Card> left = Fridge.cards(Fridge.removed(kept, "a1"));
        check(left.size() == 1 && left.get(0).id.equals("b2"), "Taken down: forgotten");
        check(Fridge.cards(kept + "junk line\n" + "x y\n").size() == 2 && Fridge.cards(null).isEmpty() && Fridge.cards("").isEmpty(), "A line that can't be read left out");
    }

    static void words() {
        for (String button : new String[]{Fridge.SEND, Fridge.CANCEL, Fridge.NOT_NOW, Fridge.CLOSE, Fridge.COPY, Fridge.SENT_PAGES, Fridge.TAKE_DOWN})
            check(button.split(" ").length <= 2 && button.matches("[A-Z][a-z]*( [A-Z][a-z]*)?"), button + ": a button, Title Case, two words at most");
        for (String note : new String[]{Fridge.FAILED, Fridge.TOO_LARGE, Fridge.TAKEN_DOWN, Fridge.DOWN_FAILED, Fridge.COPIED})
            check(note.length() <= 30 && note.split(" ").length <= 4, note + ": a short note");
    }

    static void prompts() {
        check(Fridge.sendUrl("lantern", "Ann", "Porch & tea", null).equals(Fridge.SITE + "/api/send?word=lantern&name=Ann&caption=Porch%20%26%20tea"), "A send names its word: " + Fridge.sendUrl("lantern", "Ann", "Porch & tea", null));
        check(Fridge.sendUrl("lantern", "", "", null).equals(Fridge.SITE + "/api/send?word=lantern"), "Nothing empty in the address");
        check(Fridge.sendUrl("", "Ann", "Porch", null).equals(Fridge.SEND_API + "?name=Ann&caption=Porch") && Fridge.sendUrl(null, "", "", null).equals(Fridge.SEND_API), "A post with no word: no word= (2026-11-13)");
        check(Fridge.confirmBody(" Ann@Example.org ").equals("{\"email\":\"Ann@Example.org\"}"), "The email, trimmed, as JSON");
        check(Fridge.confirmBody("a\"b@x.org").equals("{\"email\":\"a\\\"b@x.org\"}"), "Escaped");
        check(Fridge.emailish("ann@example.org") && !Fridge.emailish("ann") && !Fridge.emailish("ann@x") && !Fridge.emailish(" @x.org"), "An email, roughly, before asking the server");
        check("k123".equals(Fridge.field("{\"key\":\"k123\"}", "key")) && Fridge.field("{\"ok\":true}", "key") == null, "A new tablet's key, read");
        check(Fridge.isTrue("{\"confirmed\":true}", "confirmed") && !Fridge.isTrue("{\"confirmed\":false}", "confirmed"), "Confirmed, read");
        try {
            String key = "AbCdEfGh_-0123456789";
            check(Fridge.tablet(new Fridge.Answer(202, "{\"key\":\"" + key + "\"}"), null).equals(key) && Fridge.tablet(new Fridge.Answer(202, "{\"ok\":true}"), "mine-already-123").equals("mine-already-123"),
                    "Confirm's answer: a new tablet's key, else the one asked with");
            check(Fridge.left(new Fridge.Answer(200, "{}")) && Fridge.left(new Fridge.Answer(403, "{\"error\":\"Not allowed.\"}")), "Stop Taking Part: done, or already gone");
        } catch (Fridge.Failed e) { throw new AssertionError("refused: " + e.getMessage()); }
        refused(() -> Fridge.tablet(new Fridge.Answer(202, "{\"ok\":true}"), null), Fridge.FAILED, 202);
        refused(() -> Fridge.tablet(new Fridge.Answer(429, "{\"error\":\"Too many at once. Try again later.\"}"), null), "Too many at once. Try again later.", 429);
        refused(() -> Fridge.left(new Fridge.Answer(500, "")), Fridge.UNREACHABLE, 500);
        refused(() -> Fridge.sent(new Fridge.Answer(409, ""), "kite", "H", 1, 0), Prompts.CLOSED, 409);
        refused(() -> Fridge.sent(new Fridge.Answer(429, "{\"error\":\"Too many today. Try again tomorrow.\"}"), null, "H", 1, 0), "Too many today. Try again tomorrow.", 429);
        check(Fridge.unconfirmed(new Fridge.Answer(403, "{\"error\":\"Confirm your email first.\"}")) && !Fridge.unconfirmed(new Fridge.Answer(403, "{\"error\":\"Not allowed.\"}")), "Back to the email step only for an unconfirmed email");
        check(Fridge.sentNote(new Fridge.Answer(201, "{\"status\":\"pending\"}"), false).equals(Fridge.WAITING) && Fridge.sentNote(new Fridge.Answer(201, "{\"status\":\"approved\"}"), false).equals(Fridge.ON_FRIDGE), "Held for a look, or on the Fridge");
        // The same word sent again: the server replaced its card, so the list does too; other words and old cards stay.
        Fridge.Card one = new Fridge.Card("a1", "d1", Fridge.SITE + "/a1", "#inktober52", 2, 20398, "kite"), two = new Fridge.Card("b2", "d2", Fridge.SITE + "/b2", "#inktober52", 3, 20399, "lantern"),
                old = new Fridge.Card("c3", "d3", Fridge.SITE + "/c3", "Harbour", 1, 20390), again = new Fridge.Card("d4", "d4", Fridge.SITE + "/d4", "#inktober52", 2, 20400, "kite");
        List<Fridge.Card> list = Fridge.cards(Fridge.added(Fridge.added(Fridge.added(Fridge.added("", old), one), two), again));
        check(list.size() == 3 && list.get(0).id.equals("d4") && list.get(0).word.equals("kite") && list.get(1).id.equals("b2") && list.get(2).id.equals("c3") && list.get(2).word.isEmpty(),
                "A word's card replaced by its next send");
        try {
            Fridge.Card free = Fridge.sent(new Fridge.Answer(201, "{\"id\":\"e5\",\"del\":\"d5\",\"status\":\"approved\"}"), null, "Harbour", 1, 20401);
            Fridge.Card next = new Fridge.Card("f6", "d6", Fridge.SITE + "/f6", "Harbour", 1, 20402);
            check(free.word.isEmpty() && Fridge.cards(Fridge.added(Fridge.added("", free), next)).size() == 2 && Fridge.cards(Fridge.added(Fridge.added("", free), free)).size() == 1,
                    "A post with no word adds a card for each new id; its own id again (a replace) updates it");
        } catch (Fridge.Failed e) { throw new AssertionError("refused: " + e.getMessage()); }
    }

    static void named() {
        check(Fridge.NAME.equals("Name") && Fridge.nameBody(" Ann \n Bo ").equals("{\"name\":\"Ann Bo\"}"), "Name, required; Fridge Name's request, one line");
        check(Fridge.confirmed(new Fridge.Answer(200, "{\"confirmed\":true,\"name\":\"Ann\"}")) && !Fridge.confirmed(new Fridge.Answer(403, "{\"confirmed\":true}")), "Confirmed, read from its answer");
        check(Fridge.nameOf(new Fridge.Answer(200, "{\"confirmed\":true,\"name\":\"Ann\"}")).equals("Ann") && Fridge.nameOf(new Fridge.Answer(200, "{\"confirmed\":false,\"name\":\"\"}")).isEmpty()
                && Fridge.nameOf(new Fridge.Answer(500, "{\"name\":\"x\"}")).isEmpty(), "The tablet's name from api/confirmed, \"\" when none");
        try {
            check(Fridge.named(new Fridge.Answer(200, "{\"name\":\"Ann B\"}"), "Ann").equals("Ann B") && Fridge.named(new Fridge.Answer(200, "{}"), " Ann ").equals("Ann"), "Fridge Name taken: the server's, else the one sent");
        } catch (Fridge.Failed e) { throw new AssertionError("refused: " + e.getMessage()); }
        refused(() -> Fridge.named(new Fridge.Answer(400, "{\"error\":\"Please choose another name.\"}"), "x"), "Please choose another name.", 400);
        refused(() -> Fridge.named(new Fridge.Answer(502, "<html>"), "x"), Fridge.UNREACHABLE, 502);
        refused(() -> Fridge.sent(new Fridge.Answer(400, "{\"error\":\"Add a name.\"}"), null, "H", 1, 0), Fridge.ADD_NAME, 400);
    }

    static void replaced() {
        check(Fridge.sendUrl("kite", "Ann", "Hi", "k3x9ab").equals(Fridge.SEND_API + "?replace=k3x9ab&name=Ann&caption=Hi") && Fridge.sendUrl(null, "Ann", "", "e5").equals(Fridge.SEND_API + "?replace=e5&name=Ann"),
                "A replace names its card, not its word (the server keeps the card's)");
        Fridge.Card word = new Fridge.Card("a1", "d1", Fridge.SITE + "/a1", "#inktober52", 2, 20398, "kite"), free = new Fridge.Card("b2", "d2", Fridge.SITE + "/b2", "Harbour", 5, 20399),
                other = new Fridge.Card("c3", "d3", Fridge.SITE + "/c3", "Harbour", 6, 20400);
        String kept = Fridge.added(Fridge.added(Fridge.added("", word), free), other);
        check(Fridge.replaces(kept, null, "Harbour", 5).id.equals("b2") && Fridge.replaces(kept, "", "Harbour", 7) == null && Fridge.replaces(kept, "", "Docks", 5) == null, "Free posts: the same notebook and page, nothing else");
        check(Fridge.replaces(kept, "kite", "#inktober52", 9).id.equals("a1") && Fridge.replaces(kept, "lantern", "#inktober52", 2) == null && Fridge.replaces(kept, null, "#inktober52", 2) == null, "A word's post: its word's card, whatever the page; a free post never a word's card");
        try {
            Fridge.Card again = Fridge.sent(new Fridge.Answer(200, "{\"id\":\"b2\",\"url\":\"https://superdood.org/fridge/b2\",\"status\":\"approved\"}"), null, "Harbour", 5, 20410, free);
            check(again.id.equals("b2") && again.del.equals("d2") && again.day == 20410, "A replace's answer (no del): the stored take-down secret kept, its day the new one");
            List<Fridge.Card> list = Fridge.cards(Fridge.added(kept, again));
            check(list.size() == 3 && list.get(1).id.equals("b2") && list.get(1).day == 20410, "Updated in its place, not a new card");
            refused(() -> Fridge.sent(new Fridge.Answer(200, "{\"id\":\"zz\"}"), null, "Harbour", 5, 0, free), Fridge.FAILED, 200);
        } catch (Fridge.Failed e) { throw new AssertionError("refused: " + e.getMessage()); }
        check(Fridge.replacedGone(new Fridge.Answer(404, "{\"error\":\"Not found.\"}")) && !Fridge.replacedGone(new Fridge.Answer(403, "")), "Gone (404): sent as new; another tablet's (403): refused");
        check(Fridge.sentNote(new Fridge.Answer(200, "{\"status\":\"approved\"}"), true).equals(Fridge.UPDATED) && Fridge.sentNote(new Fridge.Answer(200, "{\"status\":\"pending\"}"), true).equals(Fridge.WAITING), "Updated on the Fridge., or waiting for a look");
        for (String note : new String[]{Fridge.UPDATED, Fridge.ADD_NAME}) check(note.matches("[A-Z][A-Za-z ]+\\.") && note.length() <= 30, note + ": a short sentence, its full stop");
    }
}
