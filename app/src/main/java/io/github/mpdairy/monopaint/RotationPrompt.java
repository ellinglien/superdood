package io.github.mpdairy.monopaint;

import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.OrientationEventListener;

/**
 * Turns the app when the tablet is held in a new orientation (the owner, 2026-11-10: "can we make rotation happen
 * without the button?"). Android stays in portrait, so this watches the sensor itself. MonoPaint's Rotate button over
 * the page, and the shake that recalled it, until then: its ghost stayed on the panel ("lingering on the screen and
 * affecting pen marks"). The page bar's padlock ({@link ZoomButton}) holds the turn as it holds the zoom (the owner, the
 * same day: "we can make the zoom lock a dual use lock? since it's already on the screen").
 */
final class RotationPrompt {
    /** How long the pen has been away before a turn, ms: the page never turns under a hand drawing on a tilted tablet. */
    static final long PEN_AWAY_MS = 1500;
    private final PaintActivity app;
    final OrientationEventListener orientationSensor;
    final RotationSuggestion rotationSuggestion = new RotationSuggestion();
    private int lastSensorDegrees = OrientationEventListener.ORIENTATION_UNKNOWN;
    /** Looks still owed once the hold is over: the listener need not emit another event once the tablet is still. */
    private int looks;
    private final Runnable check = this::turn;

    RotationPrompt(PaintActivity app) {
        this.app = app;
        orientationSensor = new OrientationEventListener(app, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override public void onOrientationChanged(int degrees) { lastSensorDegrees = degrees; looks = 1; turn(); }
        };
    }

    void enable() { if (orientationSensor.canDetectOrientation()) orientationSensor.enable(); }
    void disable() { orientationSensor.disable(); hide(); }
    /** Forgets the sensor history. */
    void hide() { rotationSuggestion.reset(); app.root.removeCallbacks(check); }
    /** Looks again after the hold: a wait may be over, the padlock opened. */
    void again() { looks = 1; later(); }
    private void later() { app.root.removeCallbacks(check); app.root.postDelayed(check, RotationSuggestion.HOLD_MS); }

    private void turn() {
        if (!app.resumed || app.navigationLocked) { hide(); return; }
        // A menu open, a page loading, the pen at work: the turn waits for it.
        if (!app.hasWindowFocus() || app.pagePanel != null || app.busy() || app.pad.navigating || app.pad.penWithin(PEN_AWAY_MS)) { again(); return; }
        int next = rotationSuggestion.update(lastSensorDegrees, (4 - app.appRotation) % 4, SystemClock.uptimeMillis());
        if (next != RotationSuggestion.NONE) app.requestQuarter(next);
        else if (looks-- > 0) later();
    }
}
