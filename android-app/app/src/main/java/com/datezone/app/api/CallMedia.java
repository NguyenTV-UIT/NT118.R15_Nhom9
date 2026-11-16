package com.datezone.app.api;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.datezone.app.ActiveCallManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Âm thanh / hình ảnh của 1 cuộc gọi bằng WebRTC (đi thẳng giữa 2 máy, máy chủ chỉ chuyển gói tín hiệu).
 *
 * Tín hiệu đi qua WebSocket, sự kiện call.signal { callId, signal }:
 *   người nhận -> {type:"ready"}            (đã dựng xong, sẵn sàng nhận offer; gửi lặp lại tới khi có offer)
 *   người gọi  -> {type:"offer", sdp}
 *   người nhận -> {type:"answer", sdp}
 *   cả hai     -> {type:"candidate", candidate, sdpMid, sdpMLineIndex}
 *
 * Đối tượng này sống độc lập với màn hình cuộc gọi (thu nhỏ cuộc gọi vẫn nghe được); màn hình chỉ gắn / gỡ khung video.
 */
public final class CallMedia {
    public interface Listener {
        /** 2 máy đã thông nhau (nghe được). */
        void onConnected();
        /** Người kia bật / tắt hình. */
        void onRemoteVideo(boolean available);
        void onFailed(String message);
    }

    private static final String TAG = "CallMedia";
    private static final int READY_RETRIES = 15;
    private static CallMedia current;
    private static boolean factoryInitialized;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context appContext;
    private final long callId;
    private final boolean video;
    private final boolean initiator;
    private final EglBase eglBase;
    private final AudioManager audioManager;
    private final List<IceCandidate> pendingCandidates = new ArrayList<>();

    private PeerConnectionFactory factory;
    private PeerConnection peer;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private VideoTrack remoteVideoTrack;
    private AudioTrack remoteAudioTrack;
    private CameraVideoCapturer capturer;
    private SurfaceTextureHelper textureHelper;
    private SurfaceViewRenderer localView;
    private SurfaceViewRenderer remoteView;
    private Listener listener;
    private boolean offerSent;
    private boolean remoteDescriptionSet;
    private boolean connected;
    private boolean released;
    private int readySent;
    private int previousAudioMode = AudioManager.MODE_NORMAL;
    private boolean previousSpeaker;

    private final Realtime.Listener signalListener = this::onRealtimeEvent;
    private final Runnable readyTask = new Runnable() {
        @Override public void run() {
            if (released || remoteDescriptionSet || readySent >= READY_RETRIES) return;
            readySent++;
            sendSignal(signal("ready"));
            main.postDelayed(this, 2000);
        }
    };

    /** Cuộc gọi đang có âm thanh / hình ảnh (null nếu không có). */
    public static CallMedia current() {
        return current;
    }

    /**
     * Bắt đầu phần âm thanh / hình ảnh cho cuộc gọi đã được nhấc máy. Gọi lại với cùng callId thì trả về đối tượng đang chạy.
     * initiator = true: mình là người gọi (sẽ tạo offer khi bên kia báo ready).
     */
    public static CallMedia start(Context context, long callId, boolean video, boolean initiator, JSONArray iceServers) {
        if (current != null && current.callId == callId && !current.released) return current;
        if (current != null) current.release();
        CallMedia media = new CallMedia(context.getApplicationContext(), callId, video, initiator);
        current = media;
        try {
            media.setUp(iceServers);
        } catch (RuntimeException | UnsatisfiedLinkError e) {
            Log.e(TAG, "Không khởi tạo được WebRTC", e);
            media.fail("Thiết bị không khởi tạo được cuộc gọi (WebRTC)");
        }
        return media;
    }

