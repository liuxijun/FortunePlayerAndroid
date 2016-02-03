package com.fortune.mobile.media.demuxer;


import com.fortune.mobile.utils.Logger;

/**
 * Created by xjliu on 2016/1/14.
 *
 */
public class ByteArray {
    protected Logger logger = Logger.getLogger(getClass().getName());
    protected long offset;
    protected long bytesAvailable;
    protected int type;
    protected long readed;
    protected byte[] buffers;
    protected int bufferOffset;
    protected int position;
    public int getBufferOffset(){
        return bufferOffset;
    }
    public ByteArray(int length){
        bufferOffset = 0;
        offset = 0;
        readed = 0;
        position = 0;
        buffers = new byte[length];
        bytesAvailable = 0;
    }
    public ByteArray(ByteArray buffer,int bufferOffset){
        this(buffer.buffers,bufferOffset);
        buffer.position = bufferOffset;
        if(buffer.bytesAvailable<bufferOffset){
           buffer.bytesAvailable = bufferOffset;
        }
    }
    public ByteArray(byte[] buffers,int bufferOffset){
        this.buffers = buffers;
        this.bufferOffset = bufferOffset;
        this.offset = 0;
        this.readed = 0;
        this.position = 0;
        this.bytesAvailable = 0;
    }
    /*
    public static String intToType(int type) {
        StringBuilder st = new StringBuilder();
        st.append((char) ((type >> 24) & 0xff));
        st.append((char) ((type >> 16) & 0xff));
        st.append((char) ((type >> 8) & 0xff));
        st.append((char) (type & 0xff));
        return st.toString();
    }


    public static int typeToInt(String type) {
        return (type.charAt(0) << 24) + (type.charAt(1) << 16) + (type.charAt(2) << 8) + type.charAt(3);
    }
*/
    public long getOffset() {
        return offset;
    }

    public void setOffset(long offset) {
        this.offset = offset;
    }

    public long getBytesAvailable() {
        return bytesAvailable;
    }

