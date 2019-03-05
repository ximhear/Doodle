package cn.hzw.doodle;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.os.PersistableBundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.forward.androids.utils.ImageUtils;
import cn.forward.androids.utils.LogUtil;
import cn.forward.androids.utils.StatusBarUtil;
import cn.forward.androids.utils.Util;
import cn.hzw.doodle.core.IDoodle;
import cn.hzw.doodle.core.IDoodleColor;
import cn.hzw.doodle.core.IDoodleItem;
import cn.hzw.doodle.core.IDoodleItemListener;
import cn.hzw.doodle.core.IDoodlePen;
import cn.hzw.doodle.core.IDoodleSelectableItem;
import cn.hzw.doodle.core.IDoodleShape;
import cn.hzw.doodle.core.IDoodleTouchDetector;

/**
 * 涂鸦界面，根据DoodleView的接口，提供页面交互
 * （这边代码和ui比较粗糙，主要目的是告诉大家DoodleView的接口具体能实现什么功能，实际需求中的ui和交互需另提别论）
 * Created by huangziwei(154330138@qq.com) on 2016/9/3.
 */
public class DoodleActivity extends Activity {

    public static final String TAG = "Doodle";

    public static final int RESULT_ERROR = -111; // 出现错误

    /**
     * 启动涂鸦界面
     *
     * @param activity
     * @param params      涂鸦参数
     * @param requestCode startActivityForResult的请求码
     * @see DoodleParams
     */
    public static void startActivityForResult(Activity activity, DoodleParams params, int requestCode) {
        Intent intent = new Intent(activity, DoodleActivity.class);
        intent.putExtra(DoodleActivity.KEY_PARAMS, params);
        activity.startActivityForResult(intent, requestCode);
    }

    /**
     * 启动涂鸦界面
     *
     * @param activity
     * @param imagePath   　图片路径
     * @param savePath    　保存路径
     * @param isDir       　保存路径是否为目录
     * @param requestCode 　startActivityForResult的请求码
     */
    @Deprecated
    public static void startActivityForResult(Activity activity, String imagePath, String savePath, boolean isDir, int requestCode) {
        DoodleParams params = new DoodleParams();
        params.mImagePath = imagePath;
        params.mSavePath = savePath;
        params.mSavePathIsDir = isDir;
        startActivityForResult(activity, params, requestCode);
    }

    /**
     * {@link DoodleActivity#startActivityForResult(Activity, String, String, boolean, int)}
     */
    @Deprecated
    public static void startActivityForResult(Activity activity, String imagePath, int requestCode) {
        DoodleParams params = new DoodleParams();
        params.mImagePath = imagePath;
        startActivityForResult(activity, params, requestCode);
    }

    public static final String KEY_PARAMS = "key_doodle_params";
    public static final String KEY_IMAGE_PATH = "key_image_path";

    private String mImagePath;

    private FrameLayout mFrameLayout;
    private IDoodle mDoodle;
    private DoodleView mDoodleView;

    private DoodleParams mDoodleParams;

    private DoodleOnTouchGestureListener mTouchGestureListener;
    private Map<IDoodlePen, Float> mPenSizeMap = new HashMap<>(); //保存每个画笔对应的最新大小

