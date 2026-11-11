package com.datezone.app;

public class ActiveCallManager {

    private static ActiveCallManager instance;

    private boolean isCallActive = false;
    private String partnerName = "Nguyên";
    private int partnerPhotoResId = R.drawable.discovery_photo_1;
    private boolean isVideoCall = false;

    private ActiveCallManager() {}

    public static synchronized ActiveCallManager getInstance() {
        if (instance == null) {
            instance = new ActiveCallManager();
        }
        return instance;
    }

    public void startCall(String name, int photoResId, boolean isVideoCall) {
        this.isCallActive = true;
        this.partnerName = name;
        this.partnerPhotoResId = photoResId;
        this.isVideoCall = isVideoCall;
    }

    public void endCall() {
        this.isCallActive = false;
        this.callId = 0;
        this.matchId = 0;
        this.photoUrl = "";
    }

    // ── Cuộc gọi thật (đã đăng ký với máy chủ) ──
    private long callId;
    private long matchId;
    private String photoUrl = "";

    public void setRemoteCall(long matchId, long callId, String photoUrl) {
        this.matchId = matchId;
        this.callId = callId;
        this.photoUrl = photoUrl == null ? "" : photoUrl;
    }

    public long getCallId() { return callId; }
    public long getMatchId() { return matchId; }
    public String getPhotoUrl() { return photoUrl; }

    public boolean isCallActive() {
        return isCallActive;
    }

    public String getPartnerName() {
        return partnerName != null ? partnerName : "Nguyên";
    }

    public int getPartnerPhotoResId() {
        return partnerPhotoResId != 0 ? partnerPhotoResId : R.drawable.discovery_photo_1;
    }

    public boolean isVideoCall() {
        return isVideoCall;
    }
}
