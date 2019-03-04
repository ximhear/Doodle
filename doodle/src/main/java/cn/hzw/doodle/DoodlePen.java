package cn.hzw.doodle;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

import cn.hzw.doodle.core.IDoodle;
import cn.hzw.doodle.core.IDoodleItem;
import cn.hzw.doodle.core.IDoodlePen;

/**
 * 常用画笔
 */
public enum DoodlePen implements IDoodlePen {

    BRUSH, // 画笔
    ERASER; // 橡皮擦

    @Override
    public void config(IDoodleItem item, Paint paint) {
        if (this == DoodlePen.ERASER) {
            IDoodle doodle = item.getDoodle();
            if ((item.getColor() instanceof DoodleColor)
                    && ((DoodleColor) item.getColor()).getBitmap() == doodle.getBitmap()) {
                // nothing
            } else {
                item.setColor(new DoodleColor(doodle.getBitmap()));
            }
        }
    }

    @Override
    public IDoodlePen copy() {
        return this;
    }

    @Override
    public void drawHelpers(Canvas canvas, IDoodle doodle) {
    }
}
