package com.fortune.mobile.media.demuxer.model;

/**
 * Created by xjliu on 2016/1/14.
 *
 */
public class AudioTrack {
    public static final int FROM_DEMUX = 0;
    public static final int FROM_PLAYLIST = 1;
    public String title;
    public int id ;
    public int source ;
    public boolean isDefault;
    public boolean isAAC;

    public AudioTrack(String title, int source, int id, boolean isDefault, boolean isAAC) {
        this.title = title;
        this.id = id;
        this.source = source;
        this.isDefault = isDefault;
        this.isAAC = isAAC;
    }

    public String toString()  {
        return "AudioTrack ID: " + id + " Title: " + title + " Source: " + source + " Default: " + isDefault + " AAC: " + isAAC;
    }
}
