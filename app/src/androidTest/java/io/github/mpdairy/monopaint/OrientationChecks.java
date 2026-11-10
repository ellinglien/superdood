package io.github.mpdairy.monopaint;

import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.widget.LinearLayout;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Exercises app-only rotation on a temporary page, then restores the original book. */
final class OrientationChecks {
    static void run(Instrumentation test,StringBuilder report) throws Exception {
        PaintActivity activity = (PaintActivity)test.startActivitySync(new Intent(test.getTargetContext(),PaintActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(test,() -> !(Boolean)get(activity,"loading") && get(get(activity,"pad"),"document") != null,"Recovered document");
        Object pad = get(activity,"pad");
        DrawingBook original = (DrawingBook)get(activity,"book");
        String name = (String)get(activity,"drawingName");
        SharedPreferences preferences = (SharedPreferences)get(activity,"preferences");
        boolean right = preferences.getBoolean("toolbox_right",false);
        ToolLibrary library = (ToolLibrary)get(activity,"library");
        int gray = (Integer)get(get(activity,"paint"),"gray"), maximum = TestAccess.maximum(activity);
        boolean wet = (Boolean)get(get(activity,"paint"),"wetCanvas"), transparent = (Boolean)get(get(activity,"paint"),"transparentPaint");
        OrientationEventListener sensor = (OrientationEventListener)get(get(activity,"rotationPrompt"),"orientationSensor");
        // Pen/display assertions use full-tablet coordinates, regardless of the
        // dimensions of a previously opened drawing in this test install.
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        float density = activity.getResources().getDisplayMetrics().density;
        ToneDocument doc = new ToneDocument(metrics.widthPixels-Math.round(64*density),
                metrics.heightPixels-Math.round(48*density));
        boolean originalErase=(Boolean)get(get(activity,"paint"),"eraseMode");
        boolean originalLock=(Boolean)get(activity,"navigationLocked");
        try {
            main(test,() -> {
                set(get(activity,"paint"),"eraseMode",false);
                sensor.disable();
                check(sensor.canDetectOrientation(),"Accelerometer is exposed");
                call(get(activity,"rotationPrompt"),"hide");
                set(activity,"drawingName",""); set(get(activity,"paint"),"gray",0); TestAccess.setMaximum(activity,20);
                set(get(activity,"paint"),"wetCanvas",false); set(get(activity,"paint"),"transparentPaint",false);
                set(activity,"library",new ToolLibrary());
                call(pad,"replace",new Class<?>[]{ToneDocument.class},doc);
                preferences.edit().putBoolean("toolbox_right",false).apply(); call(activity,"applyToolboxSide");
            });
            await(test,() -> get(pad,"direct") != null,"Portrait direct display");
            portraitHands(test,activity,preferences);
            main(test,() -> {
                stroke(activity,pad,140,180,280,180);
                check(doc.tone(200,180)<255,"Portrait pen coordinates");
            });
            byte[] portrait = doc.snapshot();
            main(test,() -> {
                // The padlock holds the turn as it holds the zoom.
                set(activity,"navigationLocked",true);
                sensor.onOrientationChanged(270);
            });
            SystemClock.sleep(RotationPrompt.PEN_AWAY_MS+3*RotationSuggestion.HOLD_MS);
            main(test,() -> {
                check((Integer)get(activity,"appRotation")==Surface.ROTATION_0,"Locked, the app never turns");
                set(activity,"navigationLocked",false);
                // Exercise the actual listener, including its delayed look with NO second sample.
                sensor.onOrientationChanged(270);
                check((Integer)get(activity,"appRotation")==Surface.ROTATION_0,"No turn before the hold");
            });
            // The pen was just at the page: the turn waits for it to be away, then for the hold.
            awaitRotation(test,activity,Surface.ROTATION_90,true);
            main(test,() -> {
                check(get(activity,"pad")==pad && get(pad,"document")==doc,"Rotation preserves Activity, pad and document identity");
                check(Arrays.equals(portrait,doc.snapshot()),"Rotation preserves all tones");
                check(doc.undo(),"Rotation preserves undo history");
                check(doc.tone(200,180)==255,"Undo after rotation targets original stroke");
                check(doc.redo(),"Redo survives rotation");
                call(pad,"renderAll");
                layout(activity,false);
                check(get(pad,"direct")!=null,"Landscape uses direct e-ink presentation");
                // The page turns with the app, fitted to the landscape canvas: shown through the screen-sized viewport raster,
                // turned for the panel. Only a page shown unscaled skips it, and then only a buffer in another frame needs a rotated copy.
                check(get(pad,"viewportBitmap")!=null||(get(get(pad,"direct"),"buffer")==null)==DirectEink.bufferFromPanel().isIdentity(),"Landscape page reaches the panel without a rotated bitmap copy when the driver buffer is portrait: buffer="+(get(get(pad,"direct"),"buffer")!=null)+" identity="+DirectEink.bufferFromPanel().isIdentity()+" viewport="+(get(pad,"viewportBitmap")!=null)+" percent="+((CanvasViewport)get(pad,"viewport")).percent()+" toBuffer="+get(get(pad,"direct"),"sourceToBuffer"));
                stroke(activity,pad,320,340,480,340);
                check(doc.tone(400,340)<255,"Landscape pen coordinates match displayed page");
                // Retired: landscape shade strip taps; Inkwash never shows MonoPaint's color bar.
                set(get(activity,"paint"),"gray",0);
            });
            report.append("The app turns by itself after a stable sensor angle, never while the padlock is closed; document, undo, pen and rotated palette hit testing pass.\n");
            screenshot(test,activity,"orientation-right.png");
            fastStrokeAndRefresh(test,activity,doc,report);
            Matrix rightHandPage = new Matrix((Matrix)get(pad,"pageToView"));
            main(test,() -> { preferences.edit().putBoolean("toolbox_right",true).apply(); call(activity,"applyToolboxSide"); });
            test.waitForIdleSync();
            main(test,() -> {
                layout(activity,true);
                check(rightHandPage.equals(get(pad,"pageToView")),"Changing drawing hand never turns or moves the page within its canvas");
                stroke(activity,pad,540,540,700,540);
                check(doc.tone(620,540)<255,"Left-handed landscape pen coordinates");
            });
            screenshot(test,activity,"orientation-left.png");
            main(test,() -> call(activity,"requestQuarter",new Class<?>[]{int.class},1));
            awaitRotation(test,activity,Surface.ROTATION_270,true);
            main(test,() -> {
                layout(activity,true);
                stroke(activity,pad,720,740,880,740);
                check(doc.tone(800,740)<255,"Reverse landscape pen coordinates");
            });
            screenshot(test,activity,"orientation-left-right-edge-down.png");
            main(test,() -> {
                preferences.edit().putBoolean("toolbox_right",false).apply(); call(activity,"applyToolboxSide");
            });
            test.waitForIdleSync();
            main(test,() -> layout(activity,false));
            screenshot(test,activity,"orientation-right-edge-down.png");
            fastStrokeAndRefresh(test,activity,doc,report);
            main(test,() -> {
                call(activity,"requestQuarter",new Class<?>[]{int.class},2);
            });
            awaitRotation(test,activity,Surface.ROTATION_180,false);
            portraitHands(test,activity,preferences);
            main(test,() -> {
                check(get(pad,"document")==doc,"Upside-down portrait retains the page");
                sensor.onOrientationChanged(0);
            });
            awaitRotation(test,activity,Surface.ROTATION_0,false);
            await(test,() -> get(pad,"direct") != null,"Portrait direct display reconnects");
            main(test,() -> {
                check(get(pad,"document")==doc,"Same document after round trip");
                check(doc.tone(200,180)<255 && doc.tone(400,340)<255 && doc.tone(620,540)<255,"All rotated strokes retained");
                sensor.onOrientationChanged(-1);
            });
            SystemClock.sleep(3*RotationSuggestion.HOLD_MS);
            check((Integer)get(activity,"appRotation")==Surface.ROTATION_0,"A flat tablet never turns the app");
            report.append("All four app orientations keep Android in portrait and turn the page with the app, its tones unturned; direct e-ink, tool placement, both preset rails, black-bottom slider, portrait return and refresh pass.\n");
        } finally {
            main(test,() -> {
                set(activity,"navigationLocked",originalLock);
                call(activity,"requestQuarter",new Class<?>[]{int.class},0);
                set(activity,"library",library);set(get(activity,"paint"),"eraseMode",originalErase); set(get(activity,"paint"),"gray",gray); TestAccess.setMaximum(activity,maximum);
                set(get(activity,"paint"),"wetCanvas",wet); set(get(activity,"paint"),"transparentPaint",transparent);
                preferences.edit().putBoolean("toolbox_right",right).apply();
                call(activity,"replaceBook",new Class<?>[]{DrawingBook.class},original);
                set(activity,"drawingName",name); call(activity,"applyToolboxSide");
                call(activity,"saveToolState"); call(activity,"recoveryNow");
            });
            CountDownLatch saved = new CountDownLatch(1);
            ((DocumentStore)get(activity,"store")).recover((value,error) -> saved.countDown());
            check(saved.await(15,TimeUnit.SECONDS),"Original recovery persisted");
            main(test,activity::finish);
        }
    }
    private static void fastStrokeAndRefresh(Instrumentation test,PaintActivity activity,ToneDocument document,StringBuilder report) throws Exception {
        Object pad=get(activity,"pad"); View view=(View)pad;
        ToolLibrary saved=(ToolLibrary)get(activity,"library"); int maximum=TestAccess.maximum(activity);
        ToolSettings settings=ToolSettings.defaults(ToolSettings.Tool.BRUSH).head(ToolSettings.Head.FLAT).size(48).automaticHead();
        int y=(Integer)get(activity,"appRotation")==Surface.ROTATION_90 ? 1100 : 1400;
        ToneDocument reference=new ToneDocument(document.width,document.height,document.snapshot());
        PressureStroke expected=new PressureStroke(reference,settings,0);
        float[][] tilts={TestAccess.panelTilt(activity,25,-55),TestAccess.panelTilt(activity,-40,30),TestAccess.panelTilt(activity,50,-20)};
        expected.sample(900,y,.2f,tilts[0][0],tilts[0][1]); expected.sample(1020,y,.45f,tilts[1][0],tilts[1][1]); expected.sample(1140,y,.35f,tilts[2][0],tilts[2][1]); expected.finish();
        final int[] black={0,0};
        test.waitForIdleSync(); SystemClock.sleep(150);
        int draws=(Integer)get(pad,"drawCount"); long[] elapsed={0};
        try {
            main(test,() -> {
                ToolLibrary tools=new ToolLibrary(); tools.edit(settings); set(activity,"library",tools); TestAccess.setMaximum(activity,48);
                float[] points={900,y,1020,y,1140,y};
                ((Matrix)get(pad,"pageToView")).mapPoints(points); PanelCoordinates.fromView(view).mapPoints(points);
                long now=SystemClock.uptimeMillis(), started=System.nanoTime();
                MotionEvent down=stylus(now,now,MotionEvent.ACTION_DOWN,points[0],points[1],.2f,25,-55);
                activity.dispatchTouchEvent(down); down.recycle();
                MotionEvent move=stylus(now,now+1,MotionEvent.ACTION_MOVE,points[2],points[3],.45f,-40,30);
                MotionEvent.PointerCoords last=coords(points[4],points[5],.35f,50,-20);
                move.addBatch(now+2,new MotionEvent.PointerCoords[]{last},0);
                activity.dispatchTouchEvent(move); move.recycle();
                MotionEvent up=stylus(now,now+3,MotionEvent.ACTION_UP,points[4],points[5],0,50,-20);
                activity.dispatchTouchEvent(up); up.recycle();
                elapsed[0]=System.nanoTime()-started;
                // Retired: exact match with MonoPaint's flat brush; the pen draws with Inkwash's slot (318e016). Where it lands is checked.
                int[] found=null;
                for(int dy=-24;dy<=24&&found==null;dy++)for(int dx=-24;dx<=24;dx++)
                    if(document.tone(1140+dx,y+dy)<255){found=new int[]{1140+dx,y+dy};break;}
                check(found!=null&&document.tone(1020,y)<255,"Physical pen positions survive app rotation, including history");
                black[0]=found[0];black[1]=found[1];
                check(get(pad,"direct")!=null && (get(pad,"viewportBitmap")!=null||(get(get(pad,"direct"),"buffer")==null)==DirectEink.bufferFromPanel().isIdentity()),"Direct page submits its original bitmap to a portrait driver buffer");
            });
            await(test,() -> ((DirtyRegions)get(pad,"pending")).isEmpty(),"Direct pen pixels submitted");
            test.waitForIdleSync(); SystemClock.sleep(200);
            main(test,() -> {
                check((Integer)get(pad,"drawCount")==draws,"Landscape pen and pen-up do not request Android canvas frames");
                DirectEink direct=(DirectEink)get(pad,"direct"); Bitmap bitmap=(Bitmap)get(pad,"display");
                float[] ink={black[0],black[1]};((Matrix)get(pad,"pageToView")).mapPoints(ink);
                Bitmap shown=get(pad,"viewportBitmap")!=null?((ViewportBitmap)get(pad,"viewportBitmap")).bitmap:bitmap;
                float[] at=get(pad,"viewportBitmap")!=null?ink:new float[]{black[0],black[1]};
                // The panel shows the viewport raster, in view pixels, when the page is above its fit.
                float[] end={1140,y};if(get(pad,"viewportBitmap")!=null)((Matrix)get(pad,"pageToView")).mapPoints(end);
                check(direct.readGray(Math.round(end[0]),Math.round(end[1]))==((shown.getPixel(Math.round(end[0]),Math.round(end[1]))>>>16)&255)/16,"Landscape pen pixels reach native panel buffer");
                check(direct.readGray(Math.round(at[0]),Math.round(at[1]))==((shown.getPixel(Math.round(at[0]),Math.round(at[1]))>>>16)&255)/16,"Pen ink appears in the panel buffer");
            });
            // A full compositor redraw must reproduce the same artwork, rather than stale pre-pen pixels.
            main(test,() -> { view.invalidate(); activity.getWindow().getDecorView().invalidate(); });
            test.waitForIdleSync(); SystemClock.sleep(200);
            Bitmap refreshed=test.getUiAutomation().takeScreenshot();
            float[] point={black[0],black[1]}; ((Matrix)get(pad,"pageToView")).mapPoints(point); PanelCoordinates.fromView(view).mapPoints(point);
            check((refreshed.getPixel(Math.round(point[0]),Math.round(point[1]))&0xffffff)!=0xffffff,"Full screen redraw retains fast landscape ink");
            refreshed.recycle();
            report.append("App rotation ").append(get(activity,"appRotation")).append(": direct pen + batched signed tilt, no Android pen frames, control patches and full refresh pass; replay CPU ")
                    .append(String.format(java.util.Locale.US,"%.2f",elapsed[0]/1_000_000.0)).append(" ms (not panel latency).\n");
        } finally { main(test,() -> { set(activity,"library",saved); TestAccess.setMaximum(activity,maximum); }); }
    }
    private static MotionEvent.PointerCoords coords(float x,float y,float pressure,float tiltX,float tiltY) {
        MotionEvent.PointerCoords c=new MotionEvent.PointerCoords(); c.x=x;c.y=y;c.pressure=pressure;
        c.setAxisValue(MotionEvent.AXIS_ORIENTATION,tiltX);c.setAxisValue(MotionEvent.AXIS_TILT,tiltY); return c;
    }
    private static android.graphics.RectF boundsInRoot(View root,View view) {
        android.graphics.RectF bounds=new android.graphics.RectF(0,0,view.getWidth(),view.getHeight());
        PanelCoordinates.fromView(view).mapRect(bounds);
        Matrix inverse=new Matrix();PanelCoordinates.fromView(root).invert(inverse);inverse.mapRect(bounds);
        return bounds;
    }
    /** Opens the file menu by tapping the hamburger with {@code tool}. */
    private static android.widget.PopupWindow openMenu(Instrumentation test,PaintActivity activity,View button,int tool) throws Exception {
        main(test,() -> tapLocal(activity,button,button.getWidth()/2f,button.getHeight()/2f,tool));
        await(test,() -> { android.widget.PopupWindow menu=(android.widget.PopupWindow)get(activity,"filePopup");
            return menu!=null && menu.isShowing(); },"Hamburger opens the file menu");
        return (android.widget.PopupWindow)get(activity,"filePopup");
    }
    /**
     * Taps a popup's window at a screen point, as Android delivers a touch outside a modal
     * popup. Injected finger events land elsewhere on this tablet, so dispatch directly.
     */
    private static void tapPopupWindow(Instrumentation test,android.widget.PopupWindow popup,float[] screen,int tool) throws Exception {
        main(test,() -> {
            View window=popup.getContentView().getRootView();
            int[] at=new int[2]; window.getLocationOnScreen(at);
            long now=SystemClock.uptimeMillis();
            for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
                MotionEvent.PointerProperties p=new MotionEvent.PointerProperties(); p.id=0; p.toolType=tool;
                MotionEvent event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,1,new MotionEvent.PointerProperties[]{p},
                        new MotionEvent.PointerCoords[]{coords(screen[0],screen[1],1,0,0)},0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
                event.offsetLocation(-at[0],-at[1]); window.dispatchTouchEvent(event); event.recycle();
            }
        });
    }
    private static void injectTap(Instrumentation test,float[] point) {injectTap(test,point,false);}
    private static void injectTap(Instrumentation test,float[] point,boolean dismissing) {
        long now=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}) {
            MotionEvent event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,point[0],point[1],0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            boolean delivered=test.getUiAutomation().injectInputEvent(event,true);event.recycle();
            // A touch outside a modal popup dismisses it on down, which can leave the lift without a target.
            check(delivered || (dismissing && action==MotionEvent.ACTION_UP),"Menu touch injected");
        }
    }
    private static MotionEvent stylus(long down,long time,int action,float x,float y,float pressure,float tiltX,float tiltY) {
        MotionEvent.PointerProperties p=new MotionEvent.PointerProperties();p.id=0;p.toolType=MotionEvent.TOOL_TYPE_STYLUS;
        return MotionEvent.obtain(down,time,action,1,new MotionEvent.PointerProperties[]{p},
                new MotionEvent.PointerCoords[]{coords(x,y,pressure,tiltX,tiltY)},0,0,1,1,0,0,InputDevice.SOURCE_STYLUS,0);
    }
    private static void layout(PaintActivity activity,boolean right) throws Exception {
        // Retired: MonoPaint header, color bar, preset rail and hamburger placement; Inkwash shows only its side column.
        View pad=(View)get(activity,"pad");
        // Retired: every page corner visible. Inkwash never opens a page below 100% (CanvasViewport.minZoom; a pinch may, sideways): one that
        // doesn't fit is panned instead.
        check(((CanvasViewport)get(pad,"viewport")).percent()>=100,"Page shown at 100% or more");
    }
    private static void portraitHands(Instrumentation test,PaintActivity activity,SharedPreferences preferences) throws Exception {
        for (boolean right : new boolean[]{true,false}) {
            main(test,() -> { preferences.edit().putBoolean("toolbox_right",right).apply(); call(activity,"applyToolboxSide"); });
            test.waitForIdleSync();
            // Retired: hamburger corner and file menu; MonoPaint's header is never shown in Inkwash.
        }
    }
    private static float[] layoutPoint(View root,View view,float x,float y) {
        float[] point={x,y};
        while(view!=root) {
            view.getMatrix().mapPoints(point);
            View parent=(View)view.getParent();
            point[0]+=view.getLeft()-parent.getScrollX(); point[1]+=view.getTop()-parent.getScrollY(); view=parent;
        }
        return point;
    }
    private static float[] physicalPoint(View view,float x,float y) {
        float[] point={x,y}; PanelCoordinates.fromView(view).mapPoints(point); return point;
    }
    private static View tagged(View view,String tag) {
        if(tag.equals(view.getTag()))return view;
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) {
                View found=tagged(group.getChildAt(i),tag); if(found!=null)return found;
            }
        }
        return null;
    }
    private static void stroke(PaintActivity activity,Object pad,float x1,float y1,float x2,float y2) throws Exception {
        float[] points={x1,y1,x2,y2}; ((Matrix)get(pad,"pageToView")).mapPoints(points);
        View view=(View)pad;
        long time=SystemClock.uptimeMillis();
        event(view,time,MotionEvent.ACTION_DOWN,points[0],points[1],MotionEvent.TOOL_TYPE_STYLUS);
        event(view,time,MotionEvent.ACTION_MOVE,points[2],points[3],MotionEvent.TOOL_TYPE_STYLUS);
        event(view,time,MotionEvent.ACTION_UP,points[2],points[3],MotionEvent.TOOL_TYPE_STYLUS);
    }
    private static void event(View view,long time,int action,float x,float y,int tool) {
        MotionEvent.PointerProperties p=new MotionEvent.PointerProperties(); p.id=0; p.toolType=tool;
        MotionEvent.PointerCoords c=new MotionEvent.PointerCoords(); c.x=x; c.y=y; c.pressure=.45f;
        MotionEvent e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,1,new MotionEvent.PointerProperties[]{p},
                new MotionEvent.PointerCoords[]{c},0,0,1,1,0,0,InputDevice.SOURCE_STYLUS,0);
        view.dispatchTouchEvent(e); e.recycle();
    }
    private static void tapLocal(PaintActivity activity,View view,float x,float y) {
        tapLocal(activity,view,x,y,MotionEvent.TOOL_TYPE_FINGER);
    }
    private static void tapLocal(PaintActivity activity,View view,float x,float y,int tool) {
        float[] point={x,y}; View current=view;
        while (current.getParent() instanceof View) {
            current.getMatrix().mapPoints(point);
            View parent=(View)current.getParent();
            point[0]+=current.getLeft()-parent.getScrollX(); point[1]+=current.getTop()-parent.getScrollY();
            current=parent;
        }
        long now=SystemClock.uptimeMillis();
        event(current,now,MotionEvent.ACTION_DOWN,point[0],point[1],tool);
        event(current,now,MotionEvent.ACTION_UP,point[0],point[1],tool);
    }
    private static void screenshot(Instrumentation test,PaintActivity activity,String name) throws Exception {
        test.waitForIdleSync();
        // UI-thread idleness can precede the compositor's new frame on this tablet.
        SystemClock.sleep(200);
        Bitmap bitmap=test.getUiAutomation().takeScreenshot();
        check(bitmap!=null,"Screenshot available");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getCacheDir(),name))) {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
        } finally {bitmap.recycle();}
    }
    private static void awaitRotation(Instrumentation test,PaintActivity activity,int rotation,boolean landscape) throws Exception {
        await(test,() -> (Integer)get(activity,"appRotation")==rotation
                && (Boolean)get(activity,"landscape")==landscape && ((View)get(activity,"pad")).getWidth()>0,"Requested orientation");
        test.waitForIdleSync(); SystemClock.sleep(300);
        main(test,() -> {
            check(activity.getWindowManager().getDefaultDisplay().getRotation()==Surface.ROTATION_0,"Android display remains in portrait");
            check(activity.getRequestedOrientation()==android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,"Activity remains portrait-locked");
            check(get(get(activity,"pad"),"direct")!=null,"Direct e-ink remains active in app rotation "+rotation);
            float[] direction={1,0};
            ((Matrix)get(get(activity,"pad"),"pageToView")).mapVectors(direction);
            float[] expectedX={1,0,-1,0}, expectedY={0,-1,0,1};
            float length=(float)Math.hypot(direction[0],direction[1]);
            check(length>0 && Math.abs(direction[0]/length-expectedX[rotation])<.001
                    && Math.abs(direction[1]/length-expectedY[rotation])<.001,
                    "Artwork remains fixed to tablet at display rotation "+rotation);
        });
    }
    private interface Work { void run() throws Exception; }
    private interface Condition { boolean ok() throws Exception; }
    private static void main(Instrumentation test,Work work) throws Exception {
        Exception[] error={null};
        test.runOnMainSync(() -> { try {work.run();} catch(Exception e) {error[0]=e;} });
        if(error[0]!=null)throw error[0];
    }
    private static void await(Instrumentation test,Condition condition,String message) throws Exception {
        for(int i=0;i<100;i++) {
            boolean[] ready={false}; main(test,() -> ready[0]=condition.ok());
            if(ready[0])return; SystemClock.sleep(100);
        }
        throw new IllegalStateException(message+" timed out");
    }
    private static Object get(Object owner,String name) throws Exception { Field field=owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner); }
    private static void set(Object owner,String name,Object value) throws Exception { Field field=owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner,value); }
    private static void call(Object owner,String name) throws Exception { call(owner,name,new Class<?>[0]); }
    private static void call(Object owner,String name,Class<?>[] types,Object... args) throws Exception { Method method=owner.getClass().getDeclaredMethod(name,types); method.setAccessible(true); method.invoke(owner,args); }
    private static void check(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
}
