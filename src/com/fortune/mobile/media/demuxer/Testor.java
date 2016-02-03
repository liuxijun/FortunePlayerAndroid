package com.fortune.mobile.media.demuxer;

import com.fortune.mobile.media.decoder.Decoder;
import com.fortune.mobile.utils.Logger;

import java.io.*;
import java.util.Vector;

/**
 * Created by xjliu on 2016/1/15.
 * 测试分离器
 */
public class Testor extends Thread implements Decoder {
    Logger logger = Logger.getLogger(Testor.class);
    public static void main(String[] args){
        Logger logger = Logger.getLogger(Testor.class.getName());
        String path = "E:\\UserData\\lxj\\RedexPro\\web\\yanzhao\\unitv3_2100k_m3u8\\";
        String[] fileNames = new String[]{
                "0000.ts",
                "0001.ts",
                "0002.ts",
                "0003.ts",
                "0004.ts"};
        Testor testor = new Testor();
        TSDemuxer demuxer = new TSDemuxer(testor);
        demuxer.start();
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

    }

    @Override
    public long appendBuffer(byte[] buffer, int startPos, int length) {
        return 0;
    }

    @Override
    public void onVideoSizeChanged(int width, int height) {
        logger.debug("获取到了视频大小："+width+"x"+height);
    }

    public void onFramesReady(PES pes) {
        logger.debug("有PES数据来了，准备解码，pts="+pes.pts+",payload="+pes.payload+",len="+(pes.data.bytesAvailable-pes.payload));
    }

    @Override
    public void finished() {
        logger.debug("结束了");
    }
}
