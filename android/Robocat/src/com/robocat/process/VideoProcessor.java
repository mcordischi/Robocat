package com.robocat.process;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Created by marto on 7/11/13.
 */
public class VideoProcessor implements Runnable{
    public static final String TAG = "OpenCVProcessor";

    public static final int FIRST_CAMERA = 0;
    public static final int SECOND_CAMERA = 1;


    // Preview size
    private static int PREVIEW_WIDTH = 480;
    private static int PREVIEW_HEIGHT = 320;
    private Size mPreviewSize;

    //Laser Thresholding
    private static Scalar LOWER_IN_RANGE = new Scalar(114,135,135);
    private static Scalar UPPER_IN_RANGE = new Scalar(142,255,255);


    /**
     * Constant used to calculate FPS value (see measureFps())
     */
    public static final int FPS_STEPS = 20;


    /**
     * Boolean
     */
    private boolean mDoProcess;
    private int mCameraId = SECOND_CAMERA;
    private VideoCapture mCamera;
    private Set<ResultCallback> mResultCallbacks = Collections.synchronizedSet(new HashSet<ResultCallback>());
    private ConcurrentLinkedQueue<Bitmap> mResultBitmaps = new ConcurrentLinkedQueue<Bitmap>();

    /**
     * Matrices used to hold the actual image data for each processing step
     */
    private Mat mCurrentFrame;
    private Mat mFilteredFrame;
    private Mat mThreshFrameResult;
    private Mat mCurrentFrameHSV;


    private int mFpsCounter;
    private double mFpsFrequency;
    private long mPrevFrameTime;
    private double mPreviousFps;


    public VideoProcessor(int cameraId) {
        mCameraId = cameraId;
        // Default preview size
        mPreviewSize = new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT);
    }



    public void addResultCallback(ResultCallback resultCallback) {
        mResultCallbacks.add(resultCallback);
    }

    public void removeResultCallback(ResultCallback resultCallback) {
        mResultCallbacks.remove(resultCallback);
    }


    public void stopProcessing() {
        mDoProcess = false;
    }

    // Setup the camera
    private void setupCamera() {
        if (mCamera != null) {
            VideoCapture camera = mCamera;
            mCamera = null; // Make it null before releasing...
            camera.release();
        }

        mCamera = new VideoCapture(mCameraId);

        // Figure out the most appropriate preview size that this camera supports.
        // We always need to do this as each device support different preview sizes for their cameras
        List<Size> previewSizes = mCamera.getSupportedPreviewSizes();
        double largestPreviewSize = 1280 * 720; // We should be smaller than this...
        double smallestWidth = 480; // Let's not get a smaller width than this...
        for (Size previewSize : previewSizes) {
            if (previewSize.area() < largestPreviewSize && previewSize.width >= smallestWidth) {
                mPreviewSize = previewSize;
            }
        }

        mCamera.set(Highgui.CV_CAP_PROP_FRAME_WIDTH, mPreviewSize.width);
        mCamera.set(Highgui.CV_CAP_PROP_FRAME_HEIGHT, mPreviewSize.height);
    }

    /**
     * Initialize the matrices and the bitmaps we will use to draw the result
     */
    private void initMatrices() {
        mCurrentFrame = new Mat();
        mCurrentFrameHSV = new Mat();
        mFilteredFrame = new Mat();
        mThreshFrameResult = new Mat(PREVIEW_WIDTH, PREVIEW_HEIGHT,CvType.CV_8SC1);
    }

    /**
     * The thread used to grab and process frames
     */
    @Override
    public void run() {
        mDoProcess = true;
        Rect previewRect = new Rect(0, 0, (int) mPreviewSize.width, (int) mPreviewSize.height);
        double fps;
        mFpsFrequency = Core.getTickFrequency();
        mPrevFrameTime = Core.getTickCount();

        setupCamera();

        initMatrices();

        while (mDoProcess && mCamera != null) {
            boolean grabbed = mCamera.grab();
            if (grabbed) {

                // Retrieve the next frame from the camera in RGB format
                mCamera.retrieve(mCurrentFrame, Highgui.CV_CAP_ANDROID_COLOR_FRAME_RGB);


                // Convert the RGB frame to HSV as it is a more appropriate format when calling Core.inRange
                Imgproc.cvtColor(mCurrentFrame, mCurrentFrameHSV, Imgproc.COLOR_RGB2HSV_FULL);


                //Red is in the Hue=0, we must move the limit
                int rotation = 128 - 255; // 255= red
                Core.add(mCurrentFrameHSV,new Scalar(rotation,0,0), mCurrentFrameHSV);

                //InRange ThresHolding
                Core.inRange(mCurrentFrameHSV,LOWER_IN_RANGE,UPPER_IN_RANGE,mThreshFrameResult);

                notifyResultCallback(mThreshFrameResult);

                fps = measureFps();
                notifyFpsResult(fps);
            }
        }

        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }

    }


    public double measureFps() {
        mFpsCounter++;
        if (mFpsCounter % FPS_STEPS == 0) {
            long time = Core.getTickCount();
            double fps = FPS_STEPS * mFpsFrequency / (time - mPrevFrameTime);
            mPrevFrameTime = time;
            mPreviousFps = fps;
        }
        return mPreviousFps;
    }


    private void notifyFpsResult(double fps) {
        for (ResultCallback resultCallback : mResultCallbacks) {
            resultCallback.onFpsUpdate(fps);
        }
    }

    private void notifyResultCallback(Mat result) {
        Bitmap resultBitmap = mResultBitmaps.poll();
        if (resultBitmap != null) {
            Utils.matToBitmap(result, resultBitmap, true);
            for (ResultCallback resultCallback : mResultCallbacks) {
                resultCallback.onResultMatrixReady(resultBitmap);
            }
        }
    }



    /**
     * Data output interface
     */
    public interface ResultCallback {
        void onResultMatrixReady(Bitmap mat);
        void onFpsUpdate(double fps);
    }


}