package com.simbridge.home.services;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * AudioBridge captures live in-call audio from the home phone's mic/earpiece
 * and streams it to the travel phone, while injecting received audio back.
 *
 * No SIP, no VoIP server needed — uses raw PCM over WebSocket.
 *
 * Flow:
 *   [Cellular call audio] → mic/speaker → AudioRecord → encode → WebSocket → travel phone
 *   [Travel phone mic]    → WebSocket → decode → AudioTrack → home phone speaker (plays into call)
 */
public class AudioBridge {

    private static final String TAG = "AudioBridge";

    private static final int SAMPLE_RATE    = 8000;   // 8kHz — standard voice quality
    private static final int CHANNEL_IN     = AudioFormat.CHANNEL_IN_MONO;
    private static final int CHANNEL_OUT    = AudioFormat.CHANNEL_OUT_MONO;
    private static final int ENCODING       = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE_MS = 20;     // 20ms chunks (standard for voice)

    private AudioRecord audioRecord;
    private AudioTrack  audioTrack;
    private Thread captureThread;
    private Thread playbackThread;
    private volatile boolean running = false;

    private final SignalingService signalingService;
    private final AudioManager audioManager;

    public interface AudioChunkListener {
        void onChunk(byte[] pcmData);
    }

    private AudioChunkListener chunkListener;

    public AudioBridge(android.content.Context context, SignalingService signalingService) {
        this.signalingService = signalingService;
        this.audioManager = (AudioManager) context.getSystemService(android.content.Context.AUDIO_SERVICE);
    }

    public void setChunkListener(AudioChunkListener listener) {
        this.chunkListener = listener;
    }

    public void start() {
        if (running) return;
        running = true;

        // Route audio through earpiece so it captures in-call audio
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        audioManager.setSpeakerphoneOn(false);

        int inBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING);
        int outBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING);

        // Capture: mic picks up what the caller is saying (we hear them, then forward to travel)
        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE, CHANNEL_IN, ENCODING, inBufferSize * 4);

        // Playback: inject travel person's voice into the call
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_OUT)
                        .build())
                .setBufferSizeInBytes(outBufferSize * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        audioRecord.startRecording();
        audioTrack.play();

        startCaptureThread(inBufferSize);
        Log.d(TAG, "AudioBridge started");
    }

    private void startCaptureThread(int bufferSize) {
        captureThread = new Thread(() -> {
            byte[] buffer = new byte[bufferSize];
            while (running) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0 && chunkListener != null) {
                    byte[] chunk = new byte[read];
                    System.arraycopy(buffer, 0, chunk, 0, read);
                    chunkListener.onChunk(chunk);
                }
            }
        }, "AudioCapture");
        captureThread.start();
    }

    /**
     * Called when we receive PCM audio from the travel phone.
     * Inject it into the AudioTrack so it plays into the cellular call.
     */
    public void injectFromTravel(byte[] pcmData) {
        if (audioTrack != null && running) {
            audioTrack.write(pcmData, 0, pcmData.length);
        }
    }

    public void stop() {
        running = false;
        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }
            if (captureThread != null) captureThread.interrupt();
        } catch (Exception e) {
            Log.e(TAG, "stop error: " + e.getMessage());
        }
        audioManager.setMode(AudioManager.MODE_NORMAL);
        Log.d(TAG, "AudioBridge stopped");
    }

    public boolean isRunning() { return running; }
}