    private CallMedia(Context appContext, long callId, boolean video, boolean initiator) {
        this.appContext = appContext;
        this.callId = callId;
        this.video = video;
        this.initiator = initiator;
        this.eglBase = EglBase.create();
        this.audioManager = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public long getCallId() { return callId; }
    public boolean isConnected() { return connected; }
    public boolean hasRemoteVideo() { return remoteVideoTrack != null; }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ═══════════════ KHỞI TẠO ═══════════════

    private void setUp(JSONArray iceServers) {
        if (!factoryInitialized) {
            PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions());
            factoryInitialized = true;
        }
        factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBase.getEglBaseContext()))
                .createPeerConnectionFactory();

        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(parseIceServers(iceServers));
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        config.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        peer = factory.createPeerConnection(config, new PeerObserver());
        if (peer == null) throw new IllegalStateException("createPeerConnection trả về null");

        List<String> streamIds = Collections.singletonList("datezone");
        audioSource = factory.createAudioSource(new MediaConstraints());
        audioTrack = factory.createAudioTrack("audio0", audioSource);
        peer.addTrack(audioTrack, streamIds);

        if (video && hasPermission(android.Manifest.permission.CAMERA)) startCamera(streamIds);

        if (audioManager != null) {
            previousAudioMode = audioManager.getMode();
            previousSpeaker = audioManager.isSpeakerphoneOn();
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
        }

        Realtime.get().addListener(signalListener);
        if (!initiator) main.post(readyTask);
    }

    private boolean hasPermission(String permission) {
        return appContext.checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera(List<String> streamIds) {
        Camera2Enumerator enumerator = new Camera2Enumerator(appContext);
        String chosen = null;
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(name)) { chosen = name; break; }
            if (chosen == null) chosen = name;
        }
        if (chosen == null) return; // máy không có camera: gọi chỉ có tiếng
        capturer = enumerator.createCapturer(chosen, null);
        if (capturer == null) return;
        textureHelper = SurfaceTextureHelper.create("datezone-capture", eglBase.getEglBaseContext());
        videoSource = factory.createVideoSource(false);
        capturer.initialize(textureHelper, appContext, videoSource.getCapturerObserver());
        capturer.startCapture(640, 480, 24);
        videoTrack = factory.createVideoTrack("video0", videoSource);
        peer.addTrack(videoTrack, streamIds);
        if (localView != null) videoTrack.addSink(localView);
    }

    /** [{ urls: "stun:..." | [..], username?, credential? }] -> danh sách máy chủ ICE. Rỗng thì dùng STUN công cộng của Google. */
    private static List<PeerConnection.IceServer> parseIceServers(JSONArray servers) {
        List<PeerConnection.IceServer> result = new ArrayList<>();
        for (int i = 0; servers != null && i < servers.length(); i++) {
            JSONObject server = servers.optJSONObject(i);
            if (server == null) continue;
            List<String> urls = new ArrayList<>();
            JSONArray array = server.optJSONArray("urls");
            if (array != null) {
                for (int j = 0; j < array.length(); j++) urls.add(array.optString(j));
            } else if (!server.optString("urls").isEmpty()) {
                urls.add(server.optString("urls"));
            }
            if (urls.isEmpty()) continue;
            PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder(urls);
            if (!server.optString("username").isEmpty()) builder.setUsername(server.optString("username"));
            if (!server.optString("credential").isEmpty()) builder.setPassword(server.optString("credential"));
            result.add(builder.createIceServer());
        }
        if (result.isEmpty()) result.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        return result;
    }

    // ═══════════════ TÍN HIỆU ═══════════════

    private static JSONObject signal(String type) {
        try { return new JSONObject().put("type", type); }
        catch (JSONException e) { return new JSONObject(); }
    }

    private void sendSignal(JSONObject signal) {
        try {
            Realtime.get().send("call.signal", new JSONObject().put("callId", callId).put("signal", signal));
        } catch (JSONException ignored) { }
    }

    private void onRealtimeEvent(String event, JSONObject data) {
        if (released) return;
        if ("call.signal".equals(event)) {
            if (data.optLong("callId") != callId) return;
            JSONObject signal = data.optJSONObject("signal");
            if (signal != null) onSignal(signal);
        } else if ("call.ended".equals(event) || "call.cancelled".equals(event) || "call.rejected".equals(event) || "call.missed".equals(event)) {
            // Cuộc gọi kết thúc trong lúc màn hình cuộc gọi đang thu nhỏ: tự dọn dẹp
            JSONObject call = data.optJSONObject("call");
            if (call != null && call.optLong("id") == callId) {
                ActiveCallManager.getInstance().endCall();
                release();
            }
        }
    }

    private void onSignal(JSONObject signal) {
        if (peer == null) return;
        String type = signal.optString("type");
        if ("ready".equals(type)) {
            if (initiator && !offerSent) createOffer();
        } else if ("offer".equals(type) || "answer".equals(type)) {
            if ("offer".equals(type) && initiator) return;
            if ("answer".equals(type) && (!initiator || remoteDescriptionSet)) return;
            SessionDescription description = new SessionDescription(
                    "offer".equals(type) ? SessionDescription.Type.OFFER : SessionDescription.Type.ANSWER, signal.optString("sdp"));
            peer.setRemoteDescription(new SimpleSdpObserver("setRemoteDescription") {
                @Override public void onSetSuccess() {
                    main.post(() -> {
                        remoteDescriptionSet = true;
                        drainCandidates();
                        if ("offer".equals(type)) createAnswer();
                    });
                }
            }, description);
        } else if ("candidate".equals(type)) {
            IceCandidate candidate = new IceCandidate(signal.optString("sdpMid"), signal.optInt("sdpMLineIndex"), signal.optString("candidate"));
            if (remoteDescriptionSet) peer.addIceCandidate(candidate);
            else pendingCandidates.add(candidate);
        }
    }

    private void drainCandidates() {
        if (peer == null) return;
        for (IceCandidate candidate : pendingCandidates) peer.addIceCandidate(candidate);
        pendingCandidates.clear();
    }

    private MediaConstraints offerConstraints() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", video ? "true" : "false"));
        return constraints;
    }

    private void createOffer() {
        offerSent = true;
        peer.createOffer(new SimpleSdpObserver("createOffer") {
            @Override public void onCreateSuccess(SessionDescription description) {
                main.post(() -> setLocalAndSend(description));
            }
        }, offerConstraints());
    }

    private void createAnswer() {
        if (peer == null) return;
        peer.createAnswer(new SimpleSdpObserver("createAnswer") {
            @Override public void onCreateSuccess(SessionDescription description) {
                main.post(() -> setLocalAndSend(description));
            }
        }, offerConstraints());
    }

    private void setLocalAndSend(SessionDescription description) {
        if (peer == null) return;
        peer.setLocalDescription(new SimpleSdpObserver("setLocalDescription"), description);
        try {
            sendSignal(signal(description.type.canonicalForm()).put("sdp", description.description));
        } catch (JSONException ignored) { }
    }

    // ═══════════════ ĐIỀU KHIỂN TỪ MÀN HÌNH ═══════════════

    /** Gắn khung video của màn hình cuộc gọi (gọi lại mỗi lần màn hình được mở). */
    public void attachViews(SurfaceViewRenderer local, SurfaceViewRenderer remote) {
        detachViews();
        if (released) return;
        localView = local;
        remoteView = remote;
        if (local != null) {
            local.init(eglBase.getEglBaseContext(), null);
            local.setMirror(true);
            local.setZOrderMediaOverlay(true);
            if (videoTrack != null) videoTrack.addSink(local);
        }
        if (remote != null) {
            remote.init(eglBase.getEglBaseContext(), null);
            if (remoteVideoTrack != null) remoteVideoTrack.addSink(remote);
        }
    }

    public void detachViews() {
        if (localView != null) {
            if (videoTrack != null) videoTrack.removeSink(localView);
            localView.release();
            localView = null;
        }
        if (remoteView != null) {
            if (remoteVideoTrack != null) remoteVideoTrack.removeSink(remoteView);
            remoteView.release();
            remoteView = null;
        }
    }

    public boolean hasLocalVideo() { return videoTrack != null; }

    public void setMicEnabled(boolean enabled) {
        if (audioTrack != null) audioTrack.setEnabled(enabled);
    }

    public void setCameraEnabled(boolean enabled) {
        if (videoTrack != null) videoTrack.setEnabled(enabled);
    }

    public void switchCamera() {
        if (capturer != null) capturer.switchCamera(null);
    }

    /** 0: loa ngoài, 1: loa trong (áp tai), 2: tắt tiếng người kia. */
    public void setSpeakerState(int state) {
        if (audioManager != null) audioManager.setSpeakerphoneOn(state == 0);
        if (remoteAudioTrack != null) remoteAudioTrack.setEnabled(state != 2);
    }

    private void fail(String message) {
        main.post(() -> {
            if (listener != null) listener.onFailed(message);
        });
    }

    /** Kết thúc: tắt mic / camera, đóng kết nối, trả lại chế độ âm thanh. Gọi nhiều lần không sao. */
    public void release() {
        if (released) return;
        released = true;
        if (current == this) current = null;
        main.removeCallbacks(readyTask);
        Realtime.get().removeListener(signalListener);
        listener = null;
        detachViews();
        try {
            if (capturer != null) {
                try { capturer.stopCapture(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                capturer.dispose();
            }
            if (peer != null) peer.dispose(); // giải phóng luôn các track đã add
            if (videoSource != null) videoSource.dispose();
            if (audioSource != null) audioSource.dispose();
            if (textureHelper != null) textureHelper.dispose();
            if (factory != null) factory.dispose();
            eglBase.release();
        } catch (RuntimeException e) {
            Log.w(TAG, "Lỗi khi giải phóng WebRTC", e);
        }
        peer = null;
        capturer = null;
        videoTrack = null;
        audioTrack = null;
        remoteVideoTrack = null;
        remoteAudioTrack = null;
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(previousSpeaker);
            audioManager.setMode(previousAudioMode);
        }
    }

    // ═══════════════ CALLBACK CỦA WEBRTC (chạy trên luồng riêng -> chuyển về luồng giao diện) ═══════════════

    private final class PeerObserver implements PeerConnection.Observer {
        @Override public void onSignalingChange(PeerConnection.SignalingState state) { }
        @Override public void onIceConnectionReceivingChange(boolean receiving) { }
        @Override public void onIceGatheringChange(PeerConnection.IceGatheringState state) { }
        @Override public void onIceCandidatesRemoved(IceCandidate[] candidates) { }
        @Override public void onAddStream(MediaStream stream) { }
        @Override public void onRemoveStream(MediaStream stream) { }
        @Override public void onDataChannel(DataChannel channel) { }
        @Override public void onRenegotiationNeeded() { }

        @Override public void onIceCandidate(IceCandidate candidate) {
            try {
                sendSignal(signal("candidate").put("candidate", candidate.sdp)
                        .put("sdpMid", candidate.sdpMid).put("sdpMLineIndex", candidate.sdpMLineIndex));
            } catch (JSONException ignored) { }
        }

        @Override public void onIceConnectionChange(PeerConnection.IceConnectionState state) {
            main.post(() -> {
                if (released) return;
                if (state == PeerConnection.IceConnectionState.CONNECTED || state == PeerConnection.IceConnectionState.COMPLETED) {
                    if (!connected) {
                        connected = true;
                        if (listener != null) listener.onConnected();
                    }
                } else if (state == PeerConnection.IceConnectionState.FAILED) {
                    if (listener != null) listener.onFailed("Không kết nối được âm thanh giữa 2 máy (mạng chặn kết nối trực tiếp)");
                }
            });
        }

        @Override public void onAddTrack(RtpReceiver receiver, MediaStream[] streams) {
            MediaStreamTrack track = receiver.track();
            main.post(() -> {
                if (released) return;
                if (track instanceof VideoTrack) {
                    remoteVideoTrack = (VideoTrack) track;
                    if (remoteView != null) remoteVideoTrack.addSink(remoteView);
                    if (listener != null) listener.onRemoteVideo(true);
                } else if (track instanceof AudioTrack) {
                    remoteAudioTrack = (AudioTrack) track;
                }
            });
        }
    }

    private class SimpleSdpObserver implements SdpObserver {
        private final String step;

        SimpleSdpObserver(String step) { this.step = step; }

        @Override public void onCreateSuccess(SessionDescription description) { }
        @Override public void onSetSuccess() { }
        @Override public void onCreateFailure(String error) {
            Log.w(TAG, step + " lỗi: " + error);
            fail("Không thiết lập được cuộc gọi (" + step + ")");
        }
        @Override public void onSetFailure(String error) {
            Log.w(TAG, step + " lỗi: " + error);
            fail("Không thiết lập được cuộc gọi (" + step + ")");
        }
    }
}
