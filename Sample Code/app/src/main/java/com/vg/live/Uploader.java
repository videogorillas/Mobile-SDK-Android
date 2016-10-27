package com.vg.live;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.vg.util.Utils;

public class Uploader {
    private final static MediaType JSON_MIMETYPE = MediaType.parse("application/json");
    public static final String LAST_MODIFIED = "Last-Modified";

    public static Request uploadJsonRequest(StreamId sid, String filename, long mtime, String json) {
        String buildUrl = String.format("http://qa.live4.io/gopro/%s/%s/%s", sid.userId, sid.streamId, filename);

        Request request = new Request.Builder()
                .url(buildUrl)
                .post(RequestBody.create(JSON_MIMETYPE, json))
                .header(LAST_MODIFIED, Utils.httpDateFormat(mtime))
                .build();

        return request;
    }

    public static OkHttpClient newUploaderClient() {
        return new OkHttpClient();
    }
}
