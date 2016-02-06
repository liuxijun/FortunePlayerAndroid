package com.fortune.mobile.media.decoder;

import android.os.Message;
import com.fortune.mobile.media.demuxer.ByteArray;
import com.fortune.mobile.media.demuxer.PES;

import java.util.Vector;

/**
 * Created by xjliu on 2016/2/2.
 *
 */
public class BaseDecoder extends Thread implements Decoder{
    public Message initMessage(int what){
        return initMessage(what,0,0);
    }
    public Message initMessage(int what,int arg1,int arg2){
        Message msg = new Message();
        msg.arg1 = arg1;
        msg.what = what;
        msg.arg2 = arg2;
        return msg;
    }

    @Override
    public long appendBuffer(byte[] buffer, int startPos, int length) {
        return 0;
    }

    @Override
    public void onVideoSizeChanged(int width,int height){

    }

    @Override
    public void onFramesReady(PES pes){

    }

    public void finished(){

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
