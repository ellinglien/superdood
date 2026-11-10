package io.github.mpdairy.monopaint;

/** System Settings (the owner, 2026-11-04): the flash gate, the finger taps' and the edge strip hold's choices, its words. */
public final class SystemSettingsChecks {
    public static void main(String[] args) throws java.io.IOException {
        gate(); taps(); hold(); words(); wired();
        System.out.println("PASS: System Settings: Automatic Refresh off stops all the app's own refreshing (flashes, clean redraws, grey passes), a Refresh asked for still flashes; two- and three-finger taps do Undo, Redo, Refresh or Nothing (Undo and Redo at first), the edge strip hold the Trash, the Lasso or Nothing (the Trash at first), each kept under a key of its own; its words keep docs/TEXT-STYLE.md");
    }
    static void gate() {
        check(SystemSettings.flashes(false, true) && SystemSettings.flashes(true, true), "Automatic Refresh on: every flash as before");
        check(!SystemSettings.flashes(false, false), "Automatic Refresh off: no flash the app makes on its own");
        check(SystemSettings.flashes(true, false), "Automatic Refresh off: Refresh (or a tap set to it) still flashes");
    }
    static void taps() {
        FingerTool.Tap two = FingerTool.TWO_FIRST, three = FingerTool.THREE_FIRST;
        check(two == FingerTool.Tap.UNDO && three == FingerTool.Tap.REDO, "At first a two-finger tap undoes and a three-finger tap redoes, as always");
        check(FingerTool.tap(2, two, three) == FingerTool.Tap.UNDO && FingerTool.tap(3, two, three) == FingerTool.Tap.REDO
                && FingerTool.tap(0, two, three) == FingerTool.Tap.NOTHING && FingerTool.tap(4, two, three) == FingerTool.Tap.NOTHING, "Two fingers do the two's choice, three the three's; any other count nothing");
        check(FingerTool.tap(2, FingerTool.Tap.REFRESH, FingerTool.Tap.NOTHING) == FingerTool.Tap.REFRESH && FingerTool.tap(3, FingerTool.Tap.REFRESH, FingerTool.Tap.NOTHING) == FingerTool.Tap.NOTHING, "Each count its own choice");
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (FingerTool.Tap t : FingerTool.Tap.values()) check(FingerTool.Tap.saved(t.key, two) == t && keys.add(t.key), t + " is kept under a key of its own");
        check(FingerTool.Tap.saved("", two) == FingerTool.Tap.UNDO && FingerTool.Tap.saved("", three) == FingerTool.Tap.REDO && FingerTool.Tap.saved("zen", three) == FingerTool.Tap.REDO,
                "Nothing saved (or a key that doesn't read): what the tap always did");
        check(FingerTool.Tap.REFRESH.label.equals(DrawLayout.REFRESH), "Refresh is the column's Refresh, one word per concept");
        check(!FingerTool.TWO_TAP.equals(FingerTool.THREE_TAP) && !FingerTool.TWO_TAP.equals(FingerTool.KEY) && !FingerTool.THREE_TAP.equals(FingerTool.KEY), "Each kept under its own key");
        // The palm gate holds whatever the tap does: it is asked before the choice is read (DrawingPad.historyTap).
        check(!FingerTool.tapCounts(1000, 1000 - FingerTool.PEN_PALM_MS + 1, false) && !FingerTool.tapCounts(5000, 0, true), "A tap while the pen is near or down counts for nothing, whatever it is set to");
    }
    static void hold() {
        check(EdgeHold.Choice.saved("") == EdgeHold.Choice.TRASH && EdgeHold.Choice.saved("swipe") == EdgeHold.Choice.TRASH, "At first the hold is the Trash, as always");
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (EdgeHold.Choice c : EdgeHold.Choice.values()) check(EdgeHold.Choice.saved(c.key) == c && keys.add(c.key), c + " is kept under a key of its own");
        check(EdgeHold.Choice.LASSO.label.equals(DrawLayout.LASSO), "Lasso is the column's Lasso");
    }
    static void words() {
        String[] labels = {SystemSettings.TITLE, SystemSettings.AUTO_REFRESH, SystemSettings.TWO_TAP, SystemSettings.THREE_TAP, SystemSettings.EDGE_HOLD, SystemSettings.ON, SystemSettings.OFF, SystemSettings.READ_HANDWRITING, SystemSettings.TURN_ON};
        for (String l : labels) check(l.split(" ").length <= 3 && l.matches("[A-Z][A-Za-z-]*( [A-Z][A-Za-z-]*)*"), l + ": a label, Title Case, three words at most (docs/TEXT-STYLE.md)");
        for (FingerTool.Tap t : FingerTool.Tap.values()) check(t.label.matches("[A-Z][a-z]+"), t.label + ": a chip's one word");
        for (EdgeHold.Choice c : EdgeHold.Choice.values()) check(c.label.matches("[A-Z][a-z]+"), c.label + ": a chip's one word");
        check(SystemSettings.AUTO_REFRESH_NOTE.matches("[A-Z][a-z ,]+") && SystemSettings.AUTO_REFRESH_NOTE.length() <= 40,
                "The note: sentence case, short, no symbols");
        check(SystemSettings.READ_HANDWRITING_NOTE.length() <= 30, "Read Handwriting's note: about 30 characters");
        check(!SystemSettings.READ_HANDWRITING_ABOUT.contains(";"), "Read Handwriting's first-time words: no semicolon (docs/TEXT-STYLE.md)");
        check(!SystemSettings.READ_HANDWRITING_ABOUT.toLowerCase().matches(".*\\byou(r)?\\b.*"), "Read Handwriting's first-time words: no \"you\"");
    }
    /** The sites read the gate: every flash the app makes on its own goes through it; Refresh is asked for. */
    static void wired() throws java.io.IOException {
        String pad = source("DrawingPad"), screens = source("NotebookScreens"), bar = source("InkWashBar");
        check(pad.contains("if (!flashes()) { refreshRetries = 0; unrefreshed = ghosted = false; app.redrawZen(pageArea()); return; }") && pad.contains("boolean clean = direct != null && ghosted;")
                && pad.contains("restore, true, afterSplash || ghosted && flashes());") && pad.contains("boolean cleans = SystemSettings.flashes(false, app.prefs.autoRefresh());") && pad.contains("if (!cleans) return;") && pad.contains("return SystemSettings.flashes(asked, app.prefs.autoRefresh());"),
                "The page's flashes (a page turn, zoom, erasing, an effect's trail, back from another screen) ask the gate");
        check(pad.contains("ghosted = unrefreshed = asked = true; refreshSoon(\"pinch or pan ended\"") && pad.contains("private void viewportChanged() {\n        // A zoom always refreshes, whatever Automatic Refresh says (the owner, 2026-11-06: \"force it\").\n        ghosted = unrefreshed = asked = true;"),
                "A zoom, the pinch or the zoom button, refreshes as if asked, Automatic Refresh on or off");
        check(screens.contains("SystemSettings.flashes(false, app.prefs.autoRefresh())"), "The notebook screens' flash asks the gate");
        check(source("BrushTestPad").contains("SystemSettings.flashes(false,") && !source("BrushTestPad").contains("Ui.cleanWindow("), "The test area's redraw asks the gate");
        check(bar.contains("app.pad.refreshAsked(\"Refresh button\")") && pad.contains("else refreshAsked(tap.label + \" tap\");"), "Refresh, the button or a tap, is asked for");
        check(bar.contains("EdgeHold.Choice choice = app.prefs.edgeHold();"), "The edge strip hold reads its choice");
        int flashes = 0;
        for (java.io.File f : new java.io.File("app/src/main/java/io/github/mpdairy/monopaint").listFiles())
            if (f.getName().endsWith(".java") && !f.getName().equals("Ui.java"))
                flashes += new String(java.nio.file.Files.readAllBytes(f.toPath())).split("Ui\\.flashWindow\\(", -1).length - 1;
        check(flashes == 4, "Four redraws outside Ui, each behind the gate: the page's auto-refresh, its return, the notebook screens, the test area (" + flashes + ")");
    }
    private static String source(String name) throws java.io.IOException {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/src/main/java/io/github/mpdairy/monopaint/" + name + ".java")));
    }
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
}
