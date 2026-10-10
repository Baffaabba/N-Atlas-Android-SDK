from __future__ import annotations

import numpy as np

from natlas_server.audio import split_on_silence
from natlas_server.chat import StopTrimmer
from natlas_server.config import Settings
from natlas_server.registry import resolve_asr
from natlas_server.vad import UtteranceSegmenter

from .conftest import silence, tone


def run_trimmer(stops, pieces):
    t = StopTrimmer(stops)
    out = "".join(t.push(p) for p in pieces) + t.flush()
    return out, t.stopped


def test_stop_trimmer_across_pieces():
    assert run_trimmer(["END"], ["hello E", "N", "D more"]) == ("hello ", True)
    assert run_trimmer(["END"], ["hello E", "NOUGH"]) == ("hello ENOUGH", False)
    assert run_trimmer([], ["a", "b"]) == ("ab", False)
    assert run_trimmer(["x", "long stop"], ["abc long st", "op x"]) == ("abc ", True)


def test_split_short_audio_single_window():
    assert split_on_silence(np.zeros(16000 * 10, dtype=np.float32)) == [(0, 160000)]


def test_vad_segments():
    seg = UtteranceSegmenter()
    out = seg.feed(np.concatenate([silence(1.0), tone(1.0), silence(1.0)]))
    assert len(out) == 1
    u = out[0]
    assert abs(u.start - 0.7) < 0.05 and abs(u.speech_seconds - 1.0) < 0.05
    assert seg.flush() is None


def test_resolve_asr():
    assert resolve_asr("HA", None) == ("ha", "NCAIR1/Hausa-ASR")
    assert resolve_asr(None, "NCAIR1/NigerianAccentedEnglish") == ("en", "NCAIR1/NigerianAccentedEnglish")


def test_settings_from_env():
    s = Settings.from_env({"NATLAS_API_KEYS": "a, b", "NATLAS_BACKEND": "transformers",
                           "NATLAS_LLM_BACKEND": "none", "NATLAS_ASR_LANGUAGES": "ha,yo",
                           "NATLAS_USER_CAP": "5"})
    assert s.api_keys == ["a", "b"] and s.asr_backend == "transformers" and s.llm_backend == "none"
    assert s.asr_languages == ["ha", "yo"] and s.user_cap == 5
    s.validate()