    private int mMosaicLevel = -1;

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_PARAMS, mDoodleParams);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onRestoreInstanceState(savedInstanceState, persistentState);
        mDoodleParams = savedInstanceState.getParcelable(KEY_PARAMS);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StatusBarUtil.setStatusBarTranslucent(this, true, false);
        if (mDoodleParams == null) {
            mDoodleParams = getIntent().getExtras().getParcelable(KEY_PARAMS);
        }
        if (mDoodleParams == null) {
            LogUtil.e("TAG", "mDoodleParams is null!");
            this.finish();
            return;
        }

        mImagePath = mDoodleParams.mImagePath;
        if (mImagePath == null) {
            LogUtil.e("TAG", "mImagePath is null!");
            this.finish();
            return;
        }
        LogUtil.d("TAG", mImagePath);
        if (mDoodleParams.mIsFullScreen) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        Bitmap bitmap = ImageUtils.createBitmapFromPath(mImagePath, this);
        if (bitmap == null) {
            LogUtil.e("TAG", "bitmap is null!");
            this.finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.doodle_layout);
        mFrameLayout = (FrameLayout) findViewById(R.id.doodle_container);

        mDoodle = mDoodleView = new DoodleViewWrapper(this, bitmap, new IDoodleListener() {
            @Override
            public void onSaved(IDoodle doodle, Bitmap bitmap, Runnable callback) { // 保存图片为jpg格式
                File doodleFile = null;
                File file = null;
                String savePath = mDoodleParams.mSavePath;
                boolean isDir = mDoodleParams.mSavePathIsDir;
                if (TextUtils.isEmpty(savePath)) {
                    File dcimFile = new File(Environment.getExternalStorageDirectory(), "DCIM");
                    doodleFile = new File(dcimFile, "Doodle");
                    //　保存的路径
                    file = new File(doodleFile, System.currentTimeMillis() + ".jpg");
                } else {
                    if (isDir) {
                        doodleFile = new File(savePath);
                        //　保存的路径
                        file = new File(doodleFile, System.currentTimeMillis() + ".jpg");
                    } else {
                        file = new File(savePath);
                        doodleFile = file.getParentFile();
                    }
                }
                doodleFile.mkdirs();


                List<IDoodleItem> items = mDoodleView.getAllItem();
                if (items.size() < 2) {
                    return;
                }

                DoodlePath item = (DoodlePath)items.get(1);
                RectF rect = item.mBound;
                rect.inset(-mDoodle.getSize() / 2, -mDoodle.getSize() / 2);
                int bitmapWidth = mDoodle.getBitmap().getWidth();
                int bitmapHeight = mDoodle.getBitmap().getHeight();
                float norX = rect.left / bitmapWidth;
                float norY = rect.top / bitmapHeight;
                float norRight = norX + rect.width() / bitmapWidth;
                float norBottom = norY + rect.height() / bitmapHeight;

                if (norX < 0) {
                    norX = 0;
                }
                else if (norX > 1) {
                    norX = 1;
                }

                if (norY < 0) {
                    norY = 0;
                }
                else if (norX > 1) {
                    norY = 1;
                }

                if (norRight < 0) {
                    norRight = 0;
                }
                else if (norRight > 1) {
                    norRight = 1;
                }

                if (norBottom < 0) {
                    norBottom = 0;
                }
                else if (norBottom > 1) {
                    norBottom = 1;
                }

                if (norX == norRight) {
                    return;
                }
                if (norY == norBottom) {
                    return;
                }

                float norWidth = norRight - norX;
                float norHeight = norBottom - norY;


                BitmapFactory.Options options = null;
                options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(mDoodleParams.mImagePath, options);
                int width = options.outWidth;
                int height = options.outHeight;
                Bitmap bmp = ImageUtils.createBitmapFromPath(mDoodleParams.mImagePath, width, height);
                Bitmap cropped = Bitmap.createBitmap(bmp, (int)(bmp.getWidth() * norX), (int)(bmp.getHeight() * norY), (int)(bmp.getWidth() * norWidth), (int)(bmp.getHeight() * norHeight));
                bmp = null;

                FileOutputStream outputStream = null;
                try {
                    outputStream = new FileOutputStream(file);
                    cropped.compress(Bitmap.CompressFormat.JPEG, 95, outputStream);
                    ImageUtils.addImage(getContentResolver(), file.getAbsolutePath());
                    Intent intent = new Intent();
                    intent.putExtra(KEY_IMAGE_PATH, file.getAbsolutePath());
                    setResult(Activity.RESULT_OK, intent);
                    finish();
                } catch (Exception e) {
                    e.printStackTrace();
                    onError(DoodleView.ERROR_SAVE, e.getMessage());
                } finally {
                    cropped = null;
                    Util.closeQuietly(outputStream);
                }
            }

            public void onError(int i, String msg) {
                setResult(RESULT_ERROR);
                finish();
            }

            @Override
            public void onReady(IDoodle doodle) {

                float size = mDoodleParams.mPaintUnitSize > 0 ? mDoodleParams.mPaintUnitSize * mDoodle.getUnitSize() : 0;
                if (size <= 0) {
                    size = mDoodleParams.mPaintPixelSize > 0 ? mDoodleParams.mPaintPixelSize : mDoodle.getSize();
                }
                // Set initial value
                mDoodle.setSize(size);
                // 选择画笔
                mDoodle.setPen(DoodlePen.BRUSH);
                mDoodle.setShape(DoodleShape.FILL_RECT);
                mDoodle.setColor(new DoodleColor(mDoodleParams.mPaintColor));
                mDoodle.setZoomerScale(mDoodleParams.mZoomerScale);
                mTouchGestureListener.setSupportScaleItem(mDoodleParams.mSupportScaleItem);

                // 每个画笔的初始值
                mPenSizeMap.put(DoodlePen.BRUSH, mDoodle.getSize());
                mPenSizeMap.put(DoodlePen.ERASER, mDoodle.getSize());

                Log.i("doodle", "" + mDoodleView.getWidth());
                Log.i("doodle", "" + mDoodleView.getHeight());
                Log.i("doodle", "" + mDoodleView.toX(mDoodleView.getWidth()));
                Log.i("doodle", "" + mDoodleView.toY(mDoodleView.getHeight()));
                DoodlePath path = DoodlePath.toShape(mDoodle, mDoodleView.toX(0), mDoodleView.toY(0), mDoodleView.toX(mDoodleView.getWidth()), mDoodleView.toY(mDoodleView.getHeight()));
                mDoodleView.addItem(path);
                mDoodleView.refresh();

                mDoodle.setPen(DoodlePen.ERASER);
                mDoodle.setShape(DoodleShape.HAND_WRITE);

        }
        }, null);

        mTouchGestureListener = new DoodleOnTouchGestureListener(mDoodleView) {
            @Override
            public void setSupportScaleItem(boolean supportScaleItem) {
                super.setSupportScaleItem(supportScaleItem);
            }
        };

        IDoodleTouchDetector detector = new DoodleTouchDetector(getApplicationContext(), mTouchGestureListener);
        mDoodleView.setDefaultTouchDetector(detector);

        mDoodle.setIsDrawableOutside(mDoodleParams.mIsDrawableOutside);
        mFrameLayout.addView(mDoodleView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mDoodle.setDoodleMinScale(mDoodleParams.mMinScale);
        mDoodle.setDoodleMaxScale(mDoodleParams.mMaxScale);

        initView();
    }

    private boolean canChangeColor(IDoodlePen pen) {
        return pen != DoodlePen.ERASER;
    }

    //++++++++++++++++++以下为一些初始化操作和点击监听+++++++++++++++++++++++++++++++++++++++++

    //
    private void initView() {

        mDoodleView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });

        // 长按标题栏显示原图
        findViewById(R.id.doodle_txt_title).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        mDoodle.setShowOriginal(true);
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        mDoodle.setShowOriginal(false);
                        break;
                }
                return true;
            }
        });

    }

    private ValueAnimator mRotateAnimator;

    public void onClick(final View v) {
        if (v.getId() == R.id.doodle_btn_hide_panel) {
            v.setSelected(!v.isSelected());
        } else if (v.getId() == R.id.doodle_btn_finish) {
            mDoodle.save();
        } else if (v.getId() == R.id.doodle_btn_back) {
            finish();
        } else if (v.getId() == R.id.doodle_btn_rotate) {
            // 旋转图片
            if (mRotateAnimator == null) {
                mRotateAnimator = new ValueAnimator();
                mRotateAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        int value = (int) animation.getAnimatedValue();
                        mDoodle.setDoodleRotation(value);
                    }
                });
                mRotateAnimator.setDuration(250);
            }
            if (mRotateAnimator.isRunning()) {
                return;
            }
            mRotateAnimator.setIntValues(mDoodle.getDoodleRotation(), mDoodle.getDoodleRotation() + 90);
            mRotateAnimator.start();
        } else if (v.getId() == R.id.doodle_selectable_edit) {
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mDoodleView.isEditMode()) {
                mDoodleView.setEditMode(false);
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() { // 返回键监听
        findViewById(R.id.doodle_btn_back).performClick();
    }

    /**
     * 包裹DoodleView，监听相应的设置接口，以改变UI状态
     */
    private class DoodleViewWrapper extends DoodleView {

        public DoodleViewWrapper(Context context, Bitmap bitmap, IDoodleListener listener) {
            super(context, bitmap, listener);
        }

        public DoodleViewWrapper(Context context, Bitmap bitmap, IDoodleListener listener, IDoodleTouchDetector defaultDetector) {
            super(context, bitmap, listener, defaultDetector);
        }

        private Map<IDoodlePen, Integer> mBtnPenIds = new HashMap<>();

        @Override
        public void setPen(IDoodlePen pen) {
            super.setPen(pen);
        }

        @Override
        public void setShape(IDoodleShape shape) {
            super.setShape(shape);
        }

        @Override
        public void setSize(float paintSize) {
            super.setSize(paintSize);
        }

        @Override
        public void setColor(IDoodleColor color) {
            super.setColor(color);
        }

        @Override
        public void clear() {
            super.clear();
        }
    }

}
