package cn.hzw.doodle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.HashMap;
import java.util.WeakHashMap;

import cn.hzw.doodle.core.IDoodle;
import cn.hzw.doodle.core.IDoodleColor;
import cn.hzw.doodle.util.DrawUtil;

/**
 * 涂鸦轨迹
 * Created by huangziwei on 2017/3/16.
 */

public class DoodlePath extends DoodleItemBase {

    private final Path mPath = new Path(); // 画笔的路径
    private final Path mOriginPath = new Path();

    private PointF mSxy = new PointF(); // 映射后的起始坐标，（手指点击）
    private PointF mDxy = new PointF(); // 映射后的终止坐标，（手指抬起）

    private Paint mPaint = new Paint();

    private final Matrix mTransform = new Matrix();
    public Rect mRect = new Rect();
    private Matrix mBitmapColorMatrix = new Matrix();

    public DoodlePath(IDoodle doodle) {
        super(doodle, null);
    }

    public DoodlePath(IDoodle doodle, DoodlePaintAttrs attrs) {
        super(doodle, attrs);
    }

    public void updateXY(float sx, float sy, float dx, float dy) {
        mSxy.set(sx, sy);
        mDxy.set(dx, dy);
        mOriginPath.reset();

        if (DoodleShape.FILL_RECT.equals(getShape()) || DoodleShape.HOLLOW_RECT.equals(getShape())) {
            updateRectPath(mOriginPath, mSxy.x, mSxy.y, mDxy.x, mDxy.y, getSize());
        }

        adjustPath(true);
    }

    public void updatePath(Path path) {
        mOriginPath.reset();
        this.mOriginPath.addPath(path);
        adjustPath(true);
    }

    public Path getPath() {
        return mPath;
    }

    private PointF getDxy() {
        return mDxy;
    }

    private PointF getSxy() {
        return mSxy;
    }

    public static DoodlePath toShape(IDoodle doodle, float sx, float sy, float dx, float dy) {
        DoodlePath path = new DoodlePath(doodle);
        path.setPen(doodle.getPen().copy());
        path.setShape(doodle.getShape().copy());
        path.setSize(doodle.getSize());
        path.setColor(doodle.getColor().copy());

        path.updateXY(sx, sy, dx, dy);
        return path;
    }

    public static DoodlePath toPath(IDoodle doodle, Path p) {
        DoodlePath path = new DoodlePath(doodle);
        path.setPen(doodle.getPen().copy());
        path.setShape(doodle.getShape().copy());
        path.setSize(doodle.getSize());
        path.setColor(doodle.getColor().copy());

        path.updatePath(p);
        return path;
    }

    @Override
    protected void doDraw(Canvas canvas) {
        mPaint.reset();
        mPaint.setStrokeWidth(getSize());
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setAntiAlias(true);

        getPen().config(this, mPaint);
        getColor().config(this, mPaint);
        getShape().config(this, mPaint);

        canvas.drawPath(getPath(), mPaint);
    }

    public RectF mBound = new RectF();

    private void resetLocationBounds(Rect rect) {
        if (mOriginPath == null) {
            return;
        }

        int diff = (int) (getSize() / 2 + 0.5f);
        mOriginPath.computeBounds(mBound, false);
        if (getShape() == DoodleShape.FILL_RECT) {
            diff = (int) getDoodle().getUnitSize();
        }
        rect.set((int) (mBound.left - diff), (int) (mBound.top - diff), (int) (mBound.right + diff), (int) (mBound.bottom + diff));
    }

    @Override
    public boolean isDoodleEditable() {
        if (getPen() == DoodlePen.ERASER) { // eraser is not editable
            return false;
        }

        return super.isDoodleEditable();
    }

    //---------计算Path
    private Path mArrowTrianglePath;

