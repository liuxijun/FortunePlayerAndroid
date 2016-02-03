package com.fortune.mobile.media.protocol;

import com.fortune.mobile.utils.Logger;
import com.fortune.mobile.utils.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xjliu on 2014/7/13.
 * M3U8格式
 */
public class M3U8 {
    private Logger logger = Logger.getLogger(getClass());
    private String version="1.0";
    private List<M3U8Stream> streams=new ArrayList<M3U8Stream>();
    private boolean isLive=false;
    private boolean multiStream = false;
    private boolean relateUrlType = false;
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isLive() {
        return isLive;
    }

    public void setLive(boolean isLive) {
        this.isLive = isLive;
    }

    public void addStream(M3U8Stream stream){
        streams.add(stream);
    }
    public static String getValueBefore(String str,int p,String ends,String defaultVal){
        String result = "";
        while(p<str.length()){
            char ch = str.charAt(p);
            if(ends.contains(ch+"")){
                break;
            }
            result += ch;
            p++;
        }
        if("".equals(result)){
            return defaultVal;
        }
        return result;
    }
    public static int getIntValueBefore(String str,int p,String ends,int defaultVal){
        return StringUtils.string2int(getValueBefore(str, p, ends, "" + defaultVal), defaultVal);
    }
    public static String repairUrl(String sourceUrl,String newM3u8Url){
        if(newM3u8Url==null){
            return sourceUrl;
        }
        if(newM3u8Url.startsWith("http://")){
            return newM3u8Url;
        }
        if(sourceUrl==null||"".equals(sourceUrl)){
            return newM3u8Url;
        }
        String protocol = "http://";
        String result = sourceUrl.substring(7);//跳过http://这7个字母
        if(sourceUrl.startsWith("https://")){
            protocol = "https://";
            result = sourceUrl.substring(8);//跳过https://这8个字母
        }
        if(newM3u8Url.startsWith("/")){
            int p = result.indexOf("/");
            result = result.substring(0,p)+newM3u8Url;
        }else{
            if(result.endsWith("/")){
                result += newM3u8Url;
            }else{
                int p=result.lastIndexOf("/");
                if(p>0){
                    result = result.substring(0,p)+"/"+newM3u8Url;
                }
            }
        }
        return protocol+result;
    }
    public void addStream(int bandwidth,int programId,String url,String m3u8Content){
        if(m3u8Content!=null&&m3u8Content.trim().startsWith("#EXTM3U")){
            String extTag = "#EXT-X-STREAM-INF:";
            int p=m3u8Content.indexOf(extTag);
            if(p>0){
                do{
                    p+=extTag.length();
                    String nextVal = m3u8Content.substring(p);
                    extTag = "PROGRAM-ID=";
                    p=nextVal.indexOf(extTag);
                    int progId = 0;
                    if(p>=0){
                        progId = getIntValueBefore(nextVal,p+extTag.length(),", \n",0);
                    }
                    extTag = "BANDWIDTH=";
                    p = nextVal.indexOf(extTag);
                    int bw= 0;
                    if(p>=0){
                        bw = getIntValueBefore(nextVal,p+extTag.length(),", \n",bandwidth);
                    }
                    p = nextVal.indexOf("\n");
                    if(p>=0){
                        nextVal = nextVal.substring(p).trim();
                        String newM3u8Url = getValueBefore(nextVal,0,"\n",null);
                        if(newM3u8Url!=null&&!newM3u8Url.startsWith("#")){
                            ServerMessager messager = new ServerMessager();
                            String newUrl = repairUrl(url,newM3u8Url);
                            logger.debug("尝试下载M3U8："+newUrl);
                            String newM3u8Content = messager.postToHost(newUrl,null);
                            if(newM3u8Content!=null&&!"".equals(newM3u8Content)){
                                addStream(new M3U8Stream(bw,progId,newUrl,newM3u8Content));
                            }
                        }
                    }
                    extTag = "#EXT-X-STREAM-INF:";
                    p=nextVal.indexOf(extTag);
                }while(p>0);
            }else{
                addStream(new M3U8Stream(bandwidth, programId, url,m3u8Content));
            }
        }
    }

    public List<M3U8Stream> getStreams() {
        return streams;
    }

    public void setStreams(List<M3U8Stream> streams) {
        this.streams = streams;
    }

    public boolean isMultiStream() {
        return multiStream;
    }

    public void setMultiStream(boolean multiStream) {
        this.multiStream = multiStream;
    }

