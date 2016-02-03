package com.fortune.mobile.media.demuxer;

import com.fortune.mobile.media.demuxer.model.AudioTrack;

import java.util.Vector;

import com.fortune.mobile.utils.Logger;

/**
 * Created by xjliu on 2016/1/14.
 * 
 */
public class AACDemuxer {
    static Logger logger = Logger.getLogger("AACDemuxer");
    /** ADTS Syncword (0xFFF), ID:0 (MPEG4), layer (00) and protection_absent (1:no CRC).**/
    private static final int SYNCWORD  = 0xFFF1;
    /** ADTS Syncword (0xFFF), ID:1 (MPEG2), layer (00) and protection_absent (1: no CRC).**/
    private static final int SYNCWORD_2  = 0xFFF9;
    /** ADTS Syncword (0xFFF), ID:1 (MPEG2), layer (00) and protection_absent (0: CRC).**/
    private static final int SYNCWORD_3  = 0xFFF8;
    /** ADTS/ADIF sample rates index. **/
    private static final int[] RATES  = new int[]{96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350};
    /** ADIF profile index (ADTS doesn't have Null). **/
    private static final String[] PROFILES  = new String[]{"Null", "Main", "LC", "SSR", "LTP", "SBR"};
    /** Byte data to be read **/
    private ByteArray _data;
    /* callback functions for audio selection, and parsing progress/complete */
/*
    private var _callback_audioselect : Function;
    private var _callback_progress : Function;
    private var _callback_complete : Function;
*/

    /** append new data */
    public void append(ByteArray data){
        if (_data == null) {
            _data = new ByteArray(data.position);
        }
        _data.writeBytes(data);
    }

    /** cancel demux operation */
    public void cancel(){
        _data = null;
    }

    public boolean audio_expected(){
        return true;
    }

    public boolean video_expected()  {
        return false;
    }

    public void notifycomplete() {
         {
            logger.debug("AAC: extracting AAC tags");
        }
        Vector<FLVTag> audioTags   = new Vector<FLVTag>();
            /* parse AAC, convert Elementary Streams to TAG */
        _data.position = 0;
        ID3 id3  = new ID3(_data);
        // AAC should contain ID3 tag filled with a timestamp
        Vector<AudioFrame> frames  = getFrames(_data, _data.position);
        ByteArray adif  = getADIF(_data, id3.len);
        FLVTag adifTag  = new FLVTag(FLVTag.AAC_HEADER, id3.timestamp, id3.timestamp, true);
        adifTag.push(adif, 0, adif.getLength());
        audioTags.add(adifTag);

        FLVTag audioTag;
        int stamp;
        int i  = 0;

        while (i < frames.size()) {
            AudioFrame f = frames.get(i);
            stamp = Math.round(id3.timestamp + i * 1024 * 1000 / f.rate);
            audioTag = new FLVTag(FLVTag.AAC_RAW, stamp, stamp, false);
            if (i != frames.size() - 1) {
                audioTag.push(_data, f.start, f.length);
            } else {
                audioTag.push(_data, f.start, _data.getLength() - f.start);
            }
            audioTags.add(audioTag);
            i++;
        }
        Vector<AudioTrack> audiotracks = new Vector<AudioTrack>();
        audiotracks.add(new AudioTrack("AAC ES", AudioTrack.FROM_DEMUX, 0, true, true));
        // report unique audio track. dont check return value as obviously the track will be selected
        //_callback_audioselect(audiotracks);
         {
            logger.debug("AAC: all tags extracted, callback demux");
        }
        _data = null;
        //_callback_progress(audioTags);
        //_callback_complete();
    }

/*
    public function AACDemuxer(callback_audioselect : Function, callback_progress : Function, callback_complete : Function) : void {
        _callback_audioselect = callback_audioselect;
        _callback_progress = callback_progress;
        _callback_complete = callback_complete;
    };
*/

    public boolean probe(ByteArray data){
        int pos = data.position;
        ID3 id3 = new ID3(data);
        // AAC should contain ID3 tag filled with a timestamp
        if (id3.hasTimestamp) {
            while (data.bytesAvailable > 1) {
                // Check for ADTS header
                int shortV  = data.readUnsignedShort();
                if (shortV == SYNCWORD || shortV == SYNCWORD_2 || shortV == SYNCWORD_3) {
                    data.position = pos;
                    return true;
                } else {
                    data.position--;
                }
            }
            data.position = pos;
        }
        return false;
    }

