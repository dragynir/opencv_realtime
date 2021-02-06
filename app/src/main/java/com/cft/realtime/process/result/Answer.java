package com.cft.realtime.process.result;

import com.google.gson.Gson;

public class Answer {
    public Answer(int status) {
        this.status = status;
    }

    public int status;
    public Result_OCR value;
    public Result_OCR serial;
    public Result_OCR vertical;
    public String name;
    public String model;
    public int fraction;
    public int tariff = 0;


    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}