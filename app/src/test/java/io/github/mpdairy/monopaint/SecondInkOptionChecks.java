package io.github.mpdairy.monopaint;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Second Ink's notebook option (the owner, 2026-11-09; {@link NotebookOptions#ink}, {@link SecondInk}): five inks, Blue the
 * default and no record; a SINK record otherwise, read back from the file and the recovery, an index out of range Blue.
 * {@code write <file>} writes a notebook with an ink and other options for the version before it
 * ({@code SecondInkOlderCheck}, c7ebd5bb); {@code older <file>} opens what that version saved again.
 */
public final class SecondInkOptionChecks {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        if (args.length == 2 && args[0].equals("write")) { write(new File(args[1])); return; }
        if (args.length == 2 && args[0].equals("older")) { older(new File(args[1])); return; }
        inks(); records(); defaults();
        System.out.println("PASS: Second Ink: five inks (Blue, Sepia, Red, Green, Violet), Blue by default with no record and the bytes written today; each other ink a SINK record read back from the file and the recovery, "
                + "an index out of range Blue");
    }

    static void inks() {
        check(Arrays.equals(SecondInk.NAMES, new String[]{"Blue", "Sepia", "Red", "Green", "Violet"}) && SecondInk.DEFAULT == 0, "The five inks, Blue first");
        int[] rgb = {0x25354c, 0x573a1e, 0x7a1f24, 0x4a6b2a, 0x4a2a55};
        for (int i = 0; i < 5; i++) check(SecondInk.rgb(i) == rgb[i], "Ink " + i + "'s colour");
        check(SecondInk.rgb(-1) == rgb[0] && SecondInk.rgb(5) == rgb[0], "An index out of range is Blue");
        NotebookOptions o = NotebookOptions.DEFAULT;
        check(o.ink == 0 && o.isDefault() && o.withInk(2).ink == 2 && !o.withInk(2).isDefault() && o.withInk(2).equals(o.withInk(2)) && !o.withInk(2).equals(o.withInk(3)), "The option: Blue by default, a change, equal by ink");
        check(o.withInk(2).hashCode() != o.hashCode(), "and in its hash");
        check(o.withInk(9).ink == 0 && o.withInk(-3).ink == 0 && o.readInk(new byte[]{(byte) 200}).ink == 0 && o.readInk(null).ink == 0 && o.readInk(new byte[]{1, 2}).ink == 0, "Out of range or damaged reads as Blue");
    }

    static void records() throws Exception {
        for (int i = 1; i < 5; i++) {
            NotebookOptions o = NotebookOptions.DEFAULT.withInk(i);
            BookOptions.Covers covers = new BookOptions.Covers(); covers.options = o;
            byte[] block = BookOptions.bytes(covers);
            BookOptions.Found found = BookOptions.find(BookOptions.of(block), 0, block.length);
            check(found != null && found.records.size() == 1 && found.records.get(0).tag == BookOptions.SECOND_INK, "Ink " + i + " alone is one SINK record");
            check(BookOptions.options(found, BookOptions.of(block)).ink == i, "and reads back");
            DrawingBook book = NotebookOptionsChecks.book();
            book.setOptions(o.withPaper(128));
            byte[] saved = NotebookOptionsChecks.bytes(book);
            DrawingBook read = BookCodec.read(new ByteArrayInputStream(saved));
            check(read.options().ink == i && read.options().paper == 128 && Arrays.equals(NotebookOptionsChecks.bytes(read), saved), "Ink " + i + " reads back from the file, with the other options, byte for byte");
            ByteArrayOutputStream recovery = new ByteArrayOutputStream();
            RecoveryCodec.write(recovery, book.snapshot(), "");
            check(RecoveryCodec.read(new ByteArrayInputStream(recovery.toByteArray())).book.options().equals(book.options()), "and from the recovery");
        }
        // A byte out of range in the file reads as Blue.
        BookOptions.Covers covers = new BookOptions.Covers(); covers.options = NotebookOptions.DEFAULT.withInk(1);
        byte[] block = BookOptions.bytes(covers);
        block[block.length - 1] = 77;
        check(BookOptions.options(BookOptions.find(BookOptions.of(block), 0, block.length), BookOptions.of(block)).ink == 0, "A SINK record with an index out of range reads as Blue");
    }

    /** Blue writes no record: the notebook's bytes are those of one that never had the option. */
    static void defaults() throws Exception {
        DrawingBook book = NotebookOptionsChecks.book();
        byte[] before = NotebookOptionsChecks.bytes(book);
        book.setOptions(NotebookOptions.DEFAULT.withInk(3));
        check(!Arrays.equals(NotebookOptionsChecks.bytes(book), before), "An ink changes the file");
        book.setOptions(NotebookOptions.DEFAULT.withInk(3).withInk(SecondInk.DEFAULT));
        check(Arrays.equals(NotebookOptionsChecks.bytes(book), before), "Blue again: the file is byte for byte as before");
        BookOptions.Covers none = new BookOptions.Covers();
        check(BookOptions.bytes(none).length == 0, "and the defaults write no block");
    }

    // ---- the version before it ----

    static NotebookOptions options() {
        return NotebookOptions.DEFAULT.withTemplate(NotebookTemplate.COMICS).withNoErasing(true).withSeconds(180).withPaper(128).withInk(3);
    }
    static void write(File file) throws Exception {
        DrawingBook book = NotebookOptionsChecks.book();
        book.setOptions(options());
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) { BookCodec.write(out, book.snapshot()); }
        System.out.println("PASS: wrote a notebook with Green ink, a template, No Erasing, Timed Pages and a page colour");
    }
    /** What the older version saved again: the other options as they were, the SINK record dropped (Blue). */
    static void older(File file) throws Exception {
        NotebookOptions read = BookCodec.open(file).options();
        NotebookOptions want = options();
        check(read.template == want.template && read.noErasing && read.seconds == want.seconds && read.paper == 128, "The version before Second Ink kept the other options");
        check(read.ink == SecondInk.DEFAULT, "and dropped the ink: Blue");
        System.out.println("PASS: the version before Second Ink skipped the SINK record and kept the template, No Erasing, Timed Pages and page colour; saved again, it opens here Blue");
    }
}
