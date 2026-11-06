package io.github.mpdairy.monopaint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class BrushNamesChecks {
    public static void main(String[] args) throws IOException {
        Assets.install(Assets.folder(Assets.FOLDER));
        lists(); fresh(); kin(); edited();
        traitList(); folkList(); traits(); nonsense(); deadpan(); mold(); ties(); traitKin(); rolls(); variety(); sameAsLab();
        System.out.println("PASS: names come from two text files: adjectives (-y and -ish welcome) and one- or two-syllable produce (no kiwi); a drawing is adjective-produce; a variation keeps the family part, and an older two-word name becomes adjective-family; an edited list with a BOM or blank lines, or an empty or missing list, never breaks naming;"
                + " a rolled brush is named for what it is, in Title Case: about half a paint colour's by what it has (Harbour Fog, Burnt Toast, Rainy Harbour Fog), about a third one of 24 deadpan forms saying its part (Polite Smear, Smear of Mild Regret, Pen with Opinions, Smidge of Pencil; about one in six with a made-up word: wobblefuzz, smudgekins), the rest its part and a name (Wash McGrath), the words picked by its seed so alike brushes differ; Mold (key halo) is mold; a variation keeps the rest under another word of its first word's line, and an older drip-plum keeps drip; 2,000 rolls are as before but for the name, and draw nothing more;"
                + " 500 saves in a row never repeat a deadpan form or a word of the last ten names, all fit two lines of a card, none rude; the web lab's copy of the list has the app's trait words, and 19 app codes name as pinned");
    }
    static void lists() {
        List<String> adjectives = BrushNames.adjectives(), produce = BrushNames.produce();
        check(adjectives.size() == 33 && produce.size() == 39, "The lists load whole: " + adjectives.size() + ", " + produce.size());
        for (String a : adjectives) check(a.matches("[a-z]+"), "An adjective is one lowercase word (-y and -ish welcome, the owner, 2026-10-22): " + a);
        for (String p : produce) check(p.matches("[a-z]+"), "Produce is one lowercase word: " + p);
        check(!produce.contains("kiwi"), "No kiwi");
    }
    static void fresh() {
        Random random = new Random(1);
        for (int i = 0; i < 2000; i++) {
            String name = BrushNames.fresh(random);
            String[] words = name.split("-");
            check(words.length == 2 && BrushNames.adjectives().contains(words[0]) && BrushNames.produce().contains(words[1]), "Adjective-produce: " + name);
        }
    }
    static void kin() {
        Random random = new Random(2);
        for (int i = 0; i < 50; i++) {
            String goose = BrushNames.kin("wobbly-goose", i, random);
            check(goose.endsWith("-goose") && BrushNames.adjectives().contains(goose.substring(0, goose.indexOf('-'))), "A variation keeps the last word: " + goose);
            check(BrushNames.kin("my-ink", i, random).endsWith("-ink") && BrushNames.kin("Ink", i, random).endsWith("-ink"), "and a copied built-in's");
            String old = BrushNames.kin("Bobson Dugnutt", i, random);
            check(old.matches("[a-z]+-dugnutt") && BrushNames.adjectives().contains(old.substring(0, old.indexOf('-'))), "An older two-word name becomes adjective-family: " + old);
        }
    }
    /** Elling edits the lists by hand: whatever they hold, naming keeps working. */
    static void edited() {
        byte[] bom = "﻿bold\r\n\r\nbrave\n   \nbrisk\n".getBytes(StandardCharsets.UTF_8);
        check(Assets.lines(bom).equals(Arrays.asList("bold", "brave", "brisk")), "A text file's BOM, carriage returns and blank lines are left out: " + Assets.lines(bom));
        check(BrushNames.valid(Arrays.asList("  ", ""), BrushNames.PRODUCE_DEFAULT).equals(Arrays.asList(BrushNames.PRODUCE_DEFAULT)), "Only blank lines: the built-in one");
        check(BrushNames.valid(Arrays.asList(), BrushNames.ADJECTIVES_DEFAULT).equals(Arrays.asList(BrushNames.ADJECTIVES_DEFAULT)), "An empty list: the built-in one");
        check(BrushNames.load("names/no-such-list.txt", BrushNames.PRODUCE_DEFAULT).equals(Arrays.asList(BrushNames.PRODUCE_DEFAULT)), "A missing list: the built-in one");
        for (String[] defaults : new String[][]{BrushNames.ADJECTIVES_DEFAULT, BrushNames.PRODUCE_DEFAULT})
            for (String d : defaults) check(d.matches("[a-z]+"), "Built-in names follow the lists' rules: " + d);
        Random random = new Random(3);
        for (String odd : new String[]{"-", "", " ", "fig-", "Bobson ", "--", "drip-", "drip "}) {
            String name = BrushNames.kin(odd, 7, random);
            check(name.matches("[a-z]+-[a-z]+"), "A name with no family part gets a fresh one: '" + odd + "' to " + name);
        }
        // An edited traits list: a key, a colon and lowercase words; tabs and doubled spaces are spaces, a word that
        // isn't lowercase letters goes, a key said twice keeps its first line, a line with no colon or no word goes,
        // lines before a section are its part's, and a section left empty goes.
        Map<String, Map<String, List<String>>> t = BrushNames.traits(Arrays.asList("# first", "#", "tip.multi:\trake raked", "paper.cold  press: grainy",
                "tip.multi: comb", "nonsense", "tip.split: Split 2 forky", "tip.hake: hake-y", "[fun]", "any: brisk", "[describer]", "# nothing"));
        check(t.toString().equals("{part={tip.multi=[rake, raked], paper.cold press=[grainy], tip.split=[forky]}, fun={any=[brisk]}}"), "An edited traits list reads what it can: " + t);
        check(BrushNames.traits(Collections.<String>emptyList()).isEmpty() && BrushNames.traits(Arrays.asList("# only notes", "[part]")).isEmpty(), "An empty traits list has nothing (names stay adjective-produce)");
    }

    // ---- What it is ----

    /** Every key traits.txt may use: what {@link BrushNames#traitKeys} can give. */
    static Set<String> knownKeys() {
        Set<String> known = new HashSet<>(Arrays.asList("any", "carry.stump", "carry.comb", "carry.pload", "medium.wax", "medium.dust", "medium.scratch", "medium.screen",
                "blend.white", "blend.behind", "blend.invert", "tip.taper", "medium.bloom", "medium.backrun", "medium.bleed", "medium.splatter", "medium.impasto",
                "medium.pool", "medium.fling", "tip.flick", "tip.dry", "tip.wobble"));
        for (PartChoice c : PartChoice.values()) known.add(c.part.name().toLowerCase(java.util.Locale.ROOT) + "." + c.key);
        for (PartDial d : PartDial.GRAVITY) known.add("life." + d.key);
        return known;
    }
    /** names/traits.txt: three keyed sections of short words, every key one a brush can have, each ending in "any"; [deadpan]'s lines; [title]'s. */
    static void traitList() {
        Map<String, Map<String, List<String>>> sections = BrushNames.traits();
        check(sections.keySet().equals(new HashSet<>(Arrays.asList(BrushNames.PART, BrushNames.DESCRIBER, BrushNames.FUN, BrushNames.COLOUR, BrushNames.NOUN, BrushNames.DEADPAN, BrushNames.TITLE))), "traits.txt has its six brush sections and the notebook titles': " + sections.keySet());
        titleLists(sections);
        Map<String, List<String>> deadpan = sections.get(BrushNames.DEADPAN);
        Set<String> lines = new HashSet<>(Arrays.asList("nonsense"));
        for (String f : BrushNames.FORMS) for (java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{([a-z]+)\\}").matcher(f); m.find(); )
            if (!m.group(1).equals("part") && !m.group(1).equals("describer")) lines.add(m.group(1));
        check(deadpan.keySet().equals(lines) && BrushNames.FORMS.length == 24, "[deadpan] has a line for each word the 24 forms take, and nonsense: " + deadpan.keySet() + " " + lines);
        // A form is known by its words (BrushNames.form), so no word is in two lines.
        Set<String> once = new HashSet<>();
        int deadpanWords = 0;
        for (List<String> line : deadpan.values()) for (String w : line) {
            check(w.matches("[a-z]{3,11}"), "A deadpan word is one short lowercase word: " + w);
            check(once.add(w), "A deadpan word in one line only: " + w);
            deadpanWords++;
        }
        check(deadpanWords >= 300, "Many deadpan words (54 before 2026-11-02): " + deadpanWords);
        for (String section : new String[]{BrushNames.COLOUR, BrushNames.NOUN}) for (List<String> line : sections.get(section).values()) for (String w : line)
            check(!once.contains(w), "A paint colour's word is no deadpan word, so its name is no form: " + w);
        for (Map.Entry<String, Map<String, List<String>>> s : sections.entrySet()) for (List<String> line : s.getValue().values()) for (String w : line)
            check(kind(w), "Kind words only: " + w);
        for (String end : deadpan.get("nonsense")) check(end.matches("[a-z]{3,7}"), "A nonsense ending is short: " + end);
        Set<String> known = knownKeys(), words = new HashSet<>();
        boolean y = false, ish = false;
        for (Map.Entry<String, Map<String, List<String>>> s : sections.entrySet()) {
            if (s.getKey().equals(BrushNames.DEADPAN) || BrushNames.TITLE_SECTIONS.contains(s.getKey())) continue;
            check(s.getValue().containsKey("any"), "Every brush has a word in " + s.getKey());
            for (Map.Entry<String, List<String>> e : s.getValue().entrySet()) {
                check(known.contains(e.getKey()), "A trait key a brush can have: " + e.getKey());
                for (String w : e.getValue()) {
                    check(w.matches("[a-z]{3,8}"), "A trait word is one short lowercase word: " + w);
                    y |= w.endsWith("y"); ish |= w.endsWith("ish");
                    words.add(w);
                }
            }
        }
        check(y && ish, "-y and -ish words are there (the owner, 2026-10-22: \"i like the y or ish words\")");
        check(words.containsAll(Arrays.asList("drag", "chisel", "hake", "nib", "charcoal", "inky", "dusty", "bloomy", "waxy", "sloppy", "giddy", "patient")), "The owner's words are there");
        for (List<String> line : deadpan.values()) words.addAll(line);
        check(BrushNames.traitWords().equals(words), "Every word a variation can tell");
    }

    /** Words no name has, whole or inside another (the owner: "nothing rude"). */
    static final List<String> RUDE = Arrays.asList("damn", "hell", "crap", "poop", "fart", "butt", "arse", "ass", "sexy", "drunk", "tipsy", "stupid", "dumb", "ugly", "fat", "idiot", "loser", "kill", "dead", "hate", "karen", "hickey"),
            WORST = Arrays.asList("fuck", "shit", "cunt", "cock", "dick", "piss");
    static boolean kind(String text) {
        String t = text.toLowerCase(java.util.Locale.ROOT);
        for (String r : WORST) if (t.contains(r)) return false;
        for (String w : t.split(" ")) if (RUDE.contains(w)) return false;
        return true;
    }
    /** names/folks.txt: ordinary, old-fashioned and Newfoundland names, one a line, each once, short, kind. */
    static void folkList() {
        List<String> folks = BrushNames.folks();
        check(folks.size() >= 60 && new HashSet<>(folks).size() == folks.size(), "folks.txt loads whole, each name once: " + folks.size());
        for (String f : folks) check(f.matches("(Ma?c)?[A-Z][a-z]+") && f.length() <= BrushNames.LINE_LETTERS && kind(f), "A name is one Title Case word that fits a card's line, kind: " + f);
        check(folks.containsAll(Arrays.asList("Delores", "Bartholomew", "McGrath")), "The owner's names are there");
        for (String f : BrushNames.FOLKS_DEFAULT) check(folks.contains(f), "The built-in names are the file's: " + f);
    }

    /** A new notebook's title's lists: [title]'s eight lines, short words. */
    static void titleLists(Map<String, Map<String, List<String>>> sections) {
        Map<String, List<String>> title = sections.get(BrushNames.TITLE);
        check(title.keySet().equals(new HashSet<>(Arrays.asList("things", "thing", "creature", "adj", "book", "verdict", "number", "lear"))), "[title]'s eight lines: " + title.keySet());
        for (List<String> line : title.values()) for (String w : line) check(w.matches("[a-z]{3,10}"), "A title word is one short lowercase word: " + w);
        // "a" goes before thing and creature: none starts with a vowel.
        for (String key : new String[]{"thing", "creature"}) for (String w : title.get(key)) check("aeiou".indexOf(w.charAt(0)) < 0, "A " + key + " follows \"a\": " + w);
        // The parser's own rules hold for the title's section: a section in brackets, its lines a key and words.
        Map<String, Map<String, List<String>>> parsed = BrushNames.traits(Arrays.asList("[title]", "things: lint Bad soup", "nothing here"));
        check(parsed.get("title").get("things").equals(Arrays.asList("lint", "soup")), "[title] parses as the rest: " + parsed);
        check(!BrushNames.traitWords().contains("puffin") && !BrushNames.traitWords().contains("pamphlet"), "A title's words aren't a brush's");
    }

    /** A Fineliner (round, ink, no smudge, smooth, Quick: all its usual), then {@code changes}. */
    static ModularBrush brush(String style, long seed, Object... changes) {
        ModularBrush b = new ModularBrush("dapper-plum", seed, ModularStyle.named(style));
        b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.FAST_WET));
        for (int i = 0; i < changes.length; i++) {
            if (changes[i] instanceof PartChoice) { PartChoice c = (PartChoice) changes[i]; b.set(c.part, ModularBrush.switchTo(b.part(c.part), c)); }
            else { PartDial d = (PartDial) changes[i]; b.part(d.part).set(d, ((Number) changes[++i]).doubleValue()); }
        }
        return b.normalize();
    }
    static ModularBrush rename(ModularBrush b, String name) { b.name = name; return b; }
    /** {@code b}'s describer and part are {@code want} ("inky drag"; "- drag" for any describer, "pen|liner" for either). */
    static void named(String want, ModularBrush b, String what) {
        List<String> w = BrushNames.plainWords(b);
        String[] p = want.split(" ");
        check(w.size() == 3 && Arrays.asList(p[1].split("\\|")).contains(w.get(2)) && (p[0].equals("-") || Arrays.asList(p[0].split("\\|")).contains(w.get(1))) && BrushNames.described(b, b.name).equals(BrushNames.name(b)),
                what + ": " + want + ", not " + w + " (" + BrushNames.traitKeys(b) + ")");
    }
    static void traits() {
        String f = "Fineliner";
        named("- pen|liner", brush(f, 1), "A plain Fineliner is a pen");
        check(Arrays.asList("inky", "inkish").contains(BrushNames.plainWords(brush(f, 1)).get(1)) && BrushNames.traits().get(BrushNames.FUN).get("medium.ink").contains(BrushNames.plainWords(brush(f, 1)).get(0)),
                "an inky or inkish one, and neat: " + BrushNames.words(brush(f, 1)));
        named("- nib", brush(f, 1, PartChoice.FLAT_NIB, PartChoice.NONE), "a flat nib is a nib");
        // Special tips and mediums first, then smudges, then tips, then the medium; the describer by what it does.
        named("- float", brush(f, 1, PartChoice.FLOAT), "Float");
        named("- smear|smudge", brush(f, 3, PartChoice.SMUDGE), "Smear");
        named("- stump", brush(f, 1, PartChoice.SMUDGE, PartDial.SOFTEN, .6), "a Smear at Soften .5 and over is a stump");
        named("- comb", brush(f, 1, PartChoice.MULTI_LINE, PartChoice.SMUDGE, PartDial.SOFTEN, .6), "with a Rake, a comb (ahead of the rake)");
        named("- lift", brush(f, 1, PartChoice.LIFT), "Lift");
        named("inky|inkish drag", brush(f, 2, PartChoice.DRAG), "an inky drag (the owner's)");
        named("drippy|dribbly pen|liner", brush(f, 1, PartChoice.DRYING, PartDial.DRIPS, .5), "a wash that drips");
        named("runny|trickly pen|liner", brush(f, 1, PartChoice.DRYING, PartDial.RUNS, .5), "one that runs");
        named("dusty|sooty charcoal", brush(f, 1, PartChoice.CHARCOAL, PartDial.DUST, .4), "Charcoal's dust (never stick, the owner, 2026-10-29)");
        named("dusty|sooty chisel", brush(f, 1, PartChoice.CHISEL, PartChoice.CHARCOAL, PartDial.DUST, .4), "a dusty chisel (the owner's)");
        named("- mask", brush(f, 1, PartChoice.RESIST, PartDial.ERASABLE, 1), "masking fluid");
        named("waxy wax|candle", brush(f, 1, PartChoice.RESIST, PartDial.ERASABLE, 0), "wax");
        named("- dots|stipple", brush(f, 1, PartChoice.STIPPLE), "Stipple");
        named("- hatch", brush(f, 1, PartChoice.HATCH), "Hatch");
        named("- pen|liner", brush(f, 1, PartDial.M_BLEND, 1), "Blend's White");
        check(Arrays.asList("chalky", "pale", "ghostly").contains(BrushNames.plainWords(brush(f, 1, PartDial.M_BLEND, 1)).get(1)), "a pale one");
        named("- rake", brush(f, 1, PartChoice.MULTI_LINE), "Rake");
        named("- split", brush(f, 1, PartChoice.SPLIT_NIB), "Split nib");
        named("- bristle", brush(f, 1, PartChoice.BRISTLES), "Bristle");
        named("- hake", brush(f, 1, PartChoice.HAKE), "Hake");
        named("bloomy|blossomy hake", brush(f, 1, PartChoice.HAKE, PartChoice.WASH, PartDial.BLOOM, .6), "a bloomy hake (the owner's)");
        named("- chisel", brush(f, 1, PartChoice.CHISEL), "Chisel");
        named("- float", brush(f, 1, PartChoice.MULTI_LINE, PartChoice.FLOAT), "Float comes before a tip");
        named("ribbed|ribby pen|liner", brush(f, 1, PartChoice.LAID, PartDial.TOOTH, .8), "laid paper with a strong grain");
        named("- pen|liner", brush(f, 1, PartChoice.LAID, PartDial.TOOTH, .5), "not at its usual tooth");
        named("- pencil", brush(f, 1, PartChoice.GRAPHITE), "Graphite");
        named("- wash", brush(f, 1, PartChoice.WASH), "Wash");
        named("- paint", brush(f, 1, PartChoice.COVER), "Opaque");
        // What a style always has doesn't stand out in it.
        named("- filbert", brush("Oils", 1, PartChoice.FILBERT, PartChoice.COVER, PartChoice.DRAG, PartChoice.CANVAS, PartDial.TOOTH, .9), "Oils' drag on canvas: its filbert");
        named("- hake", brush("Hake", 1, PartChoice.HAKE, PartChoice.WASH), "a Hake's hake");
        named("- pencil", brush("Pencil", 1, PartChoice.GRAPHITE), "a Pencil's graphite");
        // Never a name without a family part; off for the checks of every roll as before.
        check(BrushNames.described(rename(brush(f, 1, PartChoice.FLOAT), ""), "").isEmpty(), "no name, no new one");
        BrushNames.traitNames = false;
        try { check(BrushNames.described(brush(f, 1, PartChoice.FLOAT), "dapper-plum").equals("dapper-plum"), "trait names off"); } finally { BrushNames.traitNames = true; }
    }
    /** Made-up words (the owner, 2026-10-29: mostly deadpan, now and then an Edward Lear word): readable, short, still saying what the brush does. */
    static void nonsense() {
        String[][] stems = {{"dusty", "dust"}, {"inkish", "ink"}, {"wobbly", "wobble"}, {"drippy", "drip"}, {"shaky", "shake"}, {"ghostly", "ghost"},
                {"weavy", "weave"}, {"fuzzy", "fuzz"}, {"leaky", "leak"}, {"bloomy", "bloom"}, {"wettish", "wet"}, {"pale", null}, {"shy", null}};
        for (String[] t : stems) check(java.util.Objects.equals(BrushNames.stem(t[0]), t[1]), "A stem: " + t[0] + " " + BrushNames.stem(t[0]));
        check(BrushNames.suffixed("smudge", "ery").equals("smudgery") && BrushNames.suffixed("wobble", "fuzz").equals("wobblefuzz") && BrushNames.suffixed("drip", "ery").equals("drippery")
                && BrushNames.suffixed("drag", "y").equals("draggy") && BrushNames.suffixed("ink", "ery").equals("inkery") && BrushNames.suffixed("pen", "kins").equals("penkins"), "Endings read");
        for (List<String> plain : Arrays.asList(Arrays.asList("giddy", "dusty", "chisel"), Arrays.asList("giddy", "shy", "chisel"))) {
            int made = 0;
            Set<String> seen = new HashSet<>();
            for (int seed = 0; seed < 1000; seed++) {
                List<String> m = BrushNames.nonsense(plain, seed);
                if (m.equals(plain)) continue;
                made++;
                seen.add(String.join(" ", m));
                check(m.size() == 3 && m.get(0).equals("giddy") && (plain.get(1).equals("dusty") ? m.get(1).startsWith("dust") && m.get(2).equals("chisel") : m.get(1).equals("shy") && m.get(2).startsWith("chisel"))
                        && m.get(1).length() <= BrushNames.NONSENSE_MOST && m.get(2).length() <= BrushNames.NONSENSE_MOST, "A made-up word keeps the fun word and still says dust and chisel: " + m);
            }
            check(made > 120 && made < 220 && (seen.contains("giddy dustwhistle chisel") || seen.contains("giddy shy chiselkins")), "About one name in six has a made-up word: " + made + " " + seen);
        }
    }
    /** A name's shape: Title Case words (a McGrath too), the forms' small words between (the owner, 2026-10-29: Title Case). */
    static final String FORM = "[A-Z][a-z]+( ((Ma?c)?[A-Z][a-z]+|of|for|on|a|in|with|and)){1,3}";
    /** The forms' own words. */
    static final Set<String> FORM_WORDS = new HashSet<>(Arrays.asList("of", "for", "on", "a", "in", "with", "and"));
    /** Whether {@code name} is {@code b}'s words, plain or made up, not a deadpan form. */
    static boolean plainName(ModularBrush b, String name) {
        return name.equalsIgnoreCase(String.join(" ", BrushNames.words(b))) || name.equalsIgnoreCase(String.join(" ", BrushNames.plainWords(b)));
    }
    /** The part a name must say, as its first four letters (a made-up part ends otherwise: "smudgery"). */
    static String hint(ModularBrush b) { String p = BrushNames.plainWords(b).get(2); return p.substring(0, Math.min(4, p.length())); }
    /**
     * Deadpan names (the owner, 2026-10-29: "mostly deadpan"; "have the brush names hint at what the brush does for sure"):
     * a bloomy hake's names all say hake; most are a deadpan form; a variation of one keeps all but its first word.
     */
    static void deadpan() {
        int forms = 0;
        Set<String> seen = new HashSet<>();
        Set<Integer> kinds = new HashSet<>();
        for (int seed = 0; seed < 500; seed++) {
            ModularBrush b = brush("Fineliner", seed, PartChoice.HAKE, PartChoice.WASH, PartDial.BLOOM, .6);
            List<String> all = BrushNames.deadpan(BrushNames.words(b), BrushNames.plainWords(b), (int) b.seed ^ 0x7a175);
            check(all.size() >= 10, "A brush has many deadpan forms to try (a made-up describer: those with it): " + all);
            for (String f : all) {
                check(f.matches(FORM) && f.toLowerCase(java.util.Locale.ROOT).contains("hake") && BrushNames.form(f) >= 0, "Each deadpan form says hake: " + f);
                kinds.add(BrushNames.form(f));
            }
            String n = BrushNames.name(b);
            seen.add(n);
            check(n.length() <= Brush.NAME_MAX && BrushNames.twoLines(n) && n.matches(FORM), "A bloomy hake's name fits: " + n);
            if (BrushNames.form(n) < 0) continue;
            forms++;
            String kin = BrushNames.kin(n, seed, new Random(seed));
            // (the same name where its first word's line has no other: "Hake of Some Regret")
            check(kin.endsWith(n.substring(n.indexOf(' '))) && Character.isUpperCase(kin.charAt(0)) ||!BrushNames.traitWords().contains(n.substring(0, n.indexOf(' ')).toLowerCase(java.util.Locale.ROOT)),
                    "A deadpan name's variation keeps all but its first word: " + n + ", " + kin);
        }
        check(kinds.size() == BrushNames.FORMS.length, "Every form is made, and known again: " + kinds.size());
        check(forms > 100 && forms < 220 && seen.stream().anyMatch(x -> x.contains(" of ")) && seen.size() > 250, "About a third are deadpan forms: " + forms + ", " + seen.size() + " names");
        // A paint colour's names and a part's name are no form; its kin keeps its last word under another first word of its line.
        check(BrushNames.form("Harbour Fog") < 0 && BrushNames.form("Wash McGrath") < 0 && BrushNames.form("Polite Smear") == 0 && BrushNames.form("Sleepy Pen Junior") == 21
                && BrushNames.form("Wash for Beginners") == 5 && BrushNames.form("Inky Pen for Beginners") == 23, "Forms are known by their words");
        check(BrushNames.kin("Harbour Fog", 3, new Random(1)).matches("[A-Z][a-z]+ Fog") && !BrushNames.kin("Harbour Fog", 3, new Random(1)).equals("Harbour Fog"), "A paint colour's variation: " + BrushNames.kin("Harbour Fog", 3, new Random(1)));
        check(BrushNames.kin("Polite Smear", 3, new Random(1)).matches("[A-Z][a-z]+ Smear") && BrushNames.kin("Dusty Aunt Charcoal", 3, new Random(1)).equals("Sooty Aunt Charcoal")
                && BrushNames.kin("Smear of Mild Regret", 3, new Random(1)).equals("Smudge of Mild Regret"), "Variations: " + BrushNames.kin("Polite Smear", 3, new Random(1)));
    }
    /** A plain Fineliner at seeds 0 to 63, each name's first letter, pinned (checks.js nameChecks has the lab's older ones). */
    static final String SEEDS = "MFIIIPFPMPSFBSGLAFLTSINSMPPIPLNISMFIPRILDSILILINMMILIMTBPPMLIIIP";
    /**
     * Mold (choice key {@code halo}, shown as Mold): its word is "mold", by the key, never the label; ahead of a tip;
     * and the rolls that turn an Ink wash or Fineliner into Mold are mostly named for it.
     */
    static void mold() {
        check(PartChoice.HALO.key.equals("halo") && BrushNames.traits().get(BrushNames.PART).get("life.halo").equals(Arrays.asList("mold")), "traits.txt names Halo's key mold");
        named("- mold", brush("Fineliner", 1, PartChoice.HALO), "Mold");
        named("- mold", brush("Ink wash", 1, PartChoice.HALO, PartChoice.MULTI_LINE), "Mold ahead of a rake");
        int molds = 0, moldNamed = 0;
        for (String style : new String[]{"Ink wash", "Fineliner", ModularStyle.ANYTHING_GOES}) for (int i = 0; i < 600; i++) {
            ModularBrush b = ModularStyle.named(style).roll(new Random(9000L + i));
            if (b.mischief() != PartChoice.LATER_MOLD) continue;
            molds++;
            if (BrushNames.plainWords(b).get(2).equals("mold") && b.name.equals(BrushNames.name(b))) moldNamed++;
        }
        check(molds > 20 && moldNamed > molds / 2, "Rolled Mold brushes are mostly named for mold: " + moldNamed + " of " + molds);
    }
    /** Alike brushes differ: a plain Fineliner's seeds give many names, the same every time and in the lab. */
    static void ties() {
        StringBuilder picks = new StringBuilder();
        Set<String> names = new HashSet<>();
        for (int seed = 0; seed < 64; seed++) {
            ModularBrush b = brush("Fineliner", seed);
            String name = BrushNames.described(b, b.name);
            check(name.equals(BrushNames.described(b.copy(), b.name)), "A brush's name is its own, from its seed: " + name);
            names.add(name);
            for (String w : name.split("-")) picks.append(w.charAt(0));
        }
        check(names.size() >= 30, "Alike brushes mostly differ: " + names.size() + " names of 64");
        check(picks.toString().equals(SEEDS), "as pinned: " + picks);
    }
    /** checks.js nameChecks' too: "drip-plum"'s variations at seeds 0, 7919, 15838 … */
    static final String KIN = "drip-yam drip-garlic drip-cherry drip-pear drip-garlic drip-peach drip-carrot drip-date ";
    /** and "sloppy-inky-drag"'s. */
    static final String FUN_KIN = "loose-inky-drag gooey-inky-drag gooey-inky-drag loose-inky-drag gooey-inky-drag juicy-inky-drag lush-inky-drag loose-inky-drag ";
    /**
     * A variation keeps a name's describer and part under another fun word of its mood, and an older trait-produce name
     * its first word under a new produce, from its seed, drawing from Random as before.
     */
    static void traitKin() {
        Set<String> fruit = new HashSet<>(), fun = new HashSet<>();
        List<String> sloppy = null;
        for (List<String> line : BrushNames.traits().get(BrushNames.FUN).values()) if (line.contains("sloppy")) { sloppy = line; break; }
        for (int seed = 0; seed < 200; seed++) {
            Random a = new Random(seed), b = new Random(seed);
            String name = BrushNames.kin("drip-plum", seed * 7919L, a);
            b.nextInt(BrushNames.adjectives().size());
            String family = name.substring(name.indexOf('-') + 1);
            check(name.startsWith("drip-") && !family.equals("plum") && BrushNames.produce().contains(family) && name.equals(BrushNames.kin("drip-plum", seed * 7919L, new Random(seed))),
                    "An older name keeps the trait's word under a new produce, from its seed: " + name);
            check(a.nextInt() == b.nextInt(), "and draws from Random as it did");
            Random c = new Random(seed), d = new Random(seed);
            String kin = BrushNames.kin("sloppy-inky-drag", seed, c); d.nextInt(BrushNames.adjectives().size());
            String first = kin.substring(0, kin.indexOf('-'));
            check(kin.endsWith("-inky-drag") && !first.equals("sloppy") && sloppy.contains(first), "A variation keeps describer and part under another fun word of its mood: " + kin);
            check(c.nextInt() == d.nextInt(), "as an adjective's variation draws");
            fruit.add(family); fun.add(first);
        }
        check(fruit.size() > 20 && fun.size() == sloppy.size() - 1, "Its words vary: " + fruit.size() + " " + fun);
        StringBuilder kin = new StringBuilder(), funKin = new StringBuilder();
        for (int seed = 0; seed < 8; seed++) {
            kin.append(BrushNames.kin("drip-plum", seed * 7919L, new Random(seed))).append(' ');
            funKin.append(BrushNames.kin("sloppy-inky-drag", seed * 7919L, new Random(seed))).append(' ');
        }
        check(kin.toString().equals(KIN) && funKin.toString().equals(FUN_KIN), "as in the lab: " + kin + "/ " + funKin);
        check(BrushNames.kin("Drip Plum", 3, new Random(4)).startsWith("drip-"), "A trait's word in any case");
        ModularBrush rake = rename(brush("Fineliner", 5, PartChoice.MULTI_LINE), "tidy-inky-rake");
        for (ModularVariations.Made v : ModularVariations.make(rake, new ModularLocks(), new Random(6)))
            check(v.brush.name.endsWith("-inky-rake") && !v.brush.name.equals("tidy-inky-rake"), "Tweak it keeps the inky rake: " + v.brush.name);
    }
    /**
     * 2,000 rolls (style i mod 14, {@code new Random(4040 + i)}) with trait names on and off: the same brush and seed,
     * and the Random left where it was; on, the name is its words or a deadpan form, whole on two lines of a card, and
     * says its part (the owner, 2026-10-29: "have the brush names hint at what the brush does for sure").
     */
    static void rolls() {
        List<ModularStyle> all = ModularStyle.all();
        Set<String> names = new HashSet<>();
        List<String> examples = new ArrayList<>();
        int forms = 0, madeUp = 0, colours = 0, folks = 0;
        Set<String> folkWords = new HashSet<>();
        for (String f : BrushNames.folks()) folkWords.add(f.toLowerCase(java.util.Locale.ROOT));
        for (int i = 0; i < 2000; i++) {
            ModularStyle s = all.get(i % all.size());
            Random on = new Random(4040 + i), off = new Random(4040 + i);
            ModularBrush a = s.roll(on), b;
            BrushNames.traitNames = false;
            try { b = s.roll(off); } finally { BrushNames.traitNames = true; }
            String plain = b.name.substring(0, b.name.indexOf('-'));
            check(on.nextInt() == off.nextInt() && a.seed == b.seed && ModularCode.encode(rename(a.copy(), b.name)).equals(ModularCode.encode(b)), "A roll named for what it is is otherwise as before: " + a.name + ", " + b.name);
            check(BrushNames.adjectives().contains(plain) && a.name.equals(BrushNames.name(a)) && a.name.length() <= Brush.NAME_MAX && BrushNames.twoLines(a.name)
                    && a.name.matches(FORM) && kind(a.name), "Its name is Title Case, fits and is kind: " + a.name);
            String low = a.name.toLowerCase(java.util.Locale.ROOT), last = low.substring(low.lastIndexOf(' ') + 1);
            boolean folk = folkWords.contains(last), form = BrushNames.form(a.name) >= 0;
            // A paint colour's name hints by its words ([colour] and [noun], by what the brush has); the rest say the part.
            if (!form && !folk && !plainName(a, a.name)) {
                colours++;
                for (String w : low.split(" ")) check(BrushNames.traits().get(BrushNames.COLOUR).values().stream().anyMatch(l -> l.contains(w)) || BrushNames.traits().get(BrushNames.NOUN).values().stream().anyMatch(l -> l.contains(w)), "A paint colour's words: " + a.name);
            } else check(low.contains(hint(a)), "It says its part: " + a.name + " " + BrushNames.plainWords(a));
            if (form) forms++;
            if (folk) folks++;
            for (String w : low.split(" ")) if (!BrushNames.traitWords().contains(w) && !FORM_WORDS.contains(w) && !folkWords.contains(w)) { madeUp++; break; }
            names.add(a.name);
            if (i % 97 == 0 && examples.size() < 10) examples.add(a.name);
        }
        // The mix (the owner, 2026-11-02): paint colours about 55 in 100, deadpan 30, a part's name 15.
        check(names.size() > 1500 && colours > 900 && colours < 1300 && forms > 450 && forms < 750 && folks > 180 && folks < 420 && madeUp > 50 && madeUp < 300,
                "Rolls' names vary and mix: " + names.size() + " of 2000, " + colours + " paint colours, " + forms + " deadpan forms, " + folks + " part's names, " + madeUp + " made up " + examples);
    }
    /**
     * The owner, 2026-11-02: "it's ok but seems to repeat quite often". 500 rolled brushes saved one after another, each
     * named by the library ({@link BrushLab#suggestion}'s way): every name fits, is kind and is the same given again; no
     * deadpan form and no word but a part's or describer's comes twice in any ten saves in a row; many forms and words.
     * (Before, the same 500: 10 shapes, one shape up to 6 times in ten saves, a first word up to 4 times.)
     */
    static void variety() {
        List<ModularStyle> all = ModularStyle.all();
        List<String> library = new ArrayList<>(), forms = new ArrayList<>();
        Set<String> firsts = new HashSet<>(), words = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            ModularBrush b = all.get(i % all.size()).roll(new Random(777 + i));
            List<String> recent = new ArrayList<>(library);
            Collections.reverse(recent);
            java.util.function.Predicate<String> taken = n -> library.stream().anyMatch(x -> BrushLibrary.sameName(x, n));
            String n = BrushNames.name(b, taken, recent);
            check(n.equals(BrushNames.name(b.copy(), taken, new ArrayList<>(recent))) && n.equals(BrushNames.described(b, b.name, taken, recent)), "The same brush and library, the same name: " + n);
            check(n.length() <= Brush.NAME_MAX && BrushNames.twoLines(n) && n.matches(FORM) && kind(n) && !taken.test(n), "Every name fits two lines of a card, is kind and new: " + n);
            library.add(n);
            forms.add(BrushNames.form(n) >= 0 ? "#" + BrushNames.form(n) : null);
            firsts.add(n.substring(0, n.indexOf(' ')));
            words.addAll(BrushNames.marks(n));
        }
        int formRepeats = 0, wordRepeats = 0;
        for (int i = 0; i + BrushNames.RECENT < library.size(); i++) {
            Map<String, Integer> seen = new java.util.HashMap<>();
            for (int j = i; j <= i + BrushNames.RECENT; j++) for (String m : BrushNames.marks(library.get(j))) seen.merge(m, 1, Integer::sum);
            for (Map.Entry<String, Integer> e : seen.entrySet()) if (e.getValue() > 1) { if (e.getKey().startsWith("#")) formRepeats++; else wordRepeats++; }
        }
        Set<String> kinds = new HashSet<>(forms);
        kinds.remove(null);
        check(formRepeats == 0 && wordRepeats < 10, "No deadpan form and hardly a word again in any eleven saves in a row: " + formRepeats + ", " + wordRepeats);
        check(kinds.size() >= 22 && firsts.size() > 180 && words.size() > 350, "Many forms and words: " + kinds.size() + " forms, " + firsts.size() + " first words, " + words.size() + " words");
        // A part's name and the recent ones: the latest ten only.
        ModularBrush b = brush("Fineliner", 5, PartChoice.WASH);
        String n = BrushNames.name(b), other = BrushNames.name(b, x -> false, Collections.singletonList(n));
        List<String> old = new ArrayList<>(Collections.nCopies(BrushNames.RECENT, "Plain Brush"));
        old.add(n);
        check(!other.equals(n) && Collections.disjoint(BrushNames.marks(other), BrushNames.marks(n)) && BrushNames.name(b, x -> false, old).equals(n), "A repeat of the latest name is passed by, of an older one not: " + n + ", " + other);
    }
    /**
     * The web lab (parts.js) carries the list as one string, the older list (2026-10-29): the lab's and Brush Studio's
     * JS copy names in the older eight forms where the app's engine isn't loaded. Its [part], [describer] and [fun] are
     * the app's, line for line, in the lab's sources, its page, Brush Studio and Brush Scan. App codes the app names as pinned.
     */
    static void sameAsLab() throws IOException {
        Map<String, Map<String, List<String>>> app = BrushNames.traits();
        String line = null;
        for (String l : Files.readAllLines(Paths.get("docs/brush-lab/prototypes/modular-lab-src/parts.js"), StandardCharsets.UTF_8))
            if (l.startsWith("const NAMES_TRAITS = ")) line = l;
        check(line != null, "parts.js has NAMES_TRAITS");
        String text = line.substring(line.indexOf('"') + 1, line.lastIndexOf('"')).replace("\\n", "\n").replace("\\\"", "\"");
        Map<String, Map<String, List<String>>> lab = BrushNames.traits(Assets.lines(text.getBytes(StandardCharsets.UTF_8)));
        for (String s : new String[]{BrushNames.PART, BrushNames.DESCRIBER, BrushNames.FUN})
            check(lab.get(s).equals(app.get(s)), "The web lab's [" + s + "] is traits.txt's");
        for (String page : new String[]{"docs/brush-lab/prototypes/modular-lab.html", "docs/brush-lab/studio.html", "docs/brush-lab/match-lab.html"})
            check(new String(Files.readAllBytes(Paths.get(page)), StandardCharsets.UTF_8).contains(line), page + " carries the same traits");
        for (int i = 0; i < LAB_NAMED.length; i += 2) {
            ModularBrush b = ModularCode.decode(LAB_NAMED[i]);
            String got = b == null ? "?" : BrushNames.name(b);
            check(LAB_NAMED[i + 1].equals(got), "An app code names as pinned: " + LAB_NAMED[i + 1] + ", " + got + " " + LAB_NAMED[i]);
        }
    }
    /** App rolls' codes (checks.js nameChecks has them, with the lab's older names) and the names the app gives them now. */
    static final String[] LAB_NAMED = {
            "wild-plum.M00j40p000000at_000zz6400000000000_0_044_1jz_82500000000005w0000nz0.1o028zx", "Telegram Octopus",
            "jumbo-okra.M10i0hpxm3d00at_000ve0000000000000_0_000_1jz_8zj0000000000i60000nz0.10hs76", "Pen Debbie",
            "split-mango.M24fvokcxi06l9s00_0apzz0000000000000_0_100_1jz_8u700i8000000990000nz0.zwz2n7", "Speck of Split",
            "smear-plum.M40i000000000at_4ht3b_3hmjsat_3sj_1jz_8egh500myeb00000000nz0.1154jqa", "Fuzzmonger Buddy Smear",
            "float-quince.M60i0000000008t_1zued00000000ee_0_0h0_3lldii0_8p90000000000000000nz0.d21ddj", "Harbour Duck",
            "bristle-radish.Mb6vuj8jsqli0_2v400at_0_2qe_1jz_8dz0000000000000000nz0.171wrh6", "Wiry Mop",
            "lift-apple.M80fy000000000v_2zz00at_177at_100_1jz_8720000000000000000nz0.xgo4h0", "Allegedly Lift",
            "lead-okra.Md22h84hw00rqs500ot_4ziua_0_000_1ul_8xb00dr00000000bz00nz1.qbqfn0", "Nib Winnifred",
            "dust-bean.M51sgeei03s3fck00at_383lkdnwe_0_0xu_1jz_8uvlj00ufg700000000nz0.1eetlzr", "Dusty Twig",
            "crease-parsnip.Mb0i00000yo007v_14hat00770000ee_0_4nj_1jz_8na0000000000000000nz0.ss1dt7", "Gravel Teacup",
            "hatch-bean.Md887dj671zzm3_2u900at_0_04u_1qe_80000ep008g00gy8z00nz0.4oy7gg", "Lined Mailbox",
            "paint-beet.Md2a8qjeb006vik0066_2wp00at_0_26b_1re_8sx0000kf0000000000nz0.12l96hy", "Dutiful Nib Prototype",
            "under-lychee.M70i000on0000uq_1om00f70000phee2_0_000_1jz_8o20000000000000000nz0.169plr8", "Backroom Curtain",
            "laid-shallot.Mb25aeei00000u100ak_19qat00770000ee_0_2ln_1jz_8fy0000000000000000nz0.gdoqm5", "Bargain Scratchy Nib",
            "rake-date.Md5c08gdu00j0df75_2v3dmat_0_100_1va_8pn000000000000d500nz0.mig41m", "Skipper Rake",
            "white-quince.M80f6000000003k_2zz00at1_177at_04r_1jz_87c0000000000000000nz0.y41duu", "Blizzard Eraser",
            "invert-kale.Md0bmh92cfc00aj_1htdc00008700ee3_0_500_1et_8eyoy00000000q60000nz0.yo9dw8", "Backward Pancake",
            "mask-lychee.M60i000000000en_5_0_100_1jz_8em0000000000000000nz0.1qim1tm", "Rubber Tape",
            "grain-date.Md0ayfd000082yf_0g8ny0000000000000_0_1n6_1t2_8000000ky00pt000000nz0.rdle15", "Grainy Pen Instead"};
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
