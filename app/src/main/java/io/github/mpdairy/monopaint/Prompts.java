package io.github.mpdairy.monopaint;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Prompts notebook (docs/superpowers/specs/2026-11-13-fridge-prompts-design.md): drawing along with #inktober52,
 * a page a week, the word in a band at its top ({@link NotebookTemplate#prompt}). The words come from superdood.org
 * (GET {@link #WORDS_API}: the open ones, the current first); a page's word is its own template key. Pure Java but
 * for nothing: PromptsChecks.
 */
final class Prompts {
    private Prompts() { }

    static final String WORDS_API = Fridge.SITE + "/api/words";
    /** The Prompts notebook's name, made once on the shelf (the owner, 2026-11-13: "for the notebook name, use #inktober52 instead of prompts"). */
    static final String NOTEBOOK = "#inktober52";
    /** Words are weeks in St. John's (the owner's, and the site's). */
    static final ZoneId ZONE = ZoneId.of("America/St_Johns");
    private static final long DAY = 24L * 3600 * 1000;
    private static final Pattern OBJECT = Pattern.compile("\\{[^{}]*\\}"), SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    /** An open word: its slug, its word, its Inktober52 number (-1 none), when it started and when it ended (0: current). */
    static final class Word {
        final String slug, word;
        final int number;
        final long starts, ends;
        Word(String slug, String word, int number, long starts, long ends) { this.slug = slug; this.word = word; this.number = number; this.starts = starts; this.ends = ends; }
    }

    /** The server's {"words":[...]}, in its order; nothing for anything that doesn't read. */
    static List<Word> words(String json) {
        List<Word> out = new ArrayList<>();
        if (json == null) return out;
        Matcher m = OBJECT.matcher(json);
        while (m.find()) {
            String o = m.group(), slug = string(o, "slug"), word = string(o, "word");
            long starts = number(o, "starts"), ends = number(o, "ends"), n = number(o, "number");
            if (slug == null || !SLUG.matcher(slug).matches() || word == null || word.isEmpty() || starts <= 0) continue;
            out.add(new Word(slug, word, n < 0 || n > 999 ? -1 : (int) n, starts, Math.max(0, ends)));
        }
        return out;
    }
    /** A JSON string field's value, its escapes undone; null when missing. */
    private static String string(String o, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(o);
        if (!m.find()) return null;
        String s = m.group(1);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) { b.append(c); continue; }
            char e = s.charAt(++i);
            if (e == 'u' && i + 4 < s.length()) { b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; }
            else b.append(e == 'n' ? '\n' : e == 't' ? '\t' : e);
        }
        return b.toString();
    }
    /** A JSON number field's value; -1 when missing or null. */
    private static long number(String o, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*(\\d{1,15})").matcher(o);
        return m.find() ? Long.parseLong(m.group(1)) : -1;
    }

    /** "Nov 10–16", "Oct 27–Nov 2": from starts to the day before ends, or a week when there's no end yet. */
    static String week(long starts, long ends) {
        ZonedDateTime a = Instant.ofEpochMilli(starts).atZone(ZONE), b = Instant.ofEpochMilli(ends > 0 ? ends - DAY : starts + 6 * DAY).atZone(ZONE);
        String month = a.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
        return month + " " + a.getDayOfMonth() + "–" + (b.getMonth() == a.getMonth() ? "" : b.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " ") + b.getDayOfMonth();
    }
    /** The page key for {@code w} ({@link NotebookTemplate#PROMPT}); a | in the word becomes a space. */
    static String key(Word w) {
        return NotebookTemplate.PROMPT + w.slug + "|" + (w.number < 0 ? "" : String.valueOf(w.number)) + "|" + w.word.replace('|', ' ') + "|" + week(w.starts, w.ends);
    }
    /** A page's word, from its own template key (as {@link DrawingBook#pageTemplates} has it, "=" first, or bare); null: not a prompt page. */
    static String slug(String pageKey) { String[] f = fields(pageKey); return f == null ? null : f[0]; }
    /** A prompt page key's slug, number, word and week ({@link #key}); null: not one. */
    private static String[] fields(String pageKey) {
        if (pageKey == null) return null;
        String k = pageKey.startsWith(NotebookOptions.OWN) ? pageKey.substring(NotebookOptions.OWN.length()) : pageKey;
        if (!k.startsWith(NotebookTemplate.PROMPT)) return null;
        String[] f = k.substring(NotebookTemplate.PROMPT.length()).split("\\|", -1);
        return f.length == 4 && !f[0].isEmpty() ? f : null;
    }
    /** The open words with no page yet, oldest first: the pages to add at the notebook's end. */
    static List<Word> toAdd(List<String> pageKeys, List<Word> open) {
        java.util.Set<String> have = new java.util.HashSet<>();
        for (String k : pageKeys) { String s = slug(k); if (s != null) have.add(s); }
        List<Word> add = new ArrayList<>();
        for (Word w : open) if (!have.contains(w.slug)) add.add(w);
        add.sort((x, y) -> Long.compare(x.starts, y.starts));
        return add;
    }
    /**
     * A page at {@code book}'s end for each open word it has no page for ({@link #toAdd}), oldest first, each with its
     * word's band; the newest added is the page drawn on. False when the notebook filled before all were added.
     */
    static boolean append(DrawingBook book, List<Word> open) throws java.io.IOException {
        for (Word w : toAdd(book.pageTemplates(), open)) {
            if (book.count() >= DrawingBook.MAX_PAGES) return false;
            book.addPage();
            book.setPageTemplate(book.index(), NotebookTemplate.of(key(w)));
        }
        return true;
    }
    /** Send to the Fridge's note on a page whose word no longer takes entries (and the server's words for it, 409). */
    static final String CLOSED = "This word is closed.";
    /**
     * The word a page's Send to the Fridge posts under, as its page key: its own on a prompt page of the Prompts notebook
     * while on; null anywhere else, a post with no word (the owner, 2026-11-13: anyone posts any drawing to the Fridge
     * again, "with the email approval process in place").
     */
    static String wordKey(boolean promptsNotebook, boolean on, String pageKey) { return promptsNotebook && on && slug(pageKey) != null ? pageKey : null; }
    /**
     * The menu's Send to the Fridge's note (the row is every notebook's page's, never a cover's): {@link #CLOSED}, greyed,
     * for a word's post ({@link #wordKey}) whose word isn't among {@code open}, once a fetch has given them (null before,
     * when the server answers instead); null, the row live.
     */
    static String sendRow(String wordKey, List<Word> open) {
        String slug = slug(wordKey);
        return slug != null && open != null && !open(slug, open) ? CLOSED : null;
    }
    /**
     * The Fridge window's line under the page's word: "no. 45 · open till Nov 23", the last day its word takes pages (a
     * week after its own week; the current word's end not known yet, a week after a week from its start); either part
     * left out when unknown.
     */
    static String about(String pageKey, List<Word> open) {
        String[] f = fields(pageKey);
        if (f == null) return "";
        String till = null;
        if (open != null) for (Word w : open) if (w.slug.equals(f[0])) {
            ZonedDateTime last = Instant.ofEpochMilli(w.ends > 0 ? w.ends : w.starts).atZone(ZONE).plusDays(w.ends > 0 ? 6 : 13);
            till = last.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + last.getDayOfMonth();
        }
        return (f[1].isEmpty() ? "" : "no. " + f[1]) + (!f[1].isEmpty() && till != null ? " · " : "") + (till == null ? "" : "open till " + till);
    }
    /** Whether a page's word still takes entries. */
    static boolean open(String slug, List<Word> open) {
        if (slug == null) return false;
        for (Word w : open) if (w.slug.equals(slug)) return true;
        return false;
    }
}
