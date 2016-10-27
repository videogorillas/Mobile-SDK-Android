package com.vg.live;

import java.io.File;
import java.io.Serializable;

import com.vg.util.Utils;

public class StreamId implements Serializable, Comparable {
    private static final long serialVersionUID = 1L;
    //NOTE: these fields are intentionally final - this class is supposed to be immutable
    public final String userId;
    public final String streamId;

    public StreamId(String userId, String streamId) {
        this.userId = userId;
        this.streamId = streamId;
    }

    @Override
    public String toString() {
        return sid();
    }

    @Override
    public int compareTo(Object o) {
        return this.toString().compareTo(String.valueOf(o));
    }

    private transient int hashCode;

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = toString().hashCode();
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null) {
            return toString().equals(obj.toString());
        }
        return false;
    }

    public static StreamId sid(String userId, String streamId) {
        return new StreamId(userId, streamId);
    }

    public String sid() {
        StringBuilder sb = new StringBuilder();
        sb.append(userId);
        sb.append('/');
        sb.append(streamId);
        return sb.toString();
    }

    public static StreamId sid(String s) {
        if (Utils.isBlank(s))
            return null;
        String[] split = s.split("\\/");
        if (split.length > 1) {
            String streamId = split[split.length - 1];
            String userId = split[split.length - 2];
            return new StreamId(userId, streamId);
        }
        return null;
    }

    public static StreamId sid(File streamDir) {
        return new StreamId(streamDir.getParentFile().getName(), streamDir.getName());
    }
}
