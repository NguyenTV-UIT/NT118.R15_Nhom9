package com.datezone.app.api;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.net.ssl.SSLSocketFactory;

/**
 * Kết nối WebSocket (RFC 6455) phía client, chỉ dùng thư viện chuẩn của Java nên app không cần thêm dependency.
 * Hỗ trợ ws:// và wss://, tin văn bản, ping/pong, tin bị chia mảnh và mã đóng.
 *
 * Cách dùng: tạo -> connect() (chặn tới khi bắt tay xong) -> readLoop() trên 1 luồng riêng; send() gọi từ luồng nào cũng được.
 */
public final class WsConnection {
    public interface Handler {
        void onText(String text);
        /** Kết nối đã đóng. code = mã đóng của máy chủ (1006 nếu rớt mạng, không có khung đóng). */
        void onClosed(int code, String reason);
    }

    private static final int MAX_MESSAGE_BYTES = 4 * 1024 * 1024;
    private static final String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final URI uri;
    private final String bearerToken;
    private final Handler handler;
    private final SecureRandom random = new SecureRandom();
    private final Object writeLock = new Object();
    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private volatile boolean closed;
    private boolean closeReported;

    public WsConnection(String url, String bearerToken, Handler handler) {
        this.uri = URI.create(url);
        this.bearerToken = bearerToken;
        this.handler = handler;
    }

