package com.fortune.mobile.media.demuxer;

import java.util.Vector;

/**
 * Created by xjliu on 2016/1/14.
 *
 */
public class FLVTag {
    /** AAC Header Type ID. **/
    public static final int AAC_HEADER = 0;
    /** AAC Data Type ID. **/
    public static final int AAC_RAW = 1;
    /** AVC Header Type ID. **/
    public static final int AVC_HEADER = 2;
    /** AVC Data Type ID. **/
    public static final int AVC_NALU  = 3;
    /** MP3 Data Type ID. **/
    public static final int MP3_RAW = 4;
    /** Discontinuity Data Type ID. **/
    public static final int DISCONTINUITY = 5;
    /** metadata Type ID. **/
    public static final int METADATA = 6;
    /* FLV TAG TYPE */
    private static final int TAG_TYPE_AUDIO = 8;
    private static final int TAG_TYPE_VIDEO = 9;
    private static final int TAG_TYPE_SCRIPT  = 18;
    /** Is this a keyframe. **/
    public boolean keyframe;
    /** Array with data pointers. **/
    protected Vector<TagData> pointers = new Vector<TagData>();
    /** PTS of this frame. **/
    public long pts;
    /** DTS of this frame. **/
    public long dts;
    /** Type of FLV tag.**/
    public int type;

    /** Get the FLV file header. **/
    public ByteArray getHeader() {
        ByteArray flv = new ByteArray(13);
        // "F" + "L" + "V".
        //byte[] buffers = flv.getBuffers();
        flv.writeByte((byte)0x46);
        flv.writeByte((byte)0x4C);
        flv.writeByte((byte)0x56);
        // File version (1)
        flv.writeByte((byte)1);
        // Audio + Video tags.
        flv.writeByte((byte) 1);
        // Length of the header.
        flv.writeUnsignedInt(9);
        // PreviousTagSize0
        flv.writeUnsignedInt(0);
        return flv;
    };

    /** Get an FLV Tag header (11 bytes). **/
    public ByteArray getTagHeader(int type,int length,long stamp){
        ByteArray tag  = new ByteArray(11);
        tag.writeByte((byte) type);

        // Size of the tag in bytes after StreamID.
//        tag.write_24(length);
        tag.writeByte((byte)(length >> 16));
        tag.writeByte((byte)(length >> 8));
        tag.writeByte((byte)(length));
        // Timestamp (lower 24 plus upper 8)
        tag.writeByte((byte)(stamp >> 16));
        tag.writeByte((byte)(stamp >> 8));
        tag.writeByte((byte) stamp);
        tag.writeByte((byte)(stamp >> 24));
        // StreamID (3 empty bytes)
        tag.write_24(0);
        // All done
        return tag;
    };

    /** Save the frame data and parameters. **/
    public FLVTag(int typ,long stp_p,long stp_d,boolean key) {
        type = typ;
        pts = stp_p;
        dts = stp_d;
        keyframe = key;
    }
    ;

    /** Returns the tag data. **/
    public ByteArray getData(){
        int length = getLength();
        ByteArray array;
            /* following specification http://download.macromedia.com/f4v/video_file_format_spec_v10_1.pdf */

        // Render header data
        if (type == FLVTag.MP3_RAW) {
            array = getTagHeader(TAG_TYPE_AUDIO, length + 1, pts);
            // Presume MP3 is 44.1 stereo.
            array.writeByte(0x2F);
        } else if (type == AVC_HEADER || type == AVC_NALU) {
            array = getTagHeader(TAG_TYPE_VIDEO, length + 5, dts);
            // keyframe/interframe switch (0x10 / 0x20) + AVC (0x07)
            if(keyframe){
                array.writeByte(0x17);
            }else{
                array.writeByte(0x27);
            }
                /* AVC Packet Type :
                0 = AVC sequence header
                1 = AVC NALU
                2 = AVC end of sequence (lower level NALU sequence ender is
                not required or supported) */
             array.writeByte(type == AVC_HEADER ?0x00:0x01);
            // CompositionTime (in ms)
            // CONFIG::LOGGING {
            // Log.info("pts:"+pts+",dts:"+dts+",delta:"+compositionTime);
            // }
            long compositionTime= (pts - dts);
            array.writeByte((int)(compositionTime >> 16));
            array.writeByte((int)(compositionTime >> 8));
            array.writeByte((int)(compositionTime));
        } else if (type == DISCONTINUITY || type == METADATA) {
            array = getTagHeader(FLVTag.TAG_TYPE_SCRIPT, length, pts);
        } else {
            array = getTagHeader(TAG_TYPE_AUDIO, length + 2, pts);
            // SoundFormat, -Rate, -Size, Type and Header/Raw switch.
            array.writeByte(0xAF);
            array.writeByte(type == AAC_HEADER?0x00:0x01);
        }
        for (int i = 0; i < pointers.size(); i++) {
            TagData tag = pointers.get(i);
            if (type == AVC_NALU) {
                array.writeUnsignedInt(tag.length);
            }
            array.writeByteArray(tag.array.buffers, tag.start, tag.length);
        }
        // Write previousTagSize and return data.
        array.writeUnsignedInt((int)array.getOffset());
        return array;
    }

    /** Returns the bytesize of the frame. **/
    private int getLength(){
        int length = 0;
        for (int i  = 0; i < pointers.size(); i++) {
            length += pointers.get(i).length;
            // Account for NAL startcode length.
            if (type == AVC_NALU) {
                length += 4;
            }
        }
        return length;
    }
    ;

    /** push a data pointer into the frame. **/
    public void push(ByteArray array,int start,int length){
        pointers.add(new TagData(array, start, length));
    }
    ;

    /** Trace the contents of this tag. **/
    public String toString() {
        return "TAG (type: " + type + ", pts:" + pts + ", dts:" + dts + ", length:" + getLength() + ")";
    }
    ;

    public FLVTag clone(){
        FLVTag cloned = new FLVTag(this.type, this.pts, this.dts, this.keyframe);
        cloned.pointers = this.pointers;
        return cloned;
    }



    /** Tag Content **/
    class TagData {
        public ByteArray array;
        public int start;
        public int length;

        public TagData(ByteArray array,int start,int length) {
            this.array = array;
            this.start = start;
            this.length = length;
        }
    }
}
