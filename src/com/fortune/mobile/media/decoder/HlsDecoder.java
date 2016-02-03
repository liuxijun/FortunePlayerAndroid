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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void run(){
        running = true;
        logger.debug("准备播放连接："+url);
        reader.start();
        while(mediaCodecVideo==null){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mediaCodecVideo.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long timeoutUs = 3000l;
        ByteBuffer[] inputBuffers=null;
        ByteBuffer[] outputBuffers=null;
        outputBuffers = mediaCodecVideo.getOutputBuffers();
        inputBuffers = mediaCodecVideo.getInputBuffers();
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
