package io.github.mpdairy.monopaint;

/** Shared exact row intervals for the screen preview and the committed shape. */
final class ShapeGeometry {
    /** Inkwash: {@link #path}'s ints a row: six intervals, as many as a six-sided polygon crosses a row. */
    static final int PATH_ROW=12;
    private final ToolSettings settings;
    private final int width,height;
    private final float startX,startY;
    private int[] next;
    /**
     * Inkwash: sharp dry brushes ({@link #on}): page px to this raster's points, {@code x * k - ox}, the outline {@code k}
     * times as wide; the page's edges, where a dragged corner stops, in points. On the page: 1, none, and the page.
     */
    private final int k,ox,oy;
    private final float minX,minY,maxX,maxY;
    ShapeGeometry(ToolSettings settings,int width,int height,float x,float y) { this(settings,width,height,x,y,1,0,0,width,height); }
    private ShapeGeometry(ToolSettings settings,int width,int height,float x,float y,int k,int ox,int oy,int pageWidth,int pageHeight) {
        this.settings=settings;this.width=width;this.height=height;this.k=k;this.ox=ox;this.oy=oy;
        startX=x*k-ox;startY=y*k-oy;minX=-ox;minY=-oy;maxX=pageWidth*k-ox;maxY=pageHeight*k-oy;
    }
    /** Inkwash: the same shape at {@code grid}'s points (a kept shape drawn finer, {@link SharpStrokes#lay}): its rows and columns the grid's, its geometry the page's. */
    static ShapeGeometry on(FineGrid grid,ToolSettings settings,float x,float y) { return new ShapeGeometry(settings,grid.width,grid.height,x,y,grid.k,grid.ox,grid.oy,grid.pageWidth,grid.pageHeight); }
    /** Inkwash: Record and Replay: the shape dragged to {@code x, y} in {@code shade} ({@link StrokeLog.Shape}), which rasters as this. */
    StrokeLog.Shape logged(int shade,float x,float y) { return StrokeLog.Shape.of(settings,shade,startX,startY,x,y); }
    void raster(float x,float y,int[] spans) {
        next=spans;java.util.Arrays.fill(next,0);
        x=x*k-ox; y=y*k-oy;
        // Input beyond the canvas remains bounded, while strokes at its edges clip naturally.
        if(settings.shape!=ToolSettings.Shape.CIRCLE) {
            x=Math.max(minX,Math.min(maxX,x)); y=Math.max(minY,Math.min(maxY,y));
        }
        if(settings.shape==ToolSettings.Shape.SQUARE) {
            float size=Math.max(Math.abs(x-startX),Math.abs(y-startY));
            x=startX+Math.copySign(size,x-startX); y=startY+Math.copySign(size,y-startY);
        }
        raster(x,y);
    }
    private void raster(float x,float y) {
        if(settings.shape==ToolSettings.Shape.LINE) { line(x,y); return; }
        double left=Math.min(startX,x), right=Math.max(startX,x);
        double top=Math.min(startY,y), bottom=Math.max(startY,y);
        if(settings.shape==ToolSettings.Shape.CIRCLE) {
            double radius=Math.hypot((double)x-startX,(double)y-startY);
            left=startX-radius;right=startX+radius;top=startY-radius;bottom=startY+radius;
        }
        if(right-left<1 || bottom-top<1) return;
        double width=settings.outlineWidth*k;
        boolean oval=settings.shape==ToolSettings.Shape.OVAL || settings.shape==ToolSettings.Shape.CIRCLE;
        double cx=(left+right)/2, cy=(top+bottom)/2, rx=(right-left)/2, ry=(bottom-top)/2;
        for(int row=Math.max(0,(int)Math.ceil(top-.5)); row<Math.min(height,(int)Math.ceil(bottom-.5)); row++) {
            double outerLeft=left,outerRight=right,innerLeft=left+width,innerRight=right-width;
            boolean hollow=!settings.filled && row+.5>=top+width && row+.5<bottom-width && innerLeft<innerRight;
            if(oval) {
                double dy=row+.5-cy;
                double extent=rx*Math.sqrt(Math.max(0,1-dy*dy/(ry*ry)));
                outerLeft=cx-extent;outerRight=cx+extent;
                double irx=rx-width,iry=ry-width;
                hollow=!settings.filled && irx>0 && iry>0 && Math.abs(dy)<iry;
                if(hollow) {
                    double inner=irx*Math.sqrt(Math.max(0,1-dy*dy/(iry*iry)));
                    innerLeft=cx-inner;innerRight=cx+inner;
                }
            }
            if(hollow) { span(row,outerLeft,innerLeft); span(row,innerRight,outerRight); }
            else span(row,outerLeft,outerRight);
        }
    }
    private void span(int y,double left,double right) {
        int end=Math.min(width,(int)Math.ceil(right-.5));
        int start=Math.max(0,(int)Math.ceil(left-.5));
        if(start>=end)return;
        int offset=y*4;
        if(next[offset]<next[offset+1])offset+=2;
        next[offset]=start;next[offset+1]=end;
    }
    private void line(float endX,float endY) {
        double dx=endX-startX,dy=endY-startY,length=dx*dx+dy*dy,r=settings.outlineWidth*k/2.0;
        int bottom=Math.min(height-1,(int)Math.floor(Math.max(startY,endY)+r));
        for(int y=Math.max(0,(int)Math.floor(Math.min(startY,endY)-r));y<=bottom;y++) {
            double a=0,b=1;
            if(Math.abs(dy)>.0001) {
                a=Math.max(0,Math.min((y+.5-r-startY)/dy,(y+.5+r-startY)/dy));
                b=Math.min(1,Math.max((y+.5-r-startY)/dy,(y+.5+r-startY)/dy));
                if(a>b) continue;
            }
            double xa=startX+dx*a,xb=startX+dx*b;
            int right=Math.min(width-1,(int)Math.floor(Math.max(xa,xb)+r));
            int first=-1,last=-1;
            for(int x=Math.max(0,(int)Math.floor(Math.min(xa,xb)-r));x<=right;x++) {
                double t=length==0?0:Math.max(0,Math.min(1,((x+.5-startX)*dx+(y+.5-startY)*dy)/length));
                double px=x+.5-startX-t*dx,py=y+.5-startY-t*dy;
                if(px*px+py*py<=r*r) { if(first<0)first=x;last=x; }
            }
            if(first>=0) { next[y*4]=first;next[y*4+1]=last+1; }
        }
    }
    /**
     * Inkwash: Hold to Snap's shape following the pen: the path {@code x, y} (page px), {@code width} wide with round ends
     * and joins, as {@link #PATH_ROW} ints a row of {@code spans} (sorted, apart; the nearest merged past six).
     */
    static void path(double[] x,double[] y,double width,int pageWidth,int[] spans) {
        java.util.Arrays.fill(spans,0);
        int rows=spans.length/PATH_ROW,n=x.length;
        double r=width/2,top=Double.POSITIVE_INFINITY,bottom=Double.NEGATIVE_INFINITY;
        for(double v:y) { top=Math.min(top,v-r);bottom=Math.max(bottom,v+r); }
        int[] iv=new int[2*n];
        for(int row=Math.max(0,(int)Math.floor(top));row<Math.min(rows,(int)Math.ceil(bottom));row++) {
            double c=row+.5;
            int k=0;
            for(int i=0;i+1<n || i==0&&n==1;i++) {
                int j=Math.min(i+1,n-1);
                if(c<Math.min(y[i],y[j])-r || c>Math.max(y[i],y[j])+r) continue;
                double[] cut=capsule(x[i],y[i],x[j],y[j],r,c);
                int a=Math.max(0,(int)Math.ceil(cut[0]-.5)),b=Math.min(pageWidth,(int)Math.ceil(cut[1]-.5));
                if(a>=b) continue;
                // In order, overlapping ones merged.
                int at=k;
                while(at>0 && iv[at-2]>a) at-=2;
                System.arraycopy(iv,at,iv,at+2,k-at);iv[at]=a;iv[at+1]=b;k+=2;
            }
            int m=0;
            for(int q=0;q<k;q+=2) {
                if(m>0 && iv[q]<=iv[m-1]) iv[m-1]=Math.max(iv[m-1],iv[q+1]);
                else { iv[m]=iv[q];iv[m+1]=iv[q+1];m+=2; }
            }
            // More than a row holds: the narrowest gaps closed.
            while(m>PATH_ROW) {
                int g=2;
                for(int q=4;q<m;q+=2) if(iv[q]-iv[q-1]<iv[g]-iv[g-1]) g=q;
                iv[g-1]=iv[g+1];System.arraycopy(iv,g+2,iv,g,m-g-2);m-=2;
            }
            System.arraycopy(iv,0,spans,row*PATH_ROW,m);
        }
    }
    /** Where row centre {@code c} crosses the round-ended segment {@code a}–{@code b}, {@code r} about it: {left, right}, left past right for none. */
    private static double[] capsule(double ax,double ay,double bx,double by,double r,double c) {
        double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
        for(int e=0;e<2;e++) {
            double ex=e==0?ax:bx,dy=c-(e==0?ay:by);
            if(Math.abs(dy)>r) continue;
            double h=Math.sqrt(r*r-dy*dy);lo=Math.min(lo,ex-h);hi=Math.max(hi,ex+h);
        }
        // The band between the ends: the share along the segment in [0, 1] and the distance across within r, both linear in x.
        double dx=bx-ax,dy=by-ay,l2=dx*dx+dy*dy,l=Math.sqrt(l2),w=c-ay;
        if(l2>0) {
            double from=Double.NEGATIVE_INFINITY,to=Double.POSITIVE_INFINITY;
            if(dx!=0) { double u=ax-w*dy/dx,v=ax+(l2-w*dy)/dx;from=Math.min(u,v);to=Math.max(u,v); }
            else if(w*dy<0 || w*dy>l2) from=to=Double.NaN;
            if(dy!=0) { double u=ax+(w*dx-r*l)/dy,v=ax+(w*dx+r*l)/dy;from=Math.max(from,Math.min(u,v));to=Math.min(to,Math.max(u,v)); }
            else if(Math.abs(w)>r) from=to=Double.NaN;
            if(from<=to) { lo=Math.min(lo,from);hi=Math.max(hi,to); }
        }
        return new double[]{lo,hi};
    }
}
