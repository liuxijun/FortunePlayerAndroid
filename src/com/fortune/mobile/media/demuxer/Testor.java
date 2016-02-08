package com.fortune.mobile.media.demuxer;

import com.fortune.mobile.media.decoder.Decoder;
import com.fortune.mobile.media.io.M3U8Reader;
import com.fortune.mobile.utils.Logger;

import java.io.*;
import java.util.Vector;

/**
 * Created by xjliu on 2016/1/15.
 * 测试分离器
 */
public class Testor extends Thread implements Decoder {
    Logger logger = Logger.getLogger(Testor.class);
    TSDemuxer demuxer;
    M3U8Reader reader;
    private boolean running=true;
    public Testor(){
        demuxer = new TSDemuxer(this);
        reader = new M3U8Reader(this,"http://192.168.1.13:28080/vod/01.mp4.m3u8");
    }

    public boolean isRunning(){
        return running;
    }
    public void run(){
        demuxer.start();
        reader.start();
        while(running){
            try {
                sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        running = false;
    }
    public static void main(String[] args){
        Logger logger = Logger.getLogger(Testor.class.getName());
        logger.setAndroid(false);
        Testor testor = new Testor();

        testor.start();
        while(testor.running){
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
/*
        String path = "E:\\UserData\\lxj\\RedexPro\\web\\yanzhao\\unitv3_2100k_m3u8\\";
        String[] fileNames = new String[]{
                "0000.ts",
                "0001.ts",
                "0002.ts",
                "0003.ts",
                "0004.ts"};
        byte[] buffer = new byte[1024*128];
        for(String fileName:fileNames){
            File file = new File(path+fileName);
            if(file.exists()){
                try {
                    logger.debug("Try to read TS file:"+file.getAbsolutePath()+","+ (file.length())+","+file.length()/188+"blocks");
                    InputStream is = new FileInputStream(file);
                    DataInputStream dataReader = new DataInputStream(is);
                    int len = dataReader.read(buffer);
                    while(len>0){
                        //logger.debug("追加数据：" + len + "字节");
                        demuxer.append(buffer,0,len);
                        len = dataReader.read(buffer);
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }else{
                logger.error("文件不存在："+file.getAbsolutePath());
            }
        }
*/

    }

    @Override
    public long appendBuffer(byte[] buffer, int startPos, int length) {
        return demuxer.append(buffer,startPos,length);
    }


    @Override
    public void onVideoSizeChanged(int width, int height) {
        logger.debug("获取到了视频大小："+width+"x"+height+",pesCount="+pesCount);
    }
    int pesCount=0;
    public void onFramesReady(PES pes) {
//        logger.debug("有PES数据来了，准备解码，pts="+pes.pts+",payload="+pes.payload+",len="+(pes.data.bytesAvailable-pes.payload));
        if(pesCount%10==0){
            logger.debug("有PES数据来了，准备解码，pts="+pes.pts+",dts="+pes.dts+",payload="+pes.payload+",len="+(pes.data.bytesAvailable-pes.payload)+",pesCount="+pesCount);
        }
        pesCount++;
    }

    @Override
    public void finished() {
        if(demuxer.isRunning()){
            demuxer.setStreamFinished(!reader.isLoading());
            logger.debug("分离器还在运行");
        }else if(reader.isLoading()){
            logger.debug("M3U8加载器还在运行");
        }else{
            logger.debug("结束啦");
            running =false;
        }
    }

    @Override
    public void onStreamStart() {

    }

    @Override
    public void onTsFinished() {
    }

    @Override
    public boolean seekTo(int position) {
        return false;
    }

    @Override
    public int getDuration() {
        return 0;
    }
}
