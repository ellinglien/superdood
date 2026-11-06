package io.github.mpdairy.monopaint;

import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Uses NomadUiChecks' disposable book and the outer suite's session restoration. */
final class PageNavigationChecks {
    static void run(Instrumentation test,PaintActivity app,StringBuilder report)throws Exception {
        verifyRaster(report);benchmark(test,app,report);
        // MonoPaint's header page row, Nomad Pages popup and page overview grid went with its header; pages turn from
        // the page bar (PageTurner), checked on the desktop.
        DrawingBook book=new DrawingBook(new ToneDocument(640,720));
        book.current().begin();for(int y=80;y<260;y++)book.current().paintSpan(70,250,y,40);book.current().finish();
        book.addPage();book.current().begin();for(int y=100;y<380;y++)book.current().paintSpan(90,400,y,160);book.current().finish();
        main(test,() -> call(app,"replaceBook",new Class<?>[]{DrawingBook.class},book));idle(test);
        for(int target:new int[]{0,1,0}) {
            main(test,() -> {
                int artwork=(Integer)get(get(app,"pad"),"pagePresentCount");
                call(app,"changePage",new Class<?>[]{int.class},target);
                check(book.index()==target,"Page turn selects the page");
                check((Integer)get(get(app,"pad"),"pagePresentCount")>artwork,"Page turn submits its artwork");
            });idle(test);
        }
        check(book.current().tone(70,80)==40,"Turning back keeps the first page's artwork");
        // Another page shown stays on the drawing screen: its fast-plane blank keeps to the page, or the column, the page bar
        // and the Zen button go white with nothing to redraw them (the owner, 2026-11-03, after the Template eye).
        if(DirectEink.fastBinaryControls())main(test,() -> {
            RectF blank=(RectF)get(app.pad,"blanked");blank.setEmpty();
            app.pad.showPage(book.current());
            check(!blank.isEmpty()&&bounds(app.pad).contains(blank),"Showing a page blanks the page's fast ink alone");
        });idle(test);
        // The page bar's + tile (the owner, 2026-10-24): a new page at the notebook's end, turned to (the tile's tap is PaintActivity.newPage).
        main(test,() -> call(app,"newPage",new Class<?>[0]));idle(test);
        check(book.count()==3&&book.index()==2&&BlankPages.blank(book,2)&&app.pad.document==book.current(),"+ adds a page at the end and turns to it");
        main(test,() -> call(app,"changePage",new Class<?>[]{int.class},1));idle(test);
        check(book.current().tone(90,100)==160,"The page before it stays");
        // An old notebook's blank page at its end goes when it is opened; the page drawn on stays.
        DrawingBook old=new DrawingBook(new ToneDocument(640,720));
        old.current().begin();for(int y=80;y<260;y++)old.current().paintSpan(70,250,y,40);old.current().finish();
        old.addPage();old.select(0);
        main(test,() -> call(app,"replaceBook",new Class<?>[]{DrawingBook.class},old));idle(test);
        check(old.count()==1&&old.index()==0,"Opened: the blank page at its end goes");
        report.append("PASS: Page turns select, submit and keep each page's artwork; the + tile adds a page at the end; an old notebook's blank page at its end goes on opening.\n");
    }
    private static void tap(Instrumentation test,View view) {
        RectF area=bounds(view);long down=SystemClock.uptimeMillis();
        event(test,MotionEvent.ACTION_DOWN,down,area.centerX(),area.centerY());
        event(test,MotionEvent.ACTION_UP,down,area.centerX(),area.centerY());
    }
    private static void benchmark(Instrumentation test,PaintActivity app,StringBuilder report)throws Exception {
        for(int[] size:new int[][]{{1284,1782},{1800,2470}}) {
            DrawingBook book=new DrawingBook(new ToneDocument(size[0],size[1]));
            for(int p=0;p<4;p++) {
                if(p>0)book.addPage();
                ToneDocument doc=book.current();doc.begin();
                for(int y=20;y<size[1]-20;y++)doc.paintSpan(20,size[0]-20,y,(y/80+p*60)%256);
                doc.finish();
            }
            main(test,() -> call(app,"replaceBook",new Class<?>[]{DrawingBook.class},book));idle(test);
            long[] times=new long[8];
            for(int i=0;i<times.length;i++) {
                final int target=i%4,slot=i;
                main(test,() -> {
                    long start=System.nanoTime();
                    call(app,"changePage",new Class<?>[]{int.class},target);
                    times[slot]=(System.nanoTime()-start)/1000000;
                });idle(test);
            }
            java.util.Arrays.sort(times);
            report.append("Page turn CPU including selection, raster, submission and recovery snapshot ")
                    .append(size[0]).append('x').append(size[1]).append(": median=").append(times[4])
                    .append(" ms, max=").append(times[7]).append(" ms (not physical panel latency).\n");
        }
    }
    private static void verifyRaster(StringBuilder report) {
        int width=257,height=193;
        java.util.ArrayList<ToneDocument.Layer> layers=new java.util.ArrayList<>();
        for(int n=0;n<8;n++) {
            byte[] tones=new byte[width*height],alpha=new byte[tones.length];
            for(int i=0;i<tones.length;i++){tones[i]=(byte)(i*17+n*61);alpha[i]=(byte)(i*7+n*31);}
            layers.add(new ToneDocument.Layer("Layer "+n,n%3!=1,tones,alpha));
        }
        ToneDocument doc=new ToneDocument(width,height,layers,0);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888);
        try {
            int[] expected=new int[width*height],actual=new int[expected.length];
            doc.render(expected,0,0,width,height);ViewportBitmap.compose(doc,bitmap,true);
            bitmap.getPixels(actual,0,width,0,0,width,height);
            check(java.util.Arrays.equals(expected,actual),"Native full-page raster matches Java for all shades, alpha and hidden layers");
            for(int i=0;i<8;i++)doc.setLayerVisible(i,false);
            ViewportBitmap.compose(doc,bitmap,true);bitmap.getPixels(actual,0,width,0,0,width,height);
            for(int pixel:actual)check(pixel==android.graphics.Color.WHITE,"All hidden layers leave white paper");
        } finally {bitmap.recycle();}
        report.append("PASS: Native page raster exactly matches calibrated Java rendering across eight alpha/hidden layers.\n");
    }
    private static void event(Instrumentation test,int action,long down,float x,float y) {
        MotionEvent.PointerProperties properties=new MotionEvent.PointerProperties();properties.id=0;properties.toolType=MotionEvent.TOOL_TYPE_STYLUS;
        MotionEvent.PointerCoords coords=new MotionEvent.PointerCoords();coords.x=x;coords.y=y;coords.pressure=.4f;
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,1,new MotionEvent.PointerProperties[]{properties},
                new MotionEvent.PointerCoords[]{coords},0,0,1,1,0,0,InputDevice.SOURCE_STYLUS,0);
        try{check(test.getUiAutomation().injectInputEvent(event,true),"Pen input injected");}finally{event.recycle();}
    }
    private static RectF bounds(View view){RectF rect=new RectF(0,0,view.getWidth(),view.getHeight());PanelCoordinates.fromView(view).mapRect(rect);return rect;}
    private static void idle(Instrumentation test){test.waitForIdleSync();SystemClock.sleep(180);}
    private static void screenshot(Instrumentation test,PaintActivity app,String name)throws Exception {
        android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(app.getCacheDir(),name))) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
        }finally{bitmap.recycle();}
    }
    private interface Work {void run()throws Exception;}
    private static void main(Instrumentation test,Work work)throws Exception {
        Throwable[] error={null};test.runOnMainSync(() -> {try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);
    }
    private static Object get(Object owner,String name)throws Exception {Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Object call(Object owner,String name,Class<?>[] types,Object... args)throws Exception {Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,args);}
    private static Object call(Object owner,String name)throws Exception{return call(owner,name,new Class<?>[0]);}
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
}
