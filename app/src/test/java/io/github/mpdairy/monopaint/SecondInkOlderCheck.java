package io.github.mpdairy.monopaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Compiled against the version before Second Ink (scripts/test_inkwash.sh, git c7ebd5bb): a notebook with a SINK record
 * ({@code SecondInkOptionChecks write}) opens there whole and from its file, the record passed by as one it doesn't know, the
 * other options read as they were; saved again, for {@code SecondInkOptionChecks older}.
 */
public final class SecondInkOlderCheck {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        byte[] bytes = Files.readAllBytes(file.toPath());
        DrawingBook opened = BookCodec.open(file), read = BookCodec.read(new ByteArrayInputStream(bytes));
        for (DrawingBook book : new DrawingBook[]{opened, read}) {
            NotebookOptions o = book.options();
            check(book.count() == 2, "The version before Second Ink reads the notebook's pages");
            check(o.template == NotebookTemplate.COMICS && o.noErasing && o.seconds == 180 && o.paper == 128, "and its other options as they were");
        }
        ByteArrayOutputStream again = new ByteArrayOutputStream();
        BookCodec.write(again, read.snapshot());
        Files.write(Paths.get(args[1]), again.toByteArray());
        System.out.println("PASS: the version before Second Ink (c7ebd5bb) reads a notebook with a SINK record, whole and from its file, the other options as they were, and saves it again");
    }
}
