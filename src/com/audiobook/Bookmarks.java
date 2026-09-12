package com.audiobook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStore;

/**
 * Persists playback position per book in RMS, plus the last-opened title.
 * One record per book: UTF title, int segment index, long position (ms).
 */
public class Bookmarks {

    private static final String MARKS = "abmarks";
    private static final String LAST = "ablast";

    public static class Mark {
        public String title;
        public int seg;
        public long ms;

        public Mark(String title, int seg, long ms) {
            this.title = title;
            this.seg = seg;
            this.ms = ms;
        }
    }

    public static void save(String title, int seg, long ms) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(MARKS, true);
            byte[] data = encode(title, seg, ms);
            int id = findId(rs, title);
            if (id < 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(id, data, 0, data.length);
            }
        } catch (Exception e) {
            // best effort
        } finally {
            close(rs);
        }
        saveLast(title);
    }

    /** Returns the saved mark for a title, or null. */
    public static Mark load(String title) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(MARKS, true);
            RecordEnumeration en = rs.enumerateRecords(null, null, false);
            while (en.hasNextElement()) {
                Mark m = decode(rs.getRecord(en.nextRecordId()));
                if (m != null && m.title.equals(title)) {
                    return m;
                }
            }
        } catch (Exception e) {
        } finally {
            close(rs);
        }
        return null;
    }

    public static String lastTitle() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(LAST, true);
            if (rs.getNumRecords() == 0) {
                return null;
            }
            RecordEnumeration en = rs.enumerateRecords(null, null, false);
            if (en.hasNextElement()) {
                byte[] b = rs.getRecord(en.nextRecordId());
                return new String(b, "UTF-8");
            }
        } catch (Exception e) {
        } finally {
            close(rs);
        }
        return null;
    }

    private static void saveLast(String title) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(LAST, true);
            byte[] b = title.getBytes("UTF-8");
            if (rs.getNumRecords() == 0) {
                rs.addRecord(b, 0, b.length);
            } else {
                RecordEnumeration en = rs.enumerateRecords(null, null, false);
                int id = en.nextRecordId();
                rs.setRecord(id, b, 0, b.length);
            }
        } catch (Exception e) {
        } finally {
            close(rs);
        }
    }

    private static int findId(RecordStore rs, String title) throws Exception {
        RecordEnumeration en = rs.enumerateRecords(null, null, false);
        while (en.hasNextElement()) {
            int id = en.nextRecordId();
            Mark m = decode(rs.getRecord(id));
            if (m != null && m.title.equals(title)) {
                return id;
            }
        }
        return -1;
    }

    private static byte[] encode(String title, int seg, long ms) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(bo);
        d.writeUTF(title);
        d.writeInt(seg);
        d.writeLong(ms);
        d.flush();
        return bo.toByteArray();
    }

    private static Mark decode(byte[] b) {
        try {
            DataInputStream d = new DataInputStream(new ByteArrayInputStream(b));
            String title = d.readUTF();
            int seg = d.readInt();
            long ms = d.readLong();
            return new Mark(title, seg, ms);
        } catch (Exception e) {
            return null;
        }
    }

    private static void close(RecordStore rs) {
        if (rs != null) {
            try { rs.closeRecordStore(); } catch (Exception e) { }
        }
    }
}
