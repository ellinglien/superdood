package io.github.mpdairy.monopaint;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.content.Intent;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * The page and Nomad suites' temporary book: runs {@link PageNavigationChecks} or {@link NomadUiChecks} on it, then
 * restores and drains the original session. (Its own MonoPaint Shapes checks went with MonoPaint's toolbar.)
 */
final class ShapeUiChecks {
    static void run(Instrumentation test,StringBuilder report,boolean perfOnly,boolean pickerOnly,boolean nomadOnly) throws Exception {
        run(test,report,perfOnly,pickerOnly,nomadOnly,false);
    }
    static void run(Instrumentation test,StringBuilder report,boolean perfOnly,boolean pickerOnly,boolean nomadOnly,boolean pagesOnly) throws Exception {
        PaintActivity activity=(PaintActivity)test.startActivitySync(new Intent(test.getTargetContext(),PaintActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<100&&((Boolean)get(activity,"loading")||!activity.hasWindowFocus());i++)SystemClock.sleep(100);
        test.waitForIdleSync();
        Object pad=get(activity,"pad"); DrawingBook original=(DrawingBook)get(activity,"book");
        String name=(String)get(activity,"drawingName"); ToolLibrary library=(ToolLibrary)get(activity,"library");
        int gray=(Integer)get(get(activity,"paint"),"gray"),maximum=TestAccess.maximum(activity);
        int quarter=(4-(Integer)get(activity,"appRotation"))%4;
        boolean erase=(Boolean)get(get(activity,"paint"),"eraseMode");
        SharedPreferences prefs=(SharedPreferences)get(activity,"preferences");
        boolean right=prefs.getBoolean("toolbox_right",false);
        boolean nomad=prefs.getBoolean("nomad_mode",false);
        DrawingBook.Snapshot originalPages=original.snapshot();
        byte[] originalPage=DrawingBook.encode(original.current());
        try {
            main(test,() -> {
                call(pad,"finishStroke");
                ((android.view.OrientationEventListener)get(get(activity,"rotationPrompt"),"orientationSensor")).disable();
                set(get(activity,"paint"),"eraseMode",false);set(activity,"drawingName","");set(activity,"library",new ToolLibrary());
            });
            if(pagesOnly){PageNavigationChecks.run(test,activity,report);return;}
            if(nomadOnly) {
                main(test,() -> call(activity,"replaceBook",new Class<?>[]{DrawingBook.class},new DrawingBook(new ToneDocument(640,720))));
                NomadUiChecks.run(test,activity,report);
            }
        } finally {
            main(test,() -> {
                call(activity,"closePagePanel");call(activity,"setSimulating",new Class<?>[]{boolean.class},nomad);
                call(pad,"finishStroke");
                set(activity,"drawingName",name);set(activity,"library",library);
                set(get(activity,"paint"),"eraseMode",erase);set(get(activity,"paint"),"gray",gray);TestAccess.setMaximum(activity,maximum);
                call(activity,"replaceBook",new Class<?>[]{DrawingBook.class},original);
                prefs.edit().putBoolean("toolbox_right",right).apply();
                call(activity,"requestQuarter",new Class<?>[]{int.class},quarter);call(activity,"applyToolboxSide");
                call(activity,"saveToolState");call(activity,"recoveryNow");
            });
            TestSessionSave.await(activity);
            try(java.io.FileInputStream input=new java.io.FileInputStream(DrawingStorage.recovery(activity))) {
                Object store=get(activity,"store");
                // A named notebook's recovery refers to the pages already in its file; read them from there, as the app does.
                RecoveryCodec.Recovered recovered=RecoveryCodec.read(input,path -> { try { return (java.io.File)call(store,"drawingFile",new Class<?>[]{String.class},path); } catch(Exception e) { return null; } });
                check(recovered.path.equals(name),"Original drawing destination restored");
                check(recovered.book.index()==originalPages.index&&recovered.book.count()==originalPages.pages.size(),"Original page restored");
                check(Arrays.equals(originalPage,DrawingBook.encode(recovered.book.current())),"Saved original page and layers unchanged");
                DrawingBook.Snapshot saved=recovered.book.snapshot();
                for(int i=0;i<saved.pages.size();i++)if(i!=saved.index)
                    check(Arrays.equals(originalPages.pages.get(i).bytes(),saved.pages.get(i).bytes()),"Other saved pages unchanged");
                report.append("PASS: Restored drawing destination, every page/layer, and page ").append(saved.index+1).append(" of ").append(saved.pages.size()).append(" verified from saved recovery.\n");
            }
        }
    }
    static void pen(Instrumentation test,Object pad,int action,long down,float x,float y) throws Exception {
        float[] point={x,y};main(test,() -> {((Matrix)get(pad,"pageToView")).mapPoints(point);PanelCoordinates.fromView((View)pad).mapPoints(point);});
        MotionEvent.PointerProperties prop=new MotionEvent.PointerProperties();prop.id=0;prop.toolType=MotionEvent.TOOL_TYPE_STYLUS;
        MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=point[0];coords.y=point[1];coords.pressure=.45f;coords.size=.1f;
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,new MotionEvent.PointerProperties[]{prop},new MotionEvent.PointerCoords[]{coords},0,0,1,1,0,0,InputDevice.SOURCE_STYLUS,0);
        try{check(test.getUiAutomation().injectInputEvent(event,true),"Pen injected");}finally{event.recycle();}
    }
    private interface Work {void run() throws Exception;}
    private static void main(Instrumentation test,Work work)throws Exception{Throwable[] error={null};test.runOnMainSync(() -> {try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);}
    private static Object get(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static Object call(Object owner,String name,Class<?>[] types,Object... args)throws Exception{Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,args);}
    private static Object call(Object owner,String name)throws Exception{return call(owner,name,new Class<?>[0]);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
