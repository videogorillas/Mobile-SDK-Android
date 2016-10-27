package com.vg.live;

public class DebugJs {
    public static final String DEBUG_JS = "debug.js";
    public static final String DEBUG_JS_GZ = "debug.js.gz";

    public String url;
    public String dcim;
    public byte[] statusArray;
    
    //strip 3 aac frames from every mp4 file (aac decoder reset issue)
    public boolean stripaac;

    public static DebugJs nostripaac() {
        DebugJs d = new DebugJs();
        d.stripaac = false;
        return d;
    }
}