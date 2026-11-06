package io.github.mpdairy.monopaint;

/**
 * Steady Hand and Long Stick (Control's last two dials, {@link PartDial#STEADY_HAND} and {@link PartDial#LONG_STICK};
 * the module gaps spec, 2026-10-31, top-10 item 2): the mark is laid not by the pen but by a tip on a spring that
 * follows it, a mass-spring follower. Steady Hand's spring is stiff and well damped (critically), so the line trails
 * the pen a little and smooths its wobble: the more the slower the hand, as a careful hand is slow (its lag time is
 * longest at rest; at speed its lag is held to a few screen px, a pulled string's). Long Stick's tip is weighted and
 * its spring loose (underdamped), Matisse's charcoal on a bamboo pole: it swings and overshoots, and the faster the
 * hand the more, as a real pole's does. Both: the pressure trails with the tip, so a press lands where the tip is, and
 * at the lift the tip glides onto where the pen lifted, so the line always ends there.
 *
 * <p>A stroke between the pen and the brush's stroke ({@link QuickStroke#start}, a mirror's copy, a sample): every
 * sample goes through it first, so a live stroke, a quick one's preview and render, a mirror's copies and a replay all
 * draw the same smoothed samples. While the pen rests it hears the pen again ({@link #buildsWhileHeld}, {@link HeldPen}),
 * so the line settles onto a resting pen. Pure, checked on the desktop ({@code SteadyHandChecks}).
 */
final class SteadyHand implements LiftingStroke {
    /** Steady Hand at full: its lag time with the pen at rest, ms, and the most it trails at speed, screen px. */
    static final double REST_MS = 90, MOST_PX = 14;
    /**
     * Long Stick, barely on (a short, stiff pole) to full: its spring's natural frequency, rad/ms (a swing of about 140
     * to 210 ms, under a hand's 8 to 12 Hz tremor, so the pole never shakes with it), and its damping ratio (an overshoot
     * of about 10% to 50%).
     */
    static final double STICK_W0 = .045, STICK_W1 = .03, STICK_Z0 = .6, STICK_Z1 = .22;
    /** The integrator's longest step, ms. */
    static final double STEP_MS = .5;
    /** A sample between two of the pen's once the tip has moved this far, page px: a swing's curve stays round. */
    static final double EMIT_PX = 1.5;
    /** Untimed samples (the desktop's, older paths) are taken as drawn at this speed, page px/ms. */
    static final double UNTIMED_SPEED = 1;
    /** The pen's speed, smoothed over this long, ms. */
    static final double SPEED_MS = 30;
    /** Between two samples the pen is taken to move in the last this long of the gap, ms, so a pause is a pause. */
    static final double MOVE_MS = 8;
    /** At the lift the tip has landed within this of the pen, page px; it glides for this long at most, ms. */
    static final double LANDED_PX = .25, LAND_MS = 3000;

    /** The brush's stroke the tip's samples go to. */
    final DrawingStroke inner;
    final Follower tip;
    private final PenBatch out = new PenBatch();
    private boolean landed;

    SteadyHand(DrawingStroke inner, double steady, double stick) { this.inner = inner; tip = new Follower(steady, stick); }

