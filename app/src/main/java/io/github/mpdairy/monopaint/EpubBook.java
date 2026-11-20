package io.github.mpdairy.monopaint;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** An EPUB read for Open File (the owner, 2026-11-17: "can we make a way to open epubs and pdfs easily from the notebooks area as if they were notebooks?"): its spine's files in reading order, its chapters, and whether it is locked (DRM). Pure (java.util.zip, javax.xml DOM), so the desktop checks it. */
final class EpubBook {
    static final String LOCKED = "Can't open this book";
    /** A chapter: its title, depth, spine item, and the id it starts at in that item (its href's #fragment, decoded; null for the item's start): many books keep several chapters in one file. */
    static final class Chapter { final String title, fragment; final int depth, spine; Chapter(String t, int d, int s, String f) { title = t; depth = d; spine = s; fragment = f; } }
    /** Each spine item's path in the zip, in reading order (linear="no" left out). */
    final List<String> spine = new ArrayList<>();
    /** The chapters: nav (EPUB 3) else NCX (EPUB 2), depth 0 and 1, each the spine item it starts in and its #fragment. */
    final List<Chapter> chapters = new ArrayList<>();

    private EpubBook() {}

    /** Reads the EPUB in {@code zip}; throws IOException(LOCKED) for DRM, IOException for anything unreadable. */
    static EpubBook read(ZipFile zip) throws IOException {
        if (zip.getEntry("META-INF/rights.xml") != null) throw new IOException(LOCKED);
        if (zip.getEntry("META-INF/encryption.xml") != null)
            for (Element m : all(parse(zip, "META-INF/encryption.xml").getDocumentElement(), "EncryptionMethod")) {
                String a = m.getAttribute("Algorithm");
                if (!a.equals("http://www.idpf.org/2008/embedding") && !a.equals("http://ns.adobe.com/pdf/enc#RC")) throw new IOException(LOCKED);
            }
        List<Element> roots = all(parse(zip, "META-INF/container.xml").getDocumentElement(), "rootfile");
        if (roots.isEmpty() || roots.get(0).getAttribute("full-path").isEmpty()) throw new IOException("No package in this book");
        String opfPath = roots.get(0).getAttribute("full-path");
        Element opf = parse(zip, opfPath).getDocumentElement();
        Map<String, Element> items = new HashMap<>();
        for (Element i : all(opf, "item")) items.put(i.getAttribute("id"), i);
        List<Element> spineEls = all(opf, "spine");
        if (spineEls.isEmpty()) throw new IOException("No reading order in this book");
        EpubBook b = new EpubBook();
        Map<String, Integer> index = new HashMap<>();
        for (Element r : all(spineEls.get(0), "itemref")) {
            Element it = items.get(r.getAttribute("idref"));
            if (it == null || "no".equals(r.getAttribute("linear"))) continue;
            String type = it.getAttribute("media-type");
            if (!type.equals("application/xhtml+xml") && !type.equals("text/html")) continue;
            String p = resolve(opfPath, it.getAttribute("href"));
            index.put(p, b.spine.size());
            b.spine.add(p);
        }
        for (Element i : items.values()) {
            if (!(" " + i.getAttribute("properties") + " ").contains(" nav ")) continue;
            String np = resolve(opfPath, i.getAttribute("href"));
            try { b.navChapters(parse(zip, np), np, index); } catch (IOException x) { b.chapters.clear(); }
            break;
        }
        Element ncx = items.get(spineEls.get(0).getAttribute("toc"));
        if (b.chapters.isEmpty() && ncx != null) {
            String np = resolve(opfPath, ncx.getAttribute("href"));
            try { b.ncxChapters(parse(zip, np), np, index); } catch (IOException x) { b.chapters.clear(); }
        }
        return b;
    }

    private void navChapters(Document d, String file, Map<String, Integer> index) {
        Element toc = null, first = null;
        for (Element n : all(d.getDocumentElement(), "nav")) {
            if (first == null) first = n;
            if (toc == null && "toc".equals(typeOf(n))) toc = n;
        }
        if (toc == null) toc = first;
        if (toc != null) navList(child(toc, "ol"), 0, file, index);
    }

