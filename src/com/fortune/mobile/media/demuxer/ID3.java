package com.fortune.mobile.media.demuxer;

import com.fortune.mobile.utils.Logger;

/**
 * Created by xjliu on 2016/1/14.
 * 
 */
public class ID3 {
    Logger logger = Logger.getLogger(getClass().getName());
    public int len;
    public boolean hasTimestamp= false;
    public long timestamp;

    /* create ID3 object by parsing ByteArray, looking for ID3 tag length, and timestamp */
    public ID3(ByteArray  data) {
        int tagSize = 0;
        try {
            int pos  = data.position;
            String header;
            do {
                header = data.readString(3);
                if ("ID3".equals(header)) {
                    // skip 24 bits
                    data.position += 3;
                    // retrieve tag length
                    int byte1 = data.readUnsignedByte() & 0x7f;
                    int byte2 = data.readUnsignedByte() & 0x7f;
                    int byte3 = data.readUnsignedByte() & 0x7f;
                    int byte4 = data.readUnsignedByte() & 0x7f;
                    tagSize = (byte1 << 21) + (byte2 << 14) + (byte3 << 7) + byte4;
                    int end_pos = data.position + tagSize;
                    {
                        logger.debug( "ID3 tag found, size/end pos:" + tagSize + "/" + end_pos);
                    }
                    // read tag
                    _parseFrame(data, end_pos);
                    data.position = end_pos;
                } else if ("3DI".equals(header)) {
                    // http://id3.org/id3v2.4.0-structure chapter 3.4.   ID3v2 footer
                    data.position += 7;
                    {
                        logger.debug("3DI footer found, end pos:" + data.position);
                    }
                } else {
                    data.position -= 3;
                    len = data.position - pos;
                    {
                        if (len>0) {
                            logger.debug("ID3 len:" + len);
                            if (!hasTimestamp) {
                                logger.warn( "ID3 tag found, but no timestamp");
                            }
                        }
                    }
                    return;
                }
            } while (true);
        } catch(Exception e) {
            e.printStackTrace();
        }
        len = 0;
    }

    /*  Each Elementary Audio Stream segment MUST signal the timestamp of
    its first sample with an ID3 PRIV tag [ID3] at the beginning of
    the segment.  The ID3 PRIV owner identifier MUST be
    "com.apple.streaming.transportStreamTimestamp".  The ID3 payload
    MUST be a 33-bit MPEG-2 Program Elementary Stream timestamp
    expressed as a big-endian eight-octet number, with the upper 31
    bits set to zero.
     */
    private void _parseFrame(ByteArray data ,int end_pos) {

        if ("PRIV".equals(data.readString(4))) {
            while (data.position + 53 <= end_pos) {
                // owner should be "com.apple.streaming.transportStreamTimestamp"
                if ("com.apple.streaming.transportStreamTimestamp".equals(data.readString(44))) {
                    // smelling even better ! we found the right descriptor
                    // skip null character (string end) + 3 first bytes
                    data.position += 4;
                    // timestamp is 33 bit expressed as a big-endian eight-octet number, with the upper 31 bits set to zero.
                    int pts_33_bit = data.readUnsignedByte() & 0x1;
                    hasTimestamp = true;
                    timestamp = (data.readUnsignedInt() / 90);
                    if (pts_33_bit>0) {
                        timestamp   += 47721858.84f; // 2^32 / 90
                    }
                    {
                        logger.debug("ID3 timestamp found:" + timestamp);
                    }
                    return;
                } else {
                    // rewind 44 read bytes + move to next byte
                    data.position -= 43;
                }
            }
        }
    }

}
