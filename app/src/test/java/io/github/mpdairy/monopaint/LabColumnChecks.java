package io.github.mpdairy.monopaint;

import java.util.List;
import java.util.Random;

/**
 * What Brush Lab's Grey Shift set and Open sheet and the drawing screen's Show Kinds, sand timer, wet indicator and
 * picker box decide, in pure Java so the Manta and the browser (web/engine's WebEngine) agree: {@link BrushLab#greys},
 * {@link LabLayout}'s sheet words, {@link DrawLayout#kindsName} and the rest, {@link EraseKind#hiddenNote},
 * {@link EraseKind#takenUp}, {@link ToneDocument#compositeTones} with Show Kinds' steps, {@link SandTimer#labels},
 * {@link SandTimer#waits}, {@link SandTimer#countdown}, {@link WetLook} and {@link DrawLayout#boxSide}.
 */
public final class LabColumnChecks {
    static int checks;
    public static void main(String[] args) {
        Assets.install(Assets.folder(Assets.FOLDER));
        greys(); kinds(); timer(); wet(); box();
        System.out.println("PASS: Brush Lab and column checks (" + checks + "): Grey Shift's set, Show Kinds' popup and screen, the sand timer, the wet indicator, the picker box");
    }
    private static void check(boolean ok, String what) { checks++; if (!ok) throw new AssertionError(what); }

    private static void greys() {
        BrushLab lab = new BrushLab(BrushLabChecks.slots(), 2, new Random(4));
        List<BrushLab.Grey> greys = lab.greys();
        int n = SwatchPicker.size(SwatchPicker.palette(lab.parts()));
        check(greys.size() == n && n >= 2, "Grey Shift's set: the brush's greys, " + n);
        for (int k = 0; k < n; k++) check(greys.get(k).in == SwatchPicker.in(lab.parts(), k) && !greys.get(k).said.isEmpty(), "Grey " + k + " in or out, with its words");
        boolean was = greys.get(0).in;
        check(lab.toggleGrey(0) && lab.greys().get(0).in != was, "A tap takes a grey in or out");
        check(lab.greys().get(0).said.endsWith(", left out") == was, "Left out, it says so: " + lab.greys().get(0).said);
        check(new LabLayout(LabLayoutChecks.MANTA[0], LabLayoutChecks.MANTA[1]).sheetLabel() == 140 && LabLayout.PASTE_CODE.equals("Paste code"), "Open's sheet: its labels' width and words");
    }

    private static void kinds() {
        int shown = EraseKind.withStep(EraseKind.withStep(0, 1, NotebookOptions.HIDDEN), 3, NotebookOptions.HALF);
        check(DrawLayout.kindsFirst(-1) == 1 && DrawLayout.kindsFirst(2) == 0, "Template's column only while the page has guides");
        check(DrawLayout.kindsName(0).equals("Template") && DrawLayout.kindsName(3).equals("Dry Media"), "The columns' names");
        check(DrawLayout.kindsStep(1, shown, -1) == NotebookOptions.HIDDEN && DrawLayout.kindsStep(0, shown, 2) == 2 && DrawLayout.kindsStep(2, shown, -1) == NotebookOptions.FULL, "Each column's step");
        check(DrawLayout.kindsSaid(1, NotebookOptions.HIDDEN).equals("Pen hidden") && DrawLayout.kindsSaid(0, 1).equals("Template Visibility: Half"), "What an eye says");
        check(DrawLayout.KINDS_EYES.length == NotebookOptions.GUIDE_STEPS.length, "An eye for each step");
        check("Pen is hidden, see Show Kinds".equals(EraseKind.hiddenNote(shown, false, ToneDocument.KIND_PEN, 0)), "A pen refused while Pen is hidden");
        check(EraseKind.hiddenNote(shown, false, ToneDocument.KIND_PENCIL, 0) == null, "Dry media at Half draws");
        int erasing = EraseKind.takes(ToneDocument.KIND_PEN, EraseKind.hidden(shown));
        check(erasing == 0 && EraseKind.hiddenNote(shown, true, 0, erasing) != null && EraseKind.hiddenNote(shown, true, 0, EraseKind.takes(0, EraseKind.hidden(shown))) == null,
                "The eraser refused when all it takes is hidden, not otherwise");
        check("Dry media is half".equals(EraseKind.takenUp(shown, ToneDocument.KIND_PENCIL)) && EraseKind.takenUp(shown, ToneDocument.KIND_PAINT) == null, "Taken up, a kind not Full says so");
        // The screen's composite: a hidden kind's plane gone, a Half one halfway; the page's own composite whole.
        ToneDocument d = InkWashLayers.blank(60, 40);
        KindLayersChecks.block(d, InkWashLayers.INK, ToneDocument.KIND_PEN, 5, 5, 10, 10, 0);
        KindLayersChecks.block(d, InkWashLayers.INK, ToneDocument.KIND_PENCIL, 30, 5, 10, 10, 0);
        int[] whole = new int[60 * 40], screen = new int[60 * 40];
        d.compositeTones(0, 0, 60, 40, whole, 0, 60);
        d.compositeTones(0, 0, 60, 40, screen, 0, 60, shown);
        check(whole[10 * 60 + 10] == d.compositeTone(10, 10) && whole[10 * 60 + 10] < 40, "All Full: the page's composite, the pen's block dark");
        check(screen[10 * 60 + 10] == d.compositeTone(0, 0), "Pen hidden: its block shows the paper");
        check(screen[10 * 60 + 35] > whole[10 * 60 + 35] && screen[10 * 60 + 35] < d.compositeTone(0, 0), "Dry media at Half: lighter, not gone");
        int[] none = new int[60 * 40];
        d.compositeTones(0, 0, 60, 40, none, 0, 60, 0);
        check(java.util.Arrays.equals(none, whole), "Steps all Full are the page's composite");
    }

