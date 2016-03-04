package com.fortune.mobile.media.decoder;

import com.fortune.mobile.media.demuxer.ByteArray;
import com.fortune.mobile.media.demuxer.PES;

import java.util.Vector;

/**
 * Created by xjliu on 2016/2/2.
 *
 */
public interface Decoder {
    long appendBuffer(byte[] buffer,int startPos,int length);
    void onVideoSizeChanged(int width,int height);
    void onAudioReady(int sampleRate,int channelCount,int audioType);
    void onFramesReady(PES pes);
    void finished();
    void onStreamStart();
    void onTsFinished();
    boolean seekTo(int position);
    int getDuration();
}
