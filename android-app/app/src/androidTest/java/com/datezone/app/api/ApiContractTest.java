package com.datezone.app.api;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
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
