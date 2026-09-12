package com.audiobook;

import java.util.Timer;
import java.util.TimerTask;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

/**
 * The player screen. Draws book/chapter/time/progress and handles both
 * keypad game-actions and on-screen Commands (Commands matter on the
 * Asha 210's QWERTY layout, where number keys may be letters).
 */
public class PlayerCanvas extends Canvas implements CommandListener, BookPlayer.Listener {

    private final AudioBookMIDlet midlet;
    private final BookPlayer bp;

    private final Command backCmd    = new Command("Library", Command.BACK, 1);
    private final Command playCmd    = new Command("Play/Pause", Command.ITEM, 1);
    private final Command fwdCmd     = new Command("+30s", Command.ITEM, 2);
    private final Command rewCmd     = new Command("-30s", Command.ITEM, 3);
    private final Command nextCmd    = new Command("Next chapter", Command.ITEM, 4);
    private final Command prevCmd    = new Command("Prev chapter", Command.ITEM, 5);
    private final Command sleepCmd   = new Command("Sleep timer", Command.ITEM, 6);
    private final Command exitCmd    = new Command("Exit", Command.EXIT, 2);

    // Sleep timer: idx 0=off, 1..5 = minutes below, 6 = end of chapter
    private static final int[] SLEEP_MIN = { 0, 5, 15, 30, 45, 60, -1 };
    private int sleepIdx;
    private long sleepDeadline;

    private Timer timer;
    private int ticksSinceSave;
    private String message;

    public PlayerCanvas(AudioBookMIDlet midlet, Book book) {
        this.midlet = midlet;
        this.bp = new BookPlayer(book, this);
        setTitle(book.title);
        addCommand(backCmd);
        addCommand(playCmd);
        addCommand(fwdCmd);
        addCommand(rewCmd);
        addCommand(nextCmd);
        addCommand(prevCmd);
        addCommand(sleepCmd);
        addCommand(exitCmd);
        setCommandListener(this);
        try { setFullScreenMode(true); } catch (Throwable t) { }
    }

    /** Position at the given chapter/offset (paused) and begin ticking. */
    public void open(int seg, long ms) {
        bp.openSegment(seg, ms);
        startTimer();
    }