    /** Get ADIF header from ADTS stream. **/
    public static ByteArray getADIF(ByteArray adts,int position) {
        adts.position = position;
        int shortV=0;
        // we need at least 6 bytes, 2 for sync word, 4 for frame length
        while ((adts.bytesAvailable > 5) && (shortV != SYNCWORD) && (shortV != SYNCWORD_2) && (shortV != SYNCWORD_3)) {
            shortV = adts.readUnsignedShort();
            adts.position--;
        }
        adts.position++;
        int srate = -1;
        int channels = -1;
        int profile = -1;
        if (shortV == SYNCWORD || shortV == SYNCWORD_2 || shortV == SYNCWORD_3) {
            profile = (adts.readByte() & 0xF0) >> 6;
            // Correcting zero-index of ADIF and Flash playing only LC/HE.
            if (profile > 3) {
                profile = 5;
            } else {
                profile = 2;
            }
            adts.position--;
            srate  = (adts.readByte() & 0x3C) >> 2;
            adts.position--;
            channels  = (adts.readShort() & 0x01C0) >> 6;
        } else {
            throw new Error("Stream did not start with ADTS header.");
        }
        // 5 bits profile + 4 bits samplerate + 4 bits channels.
        ByteArray adif = new ByteArray(10);
        adif.writeByte((profile << 3) + (srate >> 1));
        adif.writeByte((srate << 7) + (channels << 3));
         {
            logger.debug("AAC: " + PROFILES[profile] + ", " + RATES[srate] + " Hz " + channels + " channel(s)");
        }
        // Reset position and return adif.
        adts.position -= 4;
        adif.position = 0;
        return adif;
    };

    /** Get a list with AAC frames from ADTS stream. **/
    public static Vector<AudioFrame> getFrames(ByteArray adts,int position){
        Vector<AudioFrame> frames  = new Vector<AudioFrame>();
        int frame_start=0;
        int frame_length=0;
        ID3 id3  = new ID3(adts);
        position += id3.len;
        // Get raw AAC frames from audio stream.
        adts.position = position;
        int samplerate=0;
        // we need at least 6 bytes, 2 for sync word, 4 for frame length
        while ((adts.bytesAvailable-adts.position) > 5) {
            // Check for ADTS header
            int shortV = adts.readUnsignedShort();
            if (shortV == SYNCWORD || shortV == SYNCWORD_2 || shortV == SYNCWORD_3) {
                // Store samplerate for offsetting timestamps.
                if (0==samplerate) {
                    samplerate = RATES[(adts.readByte() & 0x3C) >> 2];
                    adts.position--;
                }
                // Store raw AAC preceding this header.
                if (frame_start!=0) {
                    frames.add(new AudioFrame(frame_start, frame_length, frame_length, samplerate));
                }
                // protection_absent=1, crc_len = 0,protection_absent=0,crc_len=2
                int crc_len = (1 - (shortV & 0x1)) << 1;
                // ADTS header is 7+crc_len bytes.
                frame_length = ((int)(adts.readUnsignedInt() & 0x0003FFE0) >> 5) - 7 - crc_len;
                frame_start = adts.position + 1 + crc_len;
                adts.position += frame_length + 1 + crc_len;
            } else {
                 {
                    logger.debug( "no ADTS header found, probing...");
                }
                adts.position--;
            }
        }
        if (frame_start!=0) {
            // check if we have a complete frame available at the end, i.e. last found frame is fitting in this PES packet
            int overflow  = frame_start + frame_length - adts.getLength();
            if (overflow <= 0 ) {
                // no overflow, Write raw AAC after last header.
                frames.add(new AudioFrame(frame_start, frame_length, frame_length, samplerate));
            } else {
                 {
                    logger.debug("ADTS overflow at the end of PES packet, missing " + overflow + " bytes to complete the ADTS frame");
                }
            }
        } else if (frames.size() == 0) {
            {
                logger.warn( "No ADTS headers found in this stream.");
            }
        }
        adts.position = position;
        return frames;
    };

}
