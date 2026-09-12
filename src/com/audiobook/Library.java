package com.audiobook;

import java.util.Enumeration;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;

/**
 * Finds audiobooks by scanning every file-system root for an
 * "audiobooks/" folder and loading each sub-folder's book.txt.
 */
public class Library {

    /** Returns a Vector of Book. Never null. */
    public static Vector scan() {
        Vector books = new Vector();
        Enumeration roots = FileSystemRegistry.listRoots();
        if (roots == null) {
            return books;
        }
        while (roots.hasMoreElements()) {
            String root = (String) roots.nextElement();   // e.g. "e:/"
            scanDir("file:///" + root + "audiobooks/", books);
        }
        return books;
    }

    private static void scanDir(String abURL, Vector books) {
        FileConnection dir = null;
        try {
            dir = (FileConnection) Connector.open(abURL, Connector.READ);
            if (!dir.exists() || !dir.isDirectory()) {
                return;
            }
            Enumeration e = dir.list();
            while (e.hasMoreElements()) {
                String name = (String) e.nextElement();    // "Caves of Ice/"
                if (name.endsWith("/")) {
                    try {
                        Book b = Book.load(abURL + name);
                        if (b != null) {
                            books.addElement(b);
                        }
                    } catch (Throwable t) {
                        // skip unreadable/invalid folder
                    }
                }
            }
        } catch (Throwable t) {
            // root not present or not permitted
        } finally {
            if (dir != null) {
                try { dir.close(); } catch (Exception ex) { }
            }
        }
    }
}
