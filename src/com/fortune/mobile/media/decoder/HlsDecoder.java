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
    private boolean inited = false;
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
    }
    public void run(){
        running = true;
        logger.debug("准备播放连接："+url);
        reader.start();
        logger.debug("准备启动TS分离器...");
        demuxer.start();
        int i=0;
        long startTime = System.currentTimeMillis();
        while(!inited){
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
        if(mediaCodecVideo==null){
            logger.error("等待了"+(System.currentTimeMillis()-startTime)+"毫秒，还没有初始化好！");
        }else{
            logger.error("等待了"+(System.currentTimeMillis()-startTime)+"毫秒，系统初始化完毕！");
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer[] inputBuffers;
        ByteBuffer[] outputBuffers;
        inputBuffers = mediaCodecVideo.getInputBuffers();
        //outputBuffers = mediaCodecVideo.getOutputBuffers();
        long timeoutUs = 1000*1000l;
        if(mediaCodecVideo!=null){
            startTime = System.currentTimeMillis()-1000;
            long now=0;
            long lastTime = 0;
            long startPts = -1;
            int frameCount = 0;
            while(running){
                while((tags.size()<=0||!inited)&&running){
                    try {
                        Thread.sleep(100);
                        logger.debug("数据没有准备好，正在等待数据.....");
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
                    if(pes==null){
                        continue;
                    }
                    if(pes.audio){
                        //暂时不处理音频
                        continue;
                    }
                    //tag.getData()
                    if(startPts<0){
                        startPts = pes.pts;
                    }
                    now = System.currentTimeMillis();
                    while(now-startTime<pes.pts-startPts){
                        try {
                            now = System.currentTimeMillis();
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    int inputBufferIndex =-1;
                    int times = 0;
                    while(inputBufferIndex<0&&times<5){
                        inputBufferIndex = mediaCodecVideo.dequeueInputBuffer(timeoutUs);
                        times++;
                    }
                    logger.debug("加载当前帧，准备解码：pes.pts="+pes.pts+",等待了同步时钟："+(now-lastTime)+
                            "ms,frameCount=" +frameCount+
                            ",pesCount="+pesCount+",inputBufferIndex="+inputBufferIndex);
                    lastTime = now;
                    frameCount++;
                    if(inputBufferIndex>=0){
                        ByteArray data = pes.data;
                        int length = (int)data.getBytesAvailable()-pes.payload;
                        ByteBuffer byteBuffer = inputBuffers[inputBufferIndex];
                        byteBuffer.clear();
                        byteBuffer.put(data.getBuffers(), data.getBufferOffset() + pes.payload, length);
                        //inputBuffers[inputBufferIndex].put(data.getBuffers(), data.getBufferOffset() + pes.payload, length);
                        mediaCodecVideo.queueInputBuffer(inputBufferIndex,0,length,pes.pts,0);
                        int outputBufferIndex = mediaCodecVideo.dequeueOutputBuffer(info,0);
                        if (outputBufferIndex >= 0) {
                            while (outputBufferIndex >= 0) {
                                mediaCodecVideo.releaseOutputBuffer(outputBufferIndex, true);
                                outputBufferIndex = mediaCodecVideo.dequeueOutputBuffer(info, 0);
                            }
                        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            outputBuffers = mediaCodecVideo.getOutputBuffers();
                            logger.debug("MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:outputBuffers.length="+outputBuffers.length);
                        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // Subsequent data will conform to new format.
                            MediaFormat format = mediaCodecVideo.getOutputFormat();
                            logger.debug("MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:"+format.toString());
                        }
                    }else{
                        logger.error("inputBufferIndex小于0，无法进行后续的操作！");
                    }
                }

            }
        }
        demuxer.stopDemuxer();
        if(mediaPlayer!=null){
            mediaPlayer.setIsPlaying(false);
        }
        mediaCodecVideo.stop();
        mediaCodecVideo.release();
        mediaCodecVideo = null;
        inited = false;
    }

    int pesCount=0;
    public void onFramesReady(PES pes) {
//        logger.debug("有PES数据来了，准备解码，pts="+pes.pts+",payload="+pes.payload+",len="+(pes.data.bytesAvailable-pes.payload));
        if(pesCount%100==0){
            logger.debug("有PES数据来了，准备解码，pts="+pes.pts+",dts="+pes.dts+",payload="+pes.payload+",len="+(pes.data.getBytesAvailable()-pes.payload)+",pesCount="+pesCount);
        }
        pesCount++;
        tags.add(pes);
    }

    @Override
    public void onTsFinished() {

    }

    int width=-1,height=-1;
    public void onVideoSizeChanged(int width,int height){
        try {
            if(this.width!=width||this.height!=height){
                inited=false;
                logger.debug("已经获取了视频尺寸，准备初始化解码器:"+width+"x"+height);
                MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
                if(mediaCodecVideo!=null){
                    mediaCodecVideo.release();
                }
                init();
                mediaCodecVideo = MediaCodec.createDecoderByType("video/avc");
                //mediaCodecVideo =MediaCodec.createEncoderByType("Video/AVC");
                mediaCodecVideo.configure(mediaFormat,videoSurface,null,0);
                mediaCodecVideo.start();
                if(mediaPlayer!=null){
                    mediaPlayer.notifyVideoSizeChanged(width,height);
                }
                inited = true;
            }else{
                logger.debug("视频尺寸未发生变化:"+width+"x"+height);
            }
        } catch (Exception e) {
            logger.error("初始化解码器发生异常："+e.getMessage());
            e.printStackTrace();
        }
    }
    public void stopDecoder(){
        reader.shutdownNow();
        demuxer.stopDemuxer();
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

    public void finished(){
        if(demuxer.isRunning()){
            demuxer.setStreamFinished(!reader.isLoading());
            logger.debug("分离器还在运行");
        }else if(reader.isLoading()){
            logger.debug("M3U8加载器还在运行");
        }else{
            stopDecoder();
            logger.debug("结束啦");
            running =false;
        }
    }

}
