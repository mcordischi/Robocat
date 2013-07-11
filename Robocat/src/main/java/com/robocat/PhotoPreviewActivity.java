package com.robocat;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import com.robocat.process.VideoProcessor;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

public class PhotoPreviewActivity extends Activity implements SurfaceHolder.Callback,
        View.OnTouchListener,VideoProcessor.ResultCallback{

    public static final int DRAW_RESULT_BITMAP = 10;

    private Handler mUiHandler;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private VideoProcessor mWorker;
    private Rect mSurfaceSize;

    private double mFpsResult;
    private Paint mFpsPaint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        mFpsPaint = new Paint();
        mFpsPaint.setColor(Color.GREEN);
        mFpsPaint.setDither(true);
        mFpsPaint.setFlags(Paint.SUBPIXEL_TEXT_FLAG);
        mFpsPaint.setTextSize(24);
        mFpsPaint.setTypeface(Typeface.SANS_SERIF);

        mSurfaceView = new SurfaceView(this);
        mSurfaceView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        mSurfaceHolder = mSurfaceView.getHolder();

        // Create a Handler that we can post messages to so we avoid having to use anonymous Runnables
        // and runOnUiThread() instead
        mUiHandler = new Handler(getMainLooper(), new UiCallback());
    }


    @Override
    protected void onResume(){
        mSurfaceHolder.addCallback(this);
        mSurfaceView.setOnTouchListener(this);
        setContentView(mSurfaceView);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mWorker.stopProcessing();
        mWorker.removeResultCallback(this);

        if (mSurfaceHolder != null) {
            mSurfaceHolder.removeCallback(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.photo_preview, menu);
        return true;
    }


    private void initCameraView() {
        //TODO
        mWorker = new VideoProcessor(VideoProcessor.FIRST_CAMERA);
        mWorker.addResultCallback(this);
        new Thread(mWorker).start();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        // Initializing OpenCV is done asynchronously. We do this after our SurfaceView is ready.
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, new OpenCVLoaderCallback(this));
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        mSurfaceSize = new Rect(0, 0, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        return false;
    }


    /**
     * Video Processor Callback method
     * @param resultBitmap
     */
    @Override
    public void onResultMatrixReady(Bitmap resultBitmap) {
        mUiHandler.obtainMessage(DRAW_RESULT_BITMAP, resultBitmap).sendToTarget();
    }

    /**
     * Video Processor Callback method
     * @param fps
     */
    @Override
    public void onFpsUpdate(double fps) {
        mFpsResult = fps;
    }

    /**
     * This class will receive a callback once the OpenCV library is loaded.
     */
    private static final class OpenCVLoaderCallback extends BaseLoaderCallback {
        private Context mContext;

        public OpenCVLoaderCallback(Context context) {
            super(context);
            mContext = context;
        }

        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    ((PhotoPreviewActivity) mContext).initCameraView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }

    }

    /**
     * This Handler callback is used to draw a bitmap to our SurfaceView.
     */
    private class UiCallback implements Handler.Callback {
        @Override
        public boolean handleMessage(Message message) {
            if (message.what == DRAW_RESULT_BITMAP) {
                Bitmap resultBitmap = (Bitmap) message.obj;
                Canvas canvas = null;
                try {
                    canvas = mSurfaceHolder.lockCanvas();
                    canvas.drawBitmap(resultBitmap, null, mSurfaceSize, null);
                    canvas.drawText(String.format("FPS: %.2f", mFpsResult), 35, 45, mFpsPaint);
                    String msg = getString(R.string.surface_text);
                    float width = mFpsPaint.measureText(msg);
                    canvas.drawText(msg, mSurfaceView.getWidth() / 2 - width / 2,
                            mSurfaceView.getHeight() - 30, mFpsPaint);
                } finally {
                    if (canvas != null) {
                        mSurfaceHolder.unlockCanvasAndPost(canvas);
                    }
                }
            }
            return true;
        }
    }
}
