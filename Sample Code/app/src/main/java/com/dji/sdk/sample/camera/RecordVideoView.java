package com.dji.sdk.sample.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.common.BaseThreeBtnView;
import com.dji.sdk.sample.common.DJISampleApplication;
import com.dji.sdk.sample.common.Utils;
import com.dji.sdk.sample.utils.DJIModuleVerificationUtil;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.vg.android.Log;
import com.vg.live.model.StreamLocation;
import com.vg.live.StreamId;
import com.vg.live.Uploader;
import com.vg.util.Pair;

import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.UUID;

import dji.common.camera.DJICameraSettingsDef;
import dji.common.error.DJIError;
import dji.common.flightcontroller.DJIFlightControllerCurrentState;
import dji.common.flightcontroller.DJILocationCoordinate3D;
import dji.common.util.DJICommonCallbacks;
import dji.sdk.flightcontroller.DJICompass;
import dji.sdk.flightcontroller.DJIFlightController;
import dji.sdk.products.DJIAircraft;
import rx.Observable;
import rx.Subscription;
import rx.subscriptions.Subscriptions;

import static com.dji.sdk.sample.common.Utils.checkGpsCoordinate;
import static com.github.davidmoten.rx.RetryWhen.exponentialBackoff;
import static com.vg.live.DebugJs.DEBUG_JS;
import static com.vg.live.DebugJs.nostripaac;
import static com.vg.live.SensorsManager.getLocationManager;
import static com.vg.live.SensorsManager.locationUpdates;
import static com.vg.live.Uploader.uploadJsonRequest;
import static com.vg.util.Utils.gsonToString;
import static com.vg.util.Utils.locationDateFormat;
import static com.vg.util.Utils.requestOnlyHeaders;
import static java.lang.Math.max;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Created by dji on 16/1/6.
 */
public class RecordVideoView extends BaseThreeBtnView {

    private static final String TAG = Log.tag(RecordVideoView.class);
    Timer timer = new Timer();
    private final Context context;
    private long timeCounter = 0;
    private long hours = 0;
    private long minutes = 0;
    private long seconds = 0;
    private DJIFlightController mFlightController;
    private DJICompass mCompass;
    private Subscription subscribe;

    public RecordVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        middleBtn.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (DJIModuleVerificationUtil.isCameraModuleAvailable()) {
            DJISampleApplication.getProductInstance().getCamera().setCameraMode(
                    DJICameraSettingsDef.CameraMode.RecordVideo,
                    new DJICommonCallbacks.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            Utils.setResultToToast(getContext(), "SetCameraMode to recordVideo");
                        }
                    }
            );
        }
    }

    protected void onDetachedToWindow() {
        super.onDetachedFromWindow();

        if (DJIModuleVerificationUtil.isCameraModuleAvailable()) {
            DJISampleApplication.getProductInstance().getCamera().setCameraMode(
                    DJICameraSettingsDef.CameraMode.ShootPhoto,
                    new DJICommonCallbacks.DJICompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            Utils.setResultToToast(getContext(), "SetCameraMode to shootPhoto");
                        }
                    }
            );
        }
    }

    @Override
    protected int getLeftBtnTextResourceId() {
        return R.string.record_video_start_record;
    }

    @Override
    protected int getRightBtnTextResourceId() {
        return R.string.record_video_stop_record;
    }

    @Override
    protected int getMiddleBtnTextResourceId() {
        return R.string.shoot_single_photo;
    }

    @Override
    protected int getInfoResourceId() {
        return R.string.record_video_initial_time;
    }

    @Override
    protected void getLeftBtnMethod() {

        Utils.setResultToText(context, mTexInfo, "00:00:00");
        if (DJIModuleVerificationUtil.isFlightControllerAvailable()) {
            StreamId sid = StreamId.sid("ksidemoLocationsOnly", UUID.randomUUID().toString());
            OkHttpClient client = Uploader.newUploaderClient();

            mFlightController = ((DJIAircraft) DJISampleApplication.getProductInstance()).getFlightController();
            if (DJIModuleVerificationUtil.isCompassAvailable()) {
                mCompass = mFlightController.getCompass();
            }
            Observable<DJIFlightControllerCurrentState> stateRx = Observable.create(o -> {
                o.add(Subscriptions.create(() -> mFlightController.setUpdateSystemStateCallback(null)));
                mFlightController.setUpdateSystemStateCallback(state -> o.onNext(state));
            });

            stateRx = stateRx.filter(state -> checkGpsCoordinate(state.getAircraftLocation().getLatitude(), state.getAircraftLocation().getLongitude()));
            Observable<StreamLocation> droneLocations = stateRx.map(state -> {
                StreamLocation loc = new StreamLocation();
                DJILocationCoordinate3D drone = state.getAircraftLocation();
                loc.altitude = drone.getAltitude();
                loc.latitude = drone.getLatitude();
                loc.longitude = drone.getLongitude();
                loc.course = (mCompass != null) ? mCompass.getHeading() : -1;
                loc.horizontalAccuracy = 0;
                loc.verticalAccuracy = 0;
                loc.speed = max(max(state.getVelocityX(), state.getVelocityY()), state.getVelocityZ());
                loc.timestamp = locationDateFormat().format(new Date());
                return loc;
            }).share();

            Observable<StreamLocation> phoneLocations = locationUpdates(getLocationManager(getContext())).map(StreamLocation::fromLocation).takeUntil(droneLocations);

            Observable<StreamLocation> locationsRx = droneLocations.startWith(phoneLocations);

            Observable<List<StreamLocation>> chunks = locationsRx.buffer(2, SECONDS).filter(list -> !list.isEmpty());
            Observable<Pair<Integer, List<StreamLocation>>> numberedChunks = chunks.zipWith(Observable.range(0, Integer.MAX_VALUE), (locations, mseq) -> Pair.of(mseq, locations));
            Observable<Request> requests = numberedChunks.map(p -> {
                int mseq = p.left;
                List<StreamLocation> locations = p.right;
                String filename = StreamLocation.locationsFilename(mseq);
                long mtime = System.currentTimeMillis();
                String json = gsonToString(singletonMap("locations", locations));
                Request request = uploadJsonRequest(sid, filename, mtime, json);
                return request;
            });

            Request debugjs = uploadJsonRequest(sid, DEBUG_JS, System.currentTimeMillis(), gsonToString(nostripaac()));

            Observable<Response> uploads = requests.startWith(debugjs).onBackpressureBuffer().concatMap(request -> {
                return requestOnlyHeaders(client, request).retryWhen(exponentialBackoff(100, MILLISECONDS).build());
            });

            this.subscribe = uploads.subscribe(response -> {
                Log.i(TAG, "ok " + response.request().urlString());
                Utils.setResultToText(this.context, mTexInfo, response.request().urlString());
            }, err -> {
                Log.e(TAG, "unhandled error " + err, err);
                Utils.setResultToText(this.context, mTexInfo, "ERROR: " + err.toString());
            });


        }
    }

    @Override
    protected void getRightBtnMethod() {
        if (subscribe != null) {
            subscribe.unsubscribe();
        }
    }

    @Override
    protected void getMiddleBtnMethod() {

    }
}
