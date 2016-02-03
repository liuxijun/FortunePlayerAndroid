package com.fortune.mobile.media.demuxer;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import com.fortune.mobile.utils.Logger;

/**
 * Created by xjliu on 2016/1/14.
 *
 */
public class AVCC {
    private static Logger logger = Logger.getLogger(AVCC.class);
    /** H264 profiles. **/
    private static final Map<String,String> PROFILES  = new HashMap<String,String>();
    static{
        PROFILES.put("66", "H264 Baseline");
        PROFILES.put("77","H264 Main");
        PROFILES.put("100","H264 High");
    }
    /** Get Avcc header from AVC stream
     See ISO 14496-15, 5.2.4.1 for the description of AVCDecoderConfigurationRecord
     **/
    public static ByteArray getAVCC(ByteArray sps ,Vector<ByteArray> ppsVect){
        // Write startbyte
        int length = sps.getAvailableBytes()+16;
        for(ByteArray pps:ppsVect){
            length+=pps.getAvailableBytes()+4;
        }
        ByteArray avcc  = new ByteArray(length);
        avcc.writeByte(0x01);
        // Write profile, compatibility and level.
        avcc.writeBytes(sps, 1, 3);
        // reserved (6 bits), NALU length size - 1 (2 bits)
        avcc.writeByte(0xFC | 3);
        // reserved (3 bits), num of SPS (5 bits)
        avcc.writeByte(0xE0 | 1);
        // 2 bytes for length of SPS
        avcc.writeShort(sps.getLength());
        // data of SPS
        avcc.writeBytes(sps, 0, sps.getLength());
        // Number of PPS
        avcc.writeByte(ppsVect.size());
        for(ByteArray pps: ppsVect) {
            // 2 bytes for length of PPS
            avcc.writeShort(pps.getLength());
            // data of PPS
            avcc.writeBytes(pps, 0, pps.getLength());
        }
        // Grab profile/level
        sps.position = 1;
        int prf  = sps.readByte();
        sps.position = 3;
        int lvl = sps.readByte();
        logger.debug("AVC: " + PROFILES.get(""+prf) + " level " + lvl);
        avcc.position = 0;
        return avcc;
    }

}
