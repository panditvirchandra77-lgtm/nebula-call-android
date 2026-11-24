# Token server + Sonia web call page for Nebula Call.
#
#   pip install fastapi uvicorn livekit-api
#   export LIVEKIT_API_KEY=...  LIVEKIT_API_SECRET=...      # same as livekit.yaml keys
#   export LIVEKIT_URL=wss://your-host.ts.net               # what the web page / app connects to
#   export AGENT_NAME=sonia                                 # optional, only for explicit agent dispatch
#   uvicorn token_server:app --host 0.0.0.0 --port 8093
#
# Routes
#   GET  /                  -> web/index.html  (Sonia voice call in any browser / Nebula App)
#   GET  /token?room=&identity=   -> {"url","token","accessToken","participantToken","participant_token","jwt","room","identity"}
#   POST /token  (JSON body {room, identity}) -> same
#   Android app setting "Token URL": http://<host>:8093/token
import os
from datetime import timedelta
from pathlib import Path

from fastapi import Body, FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from livekit import api

KEY = os.environ["LIVEKIT_API_KEY"]
SECRET = os.environ["LIVEKIT_API_SECRET"]
LK_URL = os.environ.get("LIVEKIT_URL", "").strip()
AGENT_NAME = os.environ.get("AGENT_NAME", "").strip()
WEB = Path(__file__).parent / "web"

app = FastAPI()
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


def _make(room: str, identity: str) -> dict:
    t = (
        api.AccessToken(KEY, SECRET)
        .with_identity(identity)
        .with_name(identity)
        .with_ttl(timedelta(hours=6))
        .with_grants(api.VideoGrants(room_join=True, room=room, can_publish=True, can_subscribe=True))
    )
    if AGENT_NAME:
        t = t.with_room_config(api.RoomConfiguration(agents=[api.RoomAgentDispatch(agent_name=AGENT_NAME)]))
    jwt = t.to_jwt()
    return {"url": LK_URL, "token": jwt, "accessToken": jwt, "participantToken": jwt,
            "participant_token": jwt, "jwt": jwt, "room": room, "identity": identity}


@app.get("/token")
def token_get(room: str = Query("nebula"), identity: str = Query("phone")):
    return _make(room, identity)


@app.post("/token")
def token_post(body: dict = Body(default={})):
    return _make(body.get("room") or body.get("room_name") or "nebula",
                 body.get("identity") or body.get("participant_identity") or "phone")


@app.get("/")
def index():
    return FileResponse(WEB / "index.html")


app.mount("/", StaticFiles(directory=WEB), name="web")
