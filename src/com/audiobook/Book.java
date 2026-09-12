package com.audiobook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;

/**
 * One audiobook, described by its book.txt manifest.
 * dirURL is a JSR-75 file URL ending in '/', e.g.
 *   file:///e:/audiobooks/Caves of Ice/
 */
public class Book {
    public String title = "";
    public String author = "";
    public String dirURL;
    public String[] segNames;   // 0001.mp3, 0002.mp3, ...
    public long[] segMs;        // per-segment duration in milliseconds
    public long totalMs;
    public int count;

    public Book(String dirURL) {
        this.dirURL = dirURL;
    }

    public String segURL(int i) {
        return dirURL + segNames[i];
    }

    /** Reads and parses book.txt in the given directory. Returns null if absent/empty. */
    public static Book load(String dirURL) throws IOException {
        Book b = new Book(dirURL);
        FileConnection fc = (FileConnection) Connector.open(dirURL + "book.txt", Connector.READ);
        InputStream in = null;
        try {
            if (!fc.exists()) {
                return null;
            }
            in = fc.openInputStream();
            b.parse(new String(readAll(in)));
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException e) { }
            }
            fc.close();
        }
        if (b.segNames == null || b.count == 0) {
            return null;
        }
        return b;
    }

    private void parse(String text) {
        Vector names = new Vector();
        Vector durs = new Vector();
        int i = 0;
        int n = text.length();
        while (i < n) {
            int e = text.indexOf('\n', i);
            if (e < 0) {
                e = n;
            }
            String line = text.substring(i, e);
            i = e + 1;
            int len = line.length();
            if (len > 0 && line.charAt(len - 1) == '\r') {
                line = line.substring(0, len - 1);
            }
            if (line.length() == 0) {
                continue;
            }
            int bar = line.indexOf('|');
            if (bar > 0) {
                names.addElement(line.substring(0, bar));
                durs.addElement(line.substring(bar + 1));
            } else {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    String k = line.substring(0, eq);
                    String v = line.substring(eq + 1);
                    if (k.equals("title")) {
                        title = v;
                    } else if (k.equals("author")) {
                        author = v;
                    } else if (k.equals("totalms")) {
                        totalMs = parseLong(v);
                    }
                }
            }
        }
        count = names.size();
        segNames = new String[count];
        segMs = new long[count];
        long tot = 0;
        for (int j = 0; j < count; j++) {
            segNames[j] = (String) names.elementAt(j);
            long ms = parseLong((String) durs.elementAt(j));
            segMs[j] = ms;
            tot += ms;
        }
        if (totalMs == 0) {
            totalMs = tot;
        }
        if (title.length() == 0) {
            title = "Audiobook";
        }
    }

    static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[512];
        int r;
        while ((r = in.read(buf)) > 0) {
            bo.write(buf, 0, r);
        }
        return bo.toByteArray();
    }
}
