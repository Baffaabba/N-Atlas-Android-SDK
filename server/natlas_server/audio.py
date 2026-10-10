"""Audio decoding to 16 kHz mono float32, and silence-based splitting.

Decoding tries libsndfile (wav, flac, ogg/vorbis/opus, mp3 with
libsndfile >= 1.1) in memory first, then ffmpeg if it is on PATH (m4a, webm,
anything else). The ffmpeg path needs a temp file for seekable containers;
it is deleted in `finally`. No audio is kept.
"""

from __future__ import annotations

import io
import os
import shutil
import subprocess
import tempfile

import numpy as np

from .errors import APIError

SAMPLE_RATE = 16000
FRAME = 480  # 30 ms at 16 kHz
SUPPORTED_TEXT = "wav, mp3, m4a, ogg, flac, webm"


def resample(x: np.ndarray, sr_in: int, sr_out: int = SAMPLE_RATE) -> np.ndarray:
    if sr_in == sr_out or x.size == 0:
        return x.astype(np.float32, copy=False)
    try:
        from math import gcd

        from scipy.signal import resample_poly

        g = gcd(sr_in, sr_out)
        return resample_poly(x, sr_out // g, sr_in // g).astype(np.float32)
    except ImportError:  # linear fallback, fine for speech
        n_out = int(round(x.size * sr_out / sr_in))
        t_in = np.arange(x.size) / sr_in
        t_out = np.arange(n_out) / sr_out
        return np.interp(t_out, t_in, x).astype(np.float32)


def pcm16_to_float(data: bytes) -> np.ndarray:
    return np.frombuffer(data, dtype="<i2").astype(np.float32) / 32768.0


def _decode_soundfile(data: bytes) -> np.ndarray | None:
    try:
        import soundfile as sf

        x, sr = sf.read(io.BytesIO(data), dtype="float32", always_2d=True)
    except Exception:
        return None
    return resample(x.mean(axis=1), int(sr))


def _decode_ffmpeg(data: bytes, suffix: str) -> np.ndarray | None:
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg:
        return None
    fd, path = tempfile.mkstemp(suffix=suffix or ".bin", prefix="natlas-")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(data)
        proc = subprocess.run(
            [ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error", "-i", path,
             "-f", "s16le", "-ac", "1", "-ar", str(SAMPLE_RATE), "pipe:1"],
            capture_output=True,
            timeout=120,
        )
        if proc.returncode != 0:
            return None
        return pcm16_to_float(proc.stdout)
    except (subprocess.SubprocessError, OSError):
        return None
    finally:
        try:
            os.remove(path)
        except OSError:
            pass


def decode_audio(data: bytes, filename: str | None = None) -> np.ndarray:
    """Return 16 kHz mono float32 in [-1, 1]. Raises 415 if it can't decode."""
    suffix = os.path.splitext(filename or "")[1].lower()
    audio = _decode_soundfile(data)
    if audio is None:
        audio = _decode_ffmpeg(data, suffix)
    if audio is None:
        hint = "" if shutil.which("ffmpeg") else " (this server has no ffmpeg, so only wav, flac, ogg and mp3 decode)"
        raise APIError(
            415,
            "unsupported_audio",
            f"Could not decode the audio file. Send one of: {SUPPORTED_TEXT}{hint}.",
            param="file",
        )
    if audio.size == 0:
        raise APIError(400, "invalid_request", "The audio file contains no samples.", param="file")
    return audio


def frame_db(frame: np.ndarray) -> float:
    rms = float(np.sqrt(np.mean(np.square(frame), dtype=np.float64))) if frame.size else 0.0
    return 20.0 * np.log10(max(rms, 1e-10))


def split_on_silence(audio: np.ndarray, max_seconds: float = 30.0, search_seconds: float = 5.0) -> list[tuple[int, int]]:
    """Split into windows of at most max_seconds, cutting at the quietest
    30 ms frame within the last search_seconds of each window."""
    max_len = int(max_seconds * SAMPLE_RATE)
    if audio.size <= max_len:
        return [(0, audio.size)]
    search = int(search_seconds * SAMPLE_RATE)
    windows: list[tuple[int, int]] = []
    start = 0
    while audio.size - start > max_len:
        lo = start + max_len - search
        hi = start + max_len
        best, best_db = hi, float("inf")
        for pos in range(lo, hi - FRAME + 1, FRAME):
            db = frame_db(audio[pos:pos + FRAME])
            if db < best_db:
                best_db, best = db, pos + FRAME // 2
        windows.append((start, best))
        start = best
    windows.append((start, audio.size))
    return windows
