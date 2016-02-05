package com.fortune.mobile.Testor;

import android.annotation.TargetApi;
import android.app.Activity;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.TimedText;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.fortune.mobile.media.decoder.HlsDecoder;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TestActivity extends Activity implements MediaPlayer.OnBufferingUpdateListener,
        MediaPlayer.OnCompletionListener,MediaPlayer.OnPreparedListener,MediaPlayer.OnErrorListener,
        MediaPlayer.OnVideoSizeChangedListener,MediaPlayer.OnInfoListener,SurfaceHolder.Callback,
        MediaPlayer.OnTimedTextListener,MediaPlayer.OnSeekCompleteListener {
    protected String TAG = getClass().getSimpleName();
    MediaPlayer mediaPlayer=null;
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    View playerContainer;
    RelativeLayout controller;
    SeekBar seekBar;
    MyUiHandler myUiHandler;
    HlsDecoder decoder=null;
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "启动中");
        setContentView(R.layout.main);
        surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceView.setOnClickListener(clickOnMovie);
        surfaceHolder.addCallback(this);
        surfaceHolder.setKeepScreenOn(true);
        surfaceHolder.setFormat(PixelFormat.RGBA_8888);
        playerContainer = findViewById(R.id.playerContainer);
        playerContainer.setOnClickListener(clickOnMovie);
        controller = (RelativeLayout) findViewById(R.id.controller);
        seekBar = (SeekBar) findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(onSeekbarChanged);

        myUiHandler = new MyUiHandler(this);
        Button buttonDoPlay = (Button) findViewById(R.id.buttonDoPlay);
        if (buttonDoPlay != null) {
            //暂时隐藏
            buttonDoPlay.setVisibility(View.GONE);
            buttonDoPlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    doPlay();
                }
            });
        }
        Button buttonDoTest = (Button) findViewById(R.id.buttonForTest);
        if (buttonDoTest != null) {
            buttonDoTest.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    doTestPlay();
                }
            });
        }
    }

    public void doTestPlay(){
        EditText editTextUrl = (EditText) findViewById(R.id.editTextUrl);
        if (editTextUrl != null) {
            String url = editTextUrl.getText().toString();
            if (url.isEmpty()) {
                Toast.makeText(this, "URL不能为空！", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    Log.d(TAG, "测试播放：" + url);
                    getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    decoder = new HlsDecoder(null,url,surfaceHolder.getSurface());
                    decoder.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }else{
            Log.e(TAG,"未找到url控件！");
        }
    }
    @Override
    public void onBufferingUpdate(MediaPlayer mediaPlayer, int i) {
        Log.d(TAG, "onBufferingUpdate：" + i);
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        Log.d(TAG, "播放完毕");
    }

    public void displayLength(int length) {
        TextView tvEnd = (TextView) findViewById(R.id.tvEnd);
        if (tvEnd != null) {
            tvEnd.setText(formatLength(length / 1000));
        }
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        Log.d(TAG, "已经准备完毕！");
        mediaPlayer.start();
        Message message = myUiHandler.obtainMessage(MyUiHandler.SHOW_PROGRESS);
        myUiHandler.sendMessage(message);
        displayLength(mediaPlayer.getDuration());
        fadeController();
    }


    public void initPlayer() {
        try {
            Log.d(TAG,"尝试初始化播放器！");
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setOnVideoSizeChangedListener(this);
            mediaPlayer.setOnBufferingUpdateListener(this);
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.setOnInfoListener(this);
            mediaPlayer.setDisplay(surfaceHolder);
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doPlay() {
        if (mediaPlayer == null) {
            initPlayer();
        }
        EditText editTextUrl = (EditText) findViewById(R.id.editTextUrl);
        if (editTextUrl != null) {
            String url = editTextUrl.getText().toString();
            if (url.isEmpty()) {
                Toast.makeText(this, "URL不能为空！", Toast.LENGTH_SHORT).show();
            } else {
                try {
                    Log.d(TAG, "尝试播放：" + url);
                    getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    mediaPlayer.setDataSource(url);
                    mediaPlayer.prepareAsync();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void fadeController() {
        Message message = myUiHandler.obtainMessage(MyUiHandler.MSG_FADE_OUT);
        myUiHandler.removeMessages(MyUiHandler.MSG_FADE_OUT);
        myUiHandler.sendMessageDelayed(message, MyUiHandler.MSG_FADE_OUT_DELAY_MILL_SECONDS);
    }

    public View.OnClickListener clickOnMovie = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mediaPlayer != null) {
                controller.setVisibility(isControllerShowing() ? View.GONE : View.VISIBLE);
                if (isControllerShowing()) {
                    fadeController();
                }
                int dur = mediaPlayer.getDuration();
                if (mediaPlayer.isPlaying()) {
                    Log.d(TAG, "正在播放，" + dur);
                } else {
                    Log.d(TAG, "没在播放状态," + dur);
                }
            }
        }
    };

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int j) {
        Log.d(TAG, "onError(" + i + "," + j + ")");
        return false;
    }

    @Override
    public boolean onInfo(MediaPlayer mediaPlayer, int i, int j) {
        Log.d(TAG, "onInfo(" + i + "," + j + ")");
        switch (i) {
            case MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING:
                break;
        }
        return false;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceCreated");
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        Log.d(TAG, "surfaceChanged" + i + "," + i1 + "," + i2);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "surfaceDestroyed");
    }

    public void onStop() {
        if (mediaPlayer != null) {
            Log.d(TAG,"尝试停止播放器");
            if(mediaPlayer.isPlaying()){
                mediaPlayer.stop();
            }
            mediaPlayer.release();
        }
        if(decoder!=null){
            Log.d(TAG,"尝试停止HlsDecoder");
            decoder.stopDecoder();
        }
        super.onStop();
    }

    boolean fullScrrening = false, strechToFullWindow = false;

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void setSurfaceSize(int w, int h, int playerWidth, int playerHeight) {
        if (surfaceView == null) {
            Log.e(TAG, "setSurfaceSize:SurfaceView为空！不能继续！");
            return;
        }
        if (playerWidth <= 0) {
            if (fullScrrening) {
                playerWidth = playerContainer.getWidth();
            } else {
                playerWidth = smallWidth;
            }
            if (playerWidth <= 0) {
                playerWidth = playerContainer.getWidth();
                smallWidth = playerWidth;
            }
        }
        if (playerHeight <= 0) {
            if (fullScrrening) {
                playerHeight = playerContainer.getHeight();
            } else {
                playerHeight = smallHeight;
            }
            if (playerHeight <= 0) {
                playerHeight = playerContainer.getHeight();
                smallHeight = playerHeight;
            }
        }

        if (w == 0 || h == 0 || playerWidth == 0 || playerHeight == 0) {
            return;
        }
        int surfaceWidth;
        int surfaceHeight;
        if (strechToFullWindow) {
            surfaceHeight = playerHeight;
            surfaceWidth = playerWidth;
        } else {
            //先以高度计算长度
            int surfaceWidthCal = playerHeight * w / h;
            int surfaceHeightCal = playerWidth * h / w;
            if (surfaceWidthCal > playerWidth) {
                surfaceWidth = playerWidth;
                surfaceHeight = surfaceHeightCal;
            } else {
                surfaceWidth = surfaceWidthCal;
                surfaceHeight = playerHeight;
            }
        }
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(surfaceWidth, surfaceHeight);
        //lp.setLayoutDirection(RelativeLayout.CENTER_IN_PARENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        surfaceView.setLayoutParams(lp);
        //surfaceView.setLayoutDirection(RelativeLayout.CENTER_IN_PARENT);
        surfaceView.getHolder().setFixedSize(surfaceWidth,
                surfaceHeight);
    }

    int videoHeight, videoWidth, smallWidth, smallHeight;

    @Override
    public void onVideoSizeChanged(MediaPlayer mediaPlayer, int w, int h) {
        Log.d(TAG, "onVideoSizeChanged to " + w + "x" + h);
        videoHeight = h;
        videoWidth = w;
        setSurfaceSize(w, h, 0, 0);
    }

    @Override
    public void onSeekComplete(MediaPlayer mediaPlayer) {

    }

    @Override
    public void onTimedText(MediaPlayer mediaPlayer, TimedText timedText) {

    }

    public String date2string(Date date, String format) {
        if (date == null) {
            date = new Date();
        }
        if (format == null) {
            format = "yyyy-MM-dd HH:mm:ss";
        }
        SimpleDateFormat sf = new SimpleDateFormat(format);
        return sf.format(date);
    }

    public String formatLength(int length) {
        String result = "";
        int i = 0;
        while (length > 0 && i <= 2) {
            int left = length % 60;
            String leftString = "0" + left;
            if (leftString.length() >= 3) {
                leftString = leftString.substring(leftString.length() - 2);
            }
            if (!result.equals("")) {
                result = ":" + result;
            }
            result = leftString + result;
            i++;
            length = length / 60;
        }
        return result;
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public void showController() {
        controller.setVisibility(View.VISIBLE);
        Message msg = myUiHandler.obtainMessage(myUiHandler.SHOW_PROGRESS);
        myUiHandler.removeMessages(myUiHandler.SHOW_PROGRESS);
        myUiHandler.sendMessage(msg);
    }

    public void hideController() {
        controller.setVisibility(View.GONE);
    }

    public void showPlayProcess() {
        if (mediaPlayer != null) {
            int duration = mediaPlayer.getDuration();
            displayLength(duration);
            if (mediaPlayer.isPlaying()) {
                if (duration > 0) {
                    seekBar.setMax(duration);
                    seekBar.setProgress(mediaPlayer.getCurrentPosition());
                    Log.d(TAG, "在播放状态！设置位置："+seekBar.getProgress());

               } else {
                    Log.d(TAG,"在播放状态！但时长为0！");
                    seekBar.setMax(100);
                    seekBar.setProgress(100);
                }
            } else {
                Log.d(TAG,"不在播放状态！");
                seekBar.setMax(100);
                seekBar.setProgress(0);
            }
        }
    }

    public boolean isControllerShowing() {
        return controller.getVisibility() == View.VISIBLE;
    }

    public class MyUiHandler extends Handler {
        public static final int MSG_FADE_OUT_DELAY_MILL_SECONDS = 3000;
        public static final int MSG_FADE_OUT = 2000;
        public static final int SHOW_PROGRESS = MSG_FADE_OUT + 1;
        public static final int SHOW_BUFFERING_PROGRESS = MSG_FADE_OUT + 2;
        public static final int MSG_PLAY_URL = MSG_FADE_OUT + 3;
        private String TAG = getClass().getSimpleName();
        private TestActivity caller;

        public MyUiHandler(TestActivity caller) {
            this.caller = caller;
        }

        public void handleMessage(Message msg) {
            //Log.d(TAG, "收到消息：" + msg.what);
            switch (msg.what) {
                case MSG_FADE_OUT:
                    Log.d(TAG, "准备隐藏控制条");
                    try {
                        if (caller.getMediaPlayer().isPlaying()) {
                            caller.hideController();
                        } else {
                            Log.d(TAG, "准备隐藏控制条");
                            msg = obtainMessage(MSG_FADE_OUT);
                            removeMessages(MSG_FADE_OUT);
                            sendMessageDelayed(msg, MSG_FADE_OUT_DELAY_MILL_SECONDS);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case SHOW_BUFFERING_PROGRESS:
                    //Log.d(TAG, "显示缓冲进度");
                    break;
                case SHOW_PROGRESS:
                    //Log.d(TAG, "显示进度");
                    caller.showPlayProcess();
                    if (caller.isControllerShowing()) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        removeMessages(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000);
                    }
                    break;
            }
        }
    }

    public SeekBar.OnSeekBarChangeListener onSeekbarChanged = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                                      boolean fromUser) {

        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            int position = seekBar.getProgress();
            if(mediaPlayer!=null){
                mediaPlayer.seekTo(position);
            }
        }
    };

}