package com.vg.util;

import java.io.PrintWriter;
import java.io.Writer;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class Log {
    public final static int ALL = 1;
    public final static int VERBOSE = 2;
    public final static int DEBUG = 3;
    public final static int INFO = 4;
    public final static int WARN = 5;
    public final static int ERROR = 6;
    public final static int ASSERT = 7;

    private final AtomicReference<Writer> writer = new AtomicReference<Writer>(new PrintWriter(System.out));

    public static void replaceWriter(Writer writer) {
        Writer oldWriter = INSTANCE.getWriter();
        INSTANCE.setWriter(writer);
        Utils.closeQuietly(oldWriter);
    }

    public void setWriter(Writer writer) {
        INSTANCE.writer.set(writer);
    }

    public Writer getWriter() {
        return INSTANCE.writer.get();
    }

    protected static Log INSTANCE = new Log();

    public static void v(String tag, String msg) {
        log(VERBOSE, tag, msg);
    }

    public static void d(String tag, String msg) {
        log(DEBUG, tag, msg);
    }

    public static void w(String tag, String msg) {
        log(WARN, tag, msg);
    }

    public static void i(String tag, String msg) {
        log(INFO, tag, msg);
    }

    public static void e(String tag, String msg) {
        log(ERROR, tag, msg);
    }

    public static void e(String tag, String msg, Throwable e) {
        INSTANCE.log(ERROR, tag, msg, e);
    }

    private final static String[] levelStrings = new String[8];

    static {
        levelStrings[0] = " ? ";
        levelStrings[1] = " ? ";
        levelStrings[VERBOSE] = " V ";
        levelStrings[DEBUG] = " D ";
        levelStrings[INFO] = " I ";
        levelStrings[WARN] = " W ";
        levelStrings[ERROR] = " E ";
        levelStrings[ASSERT] = " ! ";
    }

    private final static ConcurrentMap<String, Integer> levels = new ConcurrentHashMap<String, Integer>();

    public static void setLevel(String TAG, int level) {
        levels.put(TAG, level);
    }

    public static int getLevel(String TAG) {
        Integer integer = levels.get(TAG);
        if (integer == null) {
            return VERBOSE;
        } else {
            return integer.intValue();
        }
    }

    public static void log(int level, String tag, String msg) {
        INSTANCE.log(level, tag, msg, null);
    }

    public void log(int level, String tag, String msg, Throwable t) {
        if (level < getLevel(tag)) {
            return;
        }
        Writer w = writer.get();
        if (w != null) {
            String date = date();
            StringBuilder sb = new StringBuilder(128);
            sb.append(date);
            sb.append(levelStrings[level]);
            sb.append(' ');
            sb.append(Thread.currentThread().getName());
            sb.append(' ');
            sb.append(tag);
            sb.append(' ');
            String prefix = t != null ? sb.toString() : null;
            sb.append(msg);
            sb.append('\n');
            if (t != null && verboseLogException(t)) {
                printStackTrace(prefix, sb, t);
            }
            try {
                String str = sb.toString();
                w.write(str);
                w.flush();
                onMessageWritten(str);
            } catch (Exception e) {
                onFailure(e);
            }
        }
    }

    protected void onMessageWritten(String msg) {
    }

    protected void onFailure(Throwable cause) {
        cause.printStackTrace();
    }

    private void printStackTrace(String prefix, StringBuilder sb, Throwable t) {
        sb.append(prefix).append(t).append('\n');
        do {
            StackTraceElement[] stackTrace = t.getStackTrace();
            for (StackTraceElement stackTraceElement : stackTrace) {
                sb.append(prefix).append("    ").append(stackTraceElement.toString()).append('\n');
            }
            t = t.getCause();
            if (t != null) {
                sb.append(prefix).append("Caused by ").append(t).append('\n');
            }
        } while (t != null);
    }

    protected static String date() {
        return dftl.get().format(new Date());
    }

    private final static ThreadLocal<SimpleDateFormat> dftl = new ThreadLocal<SimpleDateFormat>() {
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US);
        };
    };

    protected String _tag(Class c) {
        return c.getSimpleName();
    }

    public static String tag(Class c) {
        return INSTANCE._tag(c);
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
        return !(cause instanceof TimeoutException || cause instanceof SocketTimeoutException
                || cause instanceof SocketException || cause instanceof java.io.InterruptedIOException);
    }
}