    private String getNumberByVersion(float duration){
        if(version.startsWith("1")||version.startsWith("1.")){
            return ""+ Math.round(duration);
        }else{
            return ""+duration;
        }
    }
    public String toString(){
        StringBuilder sb = new StringBuilder("#EXTM3U\n");
        //int discontinuitySeq=1;
        if(multiStream){
            for(M3U8Stream stream:streams){
                sb.append("#EXT-X-STREAM-INF:PROGRAM-ID=")
                        .append(stream.getProgramId())
                        .append(",BANDWIDTH=").append(stream.getBandwidth()).append("\n");
                sb.append(stream.getUrl()).append("\n");
            }
        }else{
            //先找到几道流中最大的targetDuration
            int maxTargetDuration = -1;
            int maxMediaSequence=0;
            for(M3U8Stream stream:streams){
                float targetDuration = stream.getTargetDuration();
                if(maxTargetDuration<targetDuration){
                    maxTargetDuration = Math.round(targetDuration + 0.5f);
                }
                int mediaSequence = stream.getMediaSequence();
                if(maxMediaSequence<mediaSequence){
                    maxMediaSequence = mediaSequence;
                }
            }
            boolean exportMetaEveryStream = false;
            sb.append("#EXT-X-VERSION:").append(version).append("\n");
            int i=0,l=streams.size();
            for(;i<l;i++){
                M3U8Stream stream = streams.get(i);
                boolean exportMeta = i==0 || exportMetaEveryStream;
                if(exportMeta){
                    sb.append("#").append(M3U8Stream.MEDIA_SEQUENCE).append(":").append(maxMediaSequence++).append("\n");
                    sb.append("#EXT-X-ALLOW-CACHE:YES\n");
                    sb.append("#").append(M3U8Stream.TARGET_DURATION).append(":").append(Math.round(stream.getTargetDuration())).append("\n");
                }
                if(stream.isLive()){//只要有一个直播流混在里面，就设置为直播。目前的情况下，我们对直播的处理还不够圆润，所以暂时
                    //暂时无法对直播提供较好的广告插播。因为在播放时会有问题。客户端会反复请求m3u8列表，这个列表也会变化。我们处理
                    //后会导致某些播放的块不正常。
                    isLive = true;
                }
                //String streamUrl = stream.getUrl();
                String streamUrlPath = StringUtils.getClearURL(stream.getUrl());
                int p = streamUrlPath.indexOf("?");
                String queryString = "";
                if(p>0){
                    queryString = streamUrlPath.substring(p+1);
                    streamUrlPath = streamUrlPath.substring(0,p+1);
                }
                if(!"".equals(queryString)){
                    queryString = "?"+queryString;
                }
                p = streamUrlPath.lastIndexOf("/");
                if(p>0){
                    streamUrlPath = streamUrlPath.substring(0,p)+"/";
                }else{
                    streamUrlPath = "/";
                }
                int streamUrlPathLength = streamUrlPath.length();
                List<M3U8Segment> segments = stream.getSegments();
                int segIdx = 0,segLength=segments.size();
                if(isLive){
                    segIdx = segLength-5;
                }
                for(;segIdx<segLength;segIdx++){
                    M3U8Segment segment = segments.get(segIdx);
                    String segmentUrl =segment.getUrl();
                    if(relateUrlType){//如果是相对路径模式，就截取url，保留当前url目录以下的
                        p = segmentUrl.indexOf(streamUrlPath);
                        if(p>=0){
                            segmentUrl = segmentUrl.substring(p+streamUrlPathLength);
                        }
                    }
                    p=segmentUrl.indexOf("?");
                    if(p<0){
                        //如果segment的url没有参数，就把stream的参数作为参数。
                        segmentUrl+=queryString;
                    }else{
                        //如果segment的url有参数，就看看queryString和现有的参数，有重复的，取queryString里的，没有就拼在一起。
                        if(!"".equals(queryString)){
                            String segmentQueryString = segmentUrl.substring(p+1);
                            Map<String,List<String>> parameters = new HashMap<String, List<String>>();
                            parameters=getParameters(parameters,segmentQueryString);
                            parameters=getParameters(parameters,queryString);
                            segmentUrl=segmentUrl.substring(0,p)+"?"+parametersToString(parameters);
                        }
                    }
                    sb.append("#").append(M3U8Stream.EXT_INF).append(":").append(getNumberByVersion(segment.getDuration())).append(",\n")
                            .append(segmentUrl).append("\n");
                }
                if(i<l-1){
                    sb.append("#EXT-X-DISCONTINUITY\n");
                    //sb.append("EXT-X-DISCONTINUITY-SEQUENCE:").append(discontinuitySeq).append("\n");
                    //discontinuitySeq++;
                }
            }
            if(!isLive){
                sb.append("#EXT-X-ENDLIST\n");
            }
        }

        return sb.toString();
    }

    private Map<String, List<String>> getParameters(Map<String,List<String>> parameters, String queryString) {
        if(parameters==null){
            return null;
        }
        if(queryString==null||queryString.equals("")){
            return parameters;
        }
        if(queryString.startsWith("?")){
            queryString = queryString.substring(1);
        }
        logger.debug("准备分析参数："+queryString);
        String[] data = queryString.split("&");
        for(String nameAndValue:data){
            if(nameAndValue.startsWith("amp;")){
                nameAndValue = nameAndValue.substring(4);
            }
            String[] nv = nameAndValue.split("=");
            String name = nv[0];
            String value = null;
            if(nv.length>1){
                value = nv[1];
            }
            List<String> values = parameters.get(name);
            if(values==null){
                values = new ArrayList<String>();
                parameters.put(name,values);
            }
            values.add(value);
        }
        return parameters;
    }
    public String parametersToString(Map<String,List<String>> parameters){
        String result = "";
        for(String name:parameters.keySet()){
            List<String> values = parameters.get(name);
            for(String value:values){
                if(!"".equals(result)){
                    result +="&amp;";
                }
                result+=name;
                if(value!=null){
                    result+="="+value;
                }
            }
        }
        logger.debug("分析参数复合结果："+result);
        return result;
    }
    public boolean isRelateUrlType() {
        return relateUrlType;
    }

    public void setRelateUrlType(boolean relateUrlType) {
        this.relateUrlType = relateUrlType;
    }
}
