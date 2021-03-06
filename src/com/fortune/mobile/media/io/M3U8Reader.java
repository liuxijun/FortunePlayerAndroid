package com.fortune.mobile.media.io;

import com.fortune.mobile.media.decoder.Decoder;
import com.fortune.mobile.media.protocol.*;
import com.fortune.mobile.utils.Logger;
import com.fortune.mobile.utils.StringUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;

/**
 * Created by xjliu on 2014/10/12.
 * 保存M3U8，包括媒体文件
 */
public class M3U8Reader extends Thread {
    private String url;
    private Logger logger = Logger.getLogger(this.getClass());
    private Decoder decoder;
    ServerMessager messager = new ServerMessager();
    public static final int STATUS_FINISHED=1;
    public static final int STATUS_RUNNING=2;
    public static final int STATUS_IO_ERROR=-1;
    private boolean loading=true;
    private M3U8Stream currentStream=null;

    public M3U8Reader(Decoder decoder,String url){
        this.url = url;
        this.decoder = decoder;
    }

    private Date startTime;
    private Date stopTime;
    private long allBandwidth = 0;
    private float allM3U8Duration = 0.0f;
    private String logs = "";
    public String getLogs(){
        return logs;
    }
    public long getDuration(){
        if(currentStream!=null&&!currentStream.isLive()){
            return Math.round(currentStream.getAllDuration());
        }
        return 0;
    }

    public void afterFinished() {
    }

    public void beforeStart() {
        startTime = new Date();
        logs ="";//"\r\n"+ StringUtils.date2string(new Date())+" - " +msgHeader+"已经启动";
    }

    boolean willStop = false;
    public void shutdownNow(){
        willStop=true;
    }