    public void setBytesAvailable(long bytesAvailable) {
        this.bytesAvailable = bytesAvailable;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getReaded() {
        return readed;
    }

    public void setReaded(long readed) {
        this.readed = readed;
    }

    public void init(long offset, long size,int type,long readed, byte[] buffers, int bufferPos) {
        this.offset = offset;
        this.bytesAvailable = readed;
        this.readed = readed;
        this.buffers = buffers;
        this.bufferOffset = bufferPos;
        this.type = type;
        position = (int) readed;
        initSelf();
    }

    public void reset() {
        position = 0;
    }

    public void skip(int step) {
        position += step;
    }

    public int readBytes(ByteArray toArray,int pos,int length){
        toArray.position = writeByteArray(toArray.buffers,toArray.bufferOffset+toArray.position,buffers,bufferOffset+pos,length)-toArray.bufferOffset;
        if(toArray.position>toArray.bytesAvailable) {
            toArray.bytesAvailable = toArray.position;
        }
        return toArray.position;
    }
    public long readBytes(int size) {
        long result = 0;
        if (bufferOffset+position + size <= buffers.length) {
            int i = 0;
            while (i < size) {
                result <<= 8;
                result += (buffers[i + position+bufferOffset] & 0xff);
                i++;
            }
            position += size;
        }
        return result;
    }

    public long read_64() {
        long highPart = read_32();

        return (highPart << 32) + read_32();
    }

    public long readUnsignedInt(){
        return read_32();
    }
    public long read_32() {
        return readBytes(4);
    }

    public int read_24() {
        return (int) readBytes(3);
    }

    public byte[] readByteArray(int maxLength){
        if (bufferOffset+position + maxLength >= buffers.length) {
            maxLength = buffers.length-(bufferOffset+position);
        }
        position += maxLength;
        byte[] result = new byte[maxLength];
        System.arraycopy(buffers,(position -maxLength),result,0,maxLength);
        return result;
    }

    public String readString(int maxLength){
        int strLength = 0;
        if (bufferOffset+position <= buffers.length) {
            while (strLength < maxLength) {
                if(buffers[strLength + position+bufferOffset]==0){
                    break;
                }
                strLength ++;
            }
            position += maxLength;
        }
        if(strLength==0){
            return "";
        }
        byte[] result = new byte[strLength];
        try {
            System.arraycopy(buffers,(bufferOffset+position -maxLength),result,0,result.length);
            return new String(result, "GBK");
        } catch (Exception e) {
            logger.warn("新建字符串时发生异常：" + e.getMessage());
            return new String(result);
        }
    }
    public int readUnsignedShort(){
        int result= read_16();
        if(result<0){

        }
        return result;
    }
    public int read_16() {
        return (int) readBytes(2);
    }

    public int readUnsignedByte(){
        int result= read_8();
        if(result<0){
            result +=256;
        }
        return result;
    }

    public byte readByte(){
        return read_8();
    }
    public byte read_8() {
        return buffers[bufferOffset+(position++)];
    }

    public void initSelf() {
    }

    public int writeBytes(ByteArray byteArray){
        return writeByteArray(byteArray.buffers,byteArray.bufferOffset,byteArray.getLength());
    }
    public static int writeBytes(byte[] dataBuffer, long value, int size, int startPos) {
        if (startPos + size <= dataBuffer.length) {
            int i = size;
            while (i > 0) {
                i--;
                dataBuffer[i + startPos] = (byte) (value & 0xff);
                value >>= 8;
            }
        } else {
            return -1;
        }
        return startPos + size;
    }

    public int writeByteArray(byte[] data){
        return writeByteArray(data,0,data.length);
    }
    public int writeByteArray(byte[] data,int startPos,int dataLength){
        position = writeByteArray(buffers,position+bufferOffset,data,startPos,dataLength)-bufferOffset;
        if(position>bytesAvailable){
            bytesAvailable = position;
        }
        return position;
    }

    public static int writeByteArray(byte[] toBuffer,int toPos,byte[] fromBuffer,int fromPos,int dataLength){
        int endPos = toPos+dataLength;
        if(endPos>toBuffer.length){
            dataLength = toBuffer.length-toPos;
        }
        int fromLength = fromBuffer.length-fromPos;
        if(dataLength>fromLength){
            dataLength = fromLength;
        }
        System.arraycopy(fromBuffer,fromPos,toBuffer,toPos,dataLength);
        return toPos+dataLength;
    }

    public static int writeString(byte[] dataBuffer, String value, int startPos) {
        if (value == null || "".equals(value)) {
            return startPos;
        }
        byte[] valueBytes = value.getBytes();
        if (startPos + valueBytes.length > dataBuffer.length) {
            return -1;
        }
        System.arraycopy(valueBytes, 0, dataBuffer, startPos, valueBytes.length);
        return startPos + valueBytes.length;
    }

    public static int write_64(byte[] dataBuffer, long value, int startPos) {
        return writeBytes(dataBuffer, value, 8, startPos);
    }

    public static int write_32(byte[] dataBuffer, long value, int startPos) {
        return writeBytes(dataBuffer, value, 4, startPos);
    }

    public static int write_24(byte[] dataBuffer, int value, int startPos) {
        return writeBytes(dataBuffer, value, 3, startPos);
    }

    public static int write_16(byte[] dataBuffer, int value, int startPos) {
        return writeBytes(dataBuffer, value, 2, startPos);
    }

    public int writeUnsignedInt(int value){
        return write_32(value);
    }
    public int write_32(int value){
        return writeBytes(value, 4);
    }
    public int write_24(int value){
        return writeBytes(value, 3);
    }
    public int write_16(int value){
        return writeBytes(value, 2);
    }
    public int writeByte(int value){
        return write_8(value);
    }
    public int writeBytes(int value,int size){
        position = writeBytes(buffers, value, size, bufferOffset+position)-bufferOffset;
        if(position>bytesAvailable){
            bytesAvailable = position;
        }
        return position;
    }
    public int write_8(int value){
        return writeBytes(value, 1);
    }
    public static int write_8(byte[] dataBuffer, int value, int startPos) {
        return writeBytes(dataBuffer, value, 1, startPos);
    }


    public byte[] getBuffers() {
        return buffers;
    }

    public void setBuffers(byte[] buffers) {
        this.buffers = buffers;
    }

    public int getLength(){
        if(buffers==null){
            return 0;
        }
        if(bytesAvailable>0){
            return (int) bytesAvailable;
        }
        if(position>=0){
            return position;
        }
        return 0;
    }

    public int readShort() {
        return readUnsignedShort();
    }

    public int writeBytes(ByteArray byteArray, int pos) {
        return writeBytes(byteArray, pos, byteArray.getLength() - pos);
    }

    public int writeBytes(ByteArray byteArray, int pos, int length) {
        return writeByteArray(byteArray.buffers,byteArray.bufferOffset+pos, length);
    }
    public int writeShort(int value){
        return write_16(value);
    }

    public int writeObject(Object obj) {
        if(obj==null){
            return position;
        }
        if(obj instanceof ByteArray){
            ByteArray byteArray = (ByteArray) obj;
            return writeByteArray(byteArray.buffers,byteArray.bufferOffset,byteArray.getLength());
        }else{
            position = writeString(buffers,obj.toString(),position+bufferOffset)-bufferOffset;
            if(position>bytesAvailable){
                bytesAvailable = position;
            }
            return position;
        }
    }
    protected int getAvailableBytes(){
/*
        if(readed>=position){
            return (int)readed - position;
        }
        //当前填充的数据小于当前的读取指针，那就是重头再来了
        return (int)readed+buffers.length-position;
*/
        return (int) bytesAvailable;
    }

    public String toString(){
        return TSDemuxer.bufferToHex(buffers,bufferOffset,16*4);
    }
}