    /** Mở socket và bắt tay HTTP -> WebSocket. Ném IOException nếu máy chủ từ chối. */
    public void connect(int timeoutMs) throws IOException {
        boolean secure = "wss".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort() > 0 ? uri.getPort() : secure ? 443 : 80;
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(uri.getHost(), port), timeoutMs);
        socket = secure ? ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plain, uri.getHost(), port, true) : plain;
        socket.setSoTimeout(timeoutMs);
        socket.setTcpNoDelay(true);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();

        byte[] nonce = new byte[16];
        random.nextBytes(nonce);
        String key = base64(nonce);
        String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        StringBuilder request = new StringBuilder();
        request.append("GET ").append(path).append(" HTTP/1.1\r\n")
                .append("Host: ").append(uri.getHost()).append(uri.getPort() > 0 ? ":" + uri.getPort() : "").append("\r\n")
                .append("Upgrade: websocket\r\nConnection: Upgrade\r\n")
                .append("Sec-WebSocket-Key: ").append(key).append("\r\n")
                .append("Sec-WebSocket-Version: 13\r\n");
        if (bearerToken != null && !bearerToken.isEmpty()) request.append("Authorization: Bearer ").append(bearerToken).append("\r\n");
        request.append("\r\n");
        out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
        out.flush();

        String status = readLine();
        if (status == null || !status.contains(" 101")) throw new IOException("Máy chủ từ chối WebSocket: " + status);
        String accept = null;
        for (String line = readLine(); line != null && !line.isEmpty(); line = readLine()) {
            int colon = line.indexOf(':');
            if (colon > 0 && "sec-websocket-accept".equalsIgnoreCase(line.substring(0, colon).trim())) accept = line.substring(colon + 1).trim();
        }
        try {
            String expected = base64(
                    MessageDigest.getInstance("SHA-1").digest((key + GUID).getBytes(StandardCharsets.US_ASCII)));
            if (!expected.equals(accept)) throw new IOException("Bắt tay WebSocket không hợp lệ");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        socket.setSoTimeout(0); // đọc chờ vô hạn; kết nối chết được phát hiện nhờ ping định kỳ của tầng trên
    }

    /** Base64 tự viết: java.util.Base64 cần Android 8 (API 26) trong khi app hỗ trợ từ API 24. */
    static String base64(byte[] data) {
        final String table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
        for (int i = 0; i < data.length; i += 3) {
            int b0 = data[i] & 0xFF;
            int b1 = i + 1 < data.length ? data[i + 1] & 0xFF : 0;
            int b2 = i + 2 < data.length ? data[i + 2] & 0xFF : 0;
            sb.append(table.charAt(b0 >>> 2));
            sb.append(table.charAt(((b0 & 0x03) << 4) | (b1 >>> 4)));
            sb.append(i + 1 < data.length ? table.charAt(((b1 & 0x0F) << 2) | (b2 >>> 6)) : '=');
            sb.append(i + 2 < data.length ? table.charAt(b2 & 0x3F) : '=');
        }
        return sb.toString();
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        for (int b = in.read(); b != -1; b = in.read()) {
            if (b == '\n') return line.toString("US-ASCII").trim();
            line.write(b);
            if (line.size() > 8192) throw new IOException("Header quá dài");
        }
        return line.size() == 0 ? null : line.toString("US-ASCII").trim();
    }

    /** Đọc tin cho tới khi kết nối đóng. Luôn gọi handler.onClosed đúng 1 lần trước khi trả về. */
    public void readLoop() {
        int code = 1006;
        String reason = "";
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        try {
            while (!closed) {
                int first = in.readUnsignedByte();
                int second = in.readUnsignedByte();
                boolean fin = (first & 0x80) != 0;
                int opcode = first & 0x0F;
                long length = second & 0x7F;
                if (length == 126) length = in.readUnsignedShort();
                else if (length == 127) length = in.readLong();
                if (length < 0 || length > MAX_MESSAGE_BYTES) throw new IOException("Tin quá lớn");
                byte[] mask = null;
                if ((second & 0x80) != 0) { // máy chủ không được che dữ liệu, nhưng vẫn đọc đúng nếu có
                    mask = new byte[4];
                    in.readFully(mask);
                }
                byte[] payload = new byte[(int) length];
                in.readFully(payload);
                if (mask != null) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];

                if (opcode == 0x8) { // đóng
                    if (payload.length >= 2) {
                        code = ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
                        reason = new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
                    } else {
                        code = 1005;
                    }
                    try { writeFrame(0x8, payload.length >= 2 ? new byte[]{payload[0], payload[1]} : new byte[0]); }
                    catch (IOException ignored) { }
                    break;
                } else if (opcode == 0x9) { // ping -> pong
                    writeFrame(0xA, payload);
                } else if (opcode == 0x1 || opcode == 0x2 || opcode == 0x0) {
                    if (message.size() + payload.length > MAX_MESSAGE_BYTES) throw new IOException("Tin quá lớn");
                    message.write(payload, 0, payload.length);
                    if (fin) {
                        String text = new String(message.toByteArray(), StandardCharsets.UTF_8);
                        message.reset();
                        handler.onText(text);
                    }
                }
                // 0xA (pong) và opcode lạ: bỏ qua
            }
        } catch (EOFException e) {
            reason = "Mất kết nối";
        } catch (IOException e) {
            reason = e.getMessage() == null ? "Mất kết nối" : e.getMessage();
        } catch (RuntimeException e) {
            reason = String.valueOf(e.getMessage());
        } finally {
            shutdown();
            reportClosed(closed && code == 1006 ? 1000 : code, reason);
        }
    }

    private synchronized void reportClosed(int code, String reason) {
        if (closeReported) return;
        closeReported = true;
        handler.onClosed(code, reason);
    }

    /** Gửi 1 tin văn bản. Trả về false nếu kết nối đã đóng / lỗi ghi. */
    public boolean send(String text) {
        if (closed || out == null) return false;
        try {
            writeFrame(0x1, text.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            shutdown(); // readLoop sẽ thoát và báo onClosed
            return false;
        }
    }

    private void writeFrame(int opcode, byte[] payload) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(payload.length + 14);
        frame.write(0x80 | opcode);
        if (payload.length < 126) {
            frame.write(0x80 | payload.length);
        } else if (payload.length <= 0xFFFF) {
            frame.write(0x80 | 126);
            frame.write(payload.length >>> 8);
            frame.write(payload.length);
        } else {
            frame.write(0x80 | 127);
            for (int shift = 56; shift >= 0; shift -= 8) frame.write((int) (((long) payload.length) >>> shift));
        }
        byte[] mask = new byte[4]; // client bắt buộc che dữ liệu
        random.nextBytes(mask);
        frame.write(mask, 0, 4);
        for (int i = 0; i < payload.length; i++) frame.write(payload[i] ^ mask[i % 4]);
        synchronized (writeLock) {
            out.write(frame.toByteArray());
            out.flush();
        }
    }

    /** Chủ động đóng (app xuống nền, đăng xuất...). */
    public void close() {
        if (closed) return;
        closed = true;
        try { if (out != null) writeFrame(0x8, new byte[]{0x03, (byte) 0xE8}); } // 1000
        catch (IOException ignored) { }
        shutdown();
    }

    private void shutdown() {
        closed = true;
        try { if (socket != null) socket.close(); }
        catch (IOException ignored) { }
    }

    public boolean isClosed() { return closed; }
}
