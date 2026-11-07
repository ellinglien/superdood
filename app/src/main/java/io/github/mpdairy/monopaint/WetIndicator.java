package io.github.mpdairy.monopaint;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

/**
 * The droplet, always there (the owner, 2026-10-22: "is the droplet invisible?"), in Phosphor's drops (the owner,
 * 2026-10-22: "for drop icon use phosphor drop"), keeping MonoPaint's meaning: the drop's outline dashed while the page
 * is dry (Phosphor's solid outline read as on, the owner, 2026-10-23: "the wet icon seems like it's always on now", as
 * Zen Mode's off is its dashed circle; the owner's own dashed drop from 2026-10-27, Iconoir's droplet dashed since 2026-10-29,
 * res/drawable/ic_drop_dashed);
 * half full while paint on it still moves ({@link WatercolorSession#moving}: a Slow wash settling, halos growing, wet
 * paint running, a quick stroke's render after lift once it has lasted {@link WetLook#RENDERING_MS}), where MonoPaint's droplet
 * ripples (still on e-ink); full while paint lies wet at rest ({@link WatercolorSession#wet}, a wet canvas held), as
 * MonoPaint's is solid. In the drawing screen's side column beside Play, under the Lasso (2026-10-26; on the brush line from 2026-10-24), and beside Clear over Brush Lab's test page.
 * A tap dries what is wet the way it would dry left alone, only sooner ({@link DryingTicker#hurry}: a wash runs on to
 * its end, spreading, settling and forming its edges, with no wait between its ticks, then shows once; one undo step
 * with its stroke; the owner, 2026-10-29: "looks like the hair dryer just stops the image as it is instead of accellerate
 * the drying processing"), and MonoPaint's blow dryer blows in its place meanwhile, as on MonoPaint's droplet (the owner, 2026-10-22:
 * "the hair dryer animation isn't the same as monopaint, can you pull that pls"): {@link BlowDryer}'s six frames and
 * MonoPaint's own dryer icon, in a box MonoPaint's droplet button's width ({@link #DRYER_DP}), no box drawn round it,
 * each frame put on the panel over the indicator's square alone, round again while the drying runs ({@link Host#hurrying}). It keeps its place and size either way, so nothing
 * around it moves; a change is drawn over its own square only ({@link Host#update}), and never while the pen is down: it
 * looks again once the pen lifts, and every {@link WetLook#WET_MS} while wet, for paint coming to rest.
 */
@SuppressLint("ViewConstructor")
final class WetIndicator extends View {
    interface Host {
        /** Whether anything is still wet. */
        boolean wet();
        /** Whether paint still moves ({@link WatercolorSession#moving}). */
        boolean moving();
        /** A stroke is down: the indicator waits for it to end. */
        boolean stroking();
        /** All that is wet is a quick stroke rendering after lift ({@link WatercolorSession#onlyRendering}): shown only once it lasts {@link WetLook#RENDERING_MS}. */
        boolean onlyRendering();
        /** Dries what is still wet as it would dry left alone, only sooner ({@link DryingTicker#hurry}), and shows it once done. */
        void dry();
        /** Whether a tap's drying still runs ({@link WatercolorSession#hurrying}). */
        boolean hurrying();
        /** Applies {@code change} to {@code view} and puts the result on the panel, over the view's square only. */
        void update(View view, Runnable change);
    }
    /** The drop's box and the blow dryer's room, dp ({@link WetLook#ICON_DP}, {@link WetLook#DRYER_DP}, the browser's too). */
    static final float ICON_DP = WetLook.ICON_DP, DRYER_DP = WetLook.DRYER_DP;
    private final Host host;
    private final Drawable[] drops = new Drawable[WetLook.State.values().length];
    private final Runnable sync = this::sync;
    /** What it shows; what it shows next and when it looks again ({@link WetLook}, the browser's too). */
    private WetLook.State shown = WetLook.State.DRY;
    private final WetLook look = new WetLook();
    /** The blow dryer's frame while it blows ({@link BlowDryer#FRAMES}), or −1. */
    private int blowing = -1;
    private Drawable dryer;
    private final Runnable blow = this::blow;

    WetIndicator(Context context, Host host) {
        super(context);
        this.host = host;
        setOnClickListener(v -> {
            if (host.stroking()) return;
            // Dry already, the dryer still blows, as MonoPaint's does on a tap.
            if (shown != WetLook.State.DRY) host.dry();
            // MonoPaint's blow dryer, its first frame at once.
            removeCallbacks(blow);
            host.update(this, () -> blowing = 0);
            postDelayed(blow, WetLook.BLOW_MS);
        });
        describe();
    }
    /** Whether anything is wet may have changed ({@link WatercolorSession#wetChanged}): it looks once the event is handled. */
    void changed() { removeCallbacks(sync); post(sync); }
    private void sync() {
        removeCallbacks(sync);
        look.look(shown, SystemClock.uptimeMillis(), host.stroking(), host.wet(), host.moving(), host.onlyRendering());
        if (look.next >= 0) postDelayed(sync, look.next);
        WetLook.State state = look.state;
        if (state == null || state == shown) return;
        if (!isAttachedToWindow()) { shown = state; describe(); return; }
        host.update(this, () -> { shown = state; describe(); });
    }
    /** The dryer's next frame; past the last, round again while the drying runs, else the droplet or the paper again. */
    private void blow() {
        if (blowing < 0) return;
        int next = blowing + 1 < WetLook.BLOW_FRAMES ? blowing + 1 : host.hurrying() ? 0 : -1;
        if (!isAttachedToWindow()) { blowing = -1; return; }
        host.update(this, () -> blowing = next);
        if (next >= 0) postDelayed(blow, WetLook.BLOW_MS);
    }
    private void describe() {
        setContentDescription(WetLook.said(shown));
    }
    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); removeCallbacks(sync); removeCallbacks(blow); blowing = -1; }
    /** In Zen Mode's fan: drawn in its grey ({@link ZenFanView#greyed}). */
    boolean greyed;
    @Override public void draw(Canvas canvas) { ZenFanView.greyed(canvas, greyed, super::draw); }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Theme.PAPER);
        if (blowing >= 0) {
            // The dryer and its air, as MonoPaint draws them on its droplet's button, that button's width, centred.
            if (dryer == null) dryer = getContext().getDrawable(R.drawable.ic_hair_dryer);
            float density = getResources().getDisplayMetrics().density, box = Math.min(getWidth(), DRYER_DP * density);
            int saved = canvas.save();
            canvas.translate((getWidth() - box) / 2f, 0);
            BlowDryer.draw(canvas, dryer, box, getHeight(), blowing, density);
            canvas.restoreToCount(saved);
            return;
        }
        // The drop for what it shows, centred, a short row taking it smaller.
        float side = Math.min(Ui.dp(getContext(), ICON_DP), Math.min(getWidth(), getHeight()));
        int left = Math.round((getWidth() - side) / 2f), top = Math.round((getHeight() - side) / 2f);
        Drawable drop = drops[shown.ordinal()];
        if (drop == null) drop = drops[shown.ordinal()] = getContext().getDrawable(shown == WetLook.State.DRY ? R.drawable.ic_drop_dashed
                : shown == WetLook.State.MOVING ? R.drawable.ic_ph_drop_half_bottom : R.drawable.ic_ph_drop_fill);
        drop.setBounds(left, top, left + Math.round(side), top + Math.round(side));
        drop.draw(canvas);
    }
}

