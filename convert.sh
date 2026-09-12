#!/bin/bash
# Convert an .m4b audiobook into Series-40-friendly segmented MP3 + manifest.
#
#   ./convert.sh "/path/to/book.m4b"            # one book
#   ./convert.sh --all "/path/to/Libation/Books"  # every .m4b under a dir
#
# Output goes to ./sdcard/audiobooks/<Title>/ :
#     0001.mp3, 0002.mp3, ...   (48kbps mono 44.1kHz, ~5 min each)
#     book.txt                  (manifest read by the MIDlet)
# Copy the whole ./sdcard/audiobooks folder to the root of the phone's microSD.
set -euo pipefail

export PATH="/opt/homebrew/bin:$PATH"

BITRATE="48k"
CHANNELS=1
RATE=44100
SEG_SECONDS=300

HERE="$(cd "$(dirname "$0")" && pwd)"
OUT_ROOT="$HERE/sdcard/audiobooks"

sanitize() {
    # FAT-safe folder name
    printf '%s' "$1" | tr '\\/:*?"<>|' '         ' | sed 's/  */ /g; s/^ //; s/ $//'
}

convert_one() {
    local m4b="$1"
    local title author dir count total_ms=0

    title="$(ffprobe -v error -show_entries format_tags=title -of default=nw=1:nk=1 "$m4b" 2>/dev/null || true)"
    author="$(ffprobe -v error -show_entries format_tags=artist -of default=nw=1:nk=1 "$m4b" 2>/dev/null || true)"
    [ -z "$title" ] && title="$(basename "$m4b" .m4b)"
    [ -z "$author" ] && author="Unknown"

    dir="$OUT_ROOT/$(sanitize "$title")"
    echo ">> $title  ($author)"
    echo "   -> $dir"
    mkdir -p "$dir"
    rm -f "$dir"/*.mp3 "$dir"/book.txt

    echo "   encoding + segmenting (this takes a few minutes)..."
    ffmpeg -nostdin -v error -stats -i "$m4b" \
        -map 0:a:0 -map_metadata -1 -vn \
        -ac "$CHANNELS" -ar "$RATE" -c:a libmp3lame -b:a "$BITRATE" \
        -f segment -segment_time "$SEG_SECONDS" -reset_timestamps 1 \
        -segment_start_number 1 \
        "$dir/%04d.mp3"

    # Build manifest with exact per-segment durations
    local tmp="$dir/.segments.tmp"
    : > "$tmp"
    count=0
    for f in "$dir"/*.mp3; do
        local ms
        ms="$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$f" 2>/dev/null || echo 0)"
        # seconds (float) -> integer ms, no bc dependency
        ms="$(awk -v d="$ms" 'BEGIN{printf "%d", d*1000}')"
        total_ms=$((total_ms + ms))
        count=$((count + 1))
        printf '%s|%s\n' "$(basename "$f")" "$ms" >> "$tmp"
    done

    {
        printf 'title=%s\n' "$title"
        printf 'author=%s\n' "$author"
        printf 'count=%s\n' "$count"
        printf 'totalms=%s\n' "$total_ms"
        cat "$tmp"
    } > "$dir/book.txt"
    rm -f "$tmp"

    local mins=$((total_ms / 60000))
    echo "   done: $count segments, ~${mins} min total"
}

main() {
    command -v ffmpeg >/dev/null || { echo "ffmpeg not found"; exit 1; }
    mkdir -p "$OUT_ROOT"
    if [ "${1:-}" = "--all" ]; then
        local root="${2:?usage: ./convert.sh --all <dir>}"
        find "$root" -type f -name '*.m4b' -print0 | while IFS= read -r -d '' f; do
            convert_one "$f"
        done
    else
        local m4b="${1:?usage: ./convert.sh <file.m4b> | --all <dir>}"
        convert_one "$m4b"
    fi
    echo
    echo "All output under: $OUT_ROOT"
    echo "Copy the 'audiobooks' folder to the microSD card root."
}

main "$@"
