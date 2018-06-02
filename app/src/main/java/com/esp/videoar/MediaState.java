package com.esp.videoar;

public enum MediaState {
    REACHED_END(0),
    PAUSED(1),
    STOPPED(2),
    PLAYING(3),
    READY(4),
    NOT_READY(5),
    ERROR(6);

    private int type;


    MediaState(int i) {
        this.type = i;
    }


    public int getNumericType() {
        return type;
    }
}
