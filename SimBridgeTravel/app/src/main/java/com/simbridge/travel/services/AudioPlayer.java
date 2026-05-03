package com.simbridge.travel.services;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Base64;
import android.util.Log;

import com.google.gson.JsonObject;

/**
 * Travel phone audio handler:
 *   - Plays received PCM audio from home phone (caller's voice)
 *   - Captures mic audio and sends it back to home phone (your voice)
 */
public class AudioPlayer {

    private static final String TAG = "AudioPlayer";
    private static final int SAMPLE_RATE  = 8000;
    private static final int CHANNEL_IN   = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT  = AudioFormat.CHANNEL_OUT_MONO;
    private static final int ENCODING     = AudioFormat.ENCODING_PCM_16BIT;

    private AudioTrack  audioTrack;
    private AudioRecord audioRecord;
    private Thread captureThread;
    private volatile boolean running = false;

    private final ConnectionService connectionService;
    private final AudioManager audioManager;

    public AudioPlayer(Context context, ConnectionService connectionService) {
        this.connectionService = connectionService;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    public void start() {
        if (running) return;
        running = true;

        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        int outBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING);
        int inBuf  = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING);

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(ENCODING).setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT).build())
                .setBufferSizeInBytes(outBuf * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE, CHANNEL_IN, ENCODING, inBuf * 4);

        audioTrack.play();
        audioRecord.startRecording();

        // Capture local mic → send to home phone
        captureThread = new Thread(() -> {
            byte[] buf = new byte[inBuf];
            while (running) {
                int read = audioRecord.read(buf, 0, buf.length);
                if (read > 0) {
                    byte[] chunk = new byte[read];
                    System.arraycopy(buf, 0, chunk, 0, read);
                    sendMicChunk(chunk);
                }
            }
        }, "TravelMicCapture");
        captureThread.start();

        Log.d(TAG, "AudioPlayer started");
    }

    /** Called when we receive PCM from home phone — play it in earpiece */
    public void onAudioChunkReceived(String json) {
        try {
            JsonObject msg = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            byte[] pcm = Base64.decode(msg.get("data").getAsString(), Base64.NO_WRAP);
            if (audioTrack != null && running) {
                audioTrack.write(pcm, 0, pcm.length);
            }
        } catch (Exception e) {
            Log.e(TAG, "onAudioChunkReceived: " + e.getMessage());
        }
    }

    private void sendMicChunk(byte[] pcm) {
        String b64 = Base64.encodeToString(pcm, Base64.NO_WRAP);
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "audio_chunk");
        msg.addProperty("data", b64);
        connectionService.send(msg.toString());
    }

    public void stop() {
        running = false;
        try {
            if (audioRecord != null) { audioRecord.stop(); audioRecord.release(); audioRecord = null; }
            if (audioTrack  != null) { audioTrack.stop();  audioTrack.release();  audioTrack  = null; }
            if (captureThread != null) captureThread.interrupt();
        } catch (Exception e) {
            Log.e(TAG, "stop: " + e.getMessage());
        }
        audioManager.setMode(AudioManager.MODE_NORMAL);
        Log.d(TAG, "AudioPlayer stopped");
    }
}
