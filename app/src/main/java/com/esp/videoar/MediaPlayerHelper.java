package com.esp.videoar;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;


public class MediaPlayerHelper implements OnPreparedListener, OnBufferingUpdateListener,
        OnCompletionListener, OnErrorListener {

    private static final String TAG = "MediaPlayerHelper";

    public static final int CURRENT_POSITION = -1;
    private MediaPlayer mMediaPlayer = null;
    private MediaType mMediaType = MediaType.UNKNOWN;
    private SurfaceTexture mSurfaceTexture = null;
    private int mCurrentBufferingPercentage = 0;
    private String mMovieName = "";
    private byte mTextureID = 0;
    private Activity mParentActivity = null;
    private MediaState mCurrentState = MediaState.NOT_READY;
    private boolean mShouldPlayImmediately = false;
    private int mSeekPosition = CURRENT_POSITION;
    private ReentrantLock mMediaPlayerLock = null;
    private ReentrantLock mSurfaceTextureLock = null;
    Intent mPlayerHelperActivityIntent = null;

    public boolean init() {
        mMediaPlayerLock = new ReentrantLock();
        mSurfaceTextureLock = new ReentrantLock();
        return true;
    }

    public void unInit() {
        unload();
        mSurfaceTextureLock.lock();
        mSurfaceTexture = null;
        mSurfaceTextureLock.unlock();
    }

    public boolean load(String fileName, MediaType requestedType, boolean playOnTextureImmediately, int seekPosition) {
        boolean canBeOnTexture = false;
        boolean canBeFullscreen = false;

        boolean result = false;
        mMediaPlayerLock.lock();
        mSurfaceTextureLock.lock();

        // Nếu video đã sẵn sàng hoặc đã được load từ trước đó thì bỏ qua
        if ((mCurrentState == MediaState.READY) || (mMediaPlayer != null)) {
            Log.d(TAG, "Already loaded");
        } else {
            boolean textureOnly = requestedType == MediaType.ON_TEXTURE;
            boolean isOnTextureWithFullscreen = requestedType == MediaType.ON_TEXTURE_FULLSCREEN;
            if (textureOnly || isOnTextureWithFullscreen) {
                if (mSurfaceTexture == null) {
                    Log.d(TAG, "Can't load file to ON_TEXTURE because the Surface Texture is not ready");
                } else {
                    try {
                        mMediaPlayer = new MediaPlayer();
                        AssetFileDescriptor afd = mParentActivity.getAssets().openFd(fileName);
                        mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                                afd.getLength());
                        afd.close();

                        mMediaPlayer.setOnPreparedListener(this);
                        mMediaPlayer.setOnBufferingUpdateListener(this);
                        mMediaPlayer.setOnCompletionListener(this);
                        mMediaPlayer.setOnErrorListener(this);
                        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mMediaPlayer.setSurface(new Surface(mSurfaceTexture));
                        canBeOnTexture = true;
                        mShouldPlayImmediately = playOnTextureImmediately;
                        mMediaPlayer.prepareAsync();
                    } catch (IOException e) {
                        Log.d(TAG, "Error while creating the MediaPlayer: " + e.toString());
                        mCurrentState = MediaState.ERROR;
                        mMediaPlayerLock.unlock();
                        mSurfaceTextureLock.unlock();
                        return false;
                    }
                }
            } else {
                try {
                    AssetFileDescriptor afd = mParentActivity.getAssets().openFd(fileName);
                    afd.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (requestedType == MediaType.FULLSCREEN || requestedType == MediaType.ON_TEXTURE_FULLSCREEN) {
                mPlayerHelperActivityIntent = new Intent(mParentActivity, FullScreenActivity.class);
                mPlayerHelperActivityIntent
                        .setAction(android.content.Intent.ACTION_VIEW);
                canBeFullscreen = true;
            }

            // Lưu các tham số
            mMovieName = fileName;
            mSeekPosition = seekPosition;

            if (canBeFullscreen && canBeOnTexture)
                mMediaType = MediaType.ON_TEXTURE_FULLSCREEN;
            else if (canBeFullscreen) {
                mMediaType = MediaType.FULLSCREEN;
                mCurrentState = MediaState.READY;
            } // If it is pure fullscreen then we're ready otherwise we let the
            // MediaPlayer load first
            else if (canBeOnTexture)
                mMediaType = mMediaType.ON_TEXTURE;
            else
                mMediaType = MediaType.UNKNOWN;

            result = true;
        }

        mSurfaceTextureLock.unlock();
        mMediaPlayerLock.unlock();

        return result;
    }


    public boolean unload() {
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.stop();
            } catch (Exception e) {
                mMediaPlayerLock.unlock();
                Log.e(TAG, "Could not start playback");
            }

            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        mMediaPlayerLock.unlock();

        mCurrentState = MediaState.NOT_READY;
        mMediaType = MediaType.UNKNOWN;
        return true;
    }

    // Indicates whether the movie can be played on a texture
    boolean isPlayableOnTexture() {
        return mMediaType == MediaType.ON_TEXTURE || mMediaType == MediaType.ON_TEXTURE_FULLSCREEN;
    }

    MediaState getStatus() {
        return mCurrentState;
    }


    // Returns the width of the video frame
    public int getVideoWidth() {
        if (!isPlayableOnTexture()) {
            // Log.d( LOGTAG,
            // "Cannot get the video width if it is not playable on texture");
            return -1;
        }

        if ((mCurrentState == MediaState.NOT_READY)
                || (mCurrentState == MediaState.ERROR)) {
            // Log.d( LOGTAG, "Cannot get the video width if it is not ready");
            return -1;
        }

        int result = -1;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null)
            result = mMediaPlayer.getVideoWidth();
        mMediaPlayerLock.unlock();

        return result;
    }


    public int getVideoHeight() {
        if (!isPlayableOnTexture()) {
            return -1;
        }

        if ((mCurrentState == MediaState.NOT_READY) || (mCurrentState == MediaState.ERROR)) {
            return -1;
        }
        int result = -1;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            result = mMediaPlayer.getVideoHeight();
        }
        mMediaPlayerLock.unlock();

        return result;
    }

    public float getVideoLength() {
        if (!isPlayableOnTexture()) {
            return -1;
        }

        if (mCurrentState == MediaState.NOT_READY || mCurrentState == MediaState.ERROR) {
            return -1;
        }
        int result = -1;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            result = mMediaPlayer.getDuration() / 1000;
        }
        mMediaPlayerLock.unlock();

        return result;
    }

    public boolean isPlayableFullscreen() {
        return mMediaType == MediaType.FULLSCREEN || mMediaType == MediaType.ON_TEXTURE_FULLSCREEN;
    }

    public boolean play(boolean fullScreen, int seekPosition) {
        if (fullScreen) {
            if (!isPlayableFullscreen()) {
                return false;
            }
            if (isPlayableOnTexture()) {
                mMediaPlayerLock.lock();

                if (mMediaPlayer == null) {
                    mMediaPlayerLock.unlock();
                    return false;
                }


                mPlayerHelperActivityIntent.putExtra("shouldPlayImmediately", true);
                try {
                    mMediaPlayer.pause();
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                    Log.e(TAG, "Could not pause playback");
                }
                if (seekPosition != CURRENT_POSITION) {
                    mPlayerHelperActivityIntent.putExtra("currentSeekPosition", seekPosition);
                } else {
                    mPlayerHelperActivityIntent.putExtra("currentSeekPosition", mMediaPlayer.getCurrentPosition());
                }

                mMediaPlayerLock.unlock();
            } else {
                mPlayerHelperActivityIntent.putExtra("currentSeekPosition", 0);
                mPlayerHelperActivityIntent.putExtra("shouldPlayImmediately", true);

                if (seekPosition != CURRENT_POSITION) {
                    mPlayerHelperActivityIntent.putExtra("currentSeekPosition", seekPosition);
                } else {
                    mPlayerHelperActivityIntent.putExtra("currentSeekPosition", 0);
                }
            }

            mPlayerHelperActivityIntent.putExtra("requestedOrientation", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            mPlayerHelperActivityIntent.putExtra("movieName", mMovieName);
            mParentActivity.startActivityForResult(mPlayerHelperActivityIntent, 1);
            return true;
        } else {
            if (!isPlayableOnTexture()) {
                Log.d(TAG, "Cannot play this video on texture, it was either not requested on load or is not supported on this plattform");
                return false;
            }

            if (mCurrentState == MediaState.NOT_READY || mCurrentState == MediaState.ERROR) {
                Log.d(TAG, "Cannot play this video if it is not ready");
                return false;
            }

            mMediaPlayerLock.lock();

            if (seekPosition != CURRENT_POSITION) {
                try {
                    mMediaPlayer.seekTo(seekPosition);
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                    Log.e(TAG, "Could not seek to position");
                }
            } else {
                if (mCurrentState == MediaState.REACHED_END) {
                    try {
                        mMediaPlayer.seekTo(0);
                    } catch (Exception e) {
                        mMediaPlayerLock.unlock();
                        Log.e(TAG, "Could not seek to position");
                    }
                }
            }
            try {
                mMediaPlayer.start();
            } catch (Exception e) {
                mMediaPlayerLock.unlock();
                Log.e(TAG, "Could not start playback");
            }
            mCurrentState = MediaState.PLAYING;

            mMediaPlayerLock.unlock();

            return true;
        }
    }

    boolean pause() {
        if (!isPlayableOnTexture()) {
            return false;
        }

        if (mCurrentState == MediaState.NOT_READY || mCurrentState == MediaState.ERROR) {
            return false;
        }
        boolean result = false;

        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            if (mMediaPlayer.isPlaying()) {
                try {
                    mMediaPlayer.pause();
                } catch (Exception e) {
                    mMediaPlayerLock.unlock();
                    Log.e(TAG, "Could not pause playback");
                }
                mCurrentState = MediaState.PAUSED;
                result = true;
            }
        }
        mMediaPlayerLock.unlock();

        return result;
    }

    // Stops the current movie being played
    public boolean stop() {
        if (!isPlayableOnTexture()) {
            // Log.d( TAG,
            // "Cannot stop this video since it is not on texture");
            return false;
        }

        if ((mCurrentState == MediaState.NOT_READY)
                || (mCurrentState == MediaState.ERROR)) {
            // Log.d( TAG, "Cannot stop this video if it is not ready");
            return false;
        }

        boolean result = false;

        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            mCurrentState = MediaState.STOPPED;
            try {
                mMediaPlayer.stop();
            } catch (Exception e) {
                mMediaPlayerLock.unlock();
                Log.e(TAG, "Could not stop playback");
            }

            result = true;
        }
        mMediaPlayerLock.unlock();

        return result;
    }

    // Tells the VideoPlayerHelper to update the data from the video feed
    public byte updateVideoData() {
        if (!isPlayableOnTexture()) {
            // Log.d( TAG,
            // "Cannot update the data of this video since it is not on texture");
            return -1;
        }

        byte result = -1;

        mSurfaceTextureLock.lock();
        if (mSurfaceTexture != null) {
            // Only request an update if currently playing
            if (mCurrentState == MediaState.PLAYING)
                mSurfaceTexture.updateTexImage();

            result = mTextureID;
        }
        mSurfaceTextureLock.unlock();

        return result;
    }

    // Moves the movie to the requested seek position
    public boolean seekTo(int position) {
        if (!isPlayableOnTexture()) {
            // Log.d( TAG,
            // "Cannot seek-to on this video since it is not on texture");
            return false;
        }

        if ((mCurrentState == MediaState.NOT_READY)
                || (mCurrentState == MediaState.ERROR)) {
            // Log.d( TAG,
            // "Cannot seek-to on this video if it is not ready");
            return false;
        }

        boolean result = false;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            try {
                mMediaPlayer.seekTo(position);
            } catch (Exception e) {
                mMediaPlayerLock.unlock();
                Log.e(TAG, "Could not seek to position");
            }
            result = true;
        }
        mMediaPlayerLock.unlock();

        return result;
    }


    // Gets the current seek position
    public int getCurrentPosition() {
        if (!isPlayableOnTexture()) {
            // Log.d( TAG,
            // "Cannot get the current playback position of this video since it is not on texture");
            return -1;
        }

        if ((mCurrentState == MediaState.NOT_READY)
                || (mCurrentState == MediaState.ERROR)) {
            // Log.d( TAG,
            // "Cannot get the current playback position of this video if it is not ready");
            return -1;
        }

        int result = -1;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null)
            result = mMediaPlayer.getCurrentPosition();
        mMediaPlayerLock.unlock();

        return result;
    }


    // Sets the volume of the movie to the desired value
    public boolean setVolume(float value) {
        if (!isPlayableOnTexture()) {
            // Log.d( TAG,
            // "Cannot set the volume of this video since it is not on texture");
            return false;
        }

        if ((mCurrentState == MediaState.NOT_READY)
                || (mCurrentState == MediaState.ERROR)) {
            // Log.d( TAG,
            // "Cannot set the volume of this video if it is not ready");
            return false;
        }

        boolean result = false;
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume(value, value);
            result = true;
        }
        mMediaPlayerLock.unlock();

        return result;
    }


    //
    // The following functions are specific to Android
    // and will likely not be implemented on other platforms

    // Gets the buffering percentage in case the movie is loaded from network
    public int getCurrentBufferingPercentage() {
        return mCurrentBufferingPercentage;
    }


    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int i) {
        mMediaPlayerLock.lock();
        if (mMediaPlayer != null) {
            if (mediaPlayer == mMediaPlayer)
                mCurrentBufferingPercentage = i;
        }
        mMediaPlayerLock.unlock();
    }

    // With this we can set the parent activity
    public void setActivity(Activity newActivity) {
        mParentActivity = newActivity;
    }

    @SuppressLint("NewApi")
    public boolean setupSurfaceTexture(int TextureID) {
        // We create a surface texture where the video can be played
        // We have to give it a texture id of an already created
        // OpenGL texture
        mSurfaceTextureLock.lock();
        mSurfaceTexture = new SurfaceTexture(TextureID);
        mTextureID = (byte) TextureID;
        mSurfaceTextureLock.unlock();
        return true;
    }

    public void getSurfaceTextureTransformMatrix(float[] mtx) {
        mSurfaceTextureLock.lock();
        if (mSurfaceTexture != null) {
            mSurfaceTexture.getTransformMatrix(mtx);
        }
        mSurfaceTextureLock.unlock();
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        mCurrentState = MediaState.READY;

        // If requested an immediate play
        if (mShouldPlayImmediately)
            play(false, mSeekPosition);

        mSeekPosition = 0;
    }



    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        mCurrentState = MediaState.REACHED_END;
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
        if (mediaPlayer == mMediaPlayer) {
            String errorDescription;

            switch (what) {
                case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                    errorDescription = "The video is streamed and its container is not valid for progressive playback";
                    break;
                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                    errorDescription = "Media server died";
                    break;
                case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                    errorDescription = "Unspecified media player error";
                    break;
                default:
                    errorDescription = "Unknown error " + what;
            }

            String error = "Error while opening the file. Unloading the media player ("
                    + errorDescription + ", " + extra + ")";
            Log.e(TAG, error);

            unload();

            mCurrentState = MediaState.ERROR;

            return true;
        }

        return false;
    }

    public boolean isPlaying() {
        return mMediaPlayer.isPlaying();
    }
}
