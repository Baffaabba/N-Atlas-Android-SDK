"""Run the server: `python -m natlas_server` or `natlas-server`.

If no API key is configured, a random one is generated for this run and
printed once to the console (not to the log).
"""

from __future__ import annotations

import argparse
import logging
import os
import secrets


def main() -> None:
    p = argparse.ArgumentParser(description="N-ATLAS SDK test server")
    p.add_argument("--host", default=os.environ.get("NATLAS_HOST", "127.0.0.1"))
    p.add_argument("--port", type=int, default=int(os.environ.get("NATLAS_PORT", "8000")))
    args = p.parse_args()

    from .config import Settings

    settings = Settings.from_env()
    logging.basicConfig(level=settings.log_level, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    if not settings.api_keys and not settings.api_key_hashes:
        key = "natlas-" + secrets.token_urlsafe(24)
        settings.api_keys = [key]
        print(f"\nNo NATLAS_API_KEYS set. Generated a key for this run only:\n\n    {key}\n", flush=True)

    import uvicorn

    from .app import create_app

    app = create_app(settings)
    # access_log off: our middleware logs metadata only
    uvicorn.run(app, host=args.host, port=args.port, access_log=False, log_level=settings.log_level.lower(),
                ws_ping_interval=20, ws_ping_timeout=20)


if __name__ == "__main__":
    main()