    public void startTimer() {
        stopTimer();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                tick();
            }
        }, 1000, 1000);
    }

    public void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void tick() {
        // sleep timer (timed modes only; end-of-chapter is handled by the engine)
        if (sleepIdx >= 1 && sleepIdx <= 5 && System.currentTimeMillis() >= sleepDeadline) {
            sleepIdx = 0;
            bp.pause();
        }
        repaint();
        if (bp.playing()) {
            ticksSinceSave++;
            if (ticksSinceSave >= 10) {
                ticksSinceSave = 0;
                saveBookmark();
            }
        }
    }

    private void cycleSleep() {
        sleepIdx = (sleepIdx + 1) % SLEEP_MIN.length;
        bp.setStopAtChapterEnd(false);
        int min = SLEEP_MIN[sleepIdx];
        if (min > 0) {
            sleepDeadline = System.currentTimeMillis() + (long) min * 60000L;
        } else if (min < 0) {
            bp.setStopAtChapterEnd(true);   // end of chapter
        }
        repaint();
    }

    private String sleepStatus() {
        int min = SLEEP_MIN[sleepIdx];
        if (min == 0) {
            return "off";
        }
        if (min < 0) {
            return "end of chapter";
        }
        long left = sleepDeadline - System.currentTimeMillis();
        if (left < 0) {
            left = 0;
        }
        return fmt(left);
    }

    public void saveBookmark() {
        Bookmarks.save(bp.book().title, bp.seg(), bp.positionMs());
    }

    // ---- input ----

    protected void keyPressed(int keyCode) {
        int a = 0;
        try { a = getGameAction(keyCode); } catch (Exception e) { }
        if (a == FIRE || keyCode == KEY_NUM5) {
            bp.toggle();
        } else if (a == LEFT || keyCode == KEY_NUM4) {
            bp.seekRel(-30000);
        } else if (a == RIGHT || keyCode == KEY_NUM6) {
            bp.seekRel(30000);
        } else if (a == UP || keyCode == KEY_NUM2) {
            bp.prevChapter();
        } else if (a == DOWN || keyCode == KEY_NUM8) {
            bp.nextChapter();
        } else if (keyCode == KEY_POUND) {
            bp.volumeUp();
        } else if (keyCode == KEY_STAR) {
            bp.volumeDown();
        } else if (keyCode == KEY_NUM7) {
            cycleSleep();
        }
    }

    public void commandAction(Command c, Displayable d) {
        if (c == backCmd) {
            leave();
            midlet.showLibrary();
        } else if (c == exitCmd) {
            leave();
            midlet.exit();
        } else if (c == playCmd) {
            bp.toggle();
        } else if (c == fwdCmd) {
            bp.seekRel(30000);
        } else if (c == rewCmd) {
            bp.seekRel(-30000);
        } else if (c == nextCmd) {
            bp.nextChapter();
        } else if (c == prevCmd) {
            bp.prevChapter();
        } else if (c == sleepCmd) {
            cycleSleep();
        }
    }

    private void leave() {
        stopTimer();
        saveBookmark();
        bp.pause();
        bp.close();
    }

    // ---- BookPlayer.Listener ----

    public void onStateChanged() {
        repaint();
    }

    public void onError(String msg) {
        message = msg;
        repaint();
    }

    // ---- drawing ----

    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        g.setColor(0x000000);
        g.fillRect(0, 0, w, h);

        Font big = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_MEDIUM);
        Font small = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int pad = 4;
        int y = pad;

        Book book = bp.book();

        g.setColor(0xFFFFFF);
        g.setFont(big);
        y = drawWrapped(g, book.title, pad, y, w - 2 * pad, big, 2);

        g.setFont(small);
        g.setColor(0xAAAAAA);
        if (book.author.length() > 0) {
            g.drawString(book.author, pad, y, Graphics.TOP | Graphics.LEFT);
            y += small.getHeight();
        }
        y += 4;

        // chapter line
        g.setColor(0xFFFFFF);
        String ch = "Chapter " + (bp.seg() + 1) + " / " + book.count;
        g.drawString(ch, pad, y, Graphics.TOP | Graphics.LEFT);
        y += small.getHeight() + 2;

        // segment time
        long pos = bp.positionMs();
        long dur = bp.segDurationMs();
        g.setColor(0xCCCCCC);
        g.drawString(fmt(pos) + " / " + fmt(dur), pad, y, Graphics.TOP | Graphics.LEFT);
        y += small.getHeight() + 2;

        // chapter progress bar
        y = drawBar(g, pad, y, w - 2 * pad, pos, dur, 0x33AAFF);
        y += 6;

        // whole-book progress
        g.setColor(0xCCCCCC);
        g.drawString("Book: " + fmt(bp.bookPositionMs()) + " / " + fmt(book.totalMs),
                pad, y, Graphics.TOP | Graphics.LEFT);
        y += small.getHeight() + 2;
        y = drawBar(g, pad, y, w - 2 * pad, bp.bookPositionMs(), book.totalMs, 0x66CC66);
        y += 8;

        // status
        String state;
        if (bp.loading()) {
            state = "Loading...";
        } else if (bp.playing()) {
            state = "> Playing";
        } else {
            state = "|| Paused";
        }
        g.setColor(0xFFDD55);
        g.drawString(state + "    Vol " + bp.volume(), pad, y, Graphics.TOP | Graphics.LEFT);
        y += small.getHeight() + 2;

        g.setColor(0xBB88FF);
        g.drawString("Sleep: " + sleepStatus(), pad, y, Graphics.TOP | Graphics.LEFT);
        y += small.getHeight() + 4;

        if (message != null) {
            g.setColor(0xFF6666);
            g.drawString(message, pad, y, Graphics.TOP | Graphics.LEFT);
            y += small.getHeight() + 4;
        }

        // Help: pinned near the bottom when there's room, but never above the
        // content (clamped so short screens can't overlap).
        g.setColor(0x777777);
        int hy = h - small.getHeight() * 3 - pad;
        if (hy < y + 4) {
            hy = y + 4;
        }
        g.drawString("5/OK play   4/6 -/+30s", pad, hy, Graphics.TOP | Graphics.LEFT);
        hy += small.getHeight();
        g.drawString("2/8 chapter  */# vol  7 sleep", pad, hy, Graphics.TOP | Graphics.LEFT);
        hy += small.getHeight();
        g.drawString("Menu for all actions", pad, hy, Graphics.TOP | Graphics.LEFT);
    }

    private int drawBar(Graphics g, int x, int y, int w, long val, long max, int color) {
        int bh = 6;
        g.setColor(0x333333);
        g.fillRect(x, y, w, bh);
        if (max > 0) {
            int fw = (int) ((long) w * val / max);
            if (fw < 0) fw = 0;
            if (fw > w) fw = w;
            g.setColor(color);
            g.fillRect(x, y, fw, bh);
        }
        return y + bh;
    }

    private int drawWrapped(Graphics g, String s, int x, int y, int w, Font f, int maxLines) {
        int lines = 0;
        int start = 0;
        int n = s.length();
        while (start < n && lines < maxLines) {
            int end = start;
            int lastSpace = -1;
            while (end < n) {
                char c = s.charAt(end);
                if (c == ' ') {
                    lastSpace = end;
                }
                if (f.substringWidth(s, start, end - start + 1) > w) {
                    break;
                }
                end++;
            }
            int lineEnd;
            if (end >= n) {
                lineEnd = n;
            } else if (lastSpace > start) {
                lineEnd = lastSpace;
            } else {
                lineEnd = end;
            }
            String line = s.substring(start, lineEnd);
            if (lines == maxLines - 1 && lineEnd < n) {
                line = line + "...";
            }
            g.drawString(line, x, y, Graphics.TOP | Graphics.LEFT);
            y += f.getHeight();
            lines++;
            start = (lineEnd < n && s.charAt(lineEnd) == ' ') ? lineEnd + 1 : lineEnd;
        }
        return y;
    }

    static String fmt(long ms) {
        long s = ms / 1000;
        long hh = s / 3600;
        long mm = (s % 3600) / 60;
        long ss = s % 60;
        StringBuffer b = new StringBuffer();
        if (hh > 0) {
            b.append(hh).append(':');
            if (mm < 10) b.append('0');
        }
        b.append(mm).append(':');
        if (ss < 10) b.append('0');
        b.append(ss);
        return b.toString();
    }
}
