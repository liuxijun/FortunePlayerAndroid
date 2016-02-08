package com.fortune.mobile.utils;

import android.util.Log;

import java.util.Date;
import java.util.logging.Level;

/**
 * Created by xjliu on 2016/2/2.
 *
 */
public class Logger {
    private String TAG;
    private static boolean isAndroid = true;
    private java.util.logging.Logger logger;
    public static Logger getLogger(Class cls){
        return getLogger(cls.getSimpleName());
    }
    public static Logger getLogger(String tag){
        return new Logger(tag);
    }

    public void setAndroid(boolean isAndroid){
        this.isAndroid = isAndroid;
    }
    private Logger(String tag){
        this.TAG = tag;
        if(!isAndroid){
            logger = java.util.logging.Logger.getLogger(TAG);
        }
    }
    public void debug(String msg){
        if(isAndroid){
            Log.d(TAG,msg);
        }else{
            logger.log(Level.INFO,StringUtils.date2string(new Date())+" "+TAG+" - "+msg);
        }
    }
    public void error(String msg){
        if(isAndroid){
            Log.e(TAG, msg);
        }else{
            logger.log(Level.WARNING,StringUtils.date2string(new Date())+" "+TAG+" - "+msg);
        }
    }
    public void warn(String msg){
        if(isAndroid){
            Log.w(TAG, msg);
        }else{
            logger.log(Level.WARNING,StringUtils.date2string(new Date())+" "+TAG+" - "+msg);
        }
    }
}
