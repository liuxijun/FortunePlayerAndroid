package com.fortune.mobile.media.demuxer;

import com.fortune.mobile.media.decoder.Decoder;
import com.fortune.mobile.media.demuxer.model.AudioTrack;
import com.fortune.mobile.utils.Logger;

import java.io.*;
import java.util.Vector;

/**
 * Created by xjliu on 2016/1/14.
 */
public class TSDemuxer extends Thread {
    /**
     * read position
     **/
    private int _read_position;
    /**
     * is bytearray full ?
     **/
    private boolean _data_complete;
    /**
     * TS Sync byte.
     **/
    private static byte SYNCBYTE = 0x47;
    /**
     * TS Packet size in byte.
     **/
    private static final int PACKETSIZE = 188;
    /**
     * Packet ID of the PAT (is always 0).
     **/
    private static final int PAT_ID = 0;
    /**
     * Packet ID of the SDT (is always 17).
     **/
    private static final int SDT_ID = 17;
    /**
     * has PMT been parsed ?
     **/
    private boolean _pmtParsed;
    /**
     * any TS packets before PMT ?
     **/
    private boolean _packetsBeforePMT;
    /**
     * PMT PID
     **/
    private int _pmtId;
    /**
     * video PID
     **/
    private int _avcId;
    /**
     * audio PID
     **/
    private int _audioId;
    private boolean _audioIsAAC;
    /**
     * ID3 PID
     **/
    private int _id3Id;
    /**
     * Vector of audio/video tags
     **/
    private Vector<FLVTag> _tags;
    /** Display Object used to schedule parsing **/
//    private var _displayObject : DisplayObject;
    /**
     * Byte data to be read
     **/
    private ByteArray videoBuffer;
    private ByteArray audioBuffer;
    private ByteArray nalBuffer;
    public ByteArray _data;
    private Decoder decoder;
    /* callback functions for audio selection, and parsing progress/complete */
    //private var _callback_audioselect : Function;
    //private var _callback_progress : Function;
    //private var _callback_complete : Function;
    //private var _callback_videometadata : Function;
    /* current audio PES */
    private ByteArray _curAudioPES;
    /* current video PES */
    private ByteArray _curVideoPES;
    /* current id3 PES */
    private ByteArray _curId3PES;
    /* ADTS frame overflow */
    private ByteArray _adtsFrameOverflow;
    /* current NAL unit */
    private ByteArray _curNalUnit;
    /* current AVC Tag */
    private FLVTag _curVideoTag;
    /* ADIF tag inserted ? */
    private boolean _adifTagInserted = false;
    /* last AVCC byte Array */
    private ByteArray _avcc;
    Logger logger = Logger.getLogger(getClass().getName());
    private boolean running = true;
    private boolean _dataReward = false;
    private boolean streamFinished = false;
    private final Object lock = new Object();

    public void setStreamFinished(boolean streamFinished) {
        this.streamFinished = streamFinished;
    }
    public void reset(){
        logger.debug("重置所有读取数据");
        setBytesAvailable(0);
        _data.position = 0;
        _data.readed = 0;
    }
    public boolean probe(ByteArray data) {
        int pos = data.position;
        int len = Math.min((int) data.bytesAvailable, 188 * 2);
        for (int i = 0; i < len; i++) {
            if (data.read_8() == SYNCBYTE) {
                // ensure that at least two consecutive TS start offset are found
                if (len - data.position > 188) {
                    data.position = pos + i + 188;
                    if (data.read_8() == SYNCBYTE) {
                        logger.debug("经过反复查找，跳跃了"+i+"个字节，终于找到了0x47");
                        data.position = pos + i;
                        return true;
                    } else {
                        data.position = pos + i + 1;
                    }
                }
            }
        }
        //todo 这里注释掉的原因是，如果在一段数据流内（2*188个字节）还没有找到起始字符，就让他继续找
        //data.position = pos;
        return false;
    }

    /**
     * Transmux the M2TS file into an FLV file.
     **/
    public TSDemuxer(Decoder decoder) {
        this.decoder = decoder;
        _curAudioPES = null;
        _curVideoPES = null;
        _curId3PES = null;
        _curVideoTag = null;
        _curNalUnit = null;
        _adtsFrameOverflow = null;
/*
        _callback_audioselect = callback_audioselect;
        _callback_progress = callback_progress;
        _callback_complete = callback_complete;
        _callback_videometadata = callback_videometadata;
*/
        _pmtParsed = false;
        _packetsBeforePMT = false;
        _pmtId = _avcId = _audioId = _id3Id = -1;
        _audioIsAAC = false;
        _tags = new Vector<FLVTag>();
        videoBuffer = new ByteArray(1024 * 512);//10 MB buffer for video pes data
        audioBuffer = new ByteArray(1024 * 256);//5MB buffer for audio pes data
        nalBuffer = new ByteArray(1024 * 128);
        _data = new ByteArray(1024 * 10 * 188);
//        _displayObject = displayObject;
    }

    public void setBytesAvailable(long bytesAvailable) {
//        logger.debug("修改bytesAvailable=" + bytesAvailable);
        synchronized (lock) {
            _data.bytesAvailable = bytesAvailable;
        }
//        logger.debug("修改完毕bytesAvailable=" + bytesAvailable);
    }

    /**
     * append new TS data
     */
    int appendIdx = 0;

