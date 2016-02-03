package com.fortune.mobile.media.demuxer;

/**
 * Created by xjliu on 2016/1/14.
 *
 */
public class VideoFrame {
    public int header;
    public int start;
    public int length;
    public int type;

    public VideoFrame(int header,int length, int start,  int type) {
        this.header = header;
        this.start = start;
        this.length = length;
        this.type = type;
    }
}