    private void navList(Element ol, int depth, String file, Map<String, Integer> index) {
        if (ol == null) return;
        for (Element li : children(ol, "li")) {
            Element a = child(li, "a");
            if (a != null) add(a.getTextContent(), depth, file, a.getAttribute("href"), index);
            if (depth == 0) navList(child(li, "ol"), 1, file, index);
        }
    }

    private void ncxChapters(Document d, String file, Map<String, Integer> index) {
        List<Element> maps = all(d.getDocumentElement(), "navMap");
        if (!maps.isEmpty()) ncxPoints(maps.get(0), 0, file, index);
    }

    private void ncxPoints(Element parent, int depth, String file, Map<String, Integer> index) {
        for (Element p : children(parent, "navPoint")) {
            Element label = child(p, "navLabel"), text = label == null ? null : child(label, "text"), c = child(p, "content");
            if (text != null && c != null) add(text.getTextContent(), depth, file, c.getAttribute("src"), index);
            if (depth == 0) ncxPoints(p, 1, file, index);
        }
    }

    private void add(String title, int depth, String file, String href, Map<String, Integer> index) {
        String t = title.replaceAll("\\s+", " ").trim();
        Integer s = index.get(resolve(file, href));
        int h = href.indexOf('#');
        String fragment = h < 0 || h == href.length() - 1 ? null : href.substring(h + 1);
        if (fragment != null) try { fragment = URLDecoder.decode(fragment.replace("+", "%2B"), "UTF-8"); } catch (Exception x) { /* keep it as written */ }
        if (!t.isEmpty() && s != null) chapters.add(new Chapter(t, depth, s, fragment));
    }

    /** A nav's epub:type, whichever prefix it is written with. */
    private static String typeOf(Element n) {
        for (int i = 0; i < n.getAttributes().getLength(); i++) {
            Node a = n.getAttributes().item(i);
            if ("type".equals(a.getLocalName())) return a.getNodeValue().trim();
        }
        return "";
    }

    /** {@code href} against the folder of {@code from}: its #fragment dropped, %-escapes decoded, "." and ".." folded. */
    static String resolve(String from, String href) {
        int h = href.indexOf('#');
        if (h >= 0) href = href.substring(0, h);
        try { href = URLDecoder.decode(href.replace("+", "%2B"), "UTF-8"); } catch (Exception x) { /* keep it as written */ }
        int slash = from.lastIndexOf('/');
        List<String> out = new ArrayList<>();
        for (String p : ((slash < 0 ? "" : from.substring(0, slash + 1)) + href).split("/")) {
            if (p.equals("..")) { if (!out.isEmpty()) out.remove(out.size() - 1); }
            else if (!p.isEmpty() && !p.equals(".")) out.add(p);
        }
        return String.join("/", out);
    }

    private static Document parse(ZipFile zip, String path) throws IOException {
        ZipEntry e = zip.getEntry(path);
        if (e == null) throw new IOException("Missing " + path);
        try (InputStream in = zip.getInputStream(e)) {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            try { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false); } catch (Exception x) { /* Android may not know it */ }
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setEntityResolver((p, s) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
            return b.parse(in);
        } catch (IOException x) { throw x;
        } catch (Exception x) { throw new IOException("Can't read " + path); }
    }

    /** {@code root} and everything under it with this local name, in document order. */
    private static List<Element> all(Element root, String local) {
        List<Element> out = new ArrayList<>();
        if (local.equals(root.getLocalName())) out.add(root);
        NodeList l = root.getElementsByTagNameNS("*", local);
        for (int i = 0; i < l.getLength(); i++) out.add((Element) l.item(i));
        return out;
    }

    private static List<Element> children(Element p, String local) {
        List<Element> out = new ArrayList<>();
        for (Node n = p.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element && local.equals(n.getLocalName())) out.add((Element) n);
        return out;
    }

    private static Element child(Element p, String local) { List<Element> c = children(p, local); return c.isEmpty() ? null : c.get(0); }
}
