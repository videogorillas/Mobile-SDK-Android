package com.vg.live;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.vg.util.Utils;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

public class Uploader {
    private final static MediaType JSON_MIMETYPE = MediaType.parse("application/json");
    private final static MediaType OCTET_STREAM = MediaType.parse("application/octet-stream");

    public static final String LAST_MODIFIED = "Last-Modified";

    public static Request uploadJsonRequest(StreamId sid, String filename, long mtime, String json) {
        String buildUrl = String.format("http://qa.live4.io/gopro/%s/%s/%s", sid.userId, sid.streamId, filename);
        Request.Builder builder = new Request.Builder()
                .header(LAST_MODIFIED, Utils.httpDateFormat(mtime));
        try {
            builder.url(buildUrl + ".gz").post(RequestBody.create(OCTET_STREAM, gzip(json)));
        } catch (IOException e) {
            builder.url(buildUrl).post(RequestBody.create(JSON_MIMETYPE, json));
        }

        return builder.build();
    }

    private static byte[] gzip(String json) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        GZIPOutputStream gz = new GZIPOutputStream(os);
        gz.write(json.getBytes());
        gz.finish();
        gz.close();
        return os.toByteArray();
    }

    public static OkHttpClient newUploaderClient() {
        return new OkHttpClient();
    }
}
