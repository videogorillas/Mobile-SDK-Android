package com.vg.android;

import java.io.Writer;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

public class Log extends com.vg.util.Log {
    static {
        com.vg.util.Log.INSTANCE = new com.vg.android.Log();
    }

    public static void init() {
    }

    @Override
    public void setWriter(Writer writer) {
        logSize.set(0);
        super.setWriter(writer);
    }


    protected boolean verboseLogException(Throwable cause) {
        if (cause == null) {
            return false;
        }
        if (cause instanceof ExecutionException) {
            if (cause.getCause() == null) {
                return true;
            }
            cause = cause.getCause();
        }
        return !(cause instanceof TimeoutException || cause instanceof SocketTimeoutException || cause instanceof SocketException || cause instanceof java.io.InterruptedIOException);
    }

    public void log(int level, String tag, String msg, Throwable t) {
        if (level >= getLevel(tag)) {
            super.log(level, tag, msg, t);
            android.util.Log.println(level, tag, msg);
            if (t != null && verboseLogException(t)) {
                android.util.Log.println(level, tag, t.toString());
                do {
                    StackTraceElement[] stackTrace = t.getStackTrace();
                    for (StackTraceElement stackTraceElement : stackTrace) {
                        android.util.Log.println(level, tag, "    " + stackTraceElement.toString());
                    }
                    t = t.getCause();
                    if (t != null) {
                        android.util.Log.println(level, tag, "Caused by " + t.toString());
                    }
                } while (t != null);
            }
        }
    }

    private final AtomicLong logSize = new AtomicLong(0);

    private boolean replacingLog = false;

    @Override
    protected String _tag(Class c) {
        return "LIVE4" + c.getSimpleName();
    }

}