    private static void timer() {
        String[] labels = SandTimer.labels();
        check(labels.length == SandTimer.SECONDS.length && labels[0].equals(NotebookRules.OFF), "Its menu: Off, then the times: " + String.join(", ", labels));
        check(SandTimer.chosen(SandTimer.SECONDS[2]) == 2 && SandTimer.chosen(7) == -1 && SandTimer.chosen(0) == 0, "The time it was set to, marked");
        SandTimer t = new SandTimer();
        check(t.countdown(0) == null && !t.waits(0, true), "Off: its icon, no wait");
        t.set(60, 1000);
        check("1:00".equals(t.countdown(1000)) && "0:30".equals(t.countdown(31000)), "Running: its countdown " + t.countdown(31000));
        check(NotebookRules.countdownSaid("0:30").equals("Time left 0:30") && NotebookRules.countdownSaid("Saw\n1:42").equals("Saw, time left 1:42"), "What the countdown says to a screen reader");
        check(t.waits(61000, true) && !t.waits(61000, false) && !t.waits(30000, true), "At zero it waits for the pen to lift");
    }

    private static void wet() {
        WetLook w = new WetLook();
        w.look(WetLook.State.DRY, 0, false, false, false, false);
        check(w.state == WetLook.State.DRY && w.next == -1, "Dry: dashed, no look again");
        w.look(WetLook.State.DRY, 0, true, true, true, false);
        check(w.state == null && w.next == WetLook.STROKING_MS, "The pen down: it waits");
        w.look(WetLook.State.DRY, 0, false, true, true, false);
        check(w.state == WetLook.State.MOVING && w.next == WetLook.WET_MS, "Paint moving: half full, a look a second");
        w.look(WetLook.State.MOVING, 0, false, true, false, false);
        check(w.state == WetLook.State.WET, "Wet at rest: full");
        WetLook r = new WetLook();
        r.look(WetLook.State.DRY, 100, false, true, true, true);
        check(r.state == null && r.next == WetLook.RENDERING_MS, "A quick stroke's render alone: not yet");
        r.look(WetLook.State.DRY, 100 + WetLook.RENDERING_MS, false, true, true, true);
        check(r.state == WetLook.State.MOVING, "Lasting a second, it shows");
        check(WetLook.said(WetLook.State.WET).equals("Wet. Tap to dry.") && WetLook.icon(WetLook.State.DRY).equals("ic_drop_dashed"), "Its words and drawing");
    }

    private static void box() {
        DrawLayout manta = new DrawLayout(LabLayoutChecks.MANTA[0], LabLayoutChecks.MANTA[1]);
        check(DrawLayout.boxSide(2 * manta.half(), manta.box) == manta.faceSide(), "The column's box: its squares as before");
        LabLayout lab = new LabLayout(LabLayoutChecks.MANTA[0], LabLayoutChecks.MANTA[1]);
        check(DrawLayout.boxSide(lab.pickerThick, lab.picker) > 0, "Brush Lab's box: squares that fit");
    }
}
