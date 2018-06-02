package com.esp.videoar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.esp.videoar.SampleApplication.SampleApplicationControl;
import com.esp.videoar.SampleApplication.SampleApplicationException;
import com.esp.videoar.SampleApplication.SampleApplicationSession;
import com.esp.videoar.SampleApplication.utils.LoadingDialogHandler;
import com.esp.videoar.SampleApplication.utils.SampleApplicationGLView;
import com.esp.videoar.SampleApplication.utils.Texture;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.HINT;
import com.vuforia.ObjectTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;

import java.util.Vector;

public class MainActivity extends AppCompatActivity implements SampleApplicationControl {

    private static final String TAG = "MainActivity";

    private SampleApplicationSession mAppSession;

    private Activity mActivity;

    public static final int NUM_TARGETS = 8;
    // todo
    public static final int KFC = 0;
    public static final int THTRUEMILK = 1;
    public static final int NIKE = 2;
    public static final int SAMSUNG = 3;
    public static final int PUMA = 4;
    public static final int ASUS = 5;
    public static final int PEPSI = 6;
    public static final int COCACOLA = 7;

    public static final int DEFAULT = 8;

    private MediaPlayerHelper mVideoPlayerHelper[] = null;
    private int mSeekPosition[] = null;
    private boolean mWasPlaying[] = null;
    private String mMovieName[] = null;

    private boolean mReturningFromFullScreen = false;

    private SampleApplicationGLView mGlView;

    private VideoRenderer mRenderer;

    private Vector<Texture> mTextures;

    DataSet dataSet = null;

    private RelativeLayout mUILayout;

    private boolean mPlayFullscreenVideo = false;

