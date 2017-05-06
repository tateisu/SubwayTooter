package jp.juggler.subwaytooter.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.*;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.FrameLayout;

import jp.juggler.subwaytooter.R;

/**
 * Original code from http://stackoverflow.com/a/26201117/1757964; adapted for use in this project.
 */

public class RoundedRectangleLayout extends FrameLayout {
    private Bitmap maskBitmap;
    private Paint paint, maskPaint;
    private float cornerRadius;

    public RoundedRectangleLayout(Context context) {
        this(context, null);
    }

    public RoundedRectangleLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.RoundedRectangleLayout);
        float radiusFromLayout = attributes.getFloat(R.styleable.RoundedRectangleLayout_radius, 5f);
        attributes.recycle();
        cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, radiusFromLayout, metrics);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        setWillNotDraw(false);
    }

    public RoundedRectangleLayout(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs);
    }

    @Override
    public void draw(Canvas canvas) {
        Bitmap offscreenBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas offscreenCanvas = new Canvas(offscreenBitmap);

        super.draw(offscreenCanvas);

        if (maskBitmap == null) {
            maskBitmap = createMask(canvas.getWidth(), canvas.getHeight());
        }

        offscreenCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint);
        canvas.drawBitmap(offscreenBitmap, 0f, 0f, paint);
    }

    private Bitmap createMask(int width, int height) {
        Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas(mask);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);

        canvas.drawRect(0, 0, width, height, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        canvas.drawRoundRect(new RectF(0, 0, width, height), cornerRadius, cornerRadius, paint);

        return mask;
    }
}
