import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.ArrayList;

/**
 * Injects one stylus stroke with pressure and tilt, as the shell user (who may inject input), the way Android's
 * own `input` tool does. Run: CLASSPATH=/data/local/tmp/pen.dex app_process / PenStroke <file>; each line of the
 * file is "x y pressure tilt ms" (screen px, pressure 0..1, tilt radians, ms since the stroke began).
 */
public final class PenStroke {
    public static void main(String[] args) throws Exception {
        ArrayList<float[]> samples = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new FileReader(args[0]))) {
            for (String line; (line = in.readLine()) != null; ) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 5) continue;
                samples.add(new float[]{Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]), Float.parseFloat(p[4])});
            }
        }
        InputManager im = (InputManager) InputManager.class.getMethod("getInstance").invoke(null);
        Method inject = InputManager.class.getMethod("injectInputEvent", android.view.InputEvent.class, int.class);
        long down = SystemClock.uptimeMillis();
        for (int i = 0; i < samples.size(); i++) {
            float[] s = samples.get(i);
            long at = down + (long) s[4];
            long wait = at - SystemClock.uptimeMillis();
            if (wait > 0) Thread.sleep(wait);
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i == samples.size() - 1 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent.PointerProperties[] props = {new MotionEvent.PointerProperties()};
            props[0].id = 0; props[0].toolType = MotionEvent.TOOL_TYPE_STYLUS;
            MotionEvent.PointerCoords[] coords = {new MotionEvent.PointerCoords()};
            coords[0].x = s[0]; coords[0].y = s[1]; coords[0].pressure = s[2]; coords[0].size = 1;
            coords[0].setAxisValue(MotionEvent.AXIS_TILT, s[3]);
            MotionEvent e = MotionEvent.obtain(down, at, action, 1, props, coords, 0, 0, 1, 1, 0, 0,
                    InputDevice.SOURCE_STYLUS | InputDevice.SOURCE_TOUCHSCREEN, 0);
            inject.invoke(im, e, 2);  // wait for the app to handle it
            e.recycle();
        }
    }
}
