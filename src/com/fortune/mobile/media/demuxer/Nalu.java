package com.fortune.mobile.media.demuxer;

import com.fortune.mobile.utils.Logger;

import java.util.Vector;

/**
 * Created by xjliu on 2016/1/14.
 * 
 */
public class Nalu {
    private static Logger logger = Logger.getLogger(Nalu.class);
    static boolean writeDebug=false;
    /** H264 NAL unit names. **/
    private static final String[] NAMES  = new String[]{
            "Unspecified",                  // 0
            "NDR",                          // 1 
            "Partition A",                  // 2 
            "Partition B",                  // 3 
            "Partition C",                  // 4 
            "IDR",                          // 5 
            "SEI",                          // 6 
            "SPS",                          // 7 
            "PPS",                          // 8 
            "AUD",                          // 9 
            "End of Sequence",              // 10 
            "End of Stream",                // 11 
            "Filler Data"// 12
    };

    /** Return an array with NAL delimiter indexes. **/
    public static Vector<VideoFrame> getNALU(ByteArray nalu,int position){
        Vector<VideoFrame> units  = new Vector<VideoFrame>();
        int unit_start=-1;
        int unit_type=-1;
        int unit_header=-1;
        // Loop through data to find NAL startcodes.
        long window = 0;
        nalu.position = position;
        while((nalu.bytesAvailable-nalu.position) > 4) {
            window = nalu.readUnsignedInt();
            // Match four-byte startcodes
            if ((window) == 0x01) {
                // push previous NAL unit if new start delimiter found, dont push unit with type = 0
                if (unit_start>=0 && unit_type>=0) {
                    addFrame(units, unit_header, nalu.position - 4 - unit_start, unit_start, unit_type, nalu);
                }
                unit_header = 4;
                unit_start = nalu.position;
                unit_type = nalu.readByte() & 0x1F;
                // NDR or IDR NAL unit
                if (unit_type == 1 || unit_type == 5) {
                    break;
                }
                // Match three-byte startcodes
            } else if ((window & 0xFFFFFF00) == 0x100) {
                // push previous NAL unit if new start delimiter found, dont push unit with type = 0
                if (unit_start!=0 && unit_type!=0) {
//                    units.add(new VideoFrame(unit_header, nalu.position - 4 - unit_start, unit_start, unit_type));
                    addFrame(units, unit_header, nalu.position - 4 - unit_start, unit_start, unit_type,nalu);
                }
                nalu.position--;
                unit_header = 3;
                unit_start = nalu.position;
                unit_type = nalu.readByte() & 0x1F;
                // NDR or IDR NAL unit
                if (unit_type == 1 || unit_type == 5) {
                    break;
                }
            } else {
                nalu.position -= 3;
            }
        }
        // Append the last NAL to the array.
        if (unit_start!=0) {
            addFrame(units,unit_header, (int) (nalu.bytesAvailable - unit_start), unit_start, unit_type,nalu);
        }
        // Reset position and return results.
        if (units.size()>0) {
            if(writeDebug){
                String txt = "AVC: ";
                for (VideoFrame unit : units) {
                    txt += NAMES[unit.type] + ", ";
                }
                logger.debug(txt.substring(0, txt.length() - 2) + " slices");
            }
        } else {
            logger.warn("AVC: no NALU slices found");
        }
        nalu.position = position;
        return units;
    }
    public static void addFrame(Vector<VideoFrame> frames,int unit_header,int length,int start,int type,ByteArray nalu){
        VideoFrame frame = new VideoFrame(unit_header,length,start,type);
        int maxLength = 16*2;
        if(writeDebug){
            logger.debug("Video frame bufferHex:\n"+TSDemuxer.bufferToHex(nalu.buffers,nalu.bufferOffset+frame.start,
                    frame.length>maxLength?maxLength:frame.length)+(frame.length>maxLength?"\n....":""));
        }
        frames.add(frame);
    }
}
