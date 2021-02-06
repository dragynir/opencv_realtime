package com.cft.realtime.process.utils;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

public class MyTimeStamp {

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd|HH:mm:ss|");
    public static String timestamp(){
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        return sdf.format(timestamp);
    }
}