    private LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);

    private AlertDialog mErrorDialog;

    boolean mIsDroidDevice = false;
    boolean mIsInitialized = false;

    private ImageView fullscreenButton;


    private GestureDetector mGestureDetector = null;
    private GestureDetector.SimpleOnGestureListener mSimpleListener = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);

        mAppSession = new SampleApplicationSession(this);

        mActivity = this;

        startLoadingAnimation();

        mAppSession.initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mTextures = new Vector<Texture>();
        loadTextures();

        mSimpleListener = new GestureDetector.SimpleOnGestureListener();
        mGestureDetector = new GestureDetector(getApplicationContext(),
                mSimpleListener);

        mVideoPlayerHelper = new MediaPlayerHelper[NUM_TARGETS];
        mSeekPosition = new int[NUM_TARGETS];
        mWasPlaying = new boolean[NUM_TARGETS];
        mMovieName = new String[NUM_TARGETS];

        for (int i = 0; i < NUM_TARGETS; i++) {
            mVideoPlayerHelper[i] = new MediaPlayerHelper();
            mVideoPlayerHelper[i].init();
            mVideoPlayerHelper[i].setActivity(this);
        }

        mMovieName[KFC] = "VideoPlayback/kfc.mp4";
        mMovieName[THTRUEMILK] = "VideoPlayback/THTrueMilk.mp4.mp4";
        mMovieName[NIKE] = "VideoPlayback/nike.mp4";
        mMovieName[SAMSUNG] = "VideoPlayback/samsung.mp4";
        mMovieName[PUMA] = "VideoPlayback/adidas.mp4";
        mMovieName[PEPSI] = "VideoPlayback/pepsi.mp4";
        mMovieName[ASUS] = "VideoPlayback/asus.mp4";
        mMovieName[COCACOLA] = "VideoPlayback/cocacola.mp4";
        // todo

        mGestureDetector.setOnDoubleTapListener(new GestureDetector.OnDoubleTapListener() {
            public boolean onDoubleTap(MotionEvent e) {
                // We do not react to this event
                return false;
            }


            public boolean onDoubleTapEvent(MotionEvent e) {
                // We do not react to this event
                return false;
            }


            // Handle the single tap
            public boolean onSingleTapConfirmed(MotionEvent e) {
                boolean isSingleTapHandled = false;
                // Do not react if the StartupScreen is being displayed
                for (int i = 0; i < NUM_TARGETS; i++) {
                    // Verify that the tap happened inside the target
                    if (mRenderer != null && mRenderer.isTapOnScreenInsideTarget(i, e.getX(),
                            e.getY())) {
                        // Check if it is playable on texture
                        if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                            // We can play only if the movie was paused, ready
                            // or stopped
                            if ((mVideoPlayerHelper[i].getStatus() == MediaState.PAUSED)
                                    || (mVideoPlayerHelper[i].getStatus() == MediaState.READY)
                                    || (mVideoPlayerHelper[i].getStatus() == MediaState.STOPPED)
                                    || (mVideoPlayerHelper[i].getStatus() == MediaState.REACHED_END)) {
                                // Pause all other media
                                pauseAll(i);

                                // If it has reached the end then rewind
                                if ((mVideoPlayerHelper[i].getStatus() == MediaState.REACHED_END))
                                    mSeekPosition[i] = 0;

                                mVideoPlayerHelper[i].play(mPlayFullscreenVideo,
                                        mSeekPosition[i]);
                                mSeekPosition[i] = MediaPlayerHelper.CURRENT_POSITION;
                            } else if (mVideoPlayerHelper[i].getStatus() == MediaState.PLAYING) {
                                // If it is playing then we pause it
                                mVideoPlayerHelper[i].pause();
                            }
                        } else if (mVideoPlayerHelper[i].isPlayableFullscreen()) {
                            // If it isn't playable on texture
                            // Either because it wasn't requested or because it
                            // isn't supported then request playback fullscreen.
                            mVideoPlayerHelper[i].play(true,
                                    MediaPlayerHelper.CURRENT_POSITION);
                        }

                        isSingleTapHandled = true;

                        // Even though multiple videos can be loaded only one
                        // can be playing at any point in time. This break
                        // prevents that, say, overlapping videos trigger
                        // simultaneously playback.
                        break;
                    }
                }

                return isSingleTapHandled;
            }
        });
    }

    public void playVideo(int i) {
        int position = mVideoPlayerHelper[i].getCurrentPosition();
        mVideoPlayerHelper[i].play(mPlayFullscreenVideo, position);
    }

    private void loadTextures() {
        for (int i = 0; i < NUM_TARGETS; i++) {
            mTextures.add(Texture.loadTextureFromApk("VideoPlayback/preview_alpha.png", getAssets()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mIsDroidDevice) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        try {
            mAppSession.resumeAR();
        } catch (SampleApplicationException e) {
            Log.e(TAG, e.getString());
        }

        // Resume the GL view:
        if (mGlView != null) {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }

        // Reload all the movies
        if (mRenderer != null) {
            for (int i = 0; i < NUM_TARGETS; i++) {
                if (!mReturningFromFullScreen) {
                    mRenderer.requestLoad(i, mMovieName[i], mSeekPosition[i],
                            false);
                } else {
                    mRenderer.requestLoad(i, mMovieName[i], mSeekPosition[i],
                            mWasPlaying[i]);
                }
            }
        }

        mReturningFromFullScreen = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {

            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

            if (resultCode == RESULT_OK) {
                // The following values are used to indicate the position in
                // which the video was being played and whether it was being
                // played or not:
                String movieBeingPlayed = data.getStringExtra("movieName");
                mReturningFromFullScreen = true;

                // Find the movie that was being played full screen
                for (int i = 0; i < NUM_TARGETS; i++) {
                    if (movieBeingPlayed.compareTo(mMovieName[i]) == 0) {
                        mSeekPosition[i] = data.getIntExtra(
                                "currentSeekPosition", 0);
                        mWasPlaying[i] = false;
                    }
                }
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mAppSession.onConfigurationChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mGlView != null) {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        // Store the playback state of the movies and unload them:
        for (int i = 0; i < NUM_TARGETS; i++) {
            // If the activity is paused we need to store the position in which
            // this was currently playing:
            if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                mSeekPosition[i] = mVideoPlayerHelper[i].getCurrentPosition();
                mWasPlaying[i] = mVideoPlayerHelper[i].getStatus() == MediaState.PLAYING;
            }

            // We also need to release the resources used by the helper, though
            // we don't need to destroy it:
            if (mVideoPlayerHelper[i] != null)
                mVideoPlayerHelper[i].unload();
        }

        mReturningFromFullScreen = false;

        try {
            mAppSession.pauseAR();
        } catch (SampleApplicationException e) {
            Log.e(TAG, e.getString());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (int i = 0; i < NUM_TARGETS; i++) {
            // If the activity is destroyed we need to release all resources:
            if (mVideoPlayerHelper[i] != null)
                mVideoPlayerHelper[i].unInit();
            mVideoPlayerHelper[i] = null;
        }

        try {
            mAppSession.stopAR();
        } catch (SampleApplicationException e) {
            Log.e(TAG, e.getString());
        }

        // Unload texture:
        mTextures.clear();
        mTextures = null;

        System.gc();
    }

    private void pauseAll(int except) {
        // And pause all the playing videos:
        for (int i = 0; i < NUM_TARGETS; i++) {
            // We can make one exception to the pause all calls:
            if (i != except) {
                // Check if the video is playable on texture
                if (mVideoPlayerHelper[i].isPlayableOnTexture()) {
                    // If it is playing then we pause it
                    mVideoPlayerHelper[i].pause();
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        pauseAll(-1);
        super.onBackPressed();
    }


    private void startLoadingAnimation() {
        mUILayout = (RelativeLayout) View.inflate(this, R.layout.camera_overlay,
                null);

        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
                .findViewById(R.id.loading_indicator);

        fullscreenButton = mUILayout.findViewById(R.id.fullscreen);
        fullscreenButton.setOnClickListener(this::viewFullScreen);

        // Shows the loading indicator at start
        loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

        // Adds the inflated layout to the view
        addContentView(mUILayout, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void initApplicationAR() {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();

        mGlView = new SampleApplicationGLView(this);
        mGlView.init(translucent, depthSize, stencilSize);

        mRenderer = new VideoRenderer(this, mAppSession);
        mRenderer.setTextures(mTextures);

        // The renderer comes has the OpenGL context, thus, loading to texture
        // must happen when the surface has been created. This means that we
        // can't load the movie from this thread (GUI) but instead we must
        // tell the GL thread to load it once the surface has been created.
        for (int i = 0; i < NUM_TARGETS; i++) {
            mRenderer.setVideoPlayerHelper(i, mVideoPlayerHelper[i]);
            mRenderer.requestLoad(i, mMovieName[i], 0, false);
        }

        mGlView.setRenderer(mRenderer);

        for (int i = 0; i < NUM_TARGETS; i++) {
            float[] temp = {0f, 0f, 0f};
            mRenderer.targetPositiveDimensions[i].setData(temp);
            mRenderer.videoPlaybackTextureID[i] = -1;
        }

    }
    
    @Override
    public boolean doInitTrackers() {
        boolean result = true;

        // Initialize the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        Tracker tracker = trackerManager.initTracker(ObjectTracker
                .getClassType());
        if (tracker == null) {
            Log.d(TAG, "Failed to initialize ObjectTracker.");
            result = false;
        }

        return result;
    }

    @Override
    public boolean doLoadTrackersData() {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null) {
            Log.d(
                    TAG,
                    "Failed to load tracking data set because the ObjectTracker has not been initialized.");
            return false;
        }

        // Create the data sets:
        dataSet = objectTracker.createDataSet();
        if (dataSet == null) {
            Log.d(TAG, "Failed to create a new tracking data.");
            return false;
        }

        // Load the data sets:
        if (!dataSet.load("VideoAR.xml",
                STORAGE_TYPE.STORAGE_APPRESOURCE)) {
            Log.d(TAG, "Failed to load data set.");
            return false;
        }

        // Activate the data set:
        if (!objectTracker.activateDataSet(dataSet)) {
            Log.d(TAG, "Failed to activate data set.");
            return false;
        }

        Log.d(TAG, "Successfully loaded and activated data set.");
        return true;
    }

    @Override
    public boolean doStartTrackers() {
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType());
        if (objectTracker != null) {
            objectTracker.start();
            Vuforia.setHint(HINT.HINT_MAX_SIMULTANEOUS_IMAGE_TARGETS, 2);
        } else
            result = false;

        return result;
    }

    @Override
    public boolean doStopTrackers() {
        boolean result = true;

        Tracker objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType());
        if (objectTracker != null)
            objectTracker.stop();
        else
            result = false;

        return result;
    }

    @Override
    public boolean doUnloadTrackersData() {
        boolean result = true;

        // Get the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) trackerManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null) {
            Log.d(
                    TAG,
                    "Failed to destroy the tracking data set because the ObjectTracker has not been initialized.");
            return false;
        }

        if (dataSet != null) {
            if (objectTracker.getActiveDataSet(0) == dataSet
                    && !objectTracker.deactivateDataSet(dataSet)) {
                Log.d(
                        TAG,
                        "Failed to destroy the tracking data set StonesAndChips because the data set could not be deactivated.");
                result = false;
            } else if (!objectTracker.destroyDataSet(dataSet)) {
                Log.d(TAG,
                        "Failed to destroy the tracking data set StonesAndChips.");
                result = false;
            }

            dataSet = null;
        }

        return result;
    }

    @Override
    public boolean doDeinitTrackers() {
        boolean result = true;

        // Deinit the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        trackerManager.deinitTracker(ObjectTracker.getClassType());

        return result;
    }

    @Override
    public void onInitARDone(SampleApplicationException e) {
        if (e == null) {
            initApplicationAR();

            mRenderer.mIsActive = true;

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();

            // Hides the Loading Dialog
            loadingDialogHandler
                    .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);

            // Sets the layout background to transparent
            mUILayout.setBackgroundColor(Color.TRANSPARENT);

            try {
                mAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);
            } catch (SampleApplicationException ex) {
                Log.e(TAG, ex.getString());
            }

            boolean result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);

            if (!result)
                Log.e(TAG, "Unable to enable continuous autofocus");


            mIsInitialized = true;

        } else {
            Log.e(TAG, e.getString());
            showInitializationErrorMessage(e.getString());
        }
    }

    @Override
    public void onVuforiaUpdate(State state) {

    }

    public void showInitializationErrorMessage(String message) {
        final String errorMessage = message;
        runOnUiThread(new Runnable() {
            public void run() {
                if (mErrorDialog != null) {
                    mErrorDialog.dismiss();
                }

                // Generates an Alert Dialog to show the error message
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        MainActivity.this);
                builder
                        .setMessage(errorMessage)
                        .setTitle(getString(R.string.INIT_ERROR))
                        .setCancelable(false)
                        .setIcon(0)
                        .setPositiveButton("OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        finish();
                                    }
                                });

                mErrorDialog = builder.create();
                mErrorDialog.show();
            }
        });
    }

    public void viewFullScreen(View view) {
        mPlayFullscreenVideo = !mPlayFullscreenVideo;

        for (int i = 0; i < mVideoPlayerHelper.length; i++) {
            if (mVideoPlayerHelper[i].getStatus() == MediaState.PLAYING) {
                mVideoPlayerHelper[i].pause();

                mVideoPlayerHelper[i].play(true,
                        mSeekPosition[i]);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = false;
        if (!result)
            mGestureDetector.onTouchEvent(event);

        return result;
    }
}