    private void updateArrowPath(Path path, float sx, float sy, float ex, float ey, float size) {
        float arrowSize = size;
        double H = arrowSize; // 箭头高度
        double L = arrowSize / 2; // 底边的一�?

        double awrad = Math.atan(L / 2 / H); // 箭头角度
        double arraow_len = Math.sqrt(L / 2 * L / 2 + H * H) - 5; // 箭头的长�?
        double[] arrXY_1 = DrawUtil.rotateVec(ex - sx, ey - sy, awrad, true, arraow_len);
        double[] arrXY_2 = DrawUtil.rotateVec(ex - sx, ey - sy, -awrad, true, arraow_len);
        float x_3 = (float) (ex - arrXY_1[0]); // (x3,y3)是第�?端点
        float y_3 = (float) (ey - arrXY_1[1]);
        float x_4 = (float) (ex - arrXY_2[0]); // (x4,y4)是第二端�?
        float y_4 = (float) (ey - arrXY_2[1]);
        // 画线
        path.moveTo(sx, sy);
        path.lineTo(x_3, y_3);
        path.lineTo(x_4, y_4);
        path.close();

        awrad = Math.atan(L / H); // 箭头角度
        arraow_len = Math.sqrt(L * L + H * H); // 箭头的长�?
        arrXY_1 = DrawUtil.rotateVec(ex - sx, ey - sy, awrad, true, arraow_len);
        arrXY_2 = DrawUtil.rotateVec(ex - sx, ey - sy, -awrad, true, arraow_len);
        x_3 = (float) (ex - arrXY_1[0]); // (x3,y3)是第�?端点
        y_3 = (float) (ey - arrXY_1[1]);
        x_4 = (float) (ex - arrXY_2[0]); // (x4,y4)是第二端�?
        y_4 = (float) (ey - arrXY_2[1]);
        if (mArrowTrianglePath == null) {
            mArrowTrianglePath = new Path();
        }
        mArrowTrianglePath.reset();
        mArrowTrianglePath.moveTo(ex, ey);
        mArrowTrianglePath.lineTo(x_4, y_4);
        mArrowTrianglePath.lineTo(x_3, y_3);
        mArrowTrianglePath.close();
        path.addPath(mArrowTrianglePath);
    }

    private void updateLinePath(Path path, float sx, float sy, float ex, float ey, float size) {
        path.moveTo(sx, sy);
        path.lineTo(ex, ey);
    }

    private void updateCirclePath(Path path, float sx, float sy, float dx, float dy, float size) {
        float radius = (float) Math.sqrt((sx - dx) * (sx - dx) + (sy - dy) * (sy - dy));
        path.addCircle(sx, sy, radius, Path.Direction.CCW);

    }

    private void updateRectPath(Path path, float sx, float sy, float dx, float dy, float size) {
        // 保证　左上角　与　右下角　的对应关系
        if (sx < dx) {
            if (sy < dy) {
                path.addRect(sx, sy, dx, dy, Path.Direction.CCW);
            } else {
                path.addRect(sx, dy, dx, sy, Path.Direction.CCW);
            }
        } else {
            if (sy < dy) {
                path.addRect(dx, sy, sx, dy, Path.Direction.CCW);
            } else {
                path.addRect(dx, dy, sx, sy, Path.Direction.CCW);
            }
        }
    }

    @Override
    public void setLocation(float x, float y, boolean changePivot) {
        super.setLocation(x, y, changePivot);
    }

    @Override
    public void setColor(IDoodleColor color) {
        super.setColor(color);
        adjustPath(false);
    }

    @Override
    public void setSize(float size) {
        super.setSize(size);


        if (mTransform == null) {
            return;
        }

        adjustPath(false);
    }

    @Override
    public void setScale(float scale) {
        super.setScale(scale);
    }

    @Override
    public void setItemRotate(float textRotate) {
        super.setItemRotate(textRotate);
    }

    private void adjustPath(boolean changePivot) {
        resetLocationBounds(mRect);
        mPath.reset();
        this.mPath.addPath(mOriginPath);
        mTransform.reset();
        mTransform.setTranslate(-mRect.left, -mRect.top);
        mPath.transform(mTransform);
        if (changePivot) {
            setPivotX(mRect.left + mRect.width() / 2);
            setPivotY(mRect.top + mRect.height() / 2);
            setLocation(mRect.left, mRect.top, false);
        }

        if ((getColor() instanceof DoodleColor)) {
            DoodleColor color = (DoodleColor) getColor();
            if (color.getType() == DoodleColor.Type.BITMAP && color.getBitmap() != null) {
                mBitmapColorMatrix.reset();

                mBitmapColorMatrix.setTranslate(-mRect.left, -mRect.top);

                int level = color.getLevel();
                mBitmapColorMatrix.preScale(level, level);
                color.setMatrix(mBitmapColorMatrix);
                refresh();
            }
        }

        refresh();
    }
}

