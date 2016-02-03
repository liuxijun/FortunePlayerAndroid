package com.fortune.mobile.media.demuxer;

/**
 * Created by xjliu on 2016/1/14.
 *
 */
public class PES {
    /** Is it AAC audio or AVC video. **/
    public boolean audio;
    /** The PES data (including headers). **/
    public ByteArray data;
    /** Start of the payload. **/
    public int payload ;
    /** Timestamp from the PTS header. **/
    public long pts;
    /** Timestamp from the DTS header. **/
    public long dts;
    /** PES packet len **/
    public int len;
    /** PES packet len **/
    public int payload_len;

    /** Save the first chunk of PES data. **/
    public PES(ByteArray dat,boolean aud) {
        data = dat;
        audio = aud;
        parse();
    };

    /** When all data is appended, parse the PES headers. **/
    private void parse() {
        data.position = 0;
        // Start code prefix and packet ID.
        long prefix  = data.read_32();
            /*Audio streams (0x1C0-0x1DF)
            Video streams (0x1E0-0x1EF)
            0x1BD is special case, could be audio or video (ffmpeg\libavformat\mpeg.c)
             */
        if ((audio && (prefix > 0x1df || prefix < 0x1c0 && prefix != 0x1bd)) || (!audio && prefix != 0x1e0 && prefix != 0x1ea && prefix != 0x1bd)) {
            throw new Error("PES start code not found or not AAC/AVC: " + String.format("0x%X",prefix));
        }
        // read len
        len = data.readUnsignedShort();
        // Ignore marker bits.
        data.position += 1;
        // Check for PTS
        int flags = (data.readUnsignedByte() & 192) >> 6;
        // Check PES header length
        int length  = data.readUnsignedByte();

        if (flags == 2 || flags == 3) {
            // Grab the timestamp from PTS data (spread out over 5 bytes):
            // XXXX---X -------- -------X -------- -------X

            long _pts  = (long)((data.readUnsignedByte() & 0x0e)) * (1 << 29) + (long)((data.readUnsignedShort() >> 1) << 15) +
                    ((data.readUnsignedShort() >> 1));
            // check if greater than 2^32 -1
            if (_pts > 4294967295L) {
                // decrement 2^33
                _pts -= 8589934592L;
            }
            length -= 5;
            long _dts = _pts;
            if (flags == 3) {
                // Grab the DTS (like PTS)
                _dts = ((data.readUnsignedByte() & 0x0e)) * (1 << 29) + ((data.readUnsignedShort() >> 1) << 15) +
                        ((data.readUnsignedShort() >> 1));
                // check if greater than 2^32 -1
                if (_dts > 4294967295L) {
                    // decrement 2^33
                    _dts -= 8589934592L;
                }
                length -= 5;
            }
            pts = Math.round(_pts / 90);
            dts = Math.round(_dts / 90);
            // CONFIG::LOGGING {
            // Log.info("pts/dts: " + pts + "/"+ dts);
            // }
        }
        // Skip other header data and parse payload.
        data.position += length;
        payload = data.position;
        if(len>0) {
            payload_len = len - data.position + 6;
        } else {
            payload_len = 0;
        }
    };
}
