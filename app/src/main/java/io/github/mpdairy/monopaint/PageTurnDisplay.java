package io.github.mpdairy.monopaint;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/**
 * A one-shot page update. Preserve the previous pixels and never paint over the Pages panel, nor over {@code kept}
 * (raster rectangles {left, top, right, bottom}: Zen Mode's open fan, so a turn leaves it as it is on the panel, no
 * blink; the owner, 2026-11-07: "why do we need to close and open the fan each time? it seems like it should stay").
 */
final class PageTurnDisplay implements AutoCloseable {
    private final DirectEink display;
    private final Rect bounds, covered;
    private final java.util.List<int[]> kept;

    PageTurnDisplay(View owner,Bitmap before,Matrix bitmapToView,View overlay,java.util.List<int[]> kept) {
        this.kept=kept;
        bounds=new Rect(0,0,before.getWidth(),before.getHeight());
        covered=new Rect();
        if(overlay!=null) {
            Matrix toScreen=new Matrix(bitmapToView);
            toScreen.postConcat(PanelCoordinates.fromView(owner));
            Matrix fromScreen=new Matrix();
            if(!toScreen.invert(fromScreen))throw new IllegalStateException("Invalid page transform");
            RectF area=new RectF(0,0,overlay.getWidth(),overlay.getHeight());
            PanelCoordinates.fromView(overlay).mapRect(area);fromScreen.mapRect(area);area.roundOut(covered);
            if(!covered.intersect(bounds))covered.setEmpty();
        }
        display=DirectEink.forView(owner,before,bitmapToView,0,7);
    }
    boolean present(Bitmap after) {
        if(kept!=null&&!kept.isEmpty()) {
            // First the page away from the kept shape's box, in a piece or two, so it shows at once; then the box round
            // the shape, the strips its steps leave (each a panel update of its own, ~25 ms apiece).
            int[] box=kept.get(0).clone();
            for(int[] r:kept){box[0]=Math.min(box[0],r[0]);box[1]=Math.min(box[1],r[1]);box[2]=Math.max(box[2],r[2]);box[3]=Math.max(box[3],r[3]);}
            java.util.List<int[]> off=new java.util.ArrayList<>(kept), first=new java.util.ArrayList<>();
            first.add(box);
            if(!covered.isEmpty()){int[] c={covered.left,covered.top,covered.right,covered.bottom};off.add(c);first.add(c);}
            boolean okay=true;
            for(int[] part:MagnetDrag.minus(new int[]{bounds.left,bounds.top,bounds.right,bounds.bottom},first))
                okay=present(after,part[0],part[1],part[2],part[3])&&okay;
            for(int[] part:MagnetDrag.minus(box,off))
                okay=present(after,part[0],part[1],part[2],part[3])&&okay;
            return okay;
        }
        if(covered.isEmpty())return display.present(after,bounds)>=0;
        // Each region is disjoint; a busy submission falls back to an Android
        // redraw of the latest page instead of ever retrying a stale page.
        boolean okay=present(after,0,0,bounds.right,covered.top);
        okay=present(after,0,covered.bottom,bounds.right,bounds.bottom)&&okay;
        okay=present(after,0,covered.top,covered.left,covered.bottom)&&okay;
        return present(after,covered.right,covered.top,bounds.right,covered.bottom)&&okay;
    }
    private boolean present(Bitmap bitmap,int left,int top,int right,int bottom) {
        return right<=left || bottom<=top || display.present(bitmap,new Rect(left,top,right,bottom))>=0;
    }
    @Override public void close() { display.close(); }
}
