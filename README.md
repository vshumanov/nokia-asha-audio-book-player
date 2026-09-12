# AudioBook — a Series 40 audiobook player

A Java ME (MIDP 2.0 / CLDC 1.1) audiobook player built for the **Nokia Asha 210**,
with a chapter list, **bookmark/resume per book**, seek, volume, and a **sleep timer**.

The Asha 210's stock music player can't remember your place in a 9-hour file, so
this is a real app: it splits each book into ~5-minute MP3 segments, plays them as
one continuous book, and remembers exactly where you stopped.

## Layout

```
convert.sh        m4b  ->  segmented mono MP3 + manifest (runs on the Mac)
make.sh           builds the MIDlet inside a JDK-8 Docker container
build/            build script, ProGuard (preverify) config, MIDlet manifest/JAD
src/              the MIDlet source (com.audiobook.*)
stubs/            compile-only J2ME API stubs (MMAPI + JSR-75; not shipped)
tools/            fetch.sh + downloaded build jars (gitignored)
sdcard/           staged output to copy to the phone (gitignored)
dist/             built AudioBook.jar / AudioBook.jad (gitignored)
```

## Prerequisites (Mac)

- `ffmpeg`  →  `brew install ffmpeg`
- Docker Desktop (for the build; supplies the old JDK 8 + preverification)
- `./tools/fetch.sh` once, to download the toolchain jars

## 1. Convert a book

```sh
./convert.sh "/path/to/Book.m4b"          # one book
./convert.sh --all "/path/to/Libation/Books"   # every .m4b under a folder
```

Output lands in `sdcard/audiobooks/<Title>/` as `0001.mp3 …` (48 kbps mono,
44.1 kHz — MPEG-1, which every S40 decoder handles) plus a `book.txt` manifest.

## 2. Build the app

```sh
./make.sh
```

Produces `dist/AudioBook.jar` and `dist/AudioBook.jad`.

**Why Docker?** CLDC classes must be *preverified*, and modern JDKs can't target
the old class format. The container compiles with JDK 8 (`-target 1.3`) and uses
**ProGuard's `-microedition` mode as the preverifier** — no ancient WTK binary
needed, so it works on Apple Silicon.

## 3. Copy to the phone (microSD)

Pop the card into a reader and copy **both**:

1. The whole `sdcard/audiobooks` folder → to the **card root** (so the phone sees
   `audiobooks/Caves of Ice/…`, `audiobooks/For the Emperor/…`).
2. `dist/AudioBook.jar` **and** `AudioBook.jad` → anywhere on the card.

Put the card back, open **File Manager** on the phone, tap `AudioBook.jar`, and
install. It's unsigned, so the phone will warn and ask permission for file access
and audio — allow it (you can set file access to "Always allowed" to stop repeat
prompts).

## Controls

| Key | Action | Also via |
|-----|--------|----------|
| **5 / OK** | play / pause | Menu → Play/Pause |
| **4 / 6** | seek −30s / +30s | Menu → ±30s |
| **2 / 8** | previous / next chapter | Menu → chapter |
| **\* / #** | volume down / up | — |
| **7** | cycle sleep timer | Menu → Sleep timer |

Sleep timer cycles **Off → 5 → 15 → 30 → 45 → 60 min → End of chapter**.
Your position autosaves every ~10s and on exit; relaunch offers **Resume**.

## Notes / tuning

- Segment length and bitrate are constants at the top of `convert.sh`
  (`SEG_SECONDS=300`, `BITRATE=48k`). Smaller/lower = safer on tight memory.
- Playback prefers a file locator (streams from the card); if a device rejects
  that it falls back to reading the segment as a stream.
