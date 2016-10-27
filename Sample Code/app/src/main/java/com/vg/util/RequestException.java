package com.vg.util;

import java.io.IOException;

import com.squareup.okhttp.Request;

public class RequestException extends IOException {
    private static final long serialVersionUID = 1L;
    private Request request;

    public RequestException(Request request, IOException cause) {
        super(cause);
        this.request = request;
    }

    public Request getRequest() {
        return request;
    }

}
