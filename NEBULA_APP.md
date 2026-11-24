# Sonia call inside the Nebula app (as a Nebula "App")

Nebula's own call button always runs Nebula's model, so it can't be pointed at Sonia.
Instead we run Sonia's **web call page** (`web/index.html`, served by `token_server.py`)
as a Nebula App. Apps open inside the Nebula phone app (channel -> Apps tab).

## Paste this to Nebula

Phone: Settings -> Apps -> New -> "Build with" = an agent that can use the **Home Computer** device.
(or in a channel chat: "Nebula, create me an app that ...")

```
Create me an app called "Sonia Call" that runs on my Home Computer device.

1. git clone https://github.com/panditvirchandra77-lgtm/nebula-call-android ~/sonia-call
   (private repo: use my GitHub access, or ask me for a token)
2. cd ~/sonia-call && pip install fastapi uvicorn livekit-api
3. Start it and keep it running (supervisord, same as nebula-token):
   LIVEKIT_API_KEY=<from livekit.yaml> LIVEKIT_API_SECRET=<from livekit.yaml> \
   LIVEKIT_URL=<the public wss:// URL of our LiveKit server, same one the Android app uses> \
   uvicorn token_server:app --host 0.0.0.0 --port 8094
   (8093 is already used by nebula-token; if AGENT_NAME is used for dispatch there, set it here too)
4. Make port 8094 this app's address (HTTPS).
5. Check: the app's address shows a "Sonia" page with a Call button, and
   <address>/token?room=nebula&identity=test returns JSON with "url" and "token".
```

## Using it

Open the channel -> **Apps** -> **Sonia Call** -> tap **Call** -> allow microphone.
Settings (gear top-right) lets you change LiveKit URL, room, identity, token path; they're saved.
You can also pass them in the URL: `?room=nebula&identity=jeetu-web&url=wss://...`

## Known risk

Microphone access inside the Nebula app's embedded page is not guaranteed.
If the page says "Mic permission nahi mili", open the same app address in Chrome instead
(same page, works the same), or use the Android APK.

## Same page anywhere

The page needs nothing Nebula-specific. Any HTTPS address serving `token_server.py` works
in Chrome on any phone or laptop. Mic requires HTTPS (or localhost).
