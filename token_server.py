# Token server for Nebula Call (Android app).
# pip install fastapi uvicorn livekit-api
# env: LIVEKIT_API_KEY, LIVEKIT_API_SECRET  (same as livekit.yaml keys)
#      AGENT_NAME (optional) -> only if your worker uses explicit dispatch (@server.rtc_session(agent_name=...))
# run: uvicorn token_server:app --host 0.0.0.0 --port 8093
# app setting "Token URL": http://<tailnet-host>:8093/token   (or funnel it)
import os
from datetime import timedelta

from fastapi import FastAPI, Query
from livekit import api

app = FastAPI()
KEY = os.environ["LIVEKIT_API_KEY"]
SECRET = os.environ["LIVEKIT_API_SECRET"]
AGENT_NAME = os.environ.get("AGENT_NAME", "").strip()


@app.get("/token")
def token(room: str = Query("nebula"), identity: str = Query("phone")):
    t = (
        api.AccessToken(KEY, SECRET)
        .with_identity(identity)
        .with_name(identity)
        .with_ttl(timedelta(hours=6))
        .with_grants(api.VideoGrants(room_join=True, room=room, can_publish=True, can_subscribe=True))
    )
    if AGENT_NAME:
        t = t.with_room_config(
            api.RoomConfiguration(agents=[api.RoomAgentDispatch(agent_name=AGENT_NAME)])
        )
    return {"token": t.to_jwt(), "room": room, "identity": identity}
