#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else "neon-highway")
java = root / "app/src/main/java/com/peter/neonhighway"

def replace_between(path: Path, start_marker: str, end_marker: str, replacement: str) -> None:
    text = path.read_text(encoding="utf-8")
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    path.write_text(text[:start] + replacement + text[end:], encoding="utf-8")

# Audio initialization and shutdown must be non-fatal on vendor-specific drivers.
synth = java / "SynthAudio.java"
replace_between(
    synth,
    "    void start() {",
    "    void setEnabled(boolean value) {",
    '''    synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(() -> {
            try {
                audioLoop();
            } catch (Throwable ignored) {
                // Audio hardware and vendor drivers can reject an otherwise valid
                // AudioTrack configuration. Music must fail silently, never crash
                // the complete game process.
                enabled = false;
            } finally {
                running = false;
                releaseOutput();
            }
        }, "NeonSynth");
        thread.setPriority(Thread.NORM_PRIORITY + 1);
        thread.start();
    }

    synchronized void stop() {
        running = false;
        AudioTrack localOutput = output;
        if (localOutput != null) {
            try {
                localOutput.pause();
                localOutput.flush();
            } catch (RuntimeException ignored) {
                // The audio device can disappear while the Activity is stopping.
            }
        }
        Thread t = thread;
        if (t != null && t != Thread.currentThread()) {
            t.interrupt();
            try {
                t.join(750);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        thread = null;
        releaseOutput();
    }

''')
replace_between(
    synth,
    "    private void audioLoop() {",
    "    private void synthesize(short[] out) {",
    '''    private void audioLoop() {
        int min = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) {
            throw new IllegalStateException("Stereo PCM output unavailable: " + min);
        }
        int bufferBytes = Math.max(min * 2, FRAMES * 2 * 2 * 4);
        AudioTrack created = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferBytes)
                .build();
        if (created.getState() != AudioTrack.STATE_INITIALIZED) {
            created.release();
            throw new IllegalStateException("AudioTrack did not initialize");
        }
        output = created;

        short[] buffer = new short[FRAMES * 2];
        created.play();
        while (running && !Thread.currentThread().isInterrupted()) {
            synthesize(buffer);
            int written = created.write(buffer, 0, buffer.length, AudioTrack.WRITE_BLOCKING);
            if (written == AudioTrack.ERROR_DEAD_OBJECT || written < 0) break;
        }
    }

    private synchronized void releaseOutput() {
        AudioTrack local = output;
        output = null;
        if (local == null) return;
        try {
            if (local.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) local.pause();
        } catch (RuntimeException ignored) {
        }
        try {
            local.flush();
        } catch (RuntimeException ignored) {
        }
        try {
            local.release();
        } catch (RuntimeException ignored) {
        }
    }

''')

# Start audio only after the View is attached; guard zero-sized frames/touch transforms.
game = java / "GameView.java"
text = game.read_text(encoding="utf-8")
text = text.replace("        audio.setEnabled(soundOn);\n        audio.start();\n        lastFrameNs", "        audio.setEnabled(soundOn);\n        lastFrameNs", 1)
needle = "    void resumeGame() {\n"
insert = '''    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        audio.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        audio.stop();
        super.onDetachedFromWindow();
    }

'''
text = text.replace(needle, insert + needle, 1)
text = text.replace("        lastFrameNs = System.nanoTime();\n        audio.setEnabled(soundOn);", "        lastFrameNs = System.nanoTime();\n        audio.start();\n        audio.setEnabled(soundOn);", 1)
text = text.replace("        super.onDraw(canvas);\n        long now", "        super.onDraw(canvas);\n        if (getWidth() <= 0 || getHeight() <= 0) return;\n        long now", 1)
# Defensive touch-coordinate conversion.
text = text.replace("    public boolean onTouchEvent(MotionEvent event) {\n        float x =", "    public boolean onTouchEvent(MotionEvent event) {\n        if (renderScale <= 0f || !Float.isFinite(renderScale)) return true;\n        float x =", 1)
game.write_text(text, encoding="utf-8")

# Runtime orientation and immersive UI are cosmetic and may not abort startup.
main = java / "MainActivity.java"
text = main.read_text(encoding="utf-8")
text = text.replace("import android.content.pm.ActivityInfo;\n", "")
text = text.replace("        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);\n", "")
replace_start = text.index("    private void hideSystemUi() {")
replace_end = text.index("    @Override\n    public void onWindowFocusChanged", replace_start)
new_hide = '''    private void hideSystemUi() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (RuntimeException ignored) {
            // Some vendor window managers briefly reject insets calls during rotation.
            // Fullscreen is cosmetic and must never prevent the game from starting.
        }
    }

'''
text = text[:replace_start] + new_hide + text[replace_end:]
main.write_text(text, encoding="utf-8")

build = root / "app/build.gradle"
text = build.read_text(encoding="utf-8")
text = text.replace("versionCode 1", "versionCode 2")
text = text.replace("versionName '1.0'", "versionName '1.1'")
build.write_text(text, encoding="utf-8")

print("Applied Neon Highway v1.1 crash-hardening patches")
