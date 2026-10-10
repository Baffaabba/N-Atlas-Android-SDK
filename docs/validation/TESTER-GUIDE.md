# Try the N-ATLAS SDK: tester guide

Thank you for testing. This takes about an hour.

The N-ATLAS SDK lets an Android app transcribe speech in Hausa, Igbo, Yoruba
and Nigerian English, and chat with N-ATLaS, Nigeria's language model. You
can use it from Kotlin or Flutter. It is an early build (API v0.1 draft).
Things will break. That is what we want to hear about.

## 1. What to install

- An Android phone (Android 7.0 or newer) with USB debugging on, and its cable.
- **Kotlin route:** Android Studio (recent stable) with JDK 17.
- **Flutter route:** Flutter stable and Android Studio (for the Android SDK).
- The repo: `git clone <repo-url>` (we send you the link).

You do not need a Hugging Face account or a GPU. The models run on our test server.

## 2. Point the SDK at our server

We send you two things privately: a **server URL** and a **test key**.
Never put them in a public repo, a screenshot or a chat group.

The server runs on Google Colab. Its address may change if the session
restarts. To check it is up, open `<server-url>/health` in your browser.
You should see `"status": "ok"`. If not, message us and we send a new URL.

- **Kotlin sample:** add to `android/local.properties` (this file is never committed):
  ```
  natlas.baseUrl=<server-url>
  natlas.apiKey=<test-key>
  ```
- **Flutter example:**
  ```
  flutter run --dart-define=NATLAS_BASE_URL=<server-url> --dart-define=NATLAS_API_KEY=<test-key>
  ```

(The exact property names may change before we send you the build. The
build's own README is the final word.)

## 3. Three tasks

**Task 1. Run the sample app.** Install it on your phone. Pick a language.
Record a short voice note and transcribe it. Then try live mic: speak and
watch the words appear.

**Task 2. Add the SDK to your own app.** A new blank app is fine. Follow the
README quickstart. Make one streaming chat call and show the reply on screen.
Note how long this took you and where you got stuck.

**Task 3. Build a small voice feature.** Add a button that runs the voice
pipeline: the user speaks, the app shows what it heard, then shows N-ATLaS's
reply. Use any of the four languages.

## 4. Send us your feedback

Copy this into an email or a document. Attach your screenshots.
Short answers are fine. Honest answers help most.

```
Name (as you want it shown publicly):
Role / company (optional):
Phone model and Android version:
Network (Wi-Fi, 4G, 3G):
Kotlin or Flutter:
OK to publish your name, feedback and screenshots in our NAIC entry? (yes / no)

Task 1, sample app
  What worked:
  What broke (what you did, what you saw):

Task 2, your own app
  Minutes from clone to first reply:
  What worked:
  What broke or confused you:

Task 3, voice feature
  What worked:
  What broke:

Language quality (if you speak Hausa, Igbo or Yoruba):
  Was the transcription right? Was the reply sensible?

Would you use this SDK in a real app? (yes / maybe / no) Why?
One thing we should fix first:

Screenshots attached (at least two: one from the sample, one from your app):
```

Please hide the server URL and key in any screenshot.

## About N-ATLAS

N-ATLAS is an initiative of the Federal Ministry of Communications,
Innovation and Digital Economy, and powered by Awarri Technologies.
Free use is limited to 1,000 active users in a rolling 30 days. N-ATLAS may
not be used for surveillance, profiling, disinformation or military purposes.
