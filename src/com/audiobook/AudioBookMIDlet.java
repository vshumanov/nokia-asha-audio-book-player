package com.audiobook;

import java.util.Vector;

import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Form;
import javax.microedition.lcdui.List;
import javax.microedition.midlet.MIDlet;

/**
 * Audiobook player for Series 40 (Nokia Asha 210).
 * Library screen -> per-book player with bookmark/resume.
 */
public class AudioBookMIDlet extends MIDlet implements CommandListener {

    private final Command exitCmd = new Command("Exit", Command.EXIT, 2);
    private final Command rescanCmd = new Command("Rescan", Command.SCREEN, 1);

    private Display display;
    private List list;
    private Vector listBooks;   // Book parallel to list rows

    protected void startApp() {
        if (display == null) {
            display = Display.getDisplay(this);
            showLibrary();
        }
    }

    protected void pauseApp() {
    }

    protected void destroyApp(boolean unconditional) {
    }

    public void exit() {
        destroyApp(true);
        notifyDestroyed();
    }

    public void showLibrary() {
        Vector books = Library.scan();
        listBooks = new Vector();

        if (books.size() == 0) {
            Form f = new Form("Audiobooks");
            f.append("No books found.\n\n");
            f.append("Copy an 'audiobooks' folder to the memory card. "
                    + "Put one sub-folder per book inside it, each containing "
                    + "book.txt and the mp3 files.");
            f.addCommand(exitCmd);
            f.addCommand(rescanCmd);
            f.setCommandListener(this);
            display.setCurrent(f);
            return;
        }

        list = new List("Audiobooks", List.IMPLICIT);

        // "Resume" shortcut for the last-opened book
        String last = Bookmarks.lastTitle();
        Book lastBook = null;
        if (last != null) {
            for (int i = 0; i < books.size(); i++) {
                Book b = (Book) books.elementAt(i);
                if (b.title.equals(last)) {
                    lastBook = b;
                    break;
                }
            }
        }
        if (lastBook != null) {
            list.append("Resume: " + lastBook.title, null);
            listBooks.addElement(lastBook);
        }

        for (int i = 0; i < books.size(); i++) {
            Book b = (Book) books.elementAt(i);
            list.append(b.title, null);
            listBooks.addElement(b);
        }

        list.addCommand(exitCmd);
        list.addCommand(rescanCmd);
        list.setCommandListener(this);
        display.setCurrent(list);
    }

    private void openBook(Book b) {
        Bookmarks.Mark m = Bookmarks.load(b.title);
        int seg = (m != null) ? m.seg : 0;
        long ms = (m != null) ? m.ms : 0;
        if (seg < 0 || seg >= b.count) {
            seg = 0;
            ms = 0;
        }
        PlayerCanvas pc = new PlayerCanvas(this, b);
        display.setCurrent(pc);
        pc.open(seg, ms);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exitCmd) {
            exit();
        } else if (c == rescanCmd) {
            showLibrary();
        } else if (d == list && c == List.SELECT_COMMAND) {
            int idx = list.getSelectedIndex();
            if (idx >= 0 && idx < listBooks.size()) {
                openBook((Book) listBooks.elementAt(idx));
            }
        }
    }
}
