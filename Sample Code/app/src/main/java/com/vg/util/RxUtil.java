package com.vg.util;

import android.support.annotation.NonNull;

import com.squareup.okhttp.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Action2;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
import rx.subjects.Subject;
import rx.subjects.UnicastSubject;
import rx.subscriptions.Subscriptions;

import static com.vg.util.Utils.GET;
import static com.vg.util.Utils.GETRange;
import static rx.Observable.range;
import static rx.schedulers.Schedulers.io;

/**
 * Created by zhukov on 6/14/16.
 */
public class RxUtil {
    public static Observable<Response> responseRx(OkHttpClient c, Request request) {
        Observable<Response> observable = Observable.create(o -> {
            Call newCall = c.newCall(request);
            AtomicBoolean needCancel = new AtomicBoolean(true);
            o.add(Subscriptions.create(() -> {
                if (needCancel.get() && !newCall.isCanceled()) {
                    newCall.cancel();
                }
            }));
            newCall.enqueue(new com.squareup.okhttp.Callback() {

                @Override
                public void onResponse(Response response) throws IOException {
                    needCancel.set(false);
                    o.onNext(response);
                    o.onCompleted();
                }

                @Override
                public void onFailure(Request request, IOException e) {
                    needCancel.set(false);
                    o.onError(new RequestException(request, e));
                    o.onCompleted();
                }
            });
        });
        return observable.unsubscribeOn(io());
    }

    public static void discardBody(Response r) {
        Utils.discardBody(r);
    }

    @NonNull
    public static Observable<Response> okResponseRx(OkHttpClient c, Request request) {
        return responseRx(c, request).concatMap(r -> {
            boolean ok = r != null && (r.code() == 200 || r.code() == 206);
            if (ok) {
                return Observable.just(r);
            } else {
                discardBody(r);
                return Observable.<Response>error(new UnhandledResponseException(r));
            }
        });
    }

    public static Observable<ByteBuffer> splitBuffers(Observable<ByteBuffer> rx, int size) {
        return splitBuffers(rx, size, ByteBuffer::allocate, b -> {
        });
    }

    public static Observable<ByteBuffer> splitBuffers(Observable<ByteBuffer> rx, int size,
                                                      Func1<Integer, ByteBuffer> allocator, Action1<ByteBuffer> deallocator) {
        LinkedList<ByteBuffer> dstQueue = new LinkedList<>();
        LinkedList<ByteBuffer> srcQueue = new LinkedList<>();
        Observable<ByteBuffer> concatMap = rx.concatMap(srcBuf -> {
            LinkedList<ByteBuffer> srcQueue_ = srcQueue;
            LinkedList<ByteBuffer> dstQueue_ = dstQueue;
            srcQueue_.addLast(srcBuf);
            ByteBuffer src;
            ByteBuffer dst;
            do {
                src = srcQueue_.pollFirst();
                List<ByteBuffer> output = new ArrayList<>();
                do {
                    dst = dstQueue_.isEmpty() ? allocator.call(size) : dstQueue_.pollFirst();
                    ByteBuffer _src = src.duplicate();
                    int min = Math.min(_src.remaining(), dst.remaining());
                    _src.limit(_src.position() + min);
                    dst.put(_src);
                    src.position(src.position() + min);
                    if (!dst.hasRemaining()) {
                        dst.position(0);
                        dst.limit(size);
                        output.add(dst);
                    } else {
                        dstQueue_.addLast(dst);
                        break;
                    }
                } while (src.remaining() >= size);
                if (src.hasRemaining()) {
                    srcQueue_.push(src);
                } else {
                    deallocator.call(src);
                }
                if (!output.isEmpty()) {
                    return Observable.from(output);
                }
            } while (!srcQueue_.isEmpty());

            return Observable.empty();
        }).concatWith(Observable.from(srcQueue).map(buf -> {
            ByteBuffer slice = buf.slice();
            ByteBuffer allocate = allocator.call(slice.remaining());
            allocate.put(slice);
            deallocator.call(buf);
            allocate.flip();
            return allocate;
        })).concatWith(Observable.from(dstQueue).map(buf -> {
            ByteBuffer slice = (ByteBuffer) buf.flip();
            ByteBuffer allocate = allocator.call(slice.remaining());
            allocate.put(slice);
            deallocator.call(buf);
            allocate.flip();
            return allocate;
        }));

        return concatMap;
    }

