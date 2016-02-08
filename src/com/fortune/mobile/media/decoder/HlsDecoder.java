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

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

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
    //Vector<PES> tags=new Vector<PES>();
    Queue<PES> frames = new LinkedList<PES>();
    public HlsDecoder(MediaPlayer mediaPlayer,String url,Surface videoSurface){
        this.mediaPlayer = mediaPlayer;
        this.url = url;
        this.videoSurface = videoSurface;
        reader = new M3U8Reader(this,url);
        demuxer = new TSDemuxer(this);
        //onVideoSizeChanged(1280,720);
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
        while(running&&!inited){
            try {
                Thread.sleep(100);
                i++;
                if(0==i%20){
                    logger.debug("正在等待初始化解码器》。。。。");
                }
                if(i>=300){
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
            logger.debug("等待了"+(System.currentTimeMillis()-startTime)+"毫秒，系统初始化完毕！");
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long timeoutUs = 1000*1000l;
        if(mediaCodecVideo!=null){
            ByteBuffer[] inputBuffers;
            ByteBuffer[] outputBuffers;
            inputBuffers = mediaCodecVideo.getInputBuffers();
            //outputBuffers = mediaCodecVideo.getOutputBuffers();
            startTime = System.currentTimeMillis()-1000;
            long now;
            long lastTime = 0;
            long lastPts = 0,lastDts=0;
            long startPts = -1;
            int frameCount = 0;
            while(running){
                while((frames.size()<=0||!inited)&&running){
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
                if(frames.size()>0){
                    if(mediaPlayer!=null&&!mediaPlayer.isPlaying()){
                        mediaPlayer.setIsPlaying(true);
                    }

                    PES pes = frames.poll();
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
                    long diff = pes.pts-lastPts;
                    try {
                        if(Math.abs(diff)>100){
                            logger.error("时间戳异常：pes.pts="+pes.pts+",pes.dts="+pes.dts+",lastPts="+lastPts+",lastDts=" +
                                    lastDts+",diff0="+diff+",diff1=" +(pes.dts-lastDts)+
                                    ",pesCount="+pesCount);
                            diff = 100;
                        }
                        while((now-lastTime<diff)&&running){
                            now = System.currentTimeMillis();
                            sleep(10);
                        }
                        lastTime = now;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    int inputBufferIndex =-1;
                    int times = 0;
                    while(inputBufferIndex<0&&times<5&&running){
                        if(mediaCodecVideo!=null){
                            inputBufferIndex = mediaCodecVideo.dequeueInputBuffer(timeoutUs);
                        }else{
                            break;
                        }
                        times++;
                    }
 /*
                    logger.debug("加载当前帧，准备解码：pes.pts="+pes.pts+",等待了同步时钟："+(now-lastTime)+
                            "ms,frameCount=" +frameCount+
                            ",pesCount="+pesCount+",inputBufferIndex="+inputBufferIndex);
                    lastTime = now;
// */
                    frameCount++;
                    if(inputBufferIndex>=0&&running&&inited){
                        ByteArray data = pes.data;
                        int length = (int)data.getBytesAvailable()-pes.payload;
                        ByteBuffer byteBuffer = inputBuffers[inputBufferIndex];
                        byteBuffer.clear();
                        byteBuffer.put(data.getBuffers(), data.getBufferOffset() + pes.payload, length);
                        //inputBuffers[inputBufferIndex].put(data.getBuffers(), data.getBufferOffset() + pes.payload, length);
                        mediaCodecVideo.queueInputBuffer(inputBufferIndex,0,length,pes.pts,0);
                        lastPts = pes.pts;
                        lastDts = pes.dts;
                        int outputBufferIndex = mediaCodecVideo.dequeueOutputBuffer(info,0);
                        if (outputBufferIndex >= 0&&running&&inited) {
                            while (outputBufferIndex >= 0 &&running&&inited) {
                                mediaCodecVideo.releaseOutputBuffer(outputBufferIndex, true);
                                outputBufferIndex = mediaCodecVideo.dequeueOutputBuffer(info, 0);
                            }
     /*
                            logger.debug("加载当前帧完成：pes.pts="+pes.pts+",frameCount=" +frameCount+
                                    ",pesCount="+pesCount+",inputBufferIndex="+inputBufferIndex);
                            //lastTime = now;
//                            */
                        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            outputBuffers = mediaCodecVideo.getOutputBuffers();
                            logger.debug("MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:outputBuffers.length="+outputBuffers.length);
                        } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // Subsequent data will conform to new format.
                            MediaFormat format = mediaCodecVideo.getOutputFormat();
                            logger.debug("MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:"+format.toString());
                        }
                    }else{
                        if(running){
                            logger.error("inputBufferIndex小于0，无法进行后续的操作！");
                        }else{
                            logger.error("要求退出！");
                        }
                    }
                }

            }
        }else{
            logger.debug("解码器初始化异常，无法继续任何操作！");
        }
        demuxer.stopDemuxer();
        if(mediaPlayer!=null){
            mediaPlayer.setIsPlaying(false);
        }
        inited = false;
        mediaCodecVideo.stop();
        mediaCodecVideo.release();
        mediaCodecVideo = null;
        frames.clear();
        frames = null;
        System.gc();
    }

    int pesCount=0;
    public void onFramesReady(PES pes) {
//        logger.debug("有PES数据来了，准备解码，pts="+pes.pts+",payload="+pes.payload+",len="+(pes.data.bytesAvailable-pes.payload));
/*
        if(pesCount%100==0){
            logger.debug("有PES数据来了，准备解码，pts="+pes.pts+",dts="+pes.dts+",payload="+pes.payload+",len="+(pes.data.getBytesAvailable()-pes.payload)+",pesCount="+pesCount);
        }
*/
        pesCount++;
        if(seeking){
            logger.warn("正在跳转，暂时不处理数据");
            return;
        }
        if(!inited){
            logger.warn("解码器还没初始化好，放弃当前帧："+pesCount+",pts="+pes.pts);
            return;
        }
        int i=0;
        while(running&&frames!=null&&frames.size()>125){
            try {
                sleep(100);
                i++;
                if(i%10==0){
                    logger.debug("有PES队列满了，等待1秒,队列长度："+frames.size());
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if(running&&frames!=null){
            frames.offer(pes);
        }
    }

    @Override
    public void onTsFinished() {

    }
    boolean seeking = false;
    @Override
    public boolean seekTo(int position) {
        if(reader.seekable()){
            logger.debug("准备跳转到"+position);
            seeking = true;
            frames.clear();
            reader.seekTo(position);
            demuxer.reset();
            return true;
        }else{
            logger.warn("可能是直播，不能进行跳转："+url);
        }
        return false;
    }

    @Override
    public int getDuration() {
        return (int) reader.getDuration();
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
        if(seeking){
            demuxer.reset();
            seeking = false;
        }
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

    @Override
    public void onStreamStart() {
        logger.debug("stream start！");
        demuxer.reset();
    }

}
