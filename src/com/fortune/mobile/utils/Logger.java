package com.fortune.mobile.utils;

import android.util.Log;

/**
 * Created by xjliu on 2016/2/2.
 *
 */
public class Logger {
    private String TAG;
    public static Logger getLogger(Class cls){
        return getLogger(cls.getSimpleName());
    }
    public static Logger getLogger(String tag){
        return new Logger(tag);
    }
    private Logger(String tag){
        this.TAG = tag;
    }
    public void debug(String msg){
        Log.d(TAG,msg);
    }
    public void error(String msg){
        Log.e(TAG,msg);
    }
    public void warn(String msg){
        Log.w(TAG,msg);
    }
}
