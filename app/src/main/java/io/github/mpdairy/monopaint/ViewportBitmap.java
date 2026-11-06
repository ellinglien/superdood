package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;

/** Scale logical tones first, then dither at screen resolution so zoom cannot alias shades. */
final class ViewportBitmap {
    static { System.loadLibrary("monopaint_display"); }
    /** Each tone's 8 × 8 dot pattern, as the canvas shows it unzoomed. */
    static final int[] DOTS=new int[256*64];
    static {
        for(int tone=0;tone<256;tone++) for(int y=0;y<8;y++) for(int x=0;x<8;x++)
            DOTS[tone*64+y*8+x]=DotPattern.pixel(tone,x,y);
    }
    /** Inkwash: each tone as a plain grey, no dots: the zoomed view's tones before Vector Ink's lines go over them ({@link #sharpen}). */
    private static final int[] PLAIN=new int[256*64];
    static { for(int i=0;i<PLAIN.length;i++) { int tone=i/64; PLAIN[i]=0xff000000|tone*0x010101; } }
    private static native void nativeRender(Bitmap source,Bitmap target,float[] inverse,int[] dots,
                                             int left,int top,int right,int bottom,boolean lines);
    private static native void nativeCompose(Bitmap target,byte[][] tones,byte[][] alpha,int[] opacity,int count,int paper,int[] dots,
                                             int left,int top,int right,int bottom);
    /** One logical composite when navigation starts; never derive shades from display dots. */
    static void compose(ToneDocument document,Bitmap target) {
        compose(document,target,false);
    }
    static void compose(ToneDocument document,Bitmap target,boolean dither) {
        ToneDocument.Snapshot snapshot=document.layerSnapshot();
        java.util.ArrayList<byte[]> tones=new java.util.ArrayList<>(),alpha=new java.util.ArrayList<>();
        int[] opacity=new int[ToneDocument.STACK+1];
        // Inkwash: a cover's paper pixels under the layers, as a layer covering the paper's tone (ToneDocument#visibleLayers).
        byte[] under=snapshot.shownPaper();
        if(under!=null) { opacity[0]=100; tones.add(under); alpha.add(ToneDocument.opaque(under.length)); }
        // Inkwash: each layer's base and kind planes, in the page's order.
        for(int k=0,end=ToneDocument.stackSize(snapshot.layers.size());k<end;k++) {
            ToneDocument.Layer entry=ToneDocument.stacked(snapshot.layers,k), owner=snapshot.layers.get(Math.min(ToneDocument.stackOwner(k),snapshot.layers.size()-1));
            if(entry==null||!owner.visible) continue;
            opacity[tones.size()]=ToneDocument.stackOpacity(owner,entry);
            tones.add(entry.tones);alpha.add(entry.alpha);
        }
        nativeCompose(target,tones.toArray(new byte[0][]),alpha.toArray(new byte[0][]),opacity,tones.size(),snapshot.paper,dither?DOTS:null,
                0,0,target.getWidth(),target.getHeight());
    }
    private static final byte[][] areaTones=new byte[ToneDocument.STACK+3][],areaAlpha=new byte[ToneDocument.STACK+3][];
    private static final int[] areaOpacity=new int[ToneDocument.STACK+3];
    /** An area at least this many pixels is rendered in bands of rows on several cores ({@link Cores}): each writes only its own rows. */
    private static final int SHARE_PX=64*1024;
    /**
     * Renders the page's {@code area} into {@code target}, a raster the page's size, as {@link #compose} does
     * the whole page and as {@link ToneDocument#render} would (without masking-fluid hatching). Reads the
     * page's own pixels rather than a snapshot, so call it on the page's owner thread, the main thread; a big
     * area is shared out over the cores while that thread waits.
     */
    static void compose(ToneDocument document,Bitmap target,boolean dither,Rect area) { compose(document,target,dither,area,null,0); }
    /**
     * As above with tracing paper's {@code ghost} planes ({@link GhostPlane.Planes}; null: none), one under the page's
     * layers and one over them (the layers above it in its stack), and its {@code layerSteps} ({@link
     * ToneDocument#visibleLayers}): the drawing screen's.
     */
    static void compose(ToneDocument document,Bitmap target,boolean dither,Rect area,GhostPlane.Planes ghost,int layerSteps) { compose(document,target,dither,area,ghost,layerSteps,0); }
    /** As above without the layers whose bits {@code without} sets (0: none): the zoomed view's page under Vector Ink's sharp lines ({@link VectorInk#sharpLayers}). */
    static void compose(ToneDocument document,Bitmap target,boolean dither,Rect area,GhostPlane.Planes ghost,int layerSteps,int without) {
        int count=document.visibleLayers(areaTones,areaAlpha,areaOpacity,ghost==null?null:ghost.under,layerSteps,ghost==null?null:ghost.overTones,ghost==null?null:ghost.overAlpha,without), paper=document.paperTone();
        int[] dots=dither?DOTS:null;
        try {
            int rows=area.height(),bands=Math.min(Cores.WORKERS,rows/16);
            if((long)area.width()*rows<SHARE_PX || bands<2) { nativeCompose(target,areaTones,areaAlpha,areaOpacity,count,paper,dots,area.left,area.top,area.right,area.bottom); return; }
            Cores.each(bands,(k,worker)->nativeCompose(target,areaTones,areaAlpha,areaOpacity,count,paper,dots,
                    area.left,area.top+rows*k/bands,area.right,area.top+rows*(k+1)/bands));
        }
        finally { java.util.Arrays.fill(areaTones,null); java.util.Arrays.fill(areaAlpha,null); }
    }
    final Bitmap bitmap;
    /** Inkwash: what lies beyond the page is the owner's lines ({@link CanvasViewport#backdrop}), not paper; only the drawing screen sets it, so no copy has it. */
    boolean backdrop;
    private final Matrix inverse=new Matrix();
    private final float[] values=new float[9];
    ViewportBitmap(int width,int height) {
        bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(false);
    }
    Rect update(Bitmap tones, Matrix pageToView, Rect dirty) { return update(tones,pageToView,dirty,null,pageToView,tones.getWidth(),tones.getHeight(),null,0); }
    /** As above with Vector Ink's {@code lines} (null: none) drawn sharp at the view's scale over the page that leaves them out ({@link VectorInk#sharpLayer}). */
    Rect update(Bitmap tones, Matrix pageToView, Rect dirty, VectorLines lines) { return update(tones,pageToView,dirty,lines,pageToView,tones.getWidth(),tones.getHeight(),null,0); }
    /** As above with sharp dry brushes' tiles ({@code view}'s at grid {@code k}; null or 0: none) where they are drawn, under the lines. */
    Rect update(Bitmap tones, Matrix pageToView, Rect dirty, VectorLines lines, SharpView view, int k) { return update(tones,pageToView,dirty,lines,pageToView,tones.getWidth(),tones.getHeight(),view,k); }
    /** As above, the lines' page ({@code width × height}) mapped by {@code linesToView}: a bigger copy's tones are scaled already. */
    private Rect update(Bitmap tones, Matrix pageToView, Rect dirty, VectorLines lines, Matrix linesToView, int width, int height, SharpView view, int k) {
        Rect area;
        if(dirty==null) area=new Rect(0,0,bitmap.getWidth(),bitmap.getHeight());
        else {
            RectF mapped=new RectF(dirty); pageToView.mapRect(mapped);
            area=new Rect(); mapped.roundOut(area); area.inset(-1,-1);
            if(!area.intersect(0,0,bitmap.getWidth(),bitmap.getHeight())) return new Rect();
        }
        if(!pageToView.invert(inverse)) throw new IllegalStateException("Invalid zoom transform");
        inverse.getValues(values);
        // Inkwash: where Vector Ink's lines are, the tones plain, the lines over them, then the dots (a scale and a shift alone; else the pixels).
        float[] m=new float[9]; linesToView.getValues(m);
        double scale=m[0], ox=m[2], oy=m[5];
        boolean straight=m[1]==0&&m[3]==0&&scale>0&&Math.abs(m[0]-m[4])<=.001*scale;
        double left=(area.left-ox)/scale, top=(area.top-oy)/scale, right=(area.right-ox)/scale, bottom=(area.bottom-oy)/scale;
        boolean sharp=straight&&lines!=null&&!lines.isEmpty()&&lines.meets(left,top,right,bottom);
        // Inkwash: sharp dry brushes' tiles drawn over this area (SharpView), the page's pixels where none is yet.
        boolean tiled=straight&&view!=null&&k>0&&view.shows(k,(int)Math.floor(left),(int)Math.floor(top),(int)Math.ceil(right),(int)Math.ceil(bottom));
        nativeRender(tones,bitmap,values,sharp||tiled?PLAIN:DOTS,area.left,area.top,area.right,area.bottom,backdrop);
        if(sharp||tiled) sharpen(sharp?lines:null,tiled?view:null,k,scale,ox,oy,area,width,height);
        return area;
    }
    private int[] band=new int[0], inner=new int[0];
    private byte[] cover=new byte[0];
    /**
     * Inkwash: {@code lines} over {@code area} of the raster (its plain greys), page point (x, y) at ({@code x * scale + ox},
     * {@code y * scale + oy}), within the page ({@code width × height}); then the area's dots, as {@link #nativeRender}
     * gives them. A band of rows at a time ({@code DrawingPad.bandRows}'s budget).
     */
    private void sharpen(VectorLines lines,SharpView view,int k,double scale,double ox,double oy,Rect area,int width,int height) {
        int w=area.width(), rows=Math.max(1,Math.min(area.height(),(1<<18)/Math.max(1,w)));
        if(band.length<w*rows) { band=new int[w*rows]; inner=new int[w*rows]; cover=new byte[w*rows]; }
        // The lines stay on the page: past its edge is the backdrop.
        int left=Math.max(area.left,(int)Math.ceil(ox)), right=Math.min(area.right,(int)Math.floor(ox+width*scale));
        int pageTop=(int)Math.ceil(oy), pageBottom=(int)Math.floor(oy+height*scale);
        for(int top=area.top;top<area.bottom;top+=rows) {
            int h=Math.min(rows,area.bottom-top);
            bitmap.getPixels(band,0,w,area.left,top,w,h);
            int t=Math.max(top,pageTop), b=Math.min(top+h,pageBottom);
            if(left<right&&t<b) {
                // The page's part of the band, gathered and put back.
                int across=right-left;
                for(int y=t;y<b;y++) System.arraycopy(band,(y-top)*w+left-area.left,inner,(y-t)*across,across);
                if(view!=null) view.over(k,scale,ox,oy,left,t,across,b-t,inner);
                if(lines!=null) lines.over(scale,ox,oy,left,t,across,b-t,inner,cover);
                for(int y=t;y<b;y++) System.arraycopy(inner,(y-t)*across,band,(y-top)*w+left-area.left,across);
            }
            for(int y=top,i=0;y<top+h;y++) for(int x=area.left;x<area.right;x++,i++) band[i]=DOTS[(band[i]&255)*64+(y&7)*8+(x&7)];
            bitmap.setPixels(band,0,w,area.left,top,w,h);
        }
    }
    void close() { bitmap.recycle(); }
}
