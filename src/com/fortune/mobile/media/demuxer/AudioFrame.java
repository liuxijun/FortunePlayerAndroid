package com.fortune.mobile.media.demuxer;

/**
 * Created by xjliu on 2016/1/14.
 * 
 */
public class AudioFrame {
    public int start ;
    public int length ;
    public int expected_length ;
    public int rate ;

    public AudioFrame(int start, int length, int expected_length, int rate) {
        this.start = start;
        this.length = length;
        this.expected_length = expected_length;
        this.rate = rate;
    }
}
