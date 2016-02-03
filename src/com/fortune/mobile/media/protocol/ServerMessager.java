package com.fortune.mobile.media.protocol;

import com.fortune.mobile.utils.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: xjliu
 * Date: 2010-3-4
 * Time: 10:14:47
 * �����������ϢͨѶ
 */
public class ServerMessager {
    protected Map<String,String> parameters= new HashMap<String,String>();
    protected Logger logger = Logger.getLogger(getClass());
    protected String url = "interface/service.jsp";
    protected String extraParameter = "";
    public String getParameterXml(String type, String target, Map<String, String> parameters) {
        return getParameterXml("1.0",type,target,parameters);
    }

    public void setParameter(String name,String value){
        parameters.put(name, value);
    }

    public void removeParameter(String name){
        parameters.remove(name);
    }
    
    public String getParameterXml(String version,String type, String target, Map<String, String> parameters) {
        StringBuffer result = new StringBuffer();
        result.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n")
                .append("<req version=\"").append(version).append("\">\n")
                .append("  <task type=\"").append(type).append("\" target=\"")
                .append(target).append("\">\n");
        if (parameters != null) {
            for (String key : parameters.keySet()) {
                String value = parameters.get(key);
                if (value == null) value = "";
                result.append("    <param n=\"").append(key).append("\" v=\"")
                        .append(value).append("\"/>\n");
            }
        }
        if(extraParameter!=null){
            result.append(extraParameter);
        }
        result.append("  </task>\n");
        result.append("</req>");
        return result.toString();
    }

    public String getMessage(String ip, int port, String url, String parameter) {
        logger.debug("��������" + ip + ",�˿ڣ�" + port + ",url��" + url+"���ݣ�" + parameter);
        if(!url.startsWith("/")){
            url = "/"+url;
        }
        String hostUrl = "http://" + ip;
        if(port!=80){
            hostUrl+=":"+port;
        }
        if(!url.startsWith("/")){
            url = "/"+url;
        }
        hostUrl += url;
        String result = postToHost(hostUrl, parameter);
        logger.debug("����������:" + result);
        return result;
    }
    public List<StreamData> download(String url){
        List<StreamData> result = new ArrayList<StreamData>();
        try {
            URL dataUrl = new URL(url);
            HttpURLConnection con = (HttpURLConnection) dataUrl.openConnection();
            con.setRequestMethod("GET");
            con.setDoOutput(false);
            con.setDoInput(true);
            InputStream is = con.getInputStream();
            DataInputStream dis = new DataInputStream(is);
            int code = con.getResponseCode();
            byte d[] = new byte[1024*10];
            StreamData data = new StreamData();
            while(true){
                int i= dis.read(d);

                if(i<=0){
                    break;
                }
            }
            if(code==HttpURLConnection.HTTP_OK){
                logger.debug("HTTP������ɣ�"+url);
            }else{
                logger.error("HTTP����������" + code + "\n" +
                        data);
            }
            //data = new String(d);
            con.disconnect();
        } catch (Exception ex) {
            logger.error("�޷����ӣ�" + url);
            //ex.printStackTrace();
        }

        return result;
    }
    public String postToHost(String url, String postData,String strEncoding) {
        String data = null;
        try {
            if(url==null){
                return null;
            }
            url = url.replace(":80/","/");
            URL dataUrl = new URL(url);
            HttpURLConnection con = (HttpURLConnection) dataUrl.openConnection();
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
            //System.out.println("POST���������С��"+dis.available());
            int code = con.getResponseCode();
            byte d[] = new byte[1024*10];
            data = "";
            while(true){
                int i= dis.read(d);
                if(i<=0){
                    break;
                }
                if(strEncoding!=null){
                    data+=new String(d,0,i,strEncoding);
                }else{
                    data+=new String(d,0,i);
                }
            }
            if(code==HttpURLConnection.HTTP_OK){
                logger.debug("HTTP������ɣ�"+url);
            }else{
                logger.warn("HTTP����������"+code+"\n" +
                        data);
            }
            //data = new String(d);
            con.disconnect();
        } catch (Exception ex) {
            logger.warn("�޷����ӣ�" + url+" ,������Ϣ:"+ex.getMessage());
            //ex.printStackTrace();
        }
        return data;
    }
    public String postToHost(String url, String postData) {
        return postToHost(url,postData,null);
    }

    public String getExtraParameter() {
        return extraParameter;
    }

    public void setExtraParameter(String extraParameter) {
        this.extraParameter = extraParameter;
    }
}