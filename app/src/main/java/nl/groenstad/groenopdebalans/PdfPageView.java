package nl.groenstad.groenopdebalans;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class PdfPageView extends View {
    public interface PageSwipeListener {
        void onNextPage();
        void onPreviousPage();
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap bitmap;
    private float scale = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private PageSwipeListener pageSwipeListener;

    public PdfPageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(0xFFE8EDE7);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                scale = Math.max(1f, Math.min(5f, scale * detector.getScaleFactor()));
                constrainOffsets();
                invalidate();
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }

            @Override public boolean onDoubleTap(MotionEvent e) {
                scale = scale > 1.2f ? 1f : 2.5f;
                if (scale == 1f) { offsetX = 0; offsetY = 0; }
                constrainOffsets();
                invalidate();
                return true;
            }

            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (scale > 1.02f) {
                    offsetX -= dx;
                    offsetY -= dy;
                    constrainOffsets();
                    invalidate();
                    return true;
                }
                return false;
            }

            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (scale > 1.02f || e1 == null || e2 == null || pageSwipeListener == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = Math.abs(e2.getY() - e1.getY());
                if (Math.abs(dx) > 120 && Math.abs(velocityX) > 350 && Math.abs(dx) > dy * 1.4f) {
                    if (dx < 0) pageSwipeListener.onNextPage();
                    else pageSwipeListener.onPreviousPage();
                    return true;
                }
                return false;
            }
        });
    }

    public void setPageSwipeListener(PageSwipeListener listener) {
        pageSwipeListener = listener;
    }

    public void setBitmap(Bitmap newBitmap) {
        if (bitmap != null && bitmap != newBitmap && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = newBitmap;
        scale = 1f;
        offsetX = 0f;
        offsetY = 0f;
        invalidate();
    }

    private RectF baseRect() {
        if (bitmap == null) return new RectF();
        float availableW = Math.max(1, getWidth() - getPaddingLeft() - getPaddingRight());
        float availableH = Math.max(1, getHeight() - getPaddingTop() - getPaddingBottom());
        float fit = Math.min(availableW / bitmap.getWidth(), availableH / bitmap.getHeight());
        float w = bitmap.getWidth() * fit;
        float h = bitmap.getHeight() * fit;
        float left = getPaddingLeft() + (availableW - w) / 2f;
        float top = getPaddingTop() + (availableH - h) / 2f;
        return new RectF(left, top, left + w, top + h);
    }

    private void constrainOffsets() {
        RectF r = baseRect();
        float extraX = Math.max(0, (r.width() * scale - getWidth()) / 2f);
        float extraY = Math.max(0, (r.height() * scale - getHeight()) / 2f);
        offsetX = Math.max(-extraX, Math.min(extraX, offsetX));
        offsetY = Math.max(-extraY, Math.min(extraY, offsetY));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;
        RectF r = baseRect();
        Matrix m = new Matrix();
        m.postTranslate(r.left, r.top);
        m.postScale(r.width() / bitmap.getWidth(), r.height() / bitmap.getHeight(), r.left, r.top);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        m.postScale(scale, scale, cx, cy);
        m.postTranslate(offsetX, offsetY);
        canvas.drawBitmap(bitmap, m, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        return true;
    }
}
