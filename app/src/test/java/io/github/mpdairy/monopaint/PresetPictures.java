package io.github.mpdairy.monopaint;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The Print style's Basic tools, the everyday pens (Ballpoint, Gel pen, Fountain pen, Highlighter), the eight that
 * show the engine's choices no other preset did (Spirograph to Crayon scratch) and the Wet Gouache and Oils (the wet paint
 * spec), built dial by
 * dial and encoded: {@code java PresetPictures [sheet.png]} prints each one's {@link ToolPresets} row (its code checked
 * by a decode round-trip) and draws a contact sheet with the app's engine, for looking (not a check): per brush a
 * pressure-varying S-curve at Dark, a scribble at Mid and its card's loop. The rows in {@link ToolPresets} are these
 * codes; change a brush here, run it, and paste the rows again.
 */
public final class PresetPictures {
    /** A preset to be: its style, name, caption, the seed it carries, and the brush. */
    static final class Made {
        final String style, name, line; final long seed; final ModularBrush brush;
        Made(String style, String name, String line, long seed, ModularBrush brush) { this.style = style; this.name = name; this.line = line; this.seed = seed; this.brush = brush; }
        String code() { return ModularCode.encode(brush); }
        String row() { return "{\"" + style + "\", \"" + name + "\", \"" + code() + "\", \"" + line + "\", \"" + seed + "\"},"; }
    }

    public static void main(String[] args) throws Exception {
        Assets.install(Assets.folder(Assets.FOLDER));
        List<Made> all = made();
        for (Made m : all) {
            ModularBrush back = ModularCode.decode(m.code());
            if (back == null || !ModularCode.encode(back).equals(m.code()) || back.style != ModularStyle.named(m.style)) throw new AssertionError(m.name + " doesn't round-trip");
            System.out.println(m.row());
        }
        if (args.length > 0) sheet(all, new File(args[0]));
    }

    // ---- The brushes ----

