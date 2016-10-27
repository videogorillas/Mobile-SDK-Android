package com.vg.live.model;

import android.location.Location;

import com.vg.util.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;

import rx.functions.Func1;

public class StreamLocation {
    public static final String LOCATIONS_JS = "locations.js";
    public double altitude; //": 171.9877166748047,
    public double course; //": -1,
    public double horizontalAccuracy;//": 65,
    public double latitude; //": 34.15291825095102,
    public double longitude;//": -118.3410521886585,
    public double speed; //": -1,
    public String timestamp; //": "2014-07-15T11:32:57-0700", or "1405449177000"
    public double verticalAccuracy; //": 31.86548475470511

    public static StreamLocation fromLocation(Location location) {
        StreamLocation s = new StreamLocation();
        s.altitude = location.getAltitude();
        s.course = location.getBearing();
        s.horizontalAccuracy = location.getAccuracy();
        s.latitude = location.getLatitude();
        s.longitude = location.getLongitude();
        s.speed = location.getSpeed();
        long time = location.getTime();
        SimpleDateFormat sdf = Utils.locationDateFormat();
        s.timestamp = sdf.format(new Date(time));
        s.verticalAccuracy = location.getAccuracy();
        return s;
    }

    public static final Func1<StreamLocation, Boolean> accurateLocations = l -> l.speed >= 5
            || l.horizontalAccuracy <= 20;

    public static String locationsFilename(int mseq) {
        if (mseq == 0) {
            return LOCATIONS_JS;
        }
        return "locations" + mseq + ".js";
    }
}
