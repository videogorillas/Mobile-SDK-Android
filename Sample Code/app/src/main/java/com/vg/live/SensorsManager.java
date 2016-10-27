package com.vg.live;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;

import com.vg.android.Log;

import rx.Observable;
import rx.subscriptions.Subscriptions;

public class SensorsManager {
    private final static String TAG = Log.tag(SensorsManager.class);

    public static Observable<Location> locationUpdates(LocationManager lm) {
        return Observable.create(o -> {
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    o.onNext(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                    Log.d(TAG, "onStatusChanged " + provider + " " + status + " " + extras);
                }

                @Override
                public void onProviderEnabled(String provider) {
                    Log.d(TAG, "onProviderEnabled " + provider);
                }

                @Override
                public void onProviderDisabled(String provider) {
                    Log.d(TAG, "onProviderDisabled " + provider);
                }
            };
            o.add(Subscriptions.create(() -> {
                for (String provider : lm.getProviders(false)) {
                    lm.removeUpdates(listener);
                }
            }));


            for (String provider : lm.getProviders(false)) {
                Log.d(TAG, "requestLocationUpdates " + provider);
                lm.requestLocationUpdates(provider, 1000, 1f, listener, Looper.getMainLooper());
            }
        });
    }

    public static LocationManager getLocationManager(Context context) {
        return (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

}
