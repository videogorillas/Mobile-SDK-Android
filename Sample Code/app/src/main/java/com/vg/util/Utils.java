package com.vg.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.okhttp.Connection;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okio.Buffer;
import okio.Sink;
import okio.Timeout;
import rx.Observable;

import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.apache.commons.io.filefilter.FileFilterUtils.notFileFilter;
import static org.apache.commons.io.filefilter.FileFilterUtils.prefixFileFilter;

public class Utils {
    private final static String TAG = "LIVE4Utils";

    public final static Comparator<File> FILE_CMP = (lhs, rhs) -> {
        int left = toInt(getBaseName(lhs.getName()));
        int right = toInt(getBaseName(rhs.getName()));
        if (left != right) {
            return left - right;
        } else {
            long r = rhs.lastModified();
            long l = lhs.lastModified();
            int d = Long.signum(r - l);
            return d;
        }
    };

    public static int toInt(String str) {
        return toInt(str, -1);
    }

    public static int toInt(Object object, int defaultValue) {
        if (object == null) {
            return defaultValue;
        }
        if (object instanceof Number) {
            return ((Number) object).intValue();
        } else if (object instanceof String) {
            try {
                return Integer.parseInt((String) object);
            } catch (NumberFormatException nfe) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    public static long toLong(String str) {
        if (str == null) {
            return 0;
        }
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException nfe) {
            return 0;
        }
    }

    public static final FilenameFilter NOHIDDEN = (FilenameFilter) notFileFilter(prefixFileFilter("."));

    public static byte[] toByteArrayAndClose(InputStream in) throws IOException {
        try {
            return IOUtils.toByteArray(in);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    public static String gmtDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        String date = sdf.format(new Date());
        return date;
    }

    public static SimpleDateFormat locationDateFormat() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
        return sdf;
    }

    public final static Comparator<File> NewestFirstComparator = (lhs, rhs) -> Long.signum(rhs.lastModified() - lhs.lastModified());

    public static String defaultString(String str) {
        return str != null ? str : "";
    }

    public static boolean isConnectionRefused(Exception e) {
        if (e instanceof ConnectException) {
            ConnectException ce = (ConnectException) e;
            String message = Utils.defaultString(ce.getMessage());
            return (message.contains("ECONNREFUSED"));
        }
        return false;
    }

    public static boolean isNetworkUnreachable(Exception e) {
        if (e instanceof ConnectException) {
            ConnectException ce = (ConnectException) e;
            String message = Utils.defaultString(ce.getMessage());
            return (message.contains("ENETUNREACH"));
        }
        return false;
    }

    public static boolean isBlank(CharSequence cs) {
        int strLen;
        if (cs == null || (strLen = cs.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static int skip(ByteBuffer buffer, int count) {
        int toSkip = Math.min(buffer.remaining(), count);
        buffer.position(buffer.position() + toSkip);
        return toSkip;
    }

    public static <T> List<T> list(T[] t) {
        List<T> emptyList = Collections.emptyList();
        return (t == null ? emptyList : Arrays.asList(t));
    }

    public static <T> List<T> list(List<T> list) {
        List<T> emptyList = Collections.emptyList();
        return list == null ? emptyList : list;
    }

    public static List<File> find(File file, FileFilter filter) {
        List<File> result = new ArrayList<>();
        LinkedList<File> stack = new LinkedList<>();
        stack.push(file);
        while (!stack.isEmpty()) {
            File f = stack.pop();
            if (filter == null || filter.accept(f)) {
                result.add(f);
            }

            if (f.isDirectory() && f.exists()) {
                stack.addAll(Arrays.asList(f.listFiles()));
            }
        }
        return result;
    }

    public static byte[] gzip(final File file) throws IOException {
        byte[] bytes;
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        GZIPOutputStream gz = new GZIPOutputStream(os);
        InputStream fin = new FileInputStream(file);
        try {
            IOUtils.copy(fin, gz);
        } finally {
            fin.close();
            gz.finish();
            gz.close();
        }
        bytes = os.toByteArray();
        return bytes;
    }

    public static File gzipFile(final File file) throws IOException {
        File tmpGz = File.createTempFile(file.getName(), ".gz", file.getParentFile());
        File filegz = new File(file.getParentFile(), file.getName() + ".gz");
        GZIPOutputStream gz = new GZIPOutputStream(new FileOutputStream(tmpGz));
        InputStream fin = new FileInputStream(file);
        try {
            IOUtils.copy(fin, gz);
            tmpGz.renameTo(filegz);
        } finally {
            fin.close();
            gz.finish();
            gz.close();
            tmpGz.delete();
        }
        return filegz;
    }


    public static Field getField(Object o, String name) {
        try {
            Field declaredField = o.getClass().getDeclaredField(name);
            declaredField.setAccessible(true);
            return declaredField;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    public static Object getFieldValue(Object o, String name) {
        Field field = getField(o, name);
        if (field != null) {
            try {
                if (!field.isAccessible()) {
                    field.setAccessible(true);
                }
                Object value = field.get(o);
                return value;
            } catch (Exception e) {
                Log.e(TAG, "getFieldValue " + o + " " + name + " " + e, e);
            }
        }
        return null;

    }

    public static Observable<Response> requestOnlyHeaders(final OkHttpClient client, final Request request) {
        return RxUtil.okResponseRx(client, request).doOnNext(r -> {
            if (r == null) return;
            discardBody(r);
        });
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static boolean equals(Object a, Object b) {
        return (a == null) ? (b == null) : a.equals(b);
    }

    public static URL url(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void discardBody(Throwable throwable) {
        if (throwable != null && throwable instanceof UnhandledResponseException) {
            UnhandledResponseException e = (UnhandledResponseException) throwable;
            discardBody(e.getResponse());
        }
    }

    public static void discardBody(Response response) {
        try {
            if (response != null) {
                response.body().source().readAll(DEV_NULL);
            }
        } catch (IOException e) {
            Log.e(TAG, response + " discardBody unhandled", e);
        }
    }

    public final static Sink DEV_NULL = new Sink() {

        @Override
        public void write(Buffer source, long byteCount) throws IOException {
        }

        @Override
        public Timeout timeout() {
            return null;
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public void close() throws IOException {
        }
    };

    public static int getRecycleCount(Connection connection) {
        int recycleCount = 0;
        if (connection != null) {
            try {
                Method method = connection.getClass().getDeclaredMethod("recycleCount");
                method.setAccessible(true);
                recycleCount = toInt(method.invoke(connection), 0);
            } catch (Exception e) {
            }
        }
        return recycleCount;
    }

    public static OkHttpClient newUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                        throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                        throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }
            }};

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient okHttpClient = new OkHttpClient();
            okHttpClient.setSslSocketFactory(sslSocketFactory);
            okHttpClient.setHostnameVerifier((hostname, session) -> true);

            return okHttpClient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Request GET(String url) {
        return new Request.Builder().url(url).build();
    }

    public static Request GETRange(String url, long startByte, long endByte) {
        String rangeHeader = "bytes=" + startByte + "-" + (endByte >= 0 ? endByte : "");
        Request request = new Request.Builder().url(url).addHeader("Range", rangeHeader).build();
        return request;

    }

    public static String httpDateFormat(long mtime) {
        return httpDateFormat().format(new Date(mtime));
    }

    public static SimpleDateFormat httpDateFormat() {
        return new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    }

    public static boolean ok(Response response) {
        return response != null && (response.code() == 200 || response.code() == 206);
    }

    public static Object invokeMethod(Object o, String methodName, Object... args) {
        Class[] classes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            classes[i] = args[i].getClass();
        }
        try {
            Method method = o.getClass().getDeclaredMethod(methodName, classes);
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }
            Object invoke;
            invoke = method.invoke(o, args);
            return invoke;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * <pre>
     * a/b/c.txt.dat.exe.orig --> c
     * a/b/c.txt --> c
     * a.txt     --> a
     * a/b/c     --> c
     * a/b/c/    --> ""
     * a/b/.c    --> ""
     * null      --> null;
     * </pre>
     */
    public static String basename(String name) {
        if (name != null) {
            name = FilenameUtils.getBaseName(name);
            int idx = name.indexOf('.');
            if (idx >= 0) {
                name = name.substring(0, idx);
            }
        }
        return name;
    }

    public static byte[] toArray(ByteBuffer content) {
        byte[] array = content.array();
        int remaining = content.remaining();
        if (content.arrayOffset() == 0 && remaining == array.length) {
            return array;
        }
        array = new byte[remaining];
        content.duplicate().get(array);
        return array;
    }

    public static <T> T lastElement(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public static ByteBuffer copy(ByteBuffer buf) {
        buf.mark();
        ByteBuffer allocate = ByteBuffer.allocate(buf.remaining());
        allocate.put(buf);
        allocate.clear();
        buf.reset();
        return allocate;
    }

    public static void closeQuietly(Closeable closeable) {
        IOUtils.closeQuietly(closeable);
    }

    private final static Gson gson = new GsonBuilder().create();

    public static String gsonToString(Object o) {
        return gson.toJson(o);
    }

}
