"""Energy-based voice activity detection and utterance segmentation for
live audio.

Deliberately simple and dependency-free: a 30 ms frame is speech when its RMS
level is above `threshold_db` (dBFS) and above an adaptive noise floor plus
`margin_db`. An utterance starts at the first speech frame (with a short
pre-roll) and ends after `silence_end_s` of non-speech, or when it reaches
`max_utterance_s`.
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass

import numpy as np

from .audio import FRAME, SAMPLE_RATE, frame_db


@dataclass
class Utterance:
    start: float  # seconds since stream start
    end: float
    audio: np.ndarray
    speech_seconds: float


class UtteranceSegmenter:
    def __init__(
        self,
        threshold_db: float = -45.0,
        margin_db: float = 8.0,
        silence_end_s: float = 0.8,
        max_utterance_s: float = 30.0,
        preroll_s: float = 0.3,
    ) -> None:
        self.threshold_db = threshold_db
        self.margin_db = margin_db
        self.silence_end_frames = max(1, int(round(silence_end_s * SAMPLE_RATE / FRAME)))
        self.max_frames = int(max_utterance_s * SAMPLE_RATE / FRAME)
        self._preroll: deque[np.ndarray] = deque(maxlen=max(0, int(round(preroll_s * SAMPLE_RATE / FRAME))))
        self._pending = np.zeros(0, dtype=np.float32)
        self._frames_seen = 0  # frames consumed since stream start
        self._noise_db = -60.0
        self._utt: list[np.ndarray] | None = None
        self._utt_start_frame = 0
        self._speech_frames = 0
        self._trailing_silence = 0

    # -- public -----------------------------------------------------------
    @property
    def in_utterance(self) -> bool:
        return self._utt is not None

    @property
    def stream_seconds(self) -> float:
        return self._frames_seen * FRAME / SAMPLE_RATE

    def feed(self, samples: np.ndarray) -> list[Utterance]:
        """Consume samples; return utterances that finished."""
        done: list[Utterance] = []
        buf = np.concatenate([self._pending, samples.astype(np.float32, copy=False)])
        n_full = buf.size // FRAME
        for i in range(n_full):
            u = self._frame(buf[i * FRAME:(i + 1) * FRAME])
            if u is not None:
                done.append(u)
        self._pending = buf[n_full * FRAME:]
        return done

    def current(self) -> Utterance | None:
        """The open utterance so far, or None."""
        if self._utt is None:
            return None
        return self._make(self._utt, trim_trailing=False)

    def flush(self) -> Utterance | None:
        """End of stream: close the open utterance (including any partial frame)."""
        if self._pending.size and self._utt is not None:
            self._utt.append(self._pending)
        self._pending = np.zeros(0, dtype=np.float32)
        if self._utt is None:
            return None
        u = self._make(self._utt, trim_trailing=True)
        self._reset_utt()
        return u

    # -- internals --------------------------------------------------------
    def _is_speech(self, db: float) -> bool:
        return db > self.threshold_db and db > self._noise_db + self.margin_db

    def _frame(self, frame: np.ndarray) -> Utterance | None:
        db = frame_db(frame)
        speech = self._is_speech(db)
        idx = self._frames_seen
        self._frames_seen += 1
        if self._utt is None:
            if not speech:
                # track the noise floor only outside speech
                self._noise_db = 0.95 * self._noise_db + 0.05 * db if db > -100 else self._noise_db
                self._preroll.append(frame)
                return None
            self._utt = list(self._preroll) + [frame]
            self._utt_start_frame = idx - len(self._preroll)
            self._preroll.clear()
            self._speech_frames = 1
            self._trailing_silence = 0
            return None
        self._utt.append(frame)
        if speech:
            self._speech_frames += 1
            self._trailing_silence = 0
        else:
            self._trailing_silence += 1
        if self._trailing_silence >= self.silence_end_frames or len(self._utt) >= self.max_frames:
            u = self._make(self._utt, trim_trailing=True)
            self._reset_utt()
            return u
        return None

    def _make(self, frames: list[np.ndarray], trim_trailing: bool) -> Utterance:
        keep = frames
        if trim_trailing and self._trailing_silence > 0:
            # keep ~150 ms of the trailing silence so word endings survive
            drop = max(0, self._trailing_silence - 5)
            keep = frames[: len(frames) - drop] if drop else frames
        audio = np.concatenate(keep) if keep else np.zeros(0, dtype=np.float32)
        start = self._utt_start_frame * FRAME / SAMPLE_RATE
        return Utterance(
            start=round(start, 3),
            end=round(start + audio.size / SAMPLE_RATE, 3),
            audio=audio,
            speech_seconds=self._speech_frames * FRAME / SAMPLE_RATE,
        )

    def _reset_utt(self) -> None:
        self._utt = None
        self._speech_frames = 0
        self._trailing_silence = 0
