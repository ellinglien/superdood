package io.github.mpdairy.monopaint;

import com.google.android.gms.tasks.Tasks;
import android.content.Context;
import android.util.Log;
import androidx.work.WorkManager;
import com.google.mlkit.common.MlKit;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.vision.digitalink.DigitalInkRecognition;
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel;
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier;
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer;
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions;
import com.google.mlkit.vision.digitalink.Ink;
import com.google.mlkit.vision.digitalink.RecognitionCandidate;
import com.google.mlkit.vision.digitalink.RecognitionContext;
import com.google.mlkit.vision.digitalink.WritingArea;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Read Handwriting's reader: Google's ML Kit Digital Ink, the one class that knows it (probe 2026-11-09: it runs on
 * the Manta, which has no Google services; English downloaded in 26 s, about 0.25 s a line). Downloading, deleting and
 * reading block their thread: call them on {@link HandwritingQueue}'s, never the main one. What Google gets: see the
 * spec's "What Google gets" (usage stats, never the ink or its text). ML Kit doesn't start until a reader is made (its
 * start-up provider is removed from the manifest); WorkManager, which it pulls in, starts at app start, local only.
 */
final class HandwritingReader {
    /** shortcut: English only; more languages as a few chips on the row if they're wanted. */
    static final String LANGUAGE = "en-US";
    /** ML Kit's stored download job; WorkManager tags work with the worker's class name. */
    private static final String DOWNLOAD_WORK_TAG = "com.google.mlkit.vision.digitalink.downloading.DigitalInkRecognitionFileDependencyManager$DownloadWorker";
    private final Context context;
    private final DigitalInkRecognitionModel model;
    private DigitalInkRecognizer recognizer;

    private static boolean started;

    HandwritingReader(Context context) {
        this.context = context.getApplicationContext();
        synchronized (HandwritingReader.class) {
            if (!started) {
                try { MlKit.initialize(this.context); } catch (IllegalStateException e) { Log.w("HandwritingReader", "ML Kit was already started", e); }
                started = true;
            }
        }
        DigitalInkRecognitionModelIdentifier id;
        try { id = DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANGUAGE); }
        catch (Exception e) { throw new IllegalStateException(e); }
        model = DigitalInkRecognitionModel.builder(id).build();
    }

    /** Downloads the reader if it isn't here yet (about 20 MB), and gets it ready. */
    void download() throws Exception {
        Tasks.await(RemoteModelManager.getInstance().download(model, new DownloadConditions.Builder().build()), 10, TimeUnit.MINUTES);
        if (recognizer == null) recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build());
    }
    boolean downloaded() throws Exception { return Tasks.await(RemoteModelManager.getInstance().isModelDownloaded(model), 1, TimeUnit.MINUTES); }

    /** The downloaded reader deleted (Read Handwriting switched off). */
    void delete() throws Exception {
        // a download killed half way is a stored job the library never cancels; it must not run again once switched off
        // Waited for, so the cancel is kept even if the app is closed right after.
        WorkManager.getInstance(context).cancelAllWorkByTag(DOWNLOAD_WORK_TAG).getResult().get(1, TimeUnit.MINUTES);
        if (recognizer != null) { recognizer.close(); recognizer = null; }
        if (downloaded()) Tasks.await(RemoteModelManager.getInstance().deleteDownloadedModel(model), 1, TimeUnit.MINUTES);
    }

    /** {@code line}'s three best guesses ("" where fewer), its box the writing area. */
    String[] read(WritingLines.Line line) throws Exception {
        if (recognizer == null) { // reading never downloads: only download() does, when the owner switches On
            if (!downloaded()) throw new IllegalStateException("reader not downloaded");
            recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(model).build());
        }
        Ink.Builder ink = Ink.builder();
        for (PageWriting.Stroke s : line.strokes) {
            Ink.Stroke.Builder b = Ink.Stroke.builder();
            for (int k = 0; k < s.size(); k++) b.addPoint(Ink.Point.create(s.x[k], s.y[k], s.t[k]));
            ink.addStroke(b.build());
        }
        RecognitionContext context = RecognitionContext.builder().setPreContext("")
            .setWritingArea(new WritingArea(Math.max(1, line.box[2] - line.box[0]), Math.max(1, line.box[3] - line.box[1]))).build();
        List<RecognitionCandidate> c = Tasks.await(recognizer.recognize(ink.build(), context), 30, TimeUnit.SECONDS).getCandidates();
        String[] out = {"", "", ""};
        for (int i = 0; i < Math.min(3, c.size()); i++) out[i] = c.get(i).getText();
        return out;
    }
}
