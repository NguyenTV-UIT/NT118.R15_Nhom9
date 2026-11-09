package com.datezone.app.api;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class ApiContractTest {
    private Context context;
    private SessionStore session;

    @Before public void isolateSession() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(name + "_contract_test", mode);
            }
        };
        session = new SessionStore(context);
        session.clear();
    }

    @Test public void publicProfileAcceptsNullFieldsAndEmptyArrays() throws Exception {
        RemoteProfile profile = new RemoteProfile(new JSONObject(
                "{\"id\":3,\"displayName\":\"An\",\"age\":20,\"gender\":\"male\","
                + "\"bio\":null,\"city\":null,\"distanceKm\":null,\"avatarUrl\":null,\"photos\":[],\"interests\":[]}"));
        assertEquals(3L, profile.id);
        assertEquals("", profile.bio);
        assertEquals("", profile.distance);
        assertEquals(0, profile.photos.length);
        assertTrue(profile.interests.isEmpty());
    }

    @Test public void publicProfileKeepsRemotePhotosAndInterestNames() throws Exception {
        RemoteProfile profile = new RemoteProfile(new JSONObject(
                "{\"id\":8,\"displayName\":\"Chi\",\"age\":24,\"gender\":\"female\","
                + "\"photos\":[{\"id\":1,\"url\":\"http://localhost:3000/uploads/a.jpg\",\"position\":0}],"
                + "\"interests\":[{\"id\":5,\"name\":\"Music\"}]}"));
        assertEquals("http://localhost:3000/uploads/a.jpg", profile.photos[0]);
        assertEquals("Music", profile.interests.get(0));
    }

    @Test public void missingSessionDoesNotSendNetworkRequest() throws Exception {
        try {
            new ApiClient(context).execute("GET", "home/feed", null);
            fail("Missing session must fail");
        } catch (java.io.IOException expected) {
            assertTrue(session.accessToken().isEmpty());
        }
    }

    @Test public void publicLoginSendsCredentialsWithoutAnExistingSession() throws Exception {
        try (FakeServer server = new FakeServer(new Reply(200,
                "{\"success\":true,\"data\":{\"user\":{\"id\":3,\"onboarding\":{\"completed\":true}},"
                + "\"tokens\":{\"accessToken\":\"access\",\"refreshToken\":\"refresh\"}}}"))) {
            JSONObject body = ApiUi.data("email", "an@demo.local", "password", "password123");
            JSONObject response = new ApiClient(context).executePublic("POST", "auth/login", body);
            session.saveTokens(response.getJSONObject("data").getJSONObject("tokens"));
            assertEquals("access", session.accessToken());
            assertEquals(com.datezone.app.HomeActivity.class, AuthFlow.destination(response.getJSONObject("data").getJSONObject("user")));
            String request = server.requests().get(0);
            assertTrue(request.contains("POST /api/auth/login"));
            assertTrue(request.endsWith(body.toString()));
            assertFalse(request.contains("Authorization:"));
        }
    }

    @Test public void invalidPublicLoginDoesNotRefreshOrOverwriteExistingSession() throws Exception {
        session.saveTokens(ApiUi.data("accessToken", "existing", "refreshToken", "existing-refresh"));
        try (FakeServer server = new FakeServer(new Reply(401, "{\"success\":false,\"message\":\"Wrong password\"}"))) {
            try {
                new ApiClient(context).executePublic("POST", "auth/login", ApiUi.data("email", "an@demo.local", "password", "wrong"));
                fail("Invalid login must fail");
            } catch (java.io.IOException expected) { assertEquals("Wrong password", expected.getMessage()); }
            assertEquals(1, server.requests().size());
            assertEquals("existing", session.accessToken());
        }
    }

    @Test public void emailRegistrationRequiresOnlyEmailAndPassword() throws Exception {
        try (FakeServer server = new FakeServer(new Reply(201, "{\"success\":true,\"data\":{}}"))) {
            JSONObject body = ApiUi.data("email", "new@demo.local", "password", "password123");
            new ApiClient(context).executePublic("POST", "auth/register", body);
            assertEquals(2, body.length());
            assertTrue(server.requests().get(0).endsWith(body.toString()));
        }
    }

    @Test public void resetPasswordUsesVerifiedResetTokenWithoutBearerToken() throws Exception {
        try (FakeServer server = new FakeServer(
                new Reply(200, "{\"success\":true,\"data\":{\"resetToken\":\"verified-token\",\"expiresIn\":600}}"),
                new Reply(200, "{\"success\":true,\"data\":null}"))) {
            ApiClient api = new ApiClient(context);
            JSONObject verified = api.executePublic("POST", "auth/password/verify-otp", ApiUi.data("email", "an@demo.local", "otp", "123456"));
            String resetToken = verified.getJSONObject("data").getString("resetToken");
            JSONObject body = ApiUi.data("resetToken", resetToken, "newPassword", "changed123");
            api.executePublic("POST", "auth/password/reset", body);
            List<String> requests = server.requests();
            assertTrue(requests.get(0).contains("POST /api/auth/password/verify-otp"));
            assertTrue(requests.get(1).contains("POST /api/auth/password/reset"));
            assertTrue(requests.get(1).endsWith(body.toString()));
            assertFalse(requests.get(1).contains("Authorization:"));
        }
    }

    @Test public void loginResumesEarliestRequiredOnboardingStep() throws Exception {
        assertEquals(com.datezone.app.AddPhotosActivity.class, AuthFlow.destination(new JSONObject(
                "{\"onboarding\":{\"completed\":false,\"missing\":[\"gender\",\"photos\"]}}")));
        assertEquals(com.datezone.app.EnterNameActivity.class, AuthFlow.destination(new JSONObject(
                "{\"onboarding\":{\"completed\":false,\"missing\":[\"photos\",\"displayName\"]}}")));
        assertEquals(com.datezone.app.ProfileReadyActivity.class, AuthFlow.destination(new JSONObject(
                "{\"onboarding\":{\"completed\":false,\"missing\":[]}}")));
    }

    @Test public void hiddenGenderDoesNotBecomeAnotherGender() throws Exception {
        RemoteProfile profile = new RemoteProfile(new JSONObject(
                "{\"id\":3,\"displayName\":\"An\",\"gender\":null,\"heightCm\":170}"));
        assertEquals("Cao 170 cm", profile.details);
        assertEquals("", new RemoteProfile(new JSONObject("{\"id\":4,\"displayName\":\"Chi\",\"gender\":null}")).details);
    }

    @Test public void staleRefreshCannotRestoreTokensAfterLogout() throws Exception {
        session.saveTokens(ApiUi.data("accessToken", "old", "refreshToken", "old-refresh"));
        String generation = session.snapshot()[1];
        session.clear();
        assertFalse(session.saveRefreshedTokens(generation, ApiUi.data("accessToken", "stale", "refreshToken", "stale-refresh")));
        assertTrue(session.accessToken().isEmpty());
        session.saveTokens(ApiUi.data("accessToken", "other-user", "refreshToken", "other-refresh"));
        session.clearIfCurrent(generation);
        assertEquals("other-user", session.accessToken());
    }

    @Test public void photoUploadUsesMultipartPhotoFieldAndBearerToken() throws Exception {
        session.saveTokens(ApiUi.data("accessToken", "access", "refreshToken", "refresh"));
        java.io.File photo = java.io.File.createTempFile("contract-photo", ".jpg", context.getCacheDir());
        try {
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(photo)) {
                out.write("test-image-bytes".getBytes(StandardCharsets.UTF_8));
            }
            try (FakeServer server = new FakeServer(new Reply(201,
                    "{\"success\":true,\"data\":{\"id\":12,\"url\":\"/uploads/photo.jpg\",\"position\":0}}"))) {
                JSONObject response = new ApiClient(context).executePhoto(android.net.Uri.fromFile(photo));
                assertEquals(12, response.getJSONObject("data").getInt("id"));
                String request = server.requests().get(0);
                assertTrue(request.contains("POST /api/users/me/photos"));
                assertTrue(request.contains("Bearer access"));
                assertTrue(request.contains("multipart/form-data; boundary=DateZone"));
                assertTrue(request.contains("name=\"photo\"; filename=\"photo.jpg\""));
                assertTrue(request.contains("Content-Type: image/jpeg"));
                assertTrue(request.contains("test-image-bytes"));
            }
        } finally { photo.delete(); }
    }

    @Test public void oversizedPhotoFailsBeforeUpload() throws Exception {
        java.io.File photo = java.io.File.createTempFile("contract-large-photo", ".jpg", context.getCacheDir());
        try {
            try (java.io.RandomAccessFile file = new java.io.RandomAccessFile(photo, "rw")) { file.setLength(5 * 1024 * 1024 + 1); }
            try {
                new ApiClient(context).executePhoto(android.net.Uri.fromFile(photo));
                fail("Oversized file must fail locally");
            } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("5 MB")); }
        } finally { photo.delete(); }
    }

    private JSONObject editorUser(String name, int height) throws Exception {
        return new JSONObject("{\"id\":91,\"displayName\":\"Server Name\",\"bio\":\"Server bio\","
                + "\"gender\":\"male\",\"showGender\":false,\"heightCm\":170,\"weightKg\":65,"
                + "\"birthday\":\"2002-06-01\",\"city\":\"City\",\"latitude\":10.8,\"longitude\":106.7,"
                + "\"photos\":[{\"id\":1,\"url\":\"\",\"position\":0},{\"id\":2,\"url\":\"\",\"position\":1}],"
                + "\"interests\":[{\"id\":1,\"name\":\"Music\"},{\"id\":2,\"name\":\"Coffee\"},{\"id\":3,\"name\":\"Travel\"}]}" )
                .put("displayName", name).put("heightCm", height);
    }

    private JSONObject editorOptions() throws Exception {
        return new JSONObject("{\"gender\":[{\"code\":\"male\",\"label\":\"Male\"},{\"code\":\"female\",\"label\":\"Female\"}],"
                + "\"smoking\":[{\"code\":\"never\",\"label\":\"Never\",\"shortLabel\":\"No\"}]}" );
    }

    private java.util.Map<String, String> editorFields() {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("displayName", "Server Name"); fields.put("bio", "Server bio"); fields.put("gender", "Male");
        fields.put("heightCm", "170 cm"); fields.put("weightKg", "65 kg"); fields.put("city", "City");
        return fields;
    }

    @Test public void unchangedEditorDoesNotOverwritePrivateFieldsOrPhotos() throws Exception {
        JSONObject patch = ProfileForm.patch(editorUser("Server Name", 170), editorOptions(), editorFields(), new JSONObject());
        assertEquals(0, patch.length());
    }

    @Test public void editorSendsNumericMeasurementsAndNullsClearedOptionalFields() throws Exception {
        java.util.Map<String, String> fields = editorFields();
        fields.put("displayName", "Updated Name"); fields.put("heightCm", "172 cm"); fields.put("weightKg", "");
        JSONObject patch = ProfileForm.patch(editorUser("Server Name", 170), editorOptions(), fields, new JSONObject());
        assertEquals("Updated Name", patch.getString("displayName"));
        assertEquals(172, patch.getInt("heightCm"));
        assertTrue(patch.get("heightCm") instanceof Number);
        assertTrue(patch.has("weightKg") && patch.isNull("weightKg"));
        assertFalse(patch.has("birthday")); assertFalse(patch.has("showGender")); assertFalse(patch.has("photos"));
    }

    @Test public void changingCityClearsStaleCoordinatesButUnchangedCityPreservesThem() throws Exception {
        java.util.Map<String, String> fields = editorFields();
        fields.put("city", "New City");
        JSONObject patch = ProfileForm.patch(editorUser("Server Name", 170), editorOptions(), fields, new JSONObject());
        assertEquals("New City", patch.getString("city"));
        assertTrue(patch.has("latitude") && patch.isNull("latitude"));
        assertTrue(patch.has("longitude") && patch.isNull("longitude"));
    }

    @Test public void editorRejectsInvalidMeasurementInsteadOfSavingAnIncorrectNumber() throws Exception {
        java.util.Map<String, String> fields = editorFields();
        for (String invalid : new String[]{"abc", "170.5", "201", "-1"}) {
            fields.put("heightCm", invalid);
            try {
                ProfileForm.patch(editorUser("Server Name", 170), editorOptions(), fields, new JSONObject());
                fail("Invalid height must be rejected: " + invalid);
            } catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void editorKeepsOptionCodesInsteadOfSendingDisplayLabels() throws Exception {
        JSONObject patch = ProfileForm.patch(editorUser("Server Name", 170), editorOptions(), editorFields(), ApiUi.data("smoking", "never"));
        assertEquals("never", patch.getString("smoking"));
        assertEquals("No", ProfileForm.label(editorOptions(), "smoking", "never"));
    }

    @Test public void profileEditorLoadsServerDataAndPersistsChangesBeforeClosing() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences realPrefs = target.getSharedPreferences("datezone_session", Context.MODE_PRIVATE);
        SharedPreferences legacyPrefs = target.getSharedPreferences(com.datezone.app.ProfileEditActivity.PREFS_NAME, Context.MODE_PRIVATE);
        java.util.Map<String, ?> savedSession = new java.util.HashMap<>(realPrefs.getAll());
        java.util.Map<String, ?> savedLegacy = new java.util.HashMap<>(legacyPrefs.getAll());
        JSONObject original = editorUser("Server Name", 170);
        JSONObject updated = editorUser("Updated Name", 172);
        try (FakeServer server = new FakeServer(
                new Reply(200, ApiUi.data("success", true, "data", original).toString()),
                new Reply(200, ApiUi.data("success", true, "data", editorOptions()).toString()),
                new Reply(200, ApiUi.data("success", true, "data", updated).toString()))) {
            SessionStore realSession = new SessionStore(target);
            realSession.clear();
            realSession.saveTokens(ApiUi.data("accessToken", "test-access", "refreshToken", "test-refresh"));
            legacyPrefs.edit().putString(com.datezone.app.ProfileEditActivity.KEY_NAME, "Wrong local name").commit();
            try (ActivityScenario<com.datezone.app.ProfileEditActivity> scenario = ActivityScenario.launch(com.datezone.app.ProfileEditActivity.class)) {
                long deadline = android.os.SystemClock.uptimeMillis() + 8000;
                java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
                while (!ready.get() && android.os.SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity(activity -> ready.set("Server Name".equals(
                            ((android.widget.EditText) activity.findViewById(com.datezone.app.R.id.edtEditProfileName)).getText().toString())
                            && activity.findViewById(com.datezone.app.R.id.btnEditProfileSave).isEnabled()));
                    if (!ready.get()) android.os.SystemClock.sleep(40);
                }
                assertTrue("Editor must load the current account from server", ready.get());
                scenario.onActivity(activity -> {
                    assertEquals("2/6 ảnh", ((android.widget.TextView) activity.findViewById(com.datezone.app.R.id.tvEditProfilePhotoCount)).getText().toString());
                    ((android.widget.EditText) activity.findViewById(com.datezone.app.R.id.edtEditProfileName)).setText("Updated Name");
                    ((android.widget.EditText) activity.findViewById(com.datezone.app.R.id.edtEditProfileHeight)).setText("172");
                    activity.findViewById(com.datezone.app.R.id.btnEditProfileSave).performClick();
                });
                List<String> requests = server.requests();
                assertTrue(requests.get(2).contains("PUT /api/users/me/profile"));
                JSONObject patch = new JSONObject(requests.get(2).substring(requests.get(2).indexOf('{')));
                assertEquals("Updated Name", patch.getString("displayName"));
                assertEquals(172, patch.getInt("heightCm"));
                assertEquals(2, patch.length());
                deadline = android.os.SystemClock.uptimeMillis() + 4000;
                while (!"Updated Name".equals(realSession.user().optString("displayName")) && android.os.SystemClock.uptimeMillis() < deadline)
                    android.os.SystemClock.sleep(40);
                assertEquals("Updated Name", realSession.user().getString("displayName"));
                assertFalse(realSession.user().getBoolean("showGender"));
            }
        } finally {
            restorePrefs(realPrefs, savedSession);
            restorePrefs(legacyPrefs, savedLegacy);
        }
    }

    private void restorePrefs(SharedPreferences prefs, java.util.Map<String, ?> saved) {
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (java.util.Map.Entry<String, ?> entry : saved.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
        }
        editor.commit();
    }

    @Test public void expiredAccessTokenRotatesTokensAndRetriesSwipeWithSameBody() throws Exception {
        session.saveTokens(new JSONObject("{\"accessToken\":\"old-access\",\"refreshToken\":\"old-refresh\"}"));
        try (FakeServer server = new FakeServer(
                new Reply(401, "{\"success\":false,\"message\":\"expired\"}"),
                new Reply(200, "{\"success\":true,\"data\":{\"accessToken\":\"new-access\",\"refreshToken\":\"new-refresh\"}}"),
                new Reply(200, "{\"success\":true,\"data\":{\"matched\":false}}"))) {
            JSONObject body = new JSONObject().put("targetId", 3).put("action", "pass");
            JSONObject result = new ApiClient(context).execute("POST", "home/swipe", body);
            assertFalse(result.getJSONObject("data").getBoolean("matched"));
            List<String> requests = server.requests();
            assertTrue(requests.get(0).contains("POST /api/home/swipe"));
            assertTrue(requests.get(0).contains("Bearer old-access"));
            assertTrue(requests.get(1).contains("POST /api/auth/refresh"));
            assertTrue(requests.get(1).contains("old-refresh"));
            assertFalse(requests.get(1).contains("Authorization:"));
            assertTrue(requests.get(2).contains("Bearer new-access"));
            assertTrue(requests.get(2).endsWith(body.toString()));
            assertEquals("new-refresh", session.refreshToken());
        }
    }

    @Test public void rejectedRefreshClearsSessionAndDoesNotReplaySwipe() throws Exception {
        session.saveTokens(new JSONObject("{\"accessToken\":\"old-access\",\"refreshToken\":\"revoked\"}"));
        try (FakeServer server = new FakeServer(
                new Reply(401, "{\"success\":false,\"message\":\"expired\"}"),
                new Reply(401, "{\"success\":false,\"message\":\"revoked refresh token\"}"))) {
            try {
                new ApiClient(context).execute("POST", "home/swipe", new JSONObject().put("targetId", 3).put("action", "like"));
                fail("Revoked token must fail");
            } catch (java.io.IOException expected) {
                assertEquals("revoked refresh token", expected.getMessage());
            }
            assertEquals(2, server.requests().size());
            assertTrue(session.accessToken().isEmpty());
            assertTrue(session.refreshToken().isEmpty());
        }
    }

    @Test public void validationErrorPreservesSessionAndServerMessage() throws Exception {
        session.saveTokens(new JSONObject("{\"accessToken\":\"access\",\"refreshToken\":\"refresh\"}"));
        try (FakeServer server = new FakeServer(new Reply(400, "{\"success\":false,\"message\":\"Invalid targetId\"}"))) {
            try {
                new ApiClient(context).execute("POST", "home/swipe", new JSONObject());
                fail("Validation failure must propagate");
            } catch (java.io.IOException expected) {
                assertEquals("Invalid targetId", expected.getMessage());
            }
            assertEquals(1, server.requests().size());
            assertEquals("access", session.accessToken());
        }
    }

    private static final class Reply {
        final int status;
        final String body;
        Reply(int status, String body) { this.status = status; this.body = body; }
    }

    /** Runs entirely on the test device; never calls a real backend. */
    private static final class FakeServer implements AutoCloseable {
        final ServerSocket socket;
        final FutureTask<List<String>> task;

        FakeServer(Reply... replies) throws Exception {
            assumeTrue("Use -Pdatezone.apiBaseUrl=http://127.0.0.1:38127/api/ for transport tests",
                    ApiClient.baseUrl().equals("http://127.0.0.1:38127/api/"));
            socket = new ServerSocket(38127);
            socket.setSoTimeout(10000);
            task = new FutureTask<>(() -> {
                List<String> requests = new ArrayList<>();
                for (Reply reply : replies) {
                    try (Socket peer = socket.accept()) {
                        peer.setSoTimeout(10000);
                        BufferedReader reader = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder request = new StringBuilder();
                        int length = 0;
                        String line;
                        while ((line = reader.readLine()) != null && !line.isEmpty()) {
                            request.append(line).append('\n');
                            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))
                                length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                        }
                        for (int i = 0; i < length; i++) request.append((char) reader.read());
                        requests.add(request.toString());
                        byte[] payload = reply.body.getBytes(StandardCharsets.UTF_8);
                        String header = "HTTP/1.1 " + reply.status + " Test\r\nContent-Type: application/json\r\nContent-Length: "
                                + payload.length + "\r\nConnection: close\r\n\r\n";
                        peer.getOutputStream().write(header.getBytes(StandardCharsets.UTF_8));
                        peer.getOutputStream().write(payload);
                        peer.getOutputStream().flush();
                    }
                }
                return requests;
            });
            new Thread(task, "contract-http-server").start();
        }

        List<String> requests() throws Exception { return task.get(15, TimeUnit.SECONDS); }
        @Override public void close() throws Exception { socket.close(); }
    }
}
