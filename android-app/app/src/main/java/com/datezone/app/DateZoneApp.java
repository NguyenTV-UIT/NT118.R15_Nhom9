package com.datezone.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.datezone.app.api.ChatUi;
import com.datezone.app.api.IncomingCalls;
import com.datezone.app.api.Realtime;

import org.json.JSONObject;

public class DateZoneApp extends Application {
    private static final long BACKGROUND_GRACE_MS = 5000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int startedActivities;
    private Activity currentActivity;

    /** App xuống nền quá vài giây thì ngắt realtime (trừ khi đang trong cuộc gọi). */
    private final Runnable stopRealtime = () -> {
        if (startedActivities == 0 && !ActiveCallManager.getInstance().isCallActive()) Realtime.get().stop();
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Realtime.get().addListener(this::onRealtimeEvent);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(Activity activity) {
                startedActivities++;
                handler.removeCallbacks(stopRealtime);
                Realtime.get().start(DateZoneApp.this);
            }

            @Override
            public void onActivityResumed(Activity activity) {
                currentActivity = activity;
                OngoingCallBarHelper.updateOngoingCallBar(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (currentActivity == activity) currentActivity = null;
            }

            @Override
            public void onActivityStopped(Activity activity) {
                startedActivities = Math.max(0, startedActivities - 1);
                if (startedActivities == 0) handler.postDelayed(stopRealtime, BACKGROUND_GRACE_MS);
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
    }

    /** Sự kiện cần xử lý ở MỌI màn hình: cuộc gọi đến, tương hợp mới. */
    private void onRealtimeEvent(String event, JSONObject data) {
        Activity activity = currentActivity;
        if ("call.incoming".equals(event)) {
            if (activity != null) IncomingCalls.onIncoming(activity, data.optJSONObject("call"), data.optJSONObject("from"), data.optJSONArray("iceServers"));
        } else if ("call.cancelled".equals(event) || "call.missed".equals(event) || "call.ended".equals(event)) {
            JSONObject call = data.optJSONObject("call");
            if (call != null) IncomingCalls.onCallOver(call.optLong("id"));
        } else if (Realtime.EVENT_OPEN.equals(event)) {
            // Vừa nối (lại): hỏi xem có cuộc gọi nào đang đổ chuông từ trước khi nối không
            if (activity != null) IncomingCalls.check(activity);
        } else if ("match.new".equals(event)) {
            String name = ChatUi.text(data.optJSONObject("user"), "displayName");
            if (activity != null && !name.isEmpty()) {
                Toast.makeText(activity, "🎉 Bạn và " + name + " đã tương hợp!", Toast.LENGTH_LONG).show();
            }
        }
    }
}