    public synchronized long append(byte[] buffer, int startPos, int length) {
        appendIdx++;
        if (_data == null) {
            int defaultLength = 1024 * 1024 * 10;
            logger.debug("firtst run!alloc memory :" + defaultLength + " byte");
            _data = new ByteArray(defaultLength);//10M�Ļ�����
            _data_complete = false;
            _read_position = 0;
            _avcc = null;
            //_displayObject.addEventListener(Event.ENTER_FRAME, _parseTimer);
        }
        //_data.position =(int) _data.readed;
        if (_data.readed + length > _data.buffers.length - 188 * 10) {
            int i = 0;
            boolean shouldWait = _dataReward;
            while (_dataReward && running) {
                //数据已经从头来过了，这时候必须等待有人把这个值设置为false
                try {
                    logger.debug(" append:"+appendIdx+","+"已经重置过数据，这时不能再重置了！需要等待.....position=" + _data.position + ",bytesAvailable=" + _data.bytesAvailable + ",readed=" + _data.readed);
                    sleep(1000L);
                    i++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (shouldWait) {
                try {
                    logger.debug(" append:"+appendIdx+","+"额外等待1秒钟，让重置的数据先消费起来，position=" + _data.position + ",bytesAvailable=" + _data.bytesAvailable + ",readed=" + _data.readed);
                    sleep(1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //缓冲区已经到底了，需要从头再填充进去。
            //想办法把188字节对齐了，否则后面处理会有问题
            int byteLeft = (int) _data.bytesAvailable % 188;
            if (byteLeft != 0) {
                int tempLength = 188 - byteLeft;
                if (tempLength > length) {
                    tempLength = length;
                }
                System.arraycopy(buffer, startPos, _data.buffers, (int) _data.bytesAvailable, tempLength);
                setBytesAvailable(_data.bytesAvailable + tempLength);
                startPos += tempLength;
                length -= tempLength;
            }
            logger.debug(" append:"+appendIdx+","+"buffer overflow!Refill from header! current length=" + _data.buffers.length +
                    ",readed=" + _data.readed + ",will fill length=" + length + ",bytesAvailable=" +
                    _data.bytesAvailable + "," + ((byteLeft == 0) ? "已对齐,无需处理" : ("未对齐，剩余字节" + byteLeft + "bytes")));
            _data.readed = 0;
            _dataReward = true;
//        } else {
            /**
             * 有几种情况：
             * 1、当前处理的指针小于当前已经填充的位置，可以继续处理
             * 2、当前处理的指针大于当前已经填充的位置+数据长度，可以继续处理
             * 3、当前处理的指针大于当前已经填充的位置，但小于当前已经填充位置+数据长度，等待
             *                  处理指针继续向后处理并超过了要添加的数据长度，或处理指针重头再处理
             *
             */
        }
        if (_dataReward) {
            int i=0;
            while (_data.readed + length >= _data.position && running&&_dataReward) {
                try {
                    logger.debug(" append:"+appendIdx+","+"要填充的数据追上了要处理的数据指针，等待中.....position=" + _data.position+",bytesAvailable="+_data.bytesAvailable + ",readed=" + _data.readed + ",length=" + length+",i="+i);
                    i++;
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if(!_dataReward){
                logger.warn(" append:"+appendIdx+","+"在等待期间，处理指针清零了！这有一定的风险！");
            }
        }
//            logger.debug(""+length);
        if (!running) {
            return -1;
        }
        int toPos = (int) _data.readed;
        //_data.readed = ByteArray.writeByteArray(_data.buffers, (int) _data.readed, buffer, startPos, length);
        if (length > 0) {
            System.arraycopy(buffer, startPos, _data.buffers, toPos, length);
            _data.readed = toPos + length;
        }
        //logger.debug("buffer appened:_data.position=" + _data.position + ",readed=" + _data.readed);
        if (!_dataReward) {
            setBytesAvailable(_data.readed);
        }
        return _data.readed;
    }

    public void append(ByteArray data) {
        append(data.buffers, 0, (int) data.readed);
    }

    /**
     * cancel demux operation
     */
    public void cancel() {
        _data = null;
        _curAudioPES = null;
        _curVideoPES = null;
        _curId3PES = null;
        _curVideoTag = null;
        _curNalUnit = null;
        _adtsFrameOverflow = null;
        _avcc = null;
        _tags = new Vector<FLVTag>();
        // _displayObject.removeEventListener(Event.ENTER_FRAME, _parseTimer);
    }

    public void notifycomplete() {
        _data_complete = true;
    }

    public boolean audio_expected() {
        return (_audioId != -1);
    }

    public boolean video_expected() {
        return (_avcId != -1);
    }

    public long getTimer() {
        return System.currentTimeMillis();
    }

    /**
     * Parse a limited amount of packets each time to avoid blocking
     **/

    public void stopDemuxer() {
        running = false;
    }

    int parseCount=0;
    public void run() {
        long start_time = getTimer();
        logger.debug("准备启动分离器");
        _data.position = 0;
        // dont spend more than 20ms demuxing TS packets to avoid loosing frames
        long byteReaded = _data.readed;
        long byteLeft = byteReaded - _data.position;
        running = true;
        int i = 0;
        while (byteLeft < 188 && running) {
            try {
                Thread.sleep(40);
                i++;
                if (i % 25 == 0) {
                    logger.debug("数据尚未就绪，等待了" + i + "次");
                }
                byteLeft = _data.readed - _data.position;
            } catch (InterruptedException e) {
                e.printStackTrace();
                running = false;
                break;
            }
        }
        while (running/* && ((getTimer() - start_time) < 20)*/) {
            byteLeft = _data.bytesAvailable - _data.position;
            i = 0;
            while (byteLeft < 188 && running && (!streamFinished)) {
                try {
                    Thread.sleep(40);
                    if (i % 25 == 0) {
                        logger.debug("parseTS "+parseCount+","+"数据尚未就绪，等待了" + i + "次，byteLeft=" + byteLeft);
                    }
                    i++;
                    if (i > 250) {
                        logger.error("parseTS "+parseCount+","+"超时了！不再等待TS流过来了");
                        running = false;
                        break;
                    }
                    //logger.debug("数据尚未就绪，byteLeft="+byteLeft+",_data.readed="+_data.readed+",_data.position="+_data.position);
                    if (byteLeft < 188) {
                        //数据读取完毕，看看是否需要从头再来
                        if (running && _dataReward) {
                            logger.debug("parseTS "+parseCount+","+"数据使用完毕，重头再来一次：position=" + _data.position + ",bytesAvailable="
                                    + _data.bytesAvailable + ",readed=" + _data.readed + ",byteleft=" + byteLeft);
                            setBytesAvailable(_data.readed);
                            _data.position = 0;
                            _dataReward = false;
                        } else {
                            if (!_dataReward) {
                                logger.debug("parseTS "+parseCount+","+"数据未有效填充，只能是等待！position=" + _data.position + ",bytesAvailable="
                                        + _data.bytesAvailable + ",readed=" + _data.readed);
                            } else {
                                logger.warn("parseTS "+parseCount+","+"这是要退出的节奏啊：running=" + running + ",_dataReward=" + _dataReward);
                            }
                        }
                    } else {
                        break;
                    }
                    byteLeft = _data.bytesAvailable - _data.position;
/*
                    if(_data.readed>_data.position){
                    }else{
                    }
*/
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    running = false;
                    break;
                }
            }
            if ((!running)||(byteLeft < 188 && streamFinished)) {
                break;
            }
            if (byteLeft == 188) {
                logger.debug("parseTS "+parseCount+","+"可能已经到顶了：bytesAvailable=" + _data.bytesAvailable + ",position=" + _data.position + ",readed=" + _data.readed);
            }
            _parseTSPacket(parseCount);
            parseCount++;
        }
/*
        int length = videoBuffer.bufferOffset;
        if(_curVideoPES!=null){
            length += _curAudioPES.getAvailableBytes();
        }
        try {
            OutputStream fw = new FileOutputStream("F:\\videoBuffer.264");
            fw.write(videoBuffer.buffers,0,length);
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.debug("_parseTSPacket finished");
        if (_tags.size()>0) {
            logger.debug("all tags count = "+_tags.size()+"");
            _callback_progress(_tags);
            _tags = new Vector<FLVTag>();
        }else{
            logger.warn("Can't found any tags!");
        }
        if (_data!=null) {
            _read_position = _data.position;
            // finish reading TS fragment
            if (_data_complete && _data.readed < 188) {
                // free ByteArray
                _data = null;
                // first check if TS parsing was successful
                if (!_pmtParsed) {
                        logger.warn("TS: no PMT found, report parsing complete");
                }
               // _displayObject.removeEventListener(Event.ENTER_FRAME, _parseTimer);
                _flush();
                _callback_complete();
            }
        }

*/
        running = false;
        _tags.clear();
        _tags = null;
        videoBuffer = null;
        _data = null;
        _curVideoPES = null;
        _curAudioPES = null;
        _curId3PES = null;
        _curNalUnit = null;
        _curVideoTag = null;
        logger.debug("_parseTimer finished!");
        decoder.finished();
        System.gc();
    }

    /**
     * flux demux
     **/
    private void _flush() {
        logger.debug("_flush "+parseCount+","+"TS: flushing demux");
        // check whether last parsed audio PES is complete
        if (_curAudioPES != null && _curAudioPES.buffers.length > 14) {
            PES pes = new PES(_curAudioPES, true);
            // consider that PES with unknown size (length=0 found in header) is complete
            if (pes.len == 0 || (pes.data.getLength() - pes.payload - pes.payload_len) >= 0) {
                logger.debug("_flush "+parseCount+","+"TS: complete Audio PES found at end of segment, parse it");
                // complete PES, parse and push into the queue
                if (_audioIsAAC) {
                    _parseADTSPES(pes);
                } else {
                    _parseMPEGPES(pes);
                }
                _curAudioPES = null;
            } else {
                logger.debug("_flush "+parseCount+","+"TS: partial audio PES at end of segment");
                _curAudioPES.position = _curAudioPES.getLength();
            }
        }
        // check whether last parsed video PES is complete
        if (_curVideoPES != null && _curVideoPES.getLength() > 14) {
            PES pes = new PES(_curVideoPES, false);
            // consider that PES with unknown size (length=0 found in header) is complete
            if (pes.len == 0 || (pes.data.getLength() - pes.payload - pes.payload_len) >= 0) {
                logger.debug("_flush "+parseCount+","+"TS: complete AVC PES found at end of segment, parse it");
                // complete PES, parse and push into the queue
                _parseAVCPES(pes);
                _curVideoPES = null;
                // push last video tag if any
                if (_curVideoTag != null) {
                    if (_curNalUnit != null && _curNalUnit.getLength() > 0) {
                        _curVideoTag.push(_curNalUnit, 0, _curNalUnit.getLength());
                    }
                    _tags.add(_curVideoTag);
                    _curVideoTag = null;
                    _curNalUnit = null;
                }
            } else {
                logger.debug("_flush "+parseCount+","+"TS: partial AVC PES at end of segment expected/current len:" + pes.payload_len + "/"
                        + (pes.data.getLength() - pes.payload));
                _curVideoPES.position = (int) _curVideoPES.bytesAvailable;
            }
        }
        // check whether last parsed ID3 PES is complete
        if (_curId3PES != null && _curId3PES.getLength() > 14) {
            PES pes3 = new PES(_curId3PES, false);
            if (pes3.len > 0 && (pes3.data.getLength() - pes3.payload - pes3.payload_len) >= 0) {
                logger.warn("_flush "+parseCount+","+"TS: complete ID3 PES found at end of segment, parse it");
                // complete PES, parse and push into the queue
                _parseID3PES(pes3);
                _curId3PES = null;
            } else {
                logger.debug("_flush "+parseCount+","+"TS: partial ID3 PES at end of segment");
                _curId3PES.position = _curId3PES.getLength();
            }
        }
        // push remaining tags and notify complete
        if (_tags.size() > 0) {
            logger.warn("TS: flush " + _tags.size() + " tags");
            _callback_progress(_tags);
            _tags = new Vector<FLVTag>();
        }
        logger.debug("TS: parsing complete");
    }

    /**
     * parse ADTS audio PES packet
     **/
    private void _parseADTSPES(PES pes) {
        int stamp;
        // check if previous ADTS frame was overflowing.
        if (_adtsFrameOverflow != null && _adtsFrameOverflow.getLength() > 0) {
            // if overflowing, append remaining data from previous frame at the beginning of PES packet
            {
                logger.debug("_parseADTSPES "+parseCount+","+"TS/AAC: append overflowing " + _adtsFrameOverflow.getLength() + " bytes to beginning of new PES packet");
            }
            ByteArray ba = new ByteArray(pes.payload + _adtsFrameOverflow.position);
            ba.writeBytes(_adtsFrameOverflow);
            ba.writeBytes(pes.data, pes.payload);
            pes.data = ba;
            pes.payload = 0;
            _adtsFrameOverflow = null;
        }
        if (isNaN(pes.pts)) {
            {
                logger.warn("_parseADTSPES "+parseCount+","+"TS/AAC: no PTS info in this PES packet,discarding it");
            }
            return;
        }
        // insert ADIF TAG at the beginning
        if (!_adifTagInserted) {
            FLVTag adifTag = new FLVTag(FLVTag.AAC_HEADER, pes.pts, pes.dts, true);
            ByteArray adif = AACDemuxer.getADIF(pes.data, pes.payload);
            {
                logger.debug("_parseADTSPES "+parseCount+","+"TS/AAC: insert ADIF TAG");
            }
            adifTag.push(adif, 0, adif.getLength());
            _tags.add(adifTag);
            _adifTagInserted = true;
        }
        // Store ADTS frames in array.
        Vector<AudioFrame> frames = AACDemuxer.getFrames(pes.data, pes.payload);
        AudioFrame frame = null;
        for (int j = 0; j < frames.size(); j++) {
            frame = frames.get(j);
            // Increment the timestamp of subsequent frames.
            stamp = Math.round(pes.pts + j * 1024 * 1000 / frame.rate);
            FLVTag curAudioTag = new FLVTag(FLVTag.AAC_RAW, stamp, stamp, false);
            curAudioTag.push(pes.data, frame.start, frame.length);
            _tags.add(curAudioTag);
        }
        if (frame != null) {
            // check if last ADTS frame is overflowing on next PES packet
            int adts_overflow = pes.data.getLength() - (frame.start + frame.length);
            if (adts_overflow > 0) {
                _adtsFrameOverflow = new ByteArray(audioBuffer, pes.data.bufferOffset + frame.start + frame.length);
                //_adtsFrameOverflow.writeBytes(pes.data, frame.start + frame.length);
                {
                    logger.debug("_parseADTSPES "+parseCount+","+"TS/AAC:ADTS frame overflow:" + adts_overflow);
                }
            }
        } else {
            // no frame found, add data to overflow buffer
            _adtsFrameOverflow = new ByteArray(pes.data.buffers, pes.data.bufferOffset + pes.data.position);
            //_adtsFrameOverflow.writeBytes(pes.data, pes.data.position);
            {
                logger.debug("_parseADTSPES "+parseCount+","+"TS/AAC:ADTS frame overflow:" + _adtsFrameOverflow.getLength());
            }
        }
    }

    ;

    /**
     * parse MPEG audio PES packet
     **/
    private void _parseMPEGPES(PES pes) {
        if (isNaN(pes.pts)) {
            {
                logger.warn("_parseMPEGPES "+parseCount+","+"TS/MP3: no PTS info in this MP3 PES packet,discarding it");
            }
            return;
        }
        FLVTag tag = new FLVTag(FLVTag.MP3_RAW, pes.pts, pes.dts, false);
        tag.push(pes.data, pes.payload, pes.data.getLength() - pes.payload);
        _tags.add(tag);
    }

    ;

    /**
     * parse AVC PES packet
     **/
    private void _parseAVCPES(PES pes) {
        ByteArray sps = null;
        Vector<ByteArray> ppsvect = null;
        boolean sps_found = false;
        boolean pps_found = false;
        Vector<VideoFrame> frames = Nalu.getNALU(pes.data, pes.payload);
        // If there's no NAL unit, push all data in the previous tag, if any exists
        if (frames.size() == 0) {
            if (_curNalUnit != null) {
                _curNalUnit.writeBytes(pes.data, pes.payload, pes.data.getLength() - pes.payload);
            } else {
                {
                    logger.warn("_parseAVCPES "+parseCount+","+"TS: no NAL unit found in first (?) video PES packet, discarding data. possible segmentation issue ?");
                }
            }
            return;
        }
        // If NAL units are not starting right at the beginning of the PES packet, push preceding data into previous NAL unit.
        int overflow = frames.get(0).start - frames.get(0).header - pes.payload;
        if (overflow > 0 && _curNalUnit != null) {
            _curNalUnit.writeByteArray(pes.data.buffers, pes.payload, overflow);
        }
        if (isNaN(pes.pts)) {
            {
                logger.warn("_parseAVCPES "+parseCount+","+"TS: no PTS info in this AVC PES packet,discarding it");
            }
            return;
        }
            /* first loop : look for AUD/SPS/PPS NAL unit :
             * AUD (Access Unit Delimiter) are used to detect switch to new video tag 
             * SPS/PPS are used to generate AVC HEADER
             */
        int currentNalPos = 0;
        for (VideoFrame frame : frames) {
            if (frame.type == 9) {
                if (_curVideoTag != null) {
                        /* AUD (Access Unit Delimiter) NAL unit:
                         * we need to push current video tag and start a new one
                         */
                    if (_curNalUnit != null && _curNalUnit.position > 0) {
                            /* push current data into video tag, if any */
                        _curVideoTag.push(_curNalUnit, 0, _curNalUnit.position);
                    }
                    currentNalPos = _curNalUnit.bufferOffset + (int) _curNalUnit.bytesAvailable;
                    _tags.add(_curVideoTag);
                }
                _curNalUnit = new ByteArray(nalBuffer, currentNalPos);
                _curVideoTag = new FLVTag(FLVTag.AVC_NALU, pes.pts, pes.dts, false);
                // push NAL unit 9 into TAG
                _curVideoTag.push(pes.data, frame.start, frame.length);
            } else if (frame.type == 7) {
                sps_found = true;
                sps = new ByteArray(videoBuffer, pes.data.bufferOffset + frame.start);
                sps.bytesAvailable = frame.length;
                //logger.debug("SPS found: 0x"+bufferToHex(sps.buffers,sps.bufferOffset,(int)sps.bytesAvailable));
                //pes.data.position = frame.start;
                //pes.data.readBytes(sps, 0, frame.length);
                pes.data.position = frame.start + frame.length;
                // try to retrieve video width and height from SPS
                SPSInfo spsInfo = new SPSInfo(sps);
                sps.position = 0;
                if (spsInfo.width != 0 && spsInfo.height != 0) {
                    // notify upper layer todo
                    _callback_videometadata(spsInfo.width, spsInfo.height);
                } else {
                    logger.warn("_parseAVCPES "+parseCount+","+"Can't found movie width and height!");
                }
            } else if (frame.type == 8) {
                if (!pps_found) {
                    pps_found = true;
                    ppsvect = new Vector<ByteArray>();
                }
                ByteArray pps = new ByteArray(videoBuffer, pes.data.bufferOffset + frame.start);
                pps.position = frame.length;
                pes.data.position = frame.start + frame.length;
                //pes.data.readBytes(pps, 0, frame.length);
                ppsvect.add(pps);
            }
        }
        if (decoder != null) {
            decoder.onFramesReady(pes);
        }
        // if both SPS and PPS have been found, build AVCC and push tag if needed
/*
        if (sps_found && pps_found) {
            ByteArray avcc  = AVCC.getAVCC(sps, ppsvect);
            // only push AVCC tag if never pushed or avcc different from previous one
            if (_avcc == null || !compareByteArray(_avcc, avcc)) {
                _avcc = avcc;
                FLVTag avccTag = new FLVTag(FLVTag.AVC_HEADER, pes.pts, pes.dts, true);
                avccTag.push(avcc, 0, avcc.getLength());
                // logger.debug("TS:AVC:push AVC HEADER");
                _tags.add(avccTag);
            }
        }
*/
            /* 
             * second loop, handle other NAL units and push them in tags accordingly
             */
/*
        for (VideoFrame frame : frames) {
            if (frame.type <= 6) {
                int nalPos=0;
                if (_curNalUnit!=null) {
                    if( _curNalUnit.getLength()>0){
                        _curVideoTag.push(_curNalUnit, 0, _curNalUnit.getLength());
                        nalPos = _curNalUnit.bufferOffset+_curNalUnit.position;
                        _curNalUnit = new ByteArray(nalBuffer,nalPos);
                    }
                }else{
                    _curNalUnit = new ByteArray(nalBuffer,0);
                }
                _curNalUnit.writeBytes(pes.data, frame.start, frame.length);
                // Unit type 5 indicates a keyframe.
                if (frame.type == 5) {
                    _curVideoTag.keyframe = true;
                } else if (frame.type == 1 || frame.type == 2) {
                    // retrieve slice type by parsing beginning of NAL unit (follow H264 spec, slice_header definition)
                    ByteArray ba = pes.data;
                    // +1 to skip NAL unit type
                    ba.position = frame.start + 1;
                    ExpGolomb eg  = new ExpGolomb(ba);
                        // * add a try/catch,
                        // * as NALu might be partial here (in case NALu/slice header is splitted accross several PES packet ... we might end up
                        // * with buffer overflow. prevent this and in case of overflow assume it is not a keyframe. should be fixed later on
                        //
                    try {
                        // discard first_mb_in_slice
                        eg.readUE();
                        int type  = eg.readUE();
                        if (type == 2 || type == 4 || type == 7 || type == 9) {
                            {
                                //logger.debug("TS: frame_type:" + frame.type + ",keyframe slice_type:" + type);
                            }
                            _curVideoTag.keyframe = true;
                        }
                    } catch(Exception e) {
                        {
                            logger.warn("TS: frame_type:" + frame.type + ": slice header splitted accross several PES packets, assuming not a keyframe");
                        }
                        _curVideoTag.keyframe = false;
                    }
                }
            }
        }
//   */
    }

    public boolean isNaN(long timestamp) {
        return timestamp < 0;
    }

    // return true if same Byte Array
    private boolean compareByteArray(ByteArray ba1, ByteArray ba2) {
        // compare the lengths
        int size = ba1.getLength();
        if (ba1.getLength() == ba2.getLength()) {
            ba1.position = 0;
            ba2.position = 0;
            // then the bytes
            while (ba1.position < size) {
                int v1 = ba1.readByte();
                if (v1 != ba2.readByte()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * parse ID3 PES packet
     **/
    private void _parseID3PES(PES pes) {
        // note: apple spec does not include having PTS in ID3!!!!
        // so we should really spoof the PTS by knowing the PCR at this point
        if (isNaN(pes.pts)) {
            logger.warn("_parseID3PES "+parseCount+","+"TS: no PTS info in this ID3 PES packet,discarding it");
            return;
        }

        ByteArray pespayload = new ByteArray(pes.payload_len);
        if (pes.data.position >= pes.payload + pes.payload_len) {
            pes.data.position = pes.payload;
            pespayload.writeBytes(pes.data, pes.payload, pes.payload_len);
            pespayload.position = 0;
        }
        pes.data.position = 0;

        FLVTag tag = new FLVTag(FLVTag.METADATA, pes.pts, pes.pts, false);

        ByteArray data = new ByteArray(10 + pes.payload_len);
        //data.objectEncoding = ObjectEncoding.AMF0;

        // one or more SCRIPTDATASTRING + SCRIPTDATAVALUE
        data.writeObject("onID3Data");
        // SCRIPTDATASTRING - name of object
        // to pass ByteArray, change to AMF3
        //data.objectEncoding = ObjectEncoding.AMF3;
        data.writeByte(0x11);
        // AMF3 escape
        // then write the ByteArray
        data.writeObject(pespayload);
        tag.push(data, 0, data.getLength());
        _tags.add(tag);
    }

    /**
     * Parse TS packet.
     **/
    long totalVideoLength = 0;

    private void _parseTSPacket(int parseCount) {
        // Each packet is 188 bytes.
        int todo = TSDemuxer.PACKETSIZE;
        // Sync byte.
        if (_data.readByte() != TSDemuxer.SYNCBYTE) {
            int pos_start = _data.position - 1;
            if (probe(_data)) {
                int pos_end = _data.position;
                {
                    logger.warn("_parseTSPacket "+parseCount+","+"TS: lost sync between offsets:" + pos_start + "/" + pos_end);
//                    ByteArray ba = new ByteArray(_datapos_end - pos_start);
//                    _data.position = pos_start;
//                    _data.readBytes(ba, 0, pos_end - pos_start);
                    logger.debug("_parseTSPacket "+parseCount+","+"TS: lost sync dump:\n" + bufferToHex(_data.buffers, pos_start, pos_end - pos_start));
                }
                _data.position = pos_end + 1;
            } else {
                logger.error("_parseTSPacket " + parseCount + "," + "TS: Could not parse file: sync byte not found @ offset/len " + pos_start + "/" + (_data.position-pos_start));
                return;
            }
        }
        todo--;
        // Payload unit start indicator.
        int stt = (_data.readUnsignedByte() & 64) >> 6;
        _data.position--;

        // Packet ID (last 13 bits of UI16).
        int pid = _data.readUnsignedShort() & 8191;
        // Check for adaptation field.
        todo -= 2;
        int atf = (_data.readByte() & 48) >> 4;
        todo--;
        // Read adaptation field if available.
        if (atf > 1) {
            // Length of adaptation field.
            int len = _data.readUnsignedByte();
            if (len < 0) {
                len = len + 256;
            }
            todo--;
            // Random access indicator (keyframe).
            // var rai:uint = data.readUnsignedByte() & 64;
            _data.position += len;
            todo -= len;
            // Return if there's only adaptation field.
            if (atf == 2 || len == 183) {
                _data.position += todo;
                return;
            }
        }

        // Parse the PES, split by Packet ID.
        if (pid == PAT_ID) {
            todo -= _parsePAT(stt);
            if (!_pmtParsed) {
                {
                    logger.debug("_parseTSPacket " + parseCount + "," +"TS: PAT found.PMT PID:" + _pmtId);
                }
            }
        } else if (pid == _pmtId) {
            if (!_pmtParsed) {
                {
                    logger.debug("_parseTSPacket " + parseCount + "," +"TS: PMT found");
                }
                todo -= _parsePMT(stt);
                _pmtParsed = true;
                // if PMT was not parsed before, and some unknown packets have been skipped in between,
                // rewind to beginning of the stream, it helps recovering bad segmented content
                // in theory there should be no A/V packets before PAT/PMT)
                if (_packetsBeforePMT) {
                    {
                        logger.warn("_parseTSPacket " + parseCount + "," +"TS: late PMT found, rewinding at beginning of TS");
                    }
                    _data.position = 0;
                    return;
                }
            }
        } else if (pid == _audioId) {
            int _curAudioOffset = 0;
            if (_pmtParsed) {
                if (stt != 0) {
                    if (null != _curAudioPES) {
                        _curAudioOffset = _curAudioPES.bufferOffset + _curAudioPES.position;
                        if (_curAudioOffset >= audioBuffer.buffers.length - 128 * 1024) {
                            logger.debug("_parseTSPacket " + parseCount + "," +"AudioBuffer reward!");
                            _curAudioOffset = 0;
                        }
                        if (_audioIsAAC) {
                            _parseADTSPES(new PES(_curAudioPES, true));
                        } else {
                            _parseMPEGPES(new PES(_curAudioPES, true));
                        }
                    }
                    _curAudioPES = new ByteArray(audioBuffer, _curAudioOffset);
                }
                if (null != _curAudioPES) {
                    _curAudioPES.writeBytes(_data, _data.position, todo);
                } else {
                    {
                        logger.warn("_parseTSPacket " + parseCount + "," +"TS: Discarding audio packet with id " + pid);
                    }
                }
            }

        } else if (pid == _id3Id) {
            int _curAudioOffset = 0;
            if (_pmtParsed) {
                if (0 != stt) {
                    if (null != _curId3PES) {
                        _curAudioOffset = _curId3PES.bufferOffset + _curId3PES.position;
                        _parseID3PES(new PES(_curId3PES, false));
                    }
                    _curId3PES = new ByteArray(audioBuffer, _curAudioOffset);
                }
                if (null != _curId3PES) {
                    // store data.  will normally be in a single TS
                    _curId3PES.writeBytes(_data, _data.position, todo);
                    PES pes = new PES(_curId3PES, false);
                    if (pes.len != 0 && (pes.data.getLength() - pes.payload - pes.payload_len) >= 0) {
                        {
                            logger.debug("_parseTSPacket " + parseCount + "," +"TS: complete ID3 PES found, parse it");
                        }
                        // complete PES, parse and push into the queue
                        _parseID3PES(pes);
                        _curId3PES = null;
                    } else {
                        {
                            logger.debug("_parseTSPacket " + parseCount + "," +"TS: partial ID3 PES");
                        }
                        _curId3PES.position = _curId3PES.getLength();
                    }
                } else {
                    // just to avoid compilation warnings if is false
                    {
                        logger.warn("_parseTSPacket " + parseCount + "," +"TS: Discarding ID3 packet with id " + pid + " bad TS segmentation ?");
                    }
                }
            }
        } else if (pid == _avcId) {
            if (_pmtParsed) {
                int _curVideoBufferPos = 0;
                if (0 != stt) {
                    if (null != _curVideoPES) {
                        //
                        totalVideoLength += _curVideoPES.bytesAvailable;
                        _curVideoBufferPos = _curVideoPES.bufferOffset + (int) _curVideoPES.bytesAvailable;
                        if (_curVideoBufferPos >= videoBuffer.buffers.length - 128 * 1024) {
                            logger.debug("_parseTSPacket " + parseCount + "," +"缓冲区满了！" + _curVideoBufferPos + ",_data.position=" + _data.position);
                            _curVideoBufferPos = 0;
                        }
                        try {
/*
                                logger.debug("To process AVCPES:current size "+_curVideoPES.position+" bytes,"+
                                        String.format("position=%d(0x%X)",_curVideoPES.bufferOffset,
                                                _curVideoPES.bufferOffset)+",packetNo="+
                                        (_curVideoPES.bufferOffset+187)/188+","+String.format("prefix=0x%X%X%X%X",
                                        _curVideoPES.buffers[0],_curVideoPES.buffers[1],
                                                _curVideoPES.buffers[2],_curVideoPES.buffers[3]));
*/
                            //logger.debug("videoPES:_curVideoPES.bufferOffset="+_curVideoPES.bufferOffset+",bytesAvailable="+_curVideoPES.bytesAvailable+",_data.position="+_data.position);
                            _parseAVCPES(new PES(_curVideoPES, false));
                            //logger.debug("videoPES:_curVideoPES.bufferOffset="+_curVideoPES.bufferOffset+",bytesAvailable="+_curVideoPES.bytesAvailable+",_data.position="+_data.position);
                        } catch (Exception e) {
                            logger.error("_parseTSPacket " + parseCount + "," +"To process AVCPES failed:current size " + _curVideoPES.position + " bytes,position=" +
                                    _curVideoPES.bufferOffset + "(0x" +
                                    String.format("%X", _curVideoPES.bufferOffset) + "),packetNo=" + (_curVideoPES.bufferOffset + 187) / 188
                                    + "," + String.format("prefix=0x%X%X%X%X", _curVideoPES.buffers[0], _curVideoPES.buffers[1],
                                    _curVideoPES.buffers[2], _curVideoPES.buffers[3]));
                            e.printStackTrace();
                        }
                    }
                    _curVideoPES = new ByteArray(videoBuffer, _curVideoBufferPos);//
                    //_curVideoPES.bufferOffset = _data.position;
                }
                if (null != _curVideoPES) {
/*
                        if(_curVideoPES.position==0){
                            if(_data.buffers[_data.position]==0xff&&_data.buffers[_data.position+1]==0xff){
                                logger.warn("TS:video packet will error.It is not start with 0x01E0:_data.position="+
                                        _data.position+",packetNo="+(_data.position+187)/188);
                            }
                        }
*/
                    _curVideoPES.writeBytes(_data, _data.position, todo);
                    //logger.debug("_curVideoPES.buffer=\n"+bufferToHex(_curVideoPES.buffers,_curVideoPES.bufferOffset,32));
                } else {
                    {
                        logger.warn("_parseTSPacket " + parseCount + "," +"TS: Discarding video packet with id " + pid + " bad TS segmentation ?");
                    }
                }
            }
        } else if (pid == SDT_ID) {

        } else {
            _packetsBeforePMT = true;
        }
        // Jump to the next packet.
        _data.position += todo;
    }

    ;

    /**
     * Parse the Program Association Table.
     **/
    private int _parsePAT(int stt) {
        int pointerField = 0;
        if (0 != stt) {
            pointerField = _data.readUnsignedByte();
            // skip alignment padding
            _data.position += pointerField;
        }
        // skip table id
        _data.position += 1;
        // get section length
        int sectionLen = _data.readUnsignedShort() & 0x3FF;
        // Check the section length for a single PMT.
        if (sectionLen > 13) {
            throw new Error("_parsePAT " + parseCount + "," +"TS: Multiple PMT entries are not supported.");
        }
        // Grab the PMT ID.
        _data.position += 7;
        _pmtId = _data.readUnsignedShort() & 8191;
        return 13 + pointerField;
    }

    ;

    /**
     * Read the Program Map Table.
     **/
    private int _parsePMT(int stt) {
        int pointerField = 0;

        /** audio Track List */
        Vector<AudioTrack> audioList = new Vector<AudioTrack>();

        if (0 != stt) {
            pointerField = _data.readUnsignedByte();
            // skip alignment padding
            _data.position += pointerField;
        }
        // skip table id
        _data.position += 1;
        // Check the section length for a single PMT.
        int len = _data.readUnsignedShort() & 0x3FF;
        int read = 13;
        _data.position += 7;
        // skip program info
        int pil = _data.readUnsignedShort() & 0x3FF;
        _data.position += pil;
        read += pil;
        // Loop through the streams in the PMT.
        while (read < len) {
            // stream type
            int typ = _data.readByte();
            // stream pid
            int sid = _data.readUnsignedShort() & 0x1fff;
            if (typ == 0x0F) {
                // ISO/IEC 13818-7 ADTS AAC (MPEG-2 lower bit-rate audio)
                audioList.add(new AudioTrack("_parsePMT " + parseCount + "," +"TS/AAC " + audioList.size(), AudioTrack.FROM_DEMUX, sid, (audioList.size() == 0), true));
            } else if (typ == 0x1B) {
                // ITU-T Rec. H.264 and ISO/IEC 14496-10 (lower bit-rate video)
                _avcId = sid;
                {
                    logger.debug("_parsePMT " + parseCount + "," +"TS: Selected video PID: " + _avcId);
                }
            } else if (typ == 0x03 || typ == 0x04) {
                // ISO/IEC 11172-3 (MPEG-1 audio)
                // or ISO/IEC 13818-3 (MPEG-2 halved sample rate audio)
                audioList.add(new AudioTrack("_parsePMT " + parseCount + "," +"TS/MP3 " + audioList.size(), AudioTrack.FROM_DEMUX, sid, (audioList.size() == 0), false));
            } else if (typ == 0x15) {
                // ID3 pid
                _id3Id = sid;
                {
                    logger.debug("_parsePMT " + parseCount + "," +"TS: Selected ID3 PID: " + _id3Id);
                }
            }
            // es_info_length
            int sel = _data.readUnsignedShort() & 0xFFF;
            _data.position += sel;
            // loop to next stream
            read += sel + 5;
        }

        if (audioList.size() != 0) {
            {
                logger.debug("_parsePMT " + parseCount + "," +"TS: Found " + audioList.size() + " audio tracks");
            }
        }
        // provide audio track List to audio select callback. this callback will return the selected audio track
        int audioPID;
        AudioTrack audioTrack = _callback_audioselect(audioList);
        if (null != audioTrack) {
            audioPID = audioTrack.id;
            _audioIsAAC = audioTrack.isAAC;
            {
                logger.debug("_parsePMT " + parseCount + "," +"TS: selected " + (_audioIsAAC ? "AAC" : "MP3") + " PID: " + audioPID);
            }
        } else {
            audioPID = -1;
            {
                logger.debug("_parsePMT " + parseCount + "," +"TS: no audio selected");
            }
        }
        // in case audio PID change, flush any partially parsed audio PES packet
        if (audioPID != _audioId) {
            _curAudioPES = null;
            _adtsFrameOverflow = null;
            _audioId = audioPID;
        }
        return len + pointerField;
    }

    protected static char hexDigits[] = {
            '0', '1', '2', '3',
            '4', '5', '6', '7',
            '8', '9', 'A', 'B',
            'C', 'D', 'E', 'F'
    };

    private static void appendHexPair(byte bt, StringBuffer stringbuffer) {
        char c0 = hexDigits[(bt & 0xf0) >> 4];
        char c1 = hexDigits[bt & 0xf];
        stringbuffer.append(c0);
        stringbuffer.append(c1);
    }

    public static String bufferToHex(byte bytes[]) {
        return bufferToHex(bytes, 0, bytes.length);
    }

    public static String bufferToHex(byte bytes[], int start, int length) {
        StringBuffer stringbuffer = new StringBuffer(2 * length);
        int k = start + length;
        int idx = 0;
        for (int l = start; l < k; l++) {
            if (idx % 16 == 0 && idx != 0) {
                stringbuffer.append("\r\n");
            } else if (idx % 8 == 0 && idx != 0) {
                stringbuffer.append(" ");
            }
            idx++;
            appendHexPair(bytes[l], stringbuffer);
        }
        return stringbuffer.toString();
    }

    /* callback functions for audio selection, and parsing progress/complete */
    private AudioTrack _callback_audioselect(Vector<AudioTrack> tracks) {
        if (tracks != null && tracks.size() > 0) {
            return tracks.get(0);
        }
        return null;
    }

    ;

    private void _callback_progress(Vector<FLVTag> tags) {
        int audioTagCount = 0;
        int videoTagCount = 0;
        int unknownTagCount = 0;
        for (FLVTag tag : tags) {
            switch (tag.type) {
                case FLVTag.AAC_HEADER:
                case FLVTag.AAC_RAW:
                    audioTagCount++;
                    break;
                case FLVTag.AVC_HEADER:
                case FLVTag.AVC_NALU:
                    videoTagCount++;
                    break;
                default:
                    unknownTagCount++;
                    break;
            }
            Vector<FLVTag.TagData> pointers = tag.pointers;
        }
        logger.debug("_callback_progress " + parseCount + "," +"TS:videoTagCount=" + videoTagCount + ",audioTagCount=" + audioTagCount + ",unknownTagCount=" + unknownTagCount);
    }

    private void _callback_complete() {
        logger.debug("_callback_complete " + parseCount + "," +"TS:complete!");
        decoder.finished();
    }

    int width = -1;
    int height = -1;

    private void _callback_videometadata(int width, int height) {
        logger.debug("_callback_videometadata " + parseCount + "," +"TS:videoMetadata:" + width + "x" + height);
        if (this.width != width || this.height != height) {
            this.width = width;
            this.height = height;
            decoder.onVideoSizeChanged(width, height);
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }
}
