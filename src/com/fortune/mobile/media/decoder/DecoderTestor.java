package com.fortune.mobile.media.decoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.Calendar;

/**
 * Created by xjliu on 2016/2/3.
 *
 */
public class DecoderTestor {
    public void test() throws Exception{
        String TAG = "";
        File extStorage = Environment.getExternalStorageDirectory();
        File media = new File(extStorage,"video.h264");
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(media));
        Surface videoSurface=null;
        MediaCodec codec = MediaCodec.createDecoderByType("video/avc");
        MediaFormat format = MediaFormat.createVideoFormat("video/avc", 480, 384);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 480 * 384);
        format.setString(MediaFormat.KEY_MIME, "video/avc");
        codec.configure(format, videoSurface, null, 0);
        codec.start();
//Get arrays of our codec buffers
        ByteBuffer[] inputBuffers = codec.getInputBuffers();
        ByteBuffer[] outputBuffers = codec.getOutputBuffers();
        byte[] NAL_HEADER=new byte[4];
        long timeoutUs = 3000;
        int hdrIndex=0;
        int nalUnitIndxS=-1;
        int nalUnitIndxE=-1;
        boolean configPacket=false;
        byte[] BYTES_READ = new byte[1500];
        ByteArrayOutputStream nalUnit=new ByteArrayOutputStream(BYTES_READ.length);
        while (in.read(BYTES_READ) != -1) {
            for ( byte b : BYTES_READ) {
                nalUnit.write(b);
                if ( String.format("%02X", b).equals("00") && hdrIndex < 3 ) {
                    NAL_HEADER[hdrIndex++]=b;
                } else if ( hdrIndex == 3 && String.format("%02X", b).equals("01") ) {
                    NAL_HEADER[hdrIndex++]=b;
                } else if ( hdrIndex == 4 ) {
                    NAL_HEADER[hdrIndex++]=b;
                    if (nalUnitIndxS == -1) {
                        nalUnitIndxS=0;
                        nalUnitIndxE=nalUnit.size()-5;
                    } else if (nalUnitIndxS >= 0){
                        nalUnitIndxE=nalUnit.size()-5;
                    }
                    if (nalUnitIndxE > 0 ) {
                        Log.d(TAG,"Attempting to write NAL unit to codec buffer... SIZE:"+nalUnit.size()+" IndxStart: "+nalUnitIndxS+"  IndxEnd: "+nalUnitIndxE);
                        Log.d(TAG,"NAL Unit Type: "+String.format("%02X", nalUnit.toByteArray()[4]));
/*
* Get an input buffer
*/
                        int inputBufferIndex=-1;
                        for ( int x = 0; x < 4; x++ ) {
                            inputBufferIndex = codec.dequeueInputBuffer(timeoutUs);
                            if ( inputBufferIndex >= 0 ) {
                                break;
                            } else {
                                Thread.sleep(250);
                            }
                        }
                        if (inputBufferIndex >= 0) {
// fill inputBuffers[inputBufferIndex] with valid data
                            long presentationTimeUs = Calendar.getInstance().getTimeInMillis();
                            int nalUnitLen=nalUnitIndxE-nalUnitIndxS;
                            inputBuffers[inputBufferIndex].put(nalUnit.toByteArray(), nalUnitIndxS, nalUnitLen);
                            if ( configPacket ) {
                                Log.d(TAG,"Writing payload as configuration to codec...");
                                codec.queueInputBuffer(inputBufferIndex,0,nalUnitLen,presentationTimeUs,MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                            } else {
                                codec.queueInputBuffer(inputBufferIndex,0,nalUnitLen,presentationTimeUs,0);
//deQueue the Output Buffer
                                MediaCodec.BufferInfo bufInfo = new MediaCodec.BufferInfo();
                                int outputBufferIndex = codec.dequeueOutputBuffer(bufInfo, timeoutUs);
                                if (outputBufferIndex >= 0) {
                                    Log.d(TAG,"OutputBuffer is ready to be processed or rendered.");
                                    codec.releaseOutputBuffer(outputBufferIndex,true);
                                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                                    Log.d(TAG,"Output Buffers Changed!");
                                    outputBuffers = codec.getOutputBuffers();
                                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
// Subsequent data will conform to new format.
                                    Log.d(TAG,"Output Format Changed! Updating format...");
                                    format = codec.getOutputFormat();
                                } else {
                                    Log.w(TAG,"Did not understand OutputBuffer Index Response: "+outputBufferIndex);
                                }
                            }
                            nalUnit.reset();
                            nalUnit.write(NAL_HEADER,0,5);
                            nalUnitIndxS=0;
                            nalUnitIndxE=0;
                        } else {
                            Log.w(TAG, "We did not get a buffer!");
                        }
                    }
                } else {
                    hdrIndex=0;
                }
                if ( hdrIndex == 5 && ( String.format("%02X", NAL_HEADER[4]).equals("21") || String.format("%02X", NAL_HEADER[4]).equals("25") ) ) {
                    configPacket=false;
                    hdrIndex=0;
                } else if ( hdrIndex == 5 ){
                    configPacket=true;
                    hdrIndex=0;
                }
            }
        }
        Log.d(TAG, "Cleaning up Codec and Socket....");
        codec.stop();
        codec.release();
    }
}
