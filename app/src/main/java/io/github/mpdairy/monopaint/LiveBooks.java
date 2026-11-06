package io.github.mpdairy.monopaint;

import java.io.*;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The Manta's notebooks to a browser on the Wi-Fi (Share on Wi-Fi, docs/superpowers/specs/2026-11-01-manta-host-design.md,
 * "Notebooks from a computer or iPad"): {@link LiveHost}'s {@code GET /books...}, behind its key. The shelf, a cover as
 * the shelf draws it, and downloads made by the writers the Manta's own Export Now and Export Page use; and a file sent
 * up made a new notebook ({@link #add}, {@code POST /books/add}). Pure: the
 * desktop checks run it on a folder ({@link #folder}), the Manta on its library through its store's thread.
 */
final class LiveBooks {
    /** A library's notebooks as last saved. */
    interface Library {
        /** Every notebook, the last changed first. */
        List<DrawingFiles.Entry> notebooks() throws IOException;
        /** What the shelf shows of notebook {@code path} without reading a page; throws when it doesn't read. */
        BookPictures.Cover cover(String path) throws IOException;
        /** Notebook {@code path}'s file. */
        byte[] file(String path) throws IOException;
        /**
         * A new notebook in the top folder named {@code name} ("name 2" and on when that is taken, never over one), its
         * file {@code file} (read already, {@link LiveBooks#add}) written whole, then swapped in, as the Manta saves; its path.
         */
        String add(String name, byte[] file) throws IOException;
        /**
         * A picture or PDF ({@code pdf}) made a new notebook named {@code name} as Import's New Notebook makes one
         * ({@code PdfImport}): its path, or null when it doesn't read. The desktop's folder reads none.
         */
        default String imported(String name, byte[] file, boolean pdf) throws IOException { return null; }
    }

    /** {@code files}' notebooks read as they are: an interrupted save's backup, when there is one, is the last whole file (AtomicFile). */
    static Library folder(DrawingFiles files) {
        return new Library() {
            File readable(String path) throws IOException { File f = files.drawing(path), bak = new File(f.getPath() + ".bak"); return bak.isFile() ? bak : f; }
            @Override public List<DrawingFiles.Entry> notebooks() throws IOException { return files.notebooks(); }
            @Override public BookPictures.Cover cover(String path) throws IOException { return BookPictures.cover(readable(path)); }
            @Override public byte[] file(String path) throws IOException { return Files.readAllBytes(readable(path).toPath()); }
            @Override public synchronized String add(String name, byte[] file) throws IOException {
                String path = files.unusedName("", name);
                File to = files.drawing(path), fresh = new File(to.getPath() + ".new");
                Files.write(fresh.toPath(), file);
                Files.move(fresh.toPath(), to.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                return path;
            }
        };
    }

    /** What a request gets: its type, bytes and, for a download, the file's name. */
    static final class Answer {
        final String type, name; final byte[] bytes;
        Answer(String type, String name, byte[] bytes) { this.type = type; this.name = name; this.bytes = bytes; }
    }
    static final int MOST_COVER = 1024;
    /**
     * The most a file sent up takes (POST /books/add). shortcut: it is held in memory, and a notebook read whole
     * ({@link BookCodec#read}) holds it about twice more; past 64 MB, stream it to a file instead.
     */
    static final int MOST_ADDED = 64 << 20;
    private final Library library;
    private final java.util.function.Supplier<Magnets.Tiles> tiles;

    /** {@code tiles}: what draws the magnets into copies, as the Manta's Export Now draws them, one a copy (null from it: none). */
    LiveBooks(Library library, java.util.function.Supplier<Magnets.Tiles> tiles) { this.library = library; this.tiles = tiles; }

    /**
     * {@code GET path?query}: {@code /books} the shelf, a line a notebook (its path, last change in ms, pages, 1 when it
     * doesn't read; tab-separated); {@code /books/cover?path=&w=&h=} its front cover w × h, a grey byte a pixel;
     * {@code /books/file?path=} its .mpaint; {@code /books/pdf?path=} its PDF; {@code /books/page?path=&place=&as=&size=} a
     * page's copy ({@link PageExport#CHOICES}, as: its index; size: an {@link ExportSize}'s, Page without it). Null: nothing there.
     */
    Answer answer(String path, Map<String, String> query) throws IOException {
        if (path.equals("/books")) {
            StringBuilder s = new StringBuilder();
            for (DrawingFiles.Entry e : library.notebooks()) {
                int pages = 0; boolean unreadable = false;
                try { pages = library.cover(e.name).stacks; } catch (IOException | RuntimeException damaged) { unreadable = true; }
                s.append(line(e.name)).append('\t').append(e.modified).append('\t').append(pages).append('\t').append(unreadable ? 1 : 0).append('\n');
            }
            return new Answer("text/plain; charset=utf-8", null, s.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        String notebook = query.get("path");
        if (notebook == null || !DrawingFiles.validPath(notebook)) return null;
        String name = DrawingFiles.name(notebook);
        switch (path) {
            case "/books/file": return new Answer("application/octet-stream", name + DrawingFiles.EXTENSION, library.file(notebook));
            case "/books/cover": {
                int w = number(query.get("w")), h = number(query.get("h"));
                if (w < 1 || h < 1 || w > MOST_COVER || h > MOST_COVER) return null;
                byte[] tones = NotebookCovers.of(book(notebook), DrawingBook.FRONT, notebook, w, h);
                for (int i = 0; i < tones.length; i++) tones[i] = (byte) DotPattern.exportGray(tones[i] & 255);
                return new Answer("application/octet-stream", null, tones);
            }
            case "/books/pdf": {
                ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 20);
                NotebookExport.pdf(out, book(notebook), name, tiles.get(), LocalDate.now().toEpochDay());
                return new Answer("application/pdf", NotebookPdf.base(NotebookPdf.title(name)) + ".pdf", out.toByteArray());
            }
            case "/books/page": {
                int place = number(query.get("place")), as = number(query.get("as")), size = query.containsKey("size") ? number(query.get("size")) : ExportSize.PAGE.ordinal();
                if (as < 0 || as >= PageExport.CHOICES.length || size < 0 || size >= ExportSize.values().length) return null;
                DrawingBook book = book(notebook);
                if (place < 0 || place >= NotebookStrip.count(book)) return null;
                String choice = PageExport.CHOICES[as];
                return new Answer(LiveHost.type("." + PageExport.extension(choice)), PageExport.name(NotebookPdf.title(name), place, choice),
                        PageExport.bytes(book, name, place, choice, ExportSize.values()[size], tiles.get(), LocalDate.now().toEpochDay()));
            }
            default: return null;
        }
    }

    /**
     * {@code POST /books/add?name=}: a file sent from a browser, {@code file} its name there, made a new notebook, never
     * over one (docs/superpowers/specs/2026-11-01-manta-host-design.md): a notebook's file read whole first (nothing is
     * written unless it reads), then kept as it is; a picture or PDF whose bytes are what its name says, made one as
     * Import makes one. Named after the file ({@link NotebookNames#imported}), numbered where the shelf has that name.
     * Its path; null: none of those, or it doesn't read. One at a time.
     */
    synchronized String add(String file, byte[] bytes) throws IOException {
        if (file == null || bytes.length == 0) return null;
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (DrawingFiles.Entry e : library.notebooks()) taken.add(e.name);
        String name = NotebookNames.imported(file, LocalDate.now(), new java.util.Random(), taken::contains);
        if (file.toLowerCase(java.util.Locale.ROOT).endsWith(DrawingFiles.EXTENSION)) {
            try { BookCodec.read(new ByteArrayInputStream(bytes)); } catch (IOException | RuntimeException unreadable) { return null; }
            return library.add(name, bytes);
        }
        return PictureImport.importable(file, bytes) ? library.imported(name, bytes, PictureImport.pdf(null, file)) : null;
    }

    /** Notebook {@code path} read as the Manta opens one (an old notebook's blank page at its end gone, {@link BlankPages#opened}). */
    private DrawingBook book(String path) throws IOException {
        DrawingBook book = BookCodec.read(new ByteArrayInputStream(library.file(path)));
        try { BlankPages.opened(book); } catch (IOException | RuntimeException kept) { /* the blank page stays */ }
        book.name(DrawingFiles.name(path));
        return book;
    }
    private static String line(String s) { return s.replace('\t', ' ').replace('\n', ' '); }
    private static int number(String s) { try { return s == null ? -1 : Integer.parseInt(s); } catch (NumberFormatException e) { return -1; } }
}