    public static Observable<ByteBuffer> readRx(InputStream in) {
        return Observable.create(o -> {
            AtomicBoolean unsubscribed = new AtomicBoolean();
            o.add(new Subscription() {

                @Override
                public void unsubscribe() {
                    unsubscribed.set(true);
                }

                @Override
                public boolean isUnsubscribed() {
                    return unsubscribed.get();
                }
            });
            byte[] buffer = new byte[4096];
            try {
                int read = -1;
                do {
                    read = in.read(buffer);
                    //                    System.out.println("read " + read);
                    if (read >= 0) {
                        //                        System.out.println("onNext");
                        o.onNext(ByteBuffer.wrap(buffer, 0, read));
                    }
                    if (unsubscribed.get()) {
                        //                        System.out.println("unsubscribed");
                        break;
                    }
                } while (read != -1);
            } catch (Exception e) {
                o.onError(e);
            }
            o.onCompleted();
            //            System.out.println("onCompleted");
        });
    }

    public static Observable<Response> downloadRange(OkHttpClient c, String url, long startByte, long endByte) {
        return responseRx(c, GETRange(url, startByte, endByte)).concatMap(r -> {
            if (r.code() == 200 || r.code() == 206) {
                return Observable.just(r);
            } else {
                discardBody(r);
                return Observable.error(new UnhandledResponseException(r));
            }
        });
    }

    public static Func1<Observable<? extends Throwable>, Observable<?>> retryWithDelay(int maxRetries,
                                                                                       int retryDelayMillis, Action2<Integer, Long> log) {
        return errors -> errors.zipWith(range(1, maxRetries + 1), (n, i1) -> i1).flatMap(attempt -> {
            if (attempt > maxRetries) {
                return Observable.error(new Exception("no more retries"));
            }
            long delayMsec = retryDelayMillis * attempt;
            if (log != null) {
                log.call(attempt, delayMsec);
            }
            return Observable.timer(delayMsec, TimeUnit.MILLISECONDS);
        });
    }


    public static Func1<Observable<? extends Throwable>, Observable<?>> retryWithDelay(int maxRetries,
                                                                                       int retryDelayMillis, String tag) {
        return retryWithDelay(maxRetries, retryDelayMillis, (attempt, delay) -> {
            Log.d(tag, attempt + "/" + maxRetries + " delay retry by " + delay + " msec");
        });
    }

    public static Subscription async(Runnable runnable) {
        return Observable.fromCallable(() -> {
            runnable.run();
            return null;
        }).subscribeOn(io()).subscribe(x -> {
        }, err -> {
            err.printStackTrace();
        });
    }

    public static <T> Observable<List<T>> split2(Observable<T> from, Func2<List<T>, T, Boolean> predicate) {
        Observable<List<T>> scan = from.scan(new ArrayList<T>(), (list, item) -> {
            if (list.size() != 0 && predicate.call(list, item)) {
                list = new ArrayList<>();
            }
            list.add(item);
            return list;
        });

        Observable<List<T>> tokens = scan.buffer(2, 1).flatMap(ll -> {
            if (ll.size() == 1 || ll.get(0) != ll.get(1)) {
                return Observable.just(ll.get(0));
            } else {
                return Observable.empty();
            }
        });
        return tokens;
    }

    public static <T> Observable<Observable<T>> splitBy(Observable<T> interval, Func1<T, Boolean> predicate) {
        Subject[] cur = new Subject[1];
        Observable<Observable<T>> flatMap = interval.doOnCompleted(() -> {
            if (cur[0] != null) {
                cur[0].onCompleted();
            }
        }).concatMap(x -> {
            boolean output = false;
            if (cur[0] == null) {
                cur[0] = UnicastSubject.create();
                output = true;
            }
            cur[0].onNext(x);
            if (predicate.call(x)) {
                cur[0].onCompleted();
                cur[0] = UnicastSubject.create();
                output = true;
            }
            if (output) {
                return Observable.just((Observable<T>) cur[0]);
            } else {
                return Observable.empty();
            }
        });
        return flatMap;
    }

    public static <T> Observable<List<T>> split1(Observable<T> from, Func1<T, Boolean> predicate) {
        Observable<List<T>> scan = from.scan(new ArrayList<T>(), (list, item) -> {
            if (!list.isEmpty() && predicate.call(item)) {
                list = new ArrayList<>();
            }
            list.add(item);
            return list;
        });

        Observable<List<T>> tokens = scan.buffer(2, 1).flatMap(ll -> {
            if (ll.size() == 1 || ll.get(0) != ll.get(1)) {
                return Observable.just(ll.get(0));
            } else {
                return Observable.empty();
            }
        });
        return tokens;
    }

    public static Observable<byte[]> requestBytes(OkHttpClient client, String url) {
        return okResponseRx(client, GET(url)).concatMap(r -> {
            try {
                return Observable.just(r.body().bytes());
            } catch (IOException e1) {
                return Observable.error(e1);
            }
        });
    }
}
