package com.fortune.mobile.media.demuxer;

/**
 * Created by xjliu on 2016/1/14.
 *
 */
public class ExpGolomb {
    private ByteArray _data;
    private int _bit;
    private int _curByte ;

    public ExpGolomb(ByteArray data) {
        _data = data;
        _bit = -1;
    }

    private int _readBit() {
        int res;
        if (_bit == -1) {
            // read next
            _curByte = _data.readByte();
            _bit = 7;
        }
        res = (_curByte & (1 << _bit))==0 ? 0 : 1;
        _bit--;
        return res;
    }

    public boolean readBoolean() {
        return (_readBit() == 1);
    }

    public int readBits(int nbBits)  {
        int val  = 0;
        for (int i  = 0; i < nbBits; ++i) {
            val = (val << 1) + _readBit();
        }
        return val;
    }

    public int readUE()  {
        int nbZero = 0;
        while (_readBit() == 0) {
            nbZero++;
        }
        int x  = readBits(nbZero);
        return x + (1 << nbZero) - 1;
    }

    public int readSE()  {
        int value  = readUE();
        value = (value & 1) == 0 ? value>>>1 : -(value>>>1);
        return value;
/*
        // the number is odd if the low order bit is set
        if (0!=(0x01 & value)) {
            // add 1 to make it even, and divide by 2
            return (1 + value) >> 1;
        } else {
            // divide by two then make it negative
            return -1 * (value >> 1);
        }
*/
    }


}
