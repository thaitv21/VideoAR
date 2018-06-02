package com.esp.videoar;


public enum MediaType {

    ON_TEXTURE(0),
    FULLSCREEN(1),
    ON_TEXTURE_FULLSCREEN(2),
    UNKNOWN(3);

    private int type;


    MediaType(int i) {
        this.type = i;
    }


    public int getNumericType() {
        return type;
    }

}
