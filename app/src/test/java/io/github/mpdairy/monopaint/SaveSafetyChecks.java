package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Saving safely (DrawingFiles#whole, #commit, #repaired; DocumentStore's save and open): the owner's notebook, 2026-11-14,
 * came back after the app was stopped mid-save as a file whose last page was half another save's bytes and half zeros.
 */
public final class SaveSafetyChecks {
    public static void main(String[] args) throws Exception {
        File scratch = Files.createTempDirectory("superdood-save-").toFile();
        try {
            DrawingFiles files = new DrawingFiles(scratch);
            File base = files.drawing("Diary");
            byte[] first = bytes(NotebookFileChecks.book(3, 64, 80)), second = bytes(NotebookFileChecks.book(3, 64, 96));

            // A save as DocumentStore makes it: the fresh file, read back whole, then put in place.
            save(base, first);
            check(Arrays.equals(Files.readAllBytes(base.toPath()), first) && !DrawingFiles.fresh(base).exists() && !DrawingFiles.previous(base).exists(), "A first save is the file, nothing beside it");
            save(base, second);
            check(Arrays.equals(Files.readAllBytes(base.toPath()), second) && Arrays.equals(Files.readAllBytes(DrawingFiles.previous(base).toPath()), first), "The next save keeps the one before as the previous save");
            check(DrawingFiles.whole(base) && DrawingFiles.whole(DrawingFiles.previous(base)), "Both read back whole");

            // The incident's shape: the last page's bytes zeroed from part way on, the file's length and directory intact.
            byte[] damaged = second.clone();
            int last = lastPageStart(damaged);
            Arrays.fill(damaged, last + 40, damaged.length - 200, (byte) 0);
            Files.write(base.toPath(), damaged);
            check(!DrawingFiles.whole(base), "A page whose bytes don't match its CRC-32 is not whole");
            File loose = new File(scratch, "short.mpaint"); Files.write(loose.toPath(), Arrays.copyOf(second, second.length / 2));
            check(!DrawingFiles.whole(loose), "A short file is not whole"); loose.delete();
            long made = 1_700_000_000_000L; DrawingFiles.previous(base).setLastModified(made);
            check(DrawingFiles.repaired(base) == made, "Opening puts the previous save back, saying when it was made");
            check(Arrays.equals(Files.readAllBytes(base.toPath()), first) && Arrays.equals(Files.readAllBytes(DrawingFiles.damaged(base).toPath()), damaged)
                    && !DrawingFiles.previous(base).exists(), "The previous save is the file again; the damaged one is set aside, not lost");
            check(DrawingFiles.repaired(base) == 0 && Arrays.equals(Files.readAllBytes(base.toPath()), first), "A whole file is left as it is");

            // Damaged with no previous save: left as it is (the app says it couldn't be read), never replaced by nothing.
            Files.write(base.toPath(), damaged); DrawingFiles.damaged(base).delete();
            check(DrawingFiles.repaired(base) == 0 && Arrays.equals(Files.readAllBytes(base.toPath()), damaged), "Damaged with nothing to fall back on: left alone");

            // A save that doesn't read back whole never replaces anything (DocumentStore throws before commit).
            Files.write(base.toPath(), first);
            Files.write(DrawingFiles.fresh(base).toPath(), damaged);
            check(!DrawingFiles.whole(DrawingFiles.fresh(base)), "A damaged fresh file is caught before it is put in place");
            DrawingFiles.fresh(base).delete();

            // Stopped between commit's two renames: the notebook has no file of its own for a moment.
            Files.write(base.toPath(), first); save(base, second);
            Files.move(base.toPath(), DrawingFiles.previous(base).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Files.write(DrawingFiles.fresh(base).toPath(), second);
            check(FilesHolder.names(files).contains("Diary") && DrawingFiles.exists(base), "Stopped between the renames, the notebook is still on the shelf and its name taken");
            check(DrawingFiles.repaired(base) == 0 && Arrays.equals(Files.readAllBytes(base.toPath()), second) && !DrawingFiles.fresh(base).exists(), "Opening finishes the save");
            Files.move(base.toPath(), DrawingFiles.previous(base).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            check(DrawingFiles.repaired(base) > 0 && Arrays.equals(Files.readAllBytes(base.toPath()), second), "With no fresh file, the previous save is put back");

            // The previous save and a damaged file go with their notebook: renamed, deleted.
            save(base, first); Files.write(DrawingFiles.damaged(base).toPath(), damaged);
            String renamed = files.rename("Diary", "Journal");
            File moved = files.drawing(renamed);
            check(DrawingFiles.previous(moved).exists() && DrawingFiles.damaged(moved).exists() && !DrawingFiles.previous(base).exists() && !DrawingFiles.damaged(base).exists(),
                    "A rename takes the previous save and a damaged file along");
            check(FilesHolder.names(files).equals(java.util.List.of("Journal")), "Only the notebook is on the shelf: " + FilesHolder.names(files));
            files.delete(renamed);
            check(!moved.exists() && !DrawingFiles.previous(moved).exists() && !DrawingFiles.damaged(moved).exists() && !DrawingFiles.exists(moved), "A delete takes them too");
            System.out.println("PASS: saving safely: a save is read back whole before it replaces anything and keeps the one before; a damaged file (the 2026-11-14 notebook's half-zeroed page) is set aside and its previous save opened; a save stopped between its renames is finished; renames and deletes take the previous save along");
        } finally { remove(scratch); }
    }
    private static final class FilesHolder {
        static java.util.List<String> names(DrawingFiles files) throws IOException {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (DrawingFiles.Entry e : files.list("")) names.add(e.name);
            return names;
        }
    }
    /** DocumentStore's save, the file part: fresh, read back whole, put in place. */
    private static void save(File base, byte[] bytes) throws IOException {
        Files.write(DrawingFiles.fresh(base).toPath(), bytes);
        check(DrawingFiles.whole(DrawingFiles.fresh(base)), "A fresh save reads back whole");
        DrawingFiles.commit(base);
    }
    private static byte[] bytes(DrawingBook book) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); BookCodec.write(out, book.snapshot()); return out.toByteArray(); }
    /** Where the last page's bytes begin (its stored entry's data). */
    private static int lastPageStart(byte[] zip) {
        int at = -1;
        for (int i = 0; i + 30 < zip.length; i++)
            if (zip[i] == 'P' && zip[i + 1] == 'K' && zip[i + 2] == 3 && zip[i + 3] == 4) {
                String name = new String(zip, i + 30, (zip[i + 26] & 255) | (zip[i + 27] & 255) << 8, java.nio.charset.StandardCharsets.US_ASCII);
                if (name.startsWith("pages/")) at = i + 30 + name.length() + ((zip[i + 28] & 255) | (zip[i + 29] & 255) << 8);
            }
        return at;
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError("FAIL: " + what); }
    private static void remove(File file) throws IOException {
        File[] children = file.listFiles(); if (children != null) for (File child : children) remove(child);
        Files.delete(file.toPath());
    }
}
