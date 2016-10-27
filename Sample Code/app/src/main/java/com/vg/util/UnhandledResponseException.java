package com.vg.util;

import com.squareup.okhttp.Response;

import java.io.IOException;

public class UnhandledResponseException extends IOException {
    private Response r;

    public UnhandledResponseException(Response r) {
        this.r = r;
    }

    public Response getResponse() {
        return r;
    }

    @Override
    public String getMessage() {
        return String.valueOf(r);
    }
}
