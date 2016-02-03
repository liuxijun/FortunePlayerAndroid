package com.fortune.mobile.media.decoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.view.Surface;
import com.fortune.mobile.media.MediaPlayer;
import com.fortune.mobile.media.demuxer.ByteArray;
import com.fortune.mobile.media.demuxer.PES;
import com.fortune.mobile.media.demuxer.TSDemuxer;
import com.fortune.mobile.media.io.M3U8Reader;
import com.fortune.mobile.utils.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Vector;

/**
 * Created by xjliu on 2016/2/2.
 *
 */
public class HlsDecoder extends BaseDecoder {
    private MediaPlayer mediaPlayer;
    private String url;
    private boolean running =false;
    private Surface videoSurface;
    private M3U8Reader reader;
    private TSDemuxer demuxer;
    private Logger logger=Logger.getLogger(HlsDecoder.class);
    private MediaCodec mediaCodecVideo=null;
    Vector<PES> tags=new Vector<PES>();
    public HlsDecoder(MediaPlayer mediaPlayer,String url,Surface videoSurface){
        this.mediaPlayer = mediaPlayer;
        this.url = url;
        this.videoSurface = videoSurface;
        reader = new M3U8Reader(this,url);
        demuxer = new TSDemuxer(this);
    }
    public void init(){
        try {
            mediaCodecVideo =MediaCodec.createEncoderByType("Video/AVC");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void run(){
        running = true;
        logger.debug("准备播放连接："+url);
        reader.start();
        logger.debug("准备启动TS分离器...");
        demuxer.start();
        int i=0;
        long startTime = System.currentTimeMillis();
        while(mediaCodecVideo==null){
            try {
                Thread.sleep(100);
                i++;
                if(0==i%20){
                    logger.debug("正在等待初始化解码器》。。。。");
                }
                if(i>=100){
                    logger.error("超时了！等待了"+(System.currentTimeMillis()-startTime)+"毫秒，还没有初始化好！");
                    break;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long timeoutUs = 3000l;
        ByteBuffer[] inputBuffers=null;
        ByteBuffer[] outputBuffers=null;
        outputBuffers = mediaCodecVideo.getOutputBuffers();
        inputBuffers = mediaCodecVideo.getInputBuffers();
        if(mediaCodecVideo!=null){
            startTime = System.currentTimeMillis()-1000;
            long now;
            long startPts = -1;
            while(running){
                while((tags.size()<=0||mediaCodecVideo==null)&&running){
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if(!running){
                    break;
                }
                if(tags.size()>0){
                    if(mediaPlayer!=null&&!mediaPlayer.isPlaying()){
                        mediaPlayer.setIsPlaying(true);
                    }

                    PES pes = tags.remove(0);
                    //tag.getData()
                    if(startPts<0){
                        startPts = pes.pts;
                    }
                    now = System.currentTimeMillis();
                    while(now-startTime<pes.pts-startPts){
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    logger.debug("加载当前帧，准备解码：pes.pts="+pes.pts+",等待了同步时钟："+(System.currentTimeMillis()-now));
                    int inputBufferIndex = mediaCodecVideo.dequeueInputBuffer(timeoutUs);
                    int times = 0;
                    while(inputBufferIndex<0&&times<5){
                        inputBufferIndex = mediaCodecVideo.dequeueInputBuffer(timeoutUs);
                        times++;
                    }
                    ByteArray data = pes.data;
                    int length = (int)data.getBytesAvailable()-pes.payload;
                    inputBuffers[inputBufferIndex].put(data.getBuffers(), data.getBufferOffset() + pes.payload, length);
                    mediaCodecVideo.queueInputBuffer(inputBufferIndex,0,length,10000,0);
                    int outputBufferIndex = mediaCodecVideo.dequeueOutputBuffer(info,
                            10000);
                    if (outputBufferIndex >= 0) {
                        mediaCodecVideo.releaseOutputBuffer(outputBufferIndex, true);
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        mediaCodecVideo.getOutputBuffers();
                    } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // Subsequent data will conform to new format.
                        MediaFormat format = mediaCodecVideo.getOutputFormat();
                    }
                }

            }
        }
        demuxer.stopDemuxer();
        if(mediaPlayer!=null){
            mediaPlayer.setIsPlaying(false);
        }
    }

    public void onFramesReady(PES pes){
        tags.add(pes);
    }
    public void onVideoSizeChanged(int width,int height){
        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
        if(mediaCodecVideo!=null){
            mediaCodecVideo.release();
        }
        init();
        mediaCodecVideo.configure(mediaFormat,videoSurface,null,0);
        mediaCodecVideo.start();
        if(mediaPlayer!=null){
            mediaPlayer.notifyVideoSizeChanged(width,height);
        }
    }
    public void stopDecoder(){
        running = false;
    }
    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public long appendBuffer(byte[] buffer,int startPos,int length){
        return demuxer.append(buffer,startPos,length);
    }
}
