package com.audiobook;

import java.io.InputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.media.Manager;
import javax.microedition.media.MediaException;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VolumeControl;

/**
 * Drives MMAPI playback across a book's segments: play/pause, seek,
 * chapter skip, and auto-advance at end of each segment.
 *
 * MMAPI media time is in MICROSECONDS; this class exposes milliseconds
 * everywhere and converts at the MMAPI boundary.
 */
public class BookPlayer implements PlayerListener {

    public interface Listener {
        void onStateChanged();
        void onError(String msg);
    }

    private final Book book;
    private final Listener ui;

    private Player player;
    private FileConnection fc;   // non-null only on the stream-fallback path
    private InputStream in;

    private int seg;
    private boolean playing;
    private boolean loading;
    private int volume = 70;
    private boolean stopAtChapterEnd;

    public void setStopAtChapterEnd(boolean b) {
        stopAtChapterEnd = b;
    }

    public BookPlayer(Book book, Listener ui) {
        this.book = book;
        this.ui = ui;
    }

    public Book book()       { return book; }
    public int seg()         { return seg; }
    public boolean playing() { return playing; }
    public boolean loading() { return loading; }
    public int volume()      { return volume; }

    /** Position within the current segment, in ms. */
    public long positionMs() {
        if (player == null) {
            return 0;
        }
        long us = player.getMediaTime();
        return us < 0 ? 0 : us / 1000;
    }

    public long segDurationMs() {
        return book.segMs[seg];
    }

    /** Absolute position across the whole book, in ms. */
    public long bookPositionMs() {
        long t = 0;
        for (int i = 0; i < seg; i++) {
            t += book.segMs[i];
        }
        return t + positionMs();
    }

    /** Open a segment and seek to startMs; keeps current play/pause state. */
    public void openSegment(int index, long startMs) {
        if (index < 0) index = 0;
        if (index >= book.count) index = book.count - 1;
        boolean wasPlaying = playing;
        loading = true;
        ui.onStateChanged();
        closePlayer();
        seg = index;
        try {
            player = create(book.segURL(index));
            player.addPlayerListener(this);
            applyVolume();
            if (startMs > 0) {
                try { player.setMediaTime(startMs * 1000L); } catch (MediaException me) { }
            }
            loading = false;
            if (wasPlaying) {
                player.start();
                playing = true;
            }
        } catch (Throwable t) {
            loading = false;
            playing = false;
            ui.onError("Cannot open chapter " + (index + 1));
        }
        ui.onStateChanged();
    }

    public void toggle() {
        if (playing) {
            pause();
        } else {
            play();
        }
    }

    public void play() {
        try {
            if (player == null) {
                openSegment(seg, 0);
            }
            if (player != null) {
                player.start();
                playing = true;
                ui.onStateChanged();
            }
        } catch (Throwable t) {
            ui.onError("Play failed");
        }
    }

    public void pause() {
        try {
            if (player != null) {
                player.stop();
            }
        } catch (Throwable t) {
        }
        playing = false;
        ui.onStateChanged();
    }

    /** Seek by deltaMs within the current segment (clamped). */
    public void seekRel(long deltaMs) {
        if (player == null) {
            return;
        }
        long target = positionMs() + deltaMs;
        if (target < 0) target = 0;
        long dur = segDurationMs();
        if (dur > 0 && target > dur - 500) target = dur - 500;
        try {
            player.setMediaTime(target * 1000L);
        } catch (Throwable t) {
        }
        ui.onStateChanged();
    }

    public void nextChapter() {
        if (seg + 1 < book.count) {
            openSegment(seg + 1, 0);
        }
    }

    public void prevChapter() {
        // if >3s into segment, restart it; else go to previous
        if (positionMs() > 3000) {
            openSegment(seg, 0);
        } else if (seg > 0) {
            openSegment(seg - 1, 0);
        }
    }

    public void volumeUp()   { setVolume(volume + 10); }
    public void volumeDown() { setVolume(volume - 10); }

    private void setVolume(int v) {
        if (v < 0) v = 0;
        if (v > 100) v = 100;
        volume = v;
        applyVolume();
        ui.onStateChanged();
    }

    private void applyVolume() {
        try {
            VolumeControl vc = (VolumeControl) player.getControl("VolumeControl");
            if (vc != null) {
                vc.setLevel(volume);
            }
        } catch (Throwable t) {
        }
    }

    public void close() {
        closePlayer();
        playing = false;
    }

    private void closePlayer() {
        if (player != null) {
            try { player.removePlayerListener(this); } catch (Throwable t) { }
            try { player.close(); } catch (Throwable t) { }
            player = null;
        }
        if (in != null) {
            try { in.close(); } catch (Throwable t) { }
            in = null;
        }
        if (fc != null) {
            try { fc.close(); } catch (Throwable t) { }
            fc = null;
        }
    }

    /** Prefer a file locator (streams from disk); fall back to an InputStream. */
    private Player create(String url) throws Exception {
        try {
            Player p = Manager.createPlayer(url);
            p.realize();
            p.prefetch();
            return p;
        } catch (Throwable locatorFailed) {
            fc = (FileConnection) Connector.open(url, Connector.READ);
            in = fc.openInputStream();
            Player p = Manager.createPlayer(in, "audio/mpeg");
            p.realize();
            p.prefetch();
            return p;
        }
    }

    public void playerUpdate(Player p, String event, Object data) {
        if (PlayerListener.END_OF_MEDIA.equals(event)) {
            if (stopAtChapterEnd) {
                stopAtChapterEnd = false;
                playing = false;
                ui.onStateChanged();
            } else if (seg + 1 < book.count) {
                // auto-advance; openSegment preserves the (playing) state
                playing = true;
                openSegment(seg + 1, 0);
            } else {
                playing = false;
                ui.onStateChanged();
            }
        } else if (PlayerListener.ERROR.equals(event)) {
            ui.onError("Playback error");
        }
    }
}