    /**
     * {@code stroke}, {@code b}'s, with its tip on a spring when its Steady Hand or Long Stick is on, else itself. Not a
     * Spiro: its ring is set by its first two samples, exactly where they are ({@code DrawingPad}'s ring prefix).
     */
    static DrawingStroke of(ModularBrush b, DrawingStroke stroke) {
        if (!steadies(b)) return stroke;
        ModularBrush.Part hand = b.part(BrushPart.HAND);
        return new SteadyHand(stroke, hand.get(PartDial.STEADY_HAND, 0), hand.get(PartDial.LONG_STICK, 0));
    }
    /** Whether {@code b}'s samples reach its stroke through a tip on a spring ({@link #of}). */
    static boolean steadies(ModularBrush b) {
        if (b == null || b.choice(BrushPart.TIP) == PartChoice.SPIRO) return false;
        ModularBrush.Part hand = b.part(BrushPart.HAND);
        return hand.get(PartDial.STEADY_HAND, 0) > 0 || hand.get(PartDial.LONG_STICK, 0) > 0;
    }

    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY) { sample(x, y, pressure, tiltX, tiltY, PenSpeed.UNTIMED); }
    @Override public void sample(float x, float y, float pressure, float tiltX, float tiltY, long time) {
        // A sample without a position or pressure reading draws nothing: the stroke has it as it is.
        if (landed || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(pressure)) { inner.sample(x, y, pressure, tiltX, tiltY, time); return; }
        tip.feed(x, y, pressure, tiltX, tiltY, time, out);
        out.feed(inner);
    }
    /** The pen lifted: the tip lands where the pen did, its last samples to the stroke. Once. */
    private void land() {
        if (landed) return;
        landed = true;
        tip.land(out);
        out.feed(inner);
        inner.present();
    }
    @Override public void present() { inner.present(); }
    @Override public void zoom(float scale) { tip.zoom = scale; inner.zoom(scale); }
    @Override public boolean finish() { land(); return inner.finish(); }
    @Override public boolean holdsWetPaint() { return inner.holdsWetPaint(); }
    /** A resting pen's sample again lets the tip settle onto it. */
    @Override public boolean buildsWhileHeld() { return true; }
    @Override public float ringGuide(float pressure, float tiltX, float tiltY) { return inner.ringGuide(pressure, tiltX, tiltY); }
    @Override public int ringMode() { return inner.ringMode(); }
    @Override public Unwritten unwritten() { return inner.unwritten(); }
    @Override public boolean developsAfterLift() { return inner instanceof LiftingStroke && ((LiftingStroke) inner).developsAfterLift(); }
    @Override public boolean lift() { land(); return inner instanceof LiftingStroke && ((LiftingStroke) inner).lift(); }
    @Override public boolean develop(long nanos) { return inner instanceof LiftingStroke && ((LiftingStroke) inner).develop(nanos); }
    @Override public boolean screened() { return inner instanceof LiftingStroke && ((LiftingStroke) inner).screened(); }
    @Override public boolean inShape() { return inner instanceof LiftingStroke && ((LiftingStroke) inner).inShape(); }

    /**
     * The tip: a mass on a spring pulled toward the pen, x'' = w²(pen − x) − 2zw x', stepped by backward Euler (stable
     * at any step) every {@link #STEP_MS} or less; its pressure the same, critically damped. Isotropic and linear, so a
     * mirror's copy fed the samples moved draws the tip's path moved. Pure.
     */
    static final class Follower {
        final double steady, stick;
        /** Screen px a page px shows as ({@link DrawingStroke#zoom}). */
        float zoom = 1;
        private boolean started, clocked;
        /** The tip: position, velocity (page px/ms), pressure and its rate. */
        double x, y, vx, vy, p, vp;
        /** The pen's last sample, and the pen's speed, smoothed (page px/ms). */
        private double penX, penY, penP, speed;
        private float tiltX, tiltY;
        /** The samples' clock, ms; the last sample's time (or untimed). */
        private double clock;
        private long last = PenSpeed.UNTIMED;
        /** Where the tip was last given to the stroke. */
        private double sentX, sentY;

        Follower(double steady, double stick) { this.steady = Math.max(0, Math.min(1, steady)); this.stick = Math.max(0, Math.min(1, stick)); }

        /** The spring's natural frequency now, rad/ms. */
        double w() {
            if (stick > 0) return STICK_W0 + (STICK_W1 - STICK_W0) * stick;
            // Steady Hand: lag time steady × REST_MS at rest, shortening with speed so the lag stays under steady × MOST_PX on the glass.
            double lag = steady * REST_MS / Math.hypot(1, speed * zoom * REST_MS / MOST_PX);
            return 2 / lag;
        }
        /** The spring's damping ratio now: 1, critical, for Steady Hand; Long Stick's swing, damped by a steady hand on it. */
        double z() {
            if (stick == 0) return 1;
            double z = STICK_Z0 + (STICK_Z1 - STICK_Z0) * stick;
            return z + (1 - z) * steady;
        }

        /** The pen's sample (finite), and the tip's samples since the last to {@code out}. */
        void feed(float px, float py, float pp, float tx, float ty, long time, PenBatch out) {
            tiltX = tx; tiltY = ty;
            if (!started) {
                started = true;
                x = penX = sentX = px; y = penY = sentY = py; p = penP = pp;
                clocked = time != PenSpeed.UNTIMED; clock = clocked ? time : 0; last = time;
                out.add(px, py, pp, tx, ty, time);
                return;
            }
            double seg = Math.hypot(px - penX, py - penY), dt;
            // Millisecond times repeat at 480 samples a second: a repeat is a quarter ms on, and the clock never runs ahead for long.
            if (time != PenSpeed.UNTIMED && clocked) dt = Math.max(time, clock + .25) - clock;
            else dt = Math.max(.25, seg / UNTIMED_SPEED);
            clocked = time != PenSpeed.UNTIMED;
            speed += (seg / dt - speed) * (1 - Math.exp(-dt / SPEED_MS));
            double fromX = penX, fromY = penY, fromP = penP, start = clock;
            long before = last;
            penX = px; penY = py; penP = pp; clock += dt; last = time;
            double w = w(), z = z(), still = Math.max(0, dt - MOVE_MS);
            int n = (int) Math.min(4000, Math.ceil(dt / STEP_MS));
            double h = dt / n;
            for (int i = 1; i <= n; i++) {
                double at = i * h, a = at <= still ? 0 : (at - still) / (dt - still);
                step(fromX + (px - fromX) * a, fromY + (py - fromY) * a, fromP + (pp - fromP) * a, w, z, h);
                if (i < n && Math.hypot(x - sentX, y - sentY) >= EMIT_PX) send(out, between(before, time, start, at));
            }
            send(out, time);
        }
        /** The pen lifted: the tip glides onto its last sample, critically damped (the hand steadies a swinging stick), and lands there exactly. */
        void land(PenBatch out) {
            if (!started) return;
            double w = w(), at = 0;
            while (at < LAND_MS && (Math.hypot(x - penX, y - penY) > LANDED_PX || Math.hypot(vx, vy) * 10 > LANDED_PX)) {
                at += STEP_MS;
                step(penX, penY, penP, w, 1, STEP_MS);
                if (Math.hypot(x - sentX, y - sentY) >= EMIT_PX) send(out, later(at));
            }
            x = penX; y = penY; p = penP; vx = vy = vp = 0;
            send(out, later(at + STEP_MS));
        }
        private void step(double tx, double ty, double tp, double w, double z, double h) {
            double w2h = w * w * h, d = 1 + 2 * z * w * h + w2h * h, dp = 1 + 2 * w * h + w2h * h;
            vx = (vx + w2h * (tx - x)) / d; vy = (vy + w2h * (ty - y)) / d; vp = (vp + w2h * (tp - p)) / dp;
            x += h * vx; y += h * vy; p += h * vp;
        }
        private void send(PenBatch out, long time) {
            sentX = x; sentY = y;
            out.add((float) x, (float) y, (float) Math.max(0, p), tiltX, tiltY, time);
        }
        /** The time {@code at} ms into a gap from {@code before} to {@code time}: untimed if either is. */
        private static long between(long before, long time, double start, double at) {
            if (time == PenSpeed.UNTIMED || before == PenSpeed.UNTIMED) return PenSpeed.UNTIMED;
            return Math.min(time, Math.round(start + at));
        }
        /** The time {@code at} ms after the pen's last sample: untimed if it was. */
        private long later(double at) { return last == PenSpeed.UNTIMED ? PenSpeed.UNTIMED : last + Math.round(at); }
    }
}
