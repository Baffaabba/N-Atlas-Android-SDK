"""N-ATLaS through Hugging Face transformers.

On CUDA it loads in 4-bit NF4 via bitsandbytes by default (~5.7 GB VRAM for
weights). Uses the repo's Llama-3 chat template. Streaming uses
TextIteratorStreamer. One generation runs at a time (a single GPU).
"""

from __future__ import annotations

import datetime as _dt
import logging
import threading
from typing import Any, Iterator

from .base import ChatParams, ContextTooLong, StreamEnd, StreamItem
from .hf_download import LLM_FILES, download

log = logging.getLogger("natlas.llm")


def _dtype_kwarg(transformers_mod: Any, dtype: Any) -> dict[str, Any]:
    # transformers >= 4.56 prefers `dtype`; older versions only know `torch_dtype`.
    from packaging.version import Version

    if Version(transformers_mod.__version__) >= Version("4.56.0"):
        return {"dtype": dtype}
    return {"torch_dtype": dtype}


class TransformersLLM:
    def __init__(
        self,
        model_id: str,
        quantization: str = "4bit",
        device: str = "auto",
        max_context: int = 8192,
        cache_dir: str | None = None,
    ) -> None:
        import torch
        import transformers
        from transformers import AutoModelForCausalLM, AutoTokenizer

        self.model_id = model_id
        self.max_context = max_context
        self._lock = threading.Lock()

        cuda = torch.cuda.is_available() and device in ("auto", "cuda")
        if device == "cuda" and not torch.cuda.is_available():
            raise RuntimeError("NATLAS_DEVICE=cuda but no CUDA GPU is available")
        if not cuda:
            raise RuntimeError(
                "N-ATLaS (8B) needs a CUDA GPU in this server. Use NATLAS_LLM_BACKEND=fake or none on CPU."
            )

        path = download(model_id, LLM_FILES, cache_dir)
        compute = torch.bfloat16 if torch.cuda.is_bf16_supported() else torch.float16
        kwargs: dict[str, Any] = {"device_map": "auto", **_dtype_kwarg(transformers, compute)}
        if quantization in ("4bit", "8bit"):
            from transformers import BitsAndBytesConfig

            if quantization == "4bit":
                kwargs["quantization_config"] = BitsAndBytesConfig(
                    load_in_4bit=True,
                    bnb_4bit_quant_type="nf4",
                    bnb_4bit_compute_dtype=compute,
                    bnb_4bit_use_double_quant=True,
                )
                qname = "4bit-nf4"
            else:
                kwargs["quantization_config"] = BitsAndBytesConfig(load_in_8bit=True)
                qname = "8bit"
        else:
            qname = str(compute).replace("torch.", "")

        log.info("loading %s (%s)", model_id, qname)
        self.tokenizer = AutoTokenizer.from_pretrained(path)
        self.model = AutoModelForCausalLM.from_pretrained(path, **kwargs)
        self.model.eval()
        self.device = self.model.device

        eos = self.model.generation_config.eos_token_id
        eos_ids = list(eos) if isinstance(eos, (list, tuple)) else ([eos] if eos is not None else [])
        eot = self.tokenizer.convert_tokens_to_ids("<|eot_id|>")
        if isinstance(eot, int) and eot >= 0 and eot not in eos_ids:
            eos_ids.append(eot)
        self.eos_ids = eos_ids
        self.pad_id = self.tokenizer.pad_token_id if self.tokenizer.pad_token_id is not None else eos_ids[0]
        self.info: dict[str, Any] = {
            "backend": "transformers",
            "quantization": qname,
            "device": torch.cuda.get_device_name(0),
            "max_context": max_context,
        }

    def _prompt_ids(self, messages: list[dict[str, str]]) -> Any:
        date = _dt.date.today().strftime("%d %b %Y")
        try:
            text = self.tokenizer.apply_chat_template(
                messages, add_generation_prompt=True, tokenize=False, date_string=date
            )
        except TypeError:
            text = self.tokenizer.apply_chat_template(messages, add_generation_prompt=True, tokenize=False)
        return self.tokenizer(text, return_tensors="pt", add_special_tokens=False).to(self.device)

    def stream(self, messages: list[dict[str, str]], params: ChatParams, cancel: threading.Event) -> Iterator[StreamItem]:
        import torch
        from transformers import StoppingCriteria, StoppingCriteriaList, TextIteratorStreamer

        inputs = self._prompt_ids(messages)
        prompt_tokens = int(inputs["input_ids"].shape[1])
        if prompt_tokens + params.max_tokens > self.max_context:
            raise ContextTooLong(
                f"The messages are {prompt_tokens} tokens; with max_tokens={params.max_tokens} that passes "
                f"this server's context limit of {self.max_context}. Shorten the history or lower max_tokens."
            )

        class _Cancel(StoppingCriteria):
            def __call__(self, input_ids: Any, scores: Any, **kw: Any) -> bool:  # type: ignore[override]
                return cancel.is_set()

        streamer = TextIteratorStreamer(self.tokenizer, skip_prompt=True, skip_special_tokens=True)
        gen: dict[str, Any] = dict(
            **inputs,
            max_new_tokens=params.max_tokens,
            repetition_penalty=params.repetition_penalty,
            eos_token_id=self.eos_ids,
            pad_token_id=self.pad_id,
            streamer=streamer,
            stopping_criteria=StoppingCriteriaList([_Cancel()]),
        )
        if params.temperature and params.temperature > 0:
            gen.update(do_sample=True, temperature=params.temperature)
            if params.top_p is not None:
                gen["top_p"] = params.top_p
        else:
            gen.update(do_sample=False, temperature=None, top_p=None)
        if params.stop:
            gen.update(stop_strings=params.stop, tokenizer=self.tokenizer)

        result: dict[str, Any] = {}

        def run() -> None:
            try:
                with self._lock, torch.inference_mode():
                    if cancel.is_set():
                        streamer.end()
                        return
                    result["out"] = self.model.generate(**gen)
            except BaseException as e:  # surfaced to the consumer below
                result["error"] = e
                streamer.end()

        t = threading.Thread(target=run, name="natlas-generate", daemon=True)
        t.start()
        completed = False
        try:
            for piece in streamer:
                if piece:
                    yield piece
            completed = True
        finally:
            if not completed:  # consumer went away: stop generating, free the GPU
                cancel.set()
        t.join()
        if "error" in result:
            raise result["error"]
        out = result.get("out")
        completion = int(out.shape[1]) - prompt_tokens if out is not None else 0
        last = int(out[0, -1]) if out is not None and out.shape[1] > prompt_tokens else None
        finish = "length" if completion >= params.max_tokens and last not in self.eos_ids else "stop"
        yield StreamEnd(finish, prompt_tokens, completion)
