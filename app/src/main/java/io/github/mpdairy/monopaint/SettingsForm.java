package io.github.mpdairy.monopaint;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

/** The panel typography MonoPaint's settings rows used, kept for the dialogs and lists that still apply it. */
final class SettingsForm {
    /** Text tag for secondary text, styled lighter and smaller. */
    static final String HINT = "hint";
    /** Text tag for "×" close buttons. */
    static final String CLOSE = "close";

    static float textSize(boolean largeText) { return largeText ? 18 : 16; }

    /** Applies the panel typography to every text view in a tree, honoring {@link #HINT} and {@link #CLOSE} tags. */
    static void style(View view, boolean largeText) {
        if (view instanceof LabButton) return; // styles itself: its chosen chip is on-ink
        if (view instanceof TextView && Theme.isTextFace(((TextView)view).getTypeface())) ((TextView)view).setTextColor(Color.BLACK);
        else if (view instanceof TextView) {
            TextView text = (TextView)view;
            boolean hint = HINT.equals(text.getTag());
            float size = textSize(largeText);
            text.setTextSize(CLOSE.equals(text.getTag()) ? 22 : hint ? size-1 : size);
            text.setTextColor(Color.BLACK);
            text.setTypeface(Theme.face(hint || text instanceof EditText ? 400 : 700));
            if (text instanceof Button) ((Button)text).setAllCaps(false);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) style(group.getChildAt(i), largeText);
        }
    }
}