    public void run(){
        startTime = new Date();
        ServerMessager messager = new ServerMessager();
        M3U8 m3u8 = new M3U8();
        String m3u8Content = messager.postToHost(url,null);
        if(m3u8Content!=null&&!"".equals(m3u8Content.trim())){
            m3u8.addStream(0,1,url,m3u8Content);
            byte[] buffer = new byte[188*1000];

            for(M3U8Stream stream:m3u8.getStreams()){
                if(currentStream==null){
                    currentStream = stream;
                }else{
                    logger.error("现在只播放第一道流");
                    break;
                }
                if(willStop){
                    break;
                }
                allM3U8Duration = stream.getAllDuration();
                List<M3U8Segment> segments = stream.getSegments();
                int startIdx = 0;
                if(stream.isLive()){
                    startIdx = segments.size()-3;
                    if(startIdx<0){
                        startIdx = 0;
                    }
                }
                int lastMediaSequence = stream.getMediaSequence();
                int target =Math.round(stream.getTargetDuration());
                long lastDownloadTime = System.currentTimeMillis();
                for(int i=startIdx,l=segments.size();i<l;i++){
                    if(willSeekTo>=0){
                        i = willSeekTo;
                        seeking = false;
                    }
                    M3U8Segment segment = segments.get(i);
                    if(willStop){
                        break;
                    }
                    String segmentUrl = segment.getUrl();
                    getSegementFromUrl(segmentUrl, null, null,buffer);
                    int mediaSequence = stream.getMediaSequence();
                    if(stream.isLive()&&i==l-1){
                        logger.debug("直播最后一个ts请求，所以要刷新stream！");
                        String streamUrl = stream.getUrl();
                        while(mediaSequence==lastMediaSequence){
                            if(willStop){
                                break;
                            }
                            m3u8Content = messager.postToHost(streamUrl,null);
                            stream = new M3U8Stream(stream.getBandwidth(),stream.getProgramId(),streamUrl,m3u8Content);
                            mediaSequence = stream.getMediaSequence();
                            if(mediaSequence == lastMediaSequence){
                                logger.debug("还没有更新，继续等待"+(target/2)+"秒");
                                try {
                                    sleep(target/2*1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        segments = stream.getSegments();
                        l = segments.size();
                        i=l-2;
                    }
/*
                    dataLength+=data.getContentLength();
                    if(rootPath!=null){
                        if(saveSegment(rootPath,segment,data)){
                            currentPos += segment.getDuration();
                            reportProcess(allM3U8Duration,currentPos);
                        }else{
                            logger.error("无法保存："+(segment.getUrl()));
                        }
                    }
                    long allDuration = System.currentTimeMillis()-startTime.getTime();
                    if(allDuration>0){
                        allBandwidth = dataLength*8*1000 / allDuration;
                        logs = "Total Bandwidth="+ StringUtils.formatBPS(allBandwidth);
                        logger.debug(logs);
                    }
*/
                }
            }
            m3u8.setRelateUrlType(true);
        }

        stopTime = new Date();
        long duration = (stopTime.getTime()-startTime.getTime())/1000;
        String secondStr = ""+duration%60;
        while(secondStr.length()<2){
            secondStr = "0"+secondStr;
        }
        loading =false;
        logs ="\n"+StringUtils.date2string(startTime)+"->"+StringUtils.date2string(stopTime)+
                ",bandwidth="+allBandwidth+",duration="+(duration/60)+":"+secondStr;
        reportStatus(allM3U8Duration,-1,STATUS_FINISHED,"下载完成");
        decoder.finished();
    }

    public void getSegementFromUrl(String url, String postData,String strEncoding,byte[] d) {
        HttpURLConnection con;
        int tryTimes = 0;
        int bufferLength = 1024*188;
        long size=0;
        long startTime = System.currentTimeMillis();
        while(tryTimes<5&&!willStop){
            tryTimes++;
            try {
                URL dataUrl = new URL(url);
                logger.debug("尝试访问："+url);
                con = (HttpURLConnection) dataUrl.openConnection();
                con.setConnectTimeout(2000);
                con.setReadTimeout(20000);
                if(postData==null || postData.trim().equals("")){
                    con.setRequestMethod("GET");
                    con.setDoOutput(false);
                    con.setDoInput(true);
                }else{
                    con.setRequestMethod("POST");
                    con.setDoOutput(true);
                    con.setDoInput(true);
                    con.setRequestProperty("Proxy-Connection", "Keep-Alive");
                    OutputStream os = con.getOutputStream();
                    DataOutputStream dos = new DataOutputStream(os);
                    byte[] dataBuffer;
                    if(strEncoding!=null){
                        dataBuffer = postData.getBytes(strEncoding);
                    }else{
                        dataBuffer = postData.getBytes();
                    }
                    dos.write(dataBuffer);
                    dos.flush();
                    dos.close();
                }

                InputStream is = con.getInputStream();
                DataInputStream dis = new DataInputStream(is);
                int code = con.getResponseCode();
                while(!willStop){
                    if(seeking){
                        break;
                    }
                    int i= dis.read(d);
                    if(i<=0){
                        break;
                    }
                    size+=i;
                    decoder.appendBuffer(d,0,i);
                }
                String errorMsg;
                if(code==HttpURLConnection.HTTP_OK){
                    errorMsg = "HTTP_OK";
                }else if(code == HttpURLConnection.HTTP_BAD_REQUEST){
                    errorMsg = "HTTP_BAD_REQUEST";
                }else if(code == HttpURLConnection.HTTP_CLIENT_TIMEOUT){
                    errorMsg = "HTTP_CLIENT_TIMEOUT";
                }else if(code == HttpURLConnection.HTTP_NOT_FOUND){
                    errorMsg = "HTTP_NOT_FOUND";
                }else{
                    errorMsg = "HTTP_UNKNOWN:"+code;
                }
                if(code!=HttpURLConnection.HTTP_OK){
                    logger.error("HTTP请求发生错误："+code+"," +
                            errorMsg);
                }
                //data = new String(d);
                con.disconnect();
                break;
            } catch (Exception ex) {
                logger.error("无法连接：" + url+","+ex.getMessage());
                //ex.printStackTrace();
            }
        }
        long duration = System.currentTimeMillis()-startTime;
        if(duration>30000){
            logger.warn("此次访问时间过长："+duration+"ms");
        }
        if(duration>0&&size%188!=0){
            logger.debug("Current Download Bandwidth="
                    + StringUtils.formatBPS(size * 8 * 1000 / duration)+"," +
                    duration+"ms,"+(size)+"Bytes,"+((size%188==0)?"可以对齐！":"无法对齐！"));
        }
    }

    private String repairUrl(String url){
        String pos = StringUtils.getParameter(url,"pos");
        String dur = StringUtils.getParameter(url,"dur");
        String result = StringUtils.getClearURL(url);
        int p=result.indexOf("?");
        if(p>0){
            result = result.substring(0,p);
        }
        boolean willAppendTs = false;
        if(pos!=null&&!"".equals(pos)){
            result = result+"_pos_"+pos;
            willAppendTs = true;
        }
        if(dur!=null&&!"".equals(dur)){
            result = result+"_dur_"+dur;
            willAppendTs = true;
        }
        if(willAppendTs){
            result+=".ts";
        }
        return result;
    }
    private String transUrlToFileName(String rootPath,String url){
        return rootPath+"/"+repairUrl(url);
    }
    public void reportStatus(float allDuration,float currentPos,long status,String message){
    }
    public void reportProcess(float allDuration,float currentPos){
        long status=-1L;
        if(currentPos>=0){
            status = STATUS_RUNNING;
        }
        reportStatus(allDuration,currentPos,status,"设置当前进行进度");
    }

    public boolean isLoading() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public boolean seekable() {
        return currentStream!=null&&!currentStream.isLive();
    }
    public int seekTo(int pos){
        if(!currentStream.isLive()){
            List<M3U8Segment> segments = currentStream.getSegments();
            float currentPos = 0.0f;
            for(int i=0,l=segments.size();i<l;i++){
                M3U8Segment segment = segments.get(i);
                currentPos += segment.getDuration();
                if(currentPos>pos/1000){
                    logger.debug("准备跳转到"+i+"个segment,起始时间是："+(currentPos-segment.getDuration())+",时长："+segment.getDuration());
                    setSeekTo(i);
                    return i;
                }
            }
            logger.warn("未发现可以调转的时间段，最大时间："+currentPos+",要跳转的时间："+pos/1000);
        }
        return -1;
    }
    boolean seeking = false;
    int willSeekTo= -1;
    public void setSeekTo(int idx){
        seeking = true;
        willSeekTo = idx;
    }
}
