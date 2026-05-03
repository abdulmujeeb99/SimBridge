package com.simbridge.home.services;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Handles encoding/decoding PCM audio for WebSocket transport.
 * PCM bytes → Base64 JSON → WebSocket → Base64 decode → PCM bytes
 *
 * Works alongside SignalingService using the same WebSocket connection.
 * Audio messages use type: "audio_chunk" to distinguish from signaling.
 */
public class AudioStreamService {

    private static final String TAG = "AudioStreamService";

    private final SignalingService signalingService;
    private final AudioBridge audioBridge;
    private boolean active = false;

    public AudioStreamService(Context context, SignalingService signalingService) {
        this.signalingService = signalingService;
        this.audioBridge = new AudioBridge(context, signalingService);

        // When AudioBridge captures a chunk from mic, send it to travel phone
        audioBridge.setChunkListener(pcmData -> {
            if (active) sendAudioChunk(pcmData);
        });
    }

    public void startStreaming() {
        active = true;
        audioBridge.start();
        Log.d(TAG, "Audio streaming started");
    }

    public void stopStreaming() {
        active = false;
        audioBridge.stop();
        Log.d(TAG, "Audio streaming stopped");
    }

    /** Called by SignalingService when an audio_chunk message arrives from travel phone */
    public void onAudioChunkReceived(String json) {
        try {
            JsonObject msg = JsonParser.parseString(json).getAsJsonObject();
            String b64 = msg.get("data").getAsString();
            byte[] pcm = Base64.decode(b64, Base64.NO_WRAP);
            audioBridge.injectFromTravel(pcm);
        } catch (Exception e) {
            Log.e(TAG, "onAudioChunkReceived: " + e.getMessage());
        }
    }

    private void sendAudioChunk(byte[] pcm) {
        String b64 = Base64.encodeToString(pcm, Base64.NO_WRAP);
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "audio_chunk");
        msg.addProperty("data", b64);
        signalingService.send(msg.toString());
    }

    public boolean isActive() { return active; }
}