    /** The Basic tools that come with Second Ink On ({@link ToolPresets#ROWS}); the bar's Ink wash and Watercolour wash do too ({@link ToolSlots#FIRST}). */
    static final List<String> SECOND_INK = java.util.Arrays.asList("Dry glaze", "Wet brush", "Broad oils", "Cover hake", "Wash behind", "Wet blooms", "Tilt oils", "Cracking paint", "Flat hake", "Granulating", "Rough hake", "Grainy wash", "Watercolour backrun", "Flat oils", "Oil glaze", "Opaque flat", "Grey wash", "Scumble", "Hatch pen", "Fountain pen");
    static List<Made> made() {
        List<Made> out = new ArrayList<>();
        // Print: Screentone, a broad fill (Tone's own size) laid as a pattern.
        out.add(tone("Dot tone", "even dots", 3101, 0, .42, 6, 45));
        out.add(tone("Line tone", "fine lines", 3102, 1, .45, 6, 45));
        out.add(tone("Sand tone", "speckled grain", 3103, 2, .4, 6, 45));
        {
            // "change gradient tone to full pressure for dots (so it's variable)" (the owner, 2026-11-06): its dots light to full as it presses.
            Made gradient = tone("Gradient tone", "fades down", 3104, 3, .6, 7, 45);
            fullPressDark(gradient.brush);
            out.add(gradient);
        }
        out.add(tone("Hatched tone", "crossed lines", 3105, 4, .75, 8, 45));
        // Print: ink through a Print Shading. A wet ink body glazes grey, so the screen breaks it into its pattern;
        // pressure darkens it, so the pattern fills in as the pen presses.
        {
            ModularBrush b = brush("Print", "Ben-Day brush", 3111, PartChoice.CHISEL);
            tip(b).set(PartDial.ASPECT, .55); tip(b).set(PartDial.ANGLE, 40); tip(b).set(PartDial.WEIGHT, 1.5); tip(b).set(PartDial.SOFT, .6);
            printInk(b, 6, 5, 45, 0, .3, 0, .5, 0);
            hand(b, PartChoice.PRESS_DARKER);
            out.add(new Made("Print", "Ben-Day brush", "comic dots", 3111, b));
        }
        {
            ModularBrush b = brush("Print", "Rosette ink", 3112, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.5); tip(b).set(PartDial.SOFT, .8); tip(b).set(PartDial.TAPER_IN, .2); tip(b).set(PartDial.TAPER_OUT, .3);
            printInk(b, 5, 9, 45, 0, 0, 0, .7, 0);
            hand(b, PartChoice.PRESS_BOTH);
            out.add(new Made("Print", "Rosette ink", "rosette dots", 3112, b));
        }
        {
            ModularBrush b = brush("Print", "Halftone brush", 3113, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.5); tip(b).set(PartDial.SOFT, .8); tip(b).set(PartDial.TAPER_IN, .3); tip(b).set(PartDial.TAPER_OUT, .5);
            printInk(b, 1, 7, 45, 0, .25, 0, .7, 0);
            hand(b, PartChoice.PRESS_BOTH);
            out.add(new Made("Print", "Halftone brush", "dots swell", 3113, b));
        }
        // Print: dry media through a Print Shading: their own grain gives the screen greys to work with.
        {
            ModularBrush b = brush("Print", "Mezzotint", 3121, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1); tip(b).set(PartDial.SOFT, .4);
            ModularBrush.Part m = medium(b, PartChoice.CHARCOAL);
            m.set(PartDial.SPREAD, .25); m.set(PartDial.HOLD, .45);
            screen(m, 9, 7, 30, 0, 0, .15);
            paper(b, PartChoice.COLD_PRESS, .5);
            // Tilt Shade with full Pressure Dark (2026-11-06, the owner: "all of those patterns"): the dashes fill in as it presses.
            ModularBrush.Part h = new ModularBrush.Part(PartChoice.TILT_SIDE);
            h.set(PartDial.PD, 1);
            b.set(BrushPart.HAND, ModularBrush.namedHand(h));
            out.add(new Made("Print", "Mezzotint", "velvet dashes", 3121, b));
        }
        {
            ModularBrush b = brush("Print", "Dither pencil", 3122, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.1);
            ModularBrush.Part m = medium(b, PartChoice.GRAPHITE);
            m.set(PartDial.GRADE, 1);
            screen(m, 3, 6, 45, 0, 0, 0);
            paper(b, PartChoice.COLD_PRESS, .4);
            hand(b, PartChoice.PRESS_BOTH);
            out.add(new Made("Print", "Dither pencil", "pixel shading", 3122, b));
        }
        {
            ModularBrush b = brush("Print", "Engraving pen", 3123, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.4); tip(b).set(PartDial.SOFT, .6); tip(b).set(PartDial.TAPER_IN, .3); tip(b).set(PartDial.TAPER_OUT, .4);
            printInk(b, 2, 6, 30, 0, 0, 0, .7, 0);
            hand(b, PartChoice.PRESS_BOTH);
            out.add(new Made("Print", "Engraving pen", "engraved lines", 3123, b));
        }
        {
            ModularBrush b = brush("Print", "Wavy-line pen", 3124, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.4); tip(b).set(PartDial.SOFT, .6); tip(b).set(PartDial.TAPER_IN, .3); tip(b).set(PartDial.TAPER_OUT, .4);
            printInk(b, 8, 7, 0, 0, 0, 0, .7, 0);
            hand(b, PartChoice.PRESS_BOTH);
            out.add(new Made("Print", "Wavy-line pen", "banknote style", 3124, b));
        }
        // The texture markers (2026-11-06, the owner: "all of those patterns would be awesome"): one a Print Shading no
        // shown tool had, Riso's ink line through it, the pattern filling in as it presses.
        out.add(texture("Woodcut", "carved lines", 3231, 12, 6, 30));
        out.add(texture("Diamond dots", "dotted lattice", 3232, 10, 6, 45));
        out.add(texture("Scribble", "looping lines", 3233, 13, 6, 30));
        out.add(texture("Maze", "winding paths", 3234, ScreenChecks.MAZE, 5, 0));
        out.add(texture("Brick", "brick courses", 3235, ScreenChecks.BRICK, 4, 0));
        out.add(texture("Basketweave", "woven strands", 3236, ScreenChecks.BASKETWEAVE, 4, 0));
        out.add(texture("Waves", "wave scales", 3237, ScreenChecks.WAVES, 4, 0));
        out.add(texture("Honeycomb", "hexagons", 3238, ScreenChecks.HONEYCOMB, 4, 0));
        out.add(texture("Rings", "ripples", 3239, ScreenChecks.RINGS, 5, 0));
        // Pixels and Stitches (2026-11-06, the owner: "a pixel texture brush", "maybe also a cross-stitch texture"): a grey a
        // cell, pressure picking the grey; square to the page (Angle's default), cells big enough to see on the Manta.
        out.add(texture("Pixel pen", "chunky pixels", 3240, ScreenChecks.PIXELS, 10, 45));
        out.add(texture("Stitch pen", "cross-stitches", 3241, ScreenChecks.STITCHES, 12, 45));
        // Everyday pens.
        {
            // Ballpoint: thin, a little grainy, darker pressed, now and then skipping. Breaks .2 (the owner, 2026-10-26: at .5
            // "too wobbly and leaves too many gaps"): its sideways waver 40% of what it was, a skip about every 2000 px, ~4 px.
            ModularBrush b = brush("Fineliner", "Ballpoint", 3131, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, .8); tip(b).set(PartDial.DRY, .15); tip(b).set(PartDial.SOFT, .1);
            ModularBrush.Part m = medium(b, PartChoice.INK);
            m.set(PartDial.CHAOS, .2);
            paper(b, PartChoice.COLD_PRESS, .2);
            ModularBrush.Part h = new ModularBrush.Part(PartChoice.PRESS_DARKER);
            h.set(PartDial.PD, .5);
            b.set(BrushPart.HAND, ModularBrush.namedHand(h));
            out.add(new Made("Fineliner", "Ballpoint", "grainy, skips", 3131, b));
        }
        {
            // Gel pen: smooth, even, a little bold.
            ModularBrush b = brush("Fineliner", "Gel pen", 3132, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.4); tip(b).set(PartDial.SOFT, 0);
            medium(b, PartChoice.INK).set(PartDial.POOL, .15);
            hand(b, PartChoice.STEADY);
            out.add(new Made("Fineliner", "Gel pen", "smooth, bold", 3132, b));
        }
        {
            // Fountain pen: swells a little pressed, pools where it starts.
            ModularBrush b = brush("Fineliner", "Fountain pen", 3133, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1); tip(b).set(PartDial.SOFT, .1); tip(b).set(PartDial.TAPER_OUT, .3);
            ModularBrush.Part m = medium(b, PartChoice.INK);
            m.set(PartDial.POOL, .9); m.set(PartDial.WET, .1);
            ModularBrush.Part h = new ModularBrush.Part(PartChoice.PRESS_WIDER);
            h.set(PartDial.PS, .75);
            b.set(BrushPart.HAND, ModularBrush.namedHand(h));
            out.add(new Made("Fineliner", "Fountain pen", "pools at start", 3133, b));
        }
        {
            // Highlighter: a broad chisel of wash that goes behind the lines (Blend Mode's Behind).
            ModularBrush b = brush("Marker", "Highlighter", 3134, PartChoice.CHISEL);
            tip(b).set(PartDial.ASPECT, .3); tip(b).set(PartDial.ANGLE, 70); tip(b).set(PartDial.WEIGHT, 1.2); tip(b).set(PartDial.SOFT, 0);
            ModularBrush.Part m = medium(b, PartChoice.WASH);
            m.set(PartDial.WET, 0); m.set(PartDial.POOL, .2); m.set(PartDial.BLOOM, 0); m.set(PartDial.LINE, 0);
            m.set(PartDial.M_BLEND, 2);
            hand(b, PartChoice.STEADY);
            out.add(new Made("Marker", "Highlighter", "behind marks", 3134, b));
        }
        // The brush engine's choices no preset showed (the owner's list, 2026-10-22), each set at the Lab's middle but what shows it.
        {
            // Wax crayon: Crayon on cold press, so light pressure catches the grain's peaks and a firm one goes solid.
            ModularBrush b = brush("Pastels", "Wax crayon", 3141, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.2);
            medium(b, PartChoice.CRAYON);
            paper(b, PartChoice.COLD_PRESS, .5);
            out.add(new Made("Pastels", "Wax crayon", "waxy layers", 3141, b));
        }
        {
            // Crayon scratch: the same crayon with Scratch on, scraping what is there back to white.
            ModularBrush b = brush("Pastels", "Crayon scratch", 3142, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.2);
            medium(b, PartChoice.CRAYON).set(PartDial.SCRATCH, 1);
            paper(b, PartChoice.COLD_PRESS, .5);
            out.add(new Made("Pastels", "Crayon scratch", "scratches out", 3142, b));
        }
        {
            // Spirograph: Spiro at its own defaults, in Ring, the toy itself (the owner, 2026-10-25: "it's supposed to start
            // with figuring out the size of the circle but it doesnt work"; along the line it had no ring to size).
            ModularBrush b = brush("Fineliner", "Spirograph", 3143, PartChoice.SPIRO);
            tip(b).set(PartDial.SPIRO_MODE, 1);
            out.add(new Made("Fineliner", "Spirograph", "toy loops", 3143, b));
        }
        {
            // Colouring pen: a flat wash that keeps inside the ink lines (Stay in Lines), flooding a little past the nib.
            ModularBrush b = brush("Marker", "Colouring pen", 3144, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.4);
            ModularBrush.Part m = medium(b, PartChoice.WASH);
            m.set(PartDial.WET, .3); m.set(PartDial.BLOOM, 0);
            b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.STAY_IN));
            out.add(new Made("Marker", "Colouring pen", "stays in lines", 3144, b));
        }
        {
            // Fill in (the owner, 2026-10-23: "maybe this app doesn't use a bucket, just a brush with fill in the lines"): the
            // Colouring pen's flat wash at Flood 1, so a few scribbles inside a shape fill it to its lines; on open paper a loop fills (Filled shape merged in, 2026-10-31).
            ModularBrush b = brush("Marker", "Fill in", 3149, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.4);
            ModularBrush.Part m = medium(b, PartChoice.WASH);
            m.set(PartDial.WET, .3); m.set(PartDial.BLOOM, 0);
            ModularBrush.Part c = new ModularBrush.Part(PartChoice.STAY_IN);
            c.set(PartDial.FLOOD, 1);
            b.set(BrushPart.CARRY, c);
            out.add(new Made("Marker", "Fill in", "fills shapes", 3149, b));
        }
        {
            // Vanishing ink (the owner's Disappearing Ink, too wide for the card): a pen line that fades away (Fading, its Fades In 20 s as made).
            ModularBrush b = brush("Fineliner", "Vanishing ink", 3146, PartChoice.ROUND);
            b.set(BrushPart.LIFE, timed(PartChoice.FADE, PartDial.FADE_IN, 20));
            out.add(new Made("Fineliner", "Vanishing ink", "fades away", 3146, b));
        }
        {
            // Lemon juice: a nib dipped in lemon juice, faint at first, darkening in as it dries.
            ModularBrush b = brush("Dip pen", "Lemon juice", 3147, PartChoice.ROUND);
            tip(b).set(PartDial.TAPER_OUT, .3);
            b.set(BrushPart.LIFE, timed(PartChoice.LEMON, PartDial.DRY_IN, 300));
            hand(b, PartChoice.PRESS_WIDER);
            out.add(new Made("Dip pen", "Lemon juice", "appears later", 3147, b));
        }
        {
            // Metalpoint (the module gaps survey, 2026-11-01): a silver stylus on a toothed ground, a hard, fine, pale grey
            // line that barely answers pressure and tarnishes darker over minutes (Later's Tarnish).
            ModularBrush b = brush("Pencil", "Metalpoint", 3191, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, .6); tip(b).set(PartDial.SOFT, .1);
            medium(b, PartChoice.GRAPHITE).set(PartDial.GRADE, 0);
            paper(b, PartChoice.COLD_PRESS, .4);
            ModularBrush.Part h = new ModularBrush.Part(PartChoice.PRESS_DARKER);
            h.set(PartDial.PD, .2);
            b.set(BrushPart.HAND, ModularBrush.namedHand(h));
            b.set(BrushPart.LATER, timed(PartChoice.LATER_TARNISH, PartDial.DRY_IN, 300));
            out.add(new Made("Pencil", "Metalpoint", "tarnishes", 3191, b));
        }
        {
            // Springy brush: a round brush on Control's Springy, swelling and darkening as it presses and thinning when quick.
            ModularBrush b = brush("Brush pen", "Springy brush", 3148, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.3); tip(b).set(PartDial.TAPER_IN, .3); tip(b).set(PartDial.TAPER_OUT, .5); tip(b).set(PartDial.SOFT, .1);
            hand(b, PartChoice.BRUSH_HAND);
            out.add(new Made("Brush pen", "Springy brush", "bouncy swell", 3148, b));
        }
        {
            // Fude pen (the owner, 2026-10-26: Broad nib's drawing "is actually a fude bent fountain pen.. so tilting changes the
            // size of the nib"): a fountain pen's ink on a round nib whose width follows the pen's lean through the wide Tilt
            // Range, fine held upright, about five times as broad laid low, its bent face flattening as it lies; a little wider
            // pressed. Without a tilt reading, the natural hold's line. Broad nib's code name and seed, so a copy still finds it.
            ModularBrush b = brush("Dip pen", "Broad nib", Long.parseLong("1s13pn4", 36), PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.3); tip(b).set(PartDial.SOFT, .1); tip(b).set(PartDial.TAPER_OUT, .2);
            medium(b, PartChoice.INK).set(PartDial.POOL, .3);
            ModularBrush.Part h = new ModularBrush.Part(PartChoice.STEADY);
            h.set(PartDial.PS, .3); h.set(PartDial.TS, .5); h.set(PartDial.TW, .6); h.set(PartDial.TILT_RANGE, 1);
            b.set(BrushPart.HAND, ModularBrush.namedHand(h));
            out.add(new Made("Dip pen", "Fude pen", "broad on tilt", 2, b));
        }
        // Gouache and Oils (the wet paint spec; the owner's decision, 2026-10-22: they replace the old ones): Wet, each from
        // the old preset it follows (its tip and Control), on smooth paper (the wet paint lays solid) with no Impasto.
        out.add(wet("Gouache", "Wet gouache", "mixes wet", 3151, OPAQUE_ROUND, .5, 0, null));
        out.add(wet("Gouache", "Runny gouache", "runs downhill", 3152, OPAQUE_ROUND, .85, .6, null));
        out.add(wet("Gouache", "Wet flat", "flat, wet", 3153, FLAT_GOUACHE, .5, 0, null));
        out.add(wet("Gouache", "Gouache hake", "broad, wet", 3154, WIDE_GOUACHE, .5, 0, null));
        out.add(wet("Gouache", "Tacky gouache", "barely mixes", 3155, SWELL_GOUACHE, .15, 0, null));
        // Clear water: lifts wet or dry gouache and lets it run (the owner liked "clear water lifts wet or dry paint").
        out.add(wet("Gouache", "Gouache water", "lifts paint", 3156, OPAQUE_ROUND, .6, .3, new ModularBrush.Part(PartChoice.CLEAR_WATER)));
        // Oils stay wet a little longer and drag what they cross, so they get dirtier as they go (Drag); Loaded oils runs out (Paint Load).
        out.add(wet("Oils", "Wet oils", "drags, mixes", 3161, IMPASTO, .65, 0, new ModularBrush.Part(PartChoice.DRAG)));
        out.add(wet("Oils", "Loaded oils", "runs out", 3162, OIL_PAINT, .65, 0, null));
        out.add(wet("Oils", "Flat oils", "flat, drags", 3163, FLAT_BRISTLE, .65, 0, new ModularBrush.Part(PartChoice.DRAG)));
        out.add(wet("Oils", "Broad oils", "broad, drags", 3164, OIL_HAKE, .65, 0, new ModularBrush.Part(PartChoice.DRAG)));
        out.add(wet("Oils", "Tilt oils", "tilt to widen", 3165, SIDE_OILS, .6, 0, null));
        {
            Made glaze = wet("Oils", "Oil glaze", "thin, mixes", 3166, IMPASTO, .65, 0, null);
            glaze.brush.part(BrushPart.MEDIUM).set(PartDial.COVER, .55);
            out.add(glaze);
        }
        // Wet brush (the wet brush spec; the owner's go-ahead, 2026-10-22): the old Wet brush's tip and Control, Wet on a
        // Wash (the pair's wash half, where the wet paint is): MonoPaint's wet brush pen, ink in water held between strokes.
        out.add(wetBrush());
        // explore-media (2026-11-01; docs/archive/pictures/explore/media): white chalk for a toned or dark page, a dip nib that
        // runs dry, a water-soluble pencil.
        {
            // White chalk: charcoal laid white (Blend White), catching the tooth, denser pressed; for Page Colour's toned pages and a blackboard.
            ModularBrush b = brush("Charcoal", "White chalk", 3191, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, .7);
            medium(b, PartChoice.CHARCOAL).set(PartDial.M_BLEND, 1);
            paper(b, PartChoice.COLD_PRESS, .5);
            sideShade(b);
            out.add(new Made("Charcoal", "White chalk", "for dark pages", 3191, b));
        }
        {
            // Dip nib: ink that pools where it starts and runs dry into streaks along the line (Drag's Paint Load on a line,
            // barely picking up what it crosses); each stroke starts with at least a third of a dip, a rub on a grey dips it again.
            ModularBrush b = brush("Dip pen", "Dip nib", 3192, PartChoice.ROUND);
            tip(b).set(PartDial.TAPER_OUT, .2);
            medium(b, PartChoice.INK).set(PartDial.POOL, .3);
            ModularBrush.Part c = new ModularBrush.Part(PartChoice.DRAG);
            c.set(PartDial.PICK, .05); c.set(PartDial.LAY, .1); c.set(PartDial.LOAD, 1); c.set(PartDial.PLOAD, .5); c.set(PartDial.PMIN, .35);
            b.set(BrushPart.CARRY, c);
            hand(b, PartChoice.PRESS_WIDER);
            out.add(new Made("Dip pen", "Dip nib", "runs dry", 3192, b));
        }
        {
            // Water pencil: a soft water-soluble graphite (4B); the Water brush melts it into a wash.
            ModularBrush b = brush("Pencil", "Water pencil", 3193, PartChoice.ROUND);
            ModularBrush.Part m = medium(b, PartChoice.GRAPHITE);
            m.set(PartDial.GRADE, .7); m.set(PartDial.SOLUBLE, 1);
            sideShade(b);
            out.add(new Made("Pencil", "Water pencil", "water over it", 3193, b));
        }
        {
            // Soft pencil (the owner, 2026-11-06: "a softer pencil option"): a 4B to 6B lead, broader than Another pencil and
            // dark from the lightest touch (soft Graphite .8, no Pressure Dark), grainy on cold press, a little smudgy (Messy
            // Edge); pressed, a little wider; laid low, the side of the lead (Tilt Shading).
            ModularBrush b = brush("Pencil", "Soft pencil", 3222, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.5); tip(b).set(PartDial.SOFT, .3);
            ModularBrush.Part m = medium(b, PartChoice.GRAPHITE);
            m.set(PartDial.GRADE, .8); m.set(PartDial.SPREAD, .15);
            paper(b, PartChoice.COLD_PRESS, .7);
            ModularBrush.Part h = new ModularBrush.Part(PartChoice.PRESS_WIDER);
            h.set(PartDial.PS, .3); h.set(PartDial.SIDE_SHADE, 1);
            b.set(BrushPart.HAND, ModularBrush.namedHand(h));
            out.add(new Made("Pencil", "Soft pencil", "dark, grainy", 3222, b));
        }
        // The nibs (explore-nibs), one each: a ruling pen on cold press so its dry blade skips on the tooth; a twig in ink;
        // a fan brush of ink hairs; a brayer that reprints what it rolls over.
        {
            ModularBrush b = brush("Dip pen", "Ruling pen", 3201, PartChoice.RULING);
            paper(b, PartChoice.COLD_PRESS, .4);
            out.add(new Made("Dip pen", "Ruling pen", "dries, blots", 3201, b));
        }
        {
            ModularBrush b = brush("Dip pen", "Twig pen", 3202, PartChoice.TWIG);
            out.add(new Made("Dip pen", "Twig pen", "blots, dries", 3202, b));
        }
        {
            ModularBrush b = brush("Dry brush", "Fan brush", 3203, PartChoice.FAN);
            out.add(new Made("Dry brush", "Fan brush", "hair lines", 3203, b));
        }
        {
            ModularBrush b = brush("Print", "Brayer", 3204, PartChoice.ROLLER);
            tip(b).set(PartDial.PICKUP, .7);
            out.add(new Made("Print", "Brayer", "reprints", 3204, b));
        }
        // Reprint (explore-reprint): brushed over marks already there, they lay them again in their print, no paint of
        // their own; a light touch reprints part way. A photocopy's toner on smooth paper; halftone dots on library paper.
        {
            ModularBrush b = brush("Print", "Photocopier", 3211, PartChoice.ROUND);
            medium(b, PartChoice.INK).set(PartDial.SCREEN, ScreenChecks.PHOTOCOPY);
            b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.REPRINT));
            out.add(new Made("Print", "Photocopier", "photocopies", 3211, b));
        }
        {
            ModularBrush b = brush("Print", "Newsprint", 3212, PartChoice.ROUND);
            ModularBrush.Part m = medium(b, PartChoice.INK);
            m.set(PartDial.SCREEN, 1); m.set(PartDial.CELL, 5); m.set(PartDial.S_GAIN, .5); m.set(PartDial.UNEVEN, .35);
            b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.REPRINT));
            paper(b, PartChoice.LIBRARY, .8);
            out.add(new Made("Print", "Newsprint", "halftone dots", 3212, b));
        }
        // The new print and age tools (explore-presets, 2026-11-01), so Risograph, Letterpress and Cracks are on the bar.
        // Riso: a broad chisel marker, its ink line (no wet body, which spread it wide) broken into Risograph's grain, a
        // little patchy and doubled; Steady, as a printer's flat ink is.
        {
            ModularBrush b = brush("Print", "Riso", 3213, PartChoice.CHISEL);
            tip(b).set(PartDial.ASPECT, .5); tip(b).set(PartDial.ANGLE, 40); tip(b).set(PartDial.WEIGHT, 1.5); tip(b).set(PartDial.SOFT, .5);
            printInk(b, ScreenChecks.RISO, 6, 0, 2, 0, .4, 0, 1);
            // "i adjusted riso to be pressure sensitive" (the owner, 2026-11-06).
            fullPressDark(b);
            out.add(new Made("Print", "Riso", "grainy print", 3213, b));
        }
        // Letterpress: a crisp round pen on cotton paper, its ink pressed into the paper as it sets (Later's Letterpress, a
        // light impression, so the middle mottles and the cotton's low places starve; cold press's grain hardly showed).
        {
            ModularBrush b = brush("Print", "Letterpress", 3214, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.3); tip(b).set(PartDial.SOFT, 0);
            paper(b, PartChoice.COTTON, .8);
            hand(b, PartChoice.PRESS_WIDER);
            ModularBrush.Part l = new ModularBrush.Part(PartChoice.LATER_PRESS);
            l.set(PartDial.IMPRESSION, .15); l.set(PartDial.SPREAD_IN, 2);
            b.set(BrushPart.LATER, l);
            out.add(new Made("Print", "Letterpress", "pressed type", 3214, b));
        }
        // Cracking paint (Cracks until 2026-11-06, the owner: "maybe rename it cracking paint"; its code name and seed kept,
        // so a copy still finds it): thick opaque paint from a round brush, Slow, crazing as it ages (Later's Cracks).
        {
            ModularBrush b = brush("Gouache", "Cracks", 3221, PartChoice.ROUND);
            tip(b).set(PartDial.WEIGHT, 1.5); tip(b).set(PartDial.SOFT, .2);
            ModularBrush.Part m = medium(b, PartChoice.COVER);
            m.set(PartDial.IMPASTO, .6);
            b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.DRYING));
            hand(b, PartChoice.PRESS_BOTH);
            ModularBrush.Part l = timed(PartChoice.LATER_CRACKS, PartDial.DRY_IN, 120);
            l.set(PartDial.CRACKS, .6);
            b.set(BrushPart.LATER, l);
            out.add(new Made("Gouache", "Cracking paint", "ages, cracks", 3221, b));
        }
        // The owner's Brush Lab saves (2026-11-06), kept as made (their codes and seeds), named as the owner did; built
        // here dial by dial from their codes so the rows stay PresetPictures'. Opaque flat: a flat nib of covering paint,
        // Fast wet, its Control pressed wider and tilted thinner, a little speed-thinned, its style Anything goes as saved.
        {
            ModularBrush b = brush("Anything goes", "Opaque flat", 1991437662L, PartChoice.FLAT_NIB);
            tip(b).set(PartDial.ASPECT, .2); tip(b).set(PartDial.ANGLE, 46.2); tip(b).set(PartDial.WEIGHT, .998); tip(b).set(PartDial.SOFT, .161);
            medium(b, PartChoice.COVER).set(PartDial.COVER, .8355);
            ModularBrush.Part life = new ModularBrush.Part(PartChoice.FAST_WET);
            life.set(PartDial.WINDOW, 5.045);
            b.set(BrushPart.LIFE, life);
            ModularBrush.Part h = new ModularBrush.Part(PartChoice.CUSTOM);
            h.set(PartDial.PS, 1); h.set(PartDial.TW, 1); h.set(PartDial.SD, .549); h.set(PartDial.GAIN, 1.5);
            b.set(BrushPart.HAND, ModularBrush.namedHand(h));
            out.add(new Made("Anything goes", "Opaque flat", "turns on tilt", 1991437662L, b));
        }
        // Watercolour splatter: a stipple tip of big, jittered wash dots that build up, pooling and blooming, drying
        // at once, pressed wider and a little darker.
        {
            ModularBrush b = brush("Watercolor", "Watercolour splatter", 4107326823L, PartChoice.STIPPLE);
            ModularBrush.Part t = tip(b);
            t.set(PartDial.DOT, .705); t.set(PartDial.SCATTER, .175); t.set(PartDial.JITTER, 1); t.set(PartDial.WEIGHT, 1.185); t.set(PartDial.BUILD_UP, 1);
            ModularBrush.Part m = medium(b, PartChoice.WASH);
            m.set(PartDial.WET, .706); m.set(PartDial.POOL, .669); m.set(PartDial.BLOOM, .508);
            ModularBrush.Part life = new ModularBrush.Part(PartChoice.DRYING);
            life.set(PartDial.DRYING, 0); life.set(PartDial.G_POOL, .08);
            b.set(BrushPart.LIFE, life);
            ModularBrush.Part h = new ModularBrush.Part(PartChoice.CUSTOM);
            h.set(PartDial.PS, 1); h.set(PartDial.PD, .5);
            b.set(BrushPart.HAND, ModularBrush.namedHand(h));
            out.add(new Made("Watercolor", "Watercolour splatter", "blooming dots", 4107326823L, b));
        }
        for (Made m : out) m.brush.normalize();
        // The fixed random mix (the owner, 2026-11-10; shuffled once, seed 20261110): these come with Second Ink On.
        for (Made m : out) if (SECOND_INK.contains(m.name)) m.brush.part(BrushPart.MEDIUM).set(PartDial.SECOND, 1);
        return out;
    }
    /** The old Wet brush (Brush pen, line and wash; {@link ToolPresets#RETIRED}) the Wet one follows. */
    static final String OLD_WET_BRUSH = "wet-brush.M10i0d2o54w00at_09ls2009s000000000_0_100_1jz_8yrfo00000000ny0000nz0.16ebhgk";
    /** Wet brush: {@link #OLD_WET_BRUSH} made Wet on a Wash, Stays Wet .5 and Runs Downhill .4 (its ink pools downhill). */
    static Made wetBrush() {
        Made m = wet("Brush pen", "Wet brush", "ink in water", 3171, OLD_WET_BRUSH, .5, .4, null);
        m.brush.set(BrushPart.MEDIUM, ModularBrush.switchTo(m.brush.part(BrushPart.MEDIUM), PartChoice.WASH));
        m.brush.part(BrushPart.MEDIUM).set(PartDial.ERASABLE, 1);
        return m;
    }
    /** The old Gouache and Oils presets the Wet ones follow ({@link ToolPresets#RETIRED}). */
    static final String OPAQUE_ROUND = "opaque-round.M80b70000000054_2zz00at_0_000_1jz_83z0000000000000000nz0.t4mf73",
            WIDE_GOUACHE = "wide-gouache.M83h30zz0065_2zz00at_0_100_1jz_80g0000000000000000nz0.51j8ud",
            FLAT_GOUACHE = "flat-gouache.M825aeeh2000000000j_2zz00at_0_000_1jz_83p0000000000000000nz0.1uhentb",
            SWELL_GOUACHE = "swell-gouache.M80g30000000043_2zz00at_0_05c_1jz_86d0000000000000000nz0.1h7plju",
            IMPASTO = "impasto.M90i00000il003k_2zzh900_0_500_1jz_8d30000ty0000000000nz0.t4mf73",
            OIL_HAKE = "oil-hake.M93i00zzrf34_2zzfl00_0_500_1jz_8j30000gw0000000000nz0.51j8ud",
            FLAT_BRISTLE = "flat-bristle.M925aeei00000xa004h_2zzkm00_0_500_1jz_8fh0000k30000000000nz0.69opxg",
            SIDE_OILS = "lean-oils.M90i00000sv0020_2zzug00_0_59x_1jz_8kp0000ht0000000000nz0.ib8b3u",
            OIL_PAINT = "oil-paint.M99vleei0000090005e_2zza300_2llp6zz00js_5at_1jz_1ss0000000000000000nz0.20l";
    /**
     * A Wet preset: the brush of share code {@code from} (its tip, medium and Control; its smudge unless {@code carry}
     * replaces it) under {@code style}, {@code name} and {@code seed}, on smooth paper, without Impasto or Dry Brush,
     * Wet at Stays Wet {@code stays} and Runs Downhill {@code runs}.
     */
    static Made wet(String style, String name, String line, long seed, String from, double stays, double runs, ModularBrush.Part carry) {
        ModularBrush o = ModularCode.decode(from), b = new ModularBrush(name, seed, ModularStyle.named(style));
        for (BrushPart p : BrushPart.values()) b.set(p, o.part(p).copy());
        // A dragging Wet brush lays all its own paint, dirtied by what it picks up (Own Paint 1).
        if (carry != null) { if (carry.has(PartDial.LOAD)) carry.set(PartDial.LOAD, 1); b.set(BrushPart.CARRY, carry); }
        b.set(BrushPart.PAPER, new ModularBrush.Part(PartChoice.PLAIN));
        if (b.part(BrushPart.MEDIUM).has(PartDial.IMPASTO)) b.part(BrushPart.MEDIUM).set(PartDial.IMPASTO, 0);
        // Erasable 1, as every Basic tool (2026-10-22: every brush erases unless the owner says).
        b.part(BrushPart.MEDIUM).set(PartDial.ERASABLE, 1);
        if (b.part(BrushPart.TIP).has(PartDial.DRY)) b.part(BrushPart.TIP).set(PartDial.DRY, 0);
        ModularBrush.Part life = new ModularBrush.Part(PartChoice.WET_PAINT);
        life.set(PartDial.STAYS_WET, stays); life.set(PartDial.RUNS, runs);
        b.set(BrushPart.LIFE, life);
        return new Made(style, name, line, seed, b);
    }

    /** Screentone's {@code pattern} at {@code darkness}, a {@code cell} px pattern at {@code angle}, on a broad round. */
    static Made tone(String name, String line, long seed, int pattern, double darkness, int cell, int angle) {
        ModularBrush b = brush("Print", name, seed, PartChoice.ROUND);
        tip(b).set(PartDial.SOFT, 0);
        ModularBrush.Part m = medium(b, PartChoice.TONE);
        m.set(PartDial.PATTERN, pattern); m.set(PartDial.TTONE, darkness); m.set(PartDial.CELL, cell); m.set(PartDial.S_ANGLE, angle);
        hand(b, PartChoice.STEADY);
        return new Made("Print", name, line, seed, b);
    }
    /** A texture marker: a broad round, a little soft (crisp enough that the pattern reads to its edge), of Riso's ink line through Print Shading {@code screen}, a {@code cell} px pattern at {@code angle}, full Pressure Dark. */
    static Made texture(String name, String line, long seed, int screen, int cell, int angle) {
        ModularBrush b = brush("Print", name, seed, PartChoice.ROUND);
        tip(b).set(PartDial.WEIGHT, 1.5); tip(b).set(PartDial.SOFT, .2);
        printInk(b, screen, cell, angle, 0, 0, 0, 0, 1);
        fullPressDark(b);
        return new Made("Print", name, line, seed, b);
    }
    /** Ink with a wet, line-less body through Print Shading {@code screen}. */
    static void printInk(ModularBrush b, int screen, int cell, int angle, int mis, double gain, double uneven, double wet, double line) {
        ModularBrush.Part m = medium(b, PartChoice.INK);
        m.set(PartDial.WET, wet); m.set(PartDial.LINE, line);
        screen(m, screen, cell, angle, mis, gain, uneven);
    }
    static void screen(ModularBrush.Part m, int screen, int cell, int angle, int mis, double gain, double uneven) {
        m.set(PartDial.SCREEN, screen); m.set(PartDial.CELL, cell); m.set(PartDial.S_ANGLE, angle);
        m.set(PartDial.MIS, mis); m.set(PartDial.S_GAIN, gain); m.set(PartDial.UNEVEN, uneven);
    }

    /** A plain brush: {@code tip} at its defaults (no soft edge), Ink, Smudge off, smooth paper, Instant, Steady. */
    static ModularBrush brush(String style, String name, long seed, PartChoice tip) {
        ModularBrush b = new ModularBrush(name, seed, ModularStyle.named(style));
        b.set(BrushPart.TIP, new ModularBrush.Part(tip));
        medium(b, PartChoice.INK);
        b.set(BrushPart.CARRY, new ModularBrush.Part(PartChoice.OFF));
        b.set(BrushPart.PAPER, new ModularBrush.Part(PartChoice.PLAIN));
        b.set(BrushPart.LIFE, new ModularBrush.Part(PartChoice.NONE));
        hand(b, PartChoice.STEADY);
        return b;
    }
    static ModularBrush.Part tip(ModularBrush b) { return b.part(BrushPart.TIP); }
    /**
     * A fresh {@code c} whose {@code time} (Dries In or Fades In) is {@code seconds} as made, while the dial went from 10 s:
     * the step its code holds, read over today's range from 1 s (2026-11-06), so the preset keeps its code.
     */
    static ModularBrush.Part timed(PartChoice c, PartDial time, double seconds) {
        ModularBrush.Part p = new ModularBrush.Part(c);
        long step = Math.round((seconds - 10) / (time.max - 10) * ModularCode.STEPS);
        p.set(time, time.min + step / (double) ModularCode.STEPS * (time.max - time.min));
        return p;
    }
    /** {@code b}'s medium made {@code c}, fresh, at its style's real Erasable. */
    static ModularBrush.Part medium(ModularBrush b, PartChoice c) {
        ModularBrush.Part m = new ModularBrush.Part(c);
        if (m.has(PartDial.ERASABLE)) m.set(PartDial.ERASABLE, b.style.erasable(c));
        b.set(BrushPart.MEDIUM, m);
        return m;
    }
    static void paper(ModularBrush b, PartChoice c, double tooth) { ModularBrush.Part p = new ModularBrush.Part(c); p.set(PartDial.TOOTH, tooth); b.set(BrushPart.PAPER, p); }
    static void hand(ModularBrush b, PartChoice c) { b.set(BrushPart.HAND, new ModularBrush.Part(c)); }
    /** Steady with full Pressure Dark: a light touch a fifth as dark, a full press full (Riso, Gradient tone). */
    static void fullPressDark(ModularBrush b) {
        ModularBrush.Part h = new ModularBrush.Part(PartChoice.STEADY);
        h.set(PartDial.PD, 1);
        b.set(BrushPart.HAND, ModularBrush.namedHand(h));
    }
    /** Press Dark with Tilt Shading, as every Pencil and Charcoal stick has: laid low, the side of the lead or stick. */
    static void sideShade(ModularBrush b) {
        ModularBrush.Part h = new ModularBrush.Part(PartChoice.PRESS_DARKER);
        h.set(PartDial.SIDE_SHADE, 1);
        b.set(BrushPart.HAND, ModularBrush.namedHand(h));
    }

    // ---- The sheet ----

    static final int CELL_W = 900, CELL_H = 250, LABEL = 26;

    static void sheet(List<Made> all, File file) throws Exception {
        int cols = 2, rows = (all.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * CELL_W, rows * CELL_H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int i = 0; i < all.size(); i++) {
            Made m = all.get(i);
            int ox = (i % cols) * CELL_W, oy = (i / cols) * CELL_H;
            ToneDocument d = scene(m.brush, CELL_W - 140, CELL_H - LABEL);
            for (int y = 0; y < d.height; y++) for (int x = 0; x < d.width; x++) {
                int t = Math.round(d.compositeTone(x, y) / 17f) * 17;
                img.setRGB(ox + x, oy + LABEL + y, t << 16 | t << 8 | t);
            }
            // The card's loop, as the Tools page draws it.
            int[] loop = WildSample.modular(m.brush, ModularSizes.sampleDarkness(m.brush), WildSample.Shape.LOOP, 120, 160, false);
            for (int y = 0; y < 160; y++) for (int x = 0; x < 120; x++) img.setRGB(ox + CELL_W - 130 + x, oy + LABEL + 20 + y, loop[y * 120 + x] & 0xffffff);
            g.setColor(new Color(170, 170, 170)); g.drawRect(ox, oy, CELL_W - 1, CELL_H - 1); g.drawRect(ox + CELL_W - 131, oy + LABEL + 19, 121, 161);
            g.setColor(Color.BLACK); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
            g.drawString(m.name + " · " + ModularStyle.named(m.style).label + "  —  " + m.line + "   (" + Math.round(ModularSizes.size(m.brush, InkWashSizes.Preset.MEDIUM)) + " px)", ox + 8, oy + 19);
        }
        g.dispose();
        file.getParentFile().mkdirs();
        javax.imageio.ImageIO.write(img, "png", file);
        System.out.println("wrote " + file);
    }

    /** The brush's S-curve at Dark, pressure light to firm and back, and a scribble at Mid; a highlighter over pen lines. */
    static ToneDocument scene(ModularBrush b, int w, int h) {
        ToneDocument d = InkWashLayers.blank(w, h);
        WatercolorSession session = new WatercolorSession();
        double size = ModularSizes.size(b, InkWashSizes.Preset.MEDIUM);
        boolean behind = b.part(BrushPart.MEDIUM).get(PartDial.M_BLEND) == 2 && b.choice(BrushPart.CARRY) != PartChoice.LIFT;
        if (behind) {
            ModularBrush pen = brush("Fineliner", "pen", 1, PartChoice.ROUND);
            for (int k = 0; k < 3; k++) stroke(d, session, pen, 4, 1, scribble(30, w - 40, 50 + k * 70, 14, .6));
        }
        // What a brush that works on what is there needs under it: ink rings to stay in, a wash to lift, wax to scratch.
        PartChoice carry = b.choice(BrushPart.CARRY);
        if (carry == PartChoice.STAY_IN) {
            ModularBrush pen = brush("Fineliner", "pen", 1, PartChoice.ROUND);
            for (int k = 0; k < 4; k++) stroke(d, session, pen, 4, 1, ring(90 + k * 200, h / 2.0, 70, 55));
        }
        if (carry == PartChoice.LIFT || ModularWords.scratch(b)) {
            ModularBrush under = brush("Pastels", "under", 1, PartChoice.ROUND);
            if (carry == PartChoice.LIFT) for (ToolPresets.Preset p : ToolPresets.all()) { if (p.name.equals("Soft wash")) under = p.brush().parts; }
            else medium(under, PartChoice.CRAYON);
            for (int k = 0; k < 8; k++) stroke(d, session, under, 40, .8, scribble(20, w - 20, 25 + k * (h - 50) / 7.0, 4, .9));
        }
        stroke(d, session, b, size, behind ? .35 : 1, sCurve(30, behind ? w - 40 : 430, h / 2.0, h * .32));
        stroke(d, session, b, size, behind ? .35 : 1 - 60 / 255.0, behind ? scribble(30, w - 40, h - 30, 6, .7) : scribble(470, w - 30, h / 2.0, h * .3, .7));
        session.settleNow(d);
        return d;
    }
    /** A stroke as the drawing screen starts one: what still settles laid first, then live or quick (CarryPictures'). */
    static void stroke(ToneDocument d, WatercolorSession session, ModularBrush b, double size, double darkness, double[][] path) {
        // Wet paint stays wet from stroke to stroke and moves after each (the wet paint spec); anything else is laid first.
        boolean wet = ResolvedBrush.of(b, size, darkness).wet != null;
        if (wet) session.settleBefore(d, b); else session.settleNow(d);
        DrawingStroke s = QuickStroke.start(d, b, size, darkness, session);
        for (double[] p : path) { s.sample((float) p[0], (float) p[1], ToolSettings.pressure((float) p[2]), 0, 0, (long) p[3]); s.present(); }
        s.finish();
        if (wet) while (session.tickDrying(d)) { }
    }
    /** A closed ring about {@code cx, cy}, {@code rx} by {@code ry}, at an even pressure. */
    static double[][] ring(double cx, double cy, double rx, double ry) {
        int n = 160;
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) { double a = 2 * Math.PI * i / n; o[i] = new double[]{cx + rx * Math.cos(a), cy + ry * Math.sin(a), .8, i * 4}; }
        return o;
    }
    /** An S across, pressure light at the ends and full in the middle. */
    static double[][] sCurve(double x0, double x1, double yc, double amp) {
        int n = (int) Math.ceil((x1 - x0) / 2);
        double[][] o = new double[n + 1][];
        double t = 0;
        for (int i = 0; i <= n; i++) {
            double u = (double) i / n;
            // Slow off the mark, as a hand starts.
            t += 5 * (1 + 4 * Math.max(0, 1 - u / .08));
            o[i] = new double[]{x0 + (x1 - x0) * u, yc - amp * Math.sin(u * Math.PI * 2), .05 + .95 * Math.sin(Math.PI * u), t};
        }
        return o;
    }
    /** Loops across, like handwriting, at pressure {@code p} with a little wander. */
    static double[][] scribble(double x0, double x1, double yc, double amp, double p) {
        int n = (int) Math.ceil((x1 - x0) * 1.6);
        double[][] o = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double u = (double) i / n, t = u * Math.PI * 2 * ((x1 - x0) / 40);
            o[i] = new double[]{x0 + (x1 - x0) * u + amp * .5 * Math.cos(t), yc + amp * Math.sin(t), p + .2 * Math.sin(t * .37), i * 3};
        }
        return o;
    }
}
