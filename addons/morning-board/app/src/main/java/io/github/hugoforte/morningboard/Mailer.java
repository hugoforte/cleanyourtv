package io.github.hugoforte.morningboard;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

/**
 * Sends a plain-text email over SMTP with implicit TLS (port 465, as Gmail's smtp.gmail.com offers), logging in with
 * AUTH PLAIN. No libraries: the conversation is a dozen lines. A refusal throws with the server's own reply.
 */
final class Mailer {
    static final class Account {
        final String host;
        final int port;
        final String user;
        final String password;
        final String from;

        Account(String host, int port, String user, String password, String from) {
            this.host = host;
            this.port = port;
            this.user = user;
            this.password = password;
            this.from = from;
        }

        /** Reads mail.json: {"host", "port", "user", "password", "from"}; "from" defaults to "user". */
        static Account parse(String json) {
            try {
                JSONObject o = new JSONObject(json);
                String user = o.getString("user").trim();
                Account a = new Account(o.getString("host").trim(), o.getInt("port"), user,
                        o.getString("password").replaceAll("\\s", ""), o.optString("from", user).trim());
                if (a.host.isEmpty() || a.user.isEmpty() || a.password.isEmpty()) {
                    throw new IllegalArgumentException("mail.json needs a host, a user and a password");
                }
                return a;
            } catch (JSONException e) {
                throw new IllegalArgumentException("mail.json: " + e.getMessage(), e);
            }
        }
    }

    private final SocketFactory sockets;

    Mailer() {
        this(SSLSocketFactory.getDefault());
    }

    Mailer(SocketFactory sockets) {
        this.sockets = sockets;
    }

    void send(Account account, List<String> to, String subject, String body) throws IOException {
        try (Socket socket = sockets.createSocket(account.host, account.port)) {
            socket.setSoTimeout(20_000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = socket.getOutputStream();
            expect(in, 220, "greeting");
            command(out, in, "EHLO morning-board", 250);
            String plain = "\0" + account.user + "\0" + account.password;
            command(out, in, "AUTH PLAIN " + Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8)), 235);
            command(out, in, "MAIL FROM:<" + account.from + ">", 250);
            for (String address : to) command(out, in, "RCPT TO:<" + address + ">", 250);
            command(out, in, "DATA", 354);
            write(out, message(account.from, to, subject, body) + ".\r\n");
            expect(in, 250, "the message");
            write(out, "QUIT\r\n");
        }
    }

    static String message(String from, List<String> to, String subject, String body) {
        StringBuilder m = new StringBuilder();
        m.append("From: Morning Board <").append(from).append(">\r\n");
        m.append("To: ").append(String.join(", ", to)).append("\r\n");
        m.append("Subject: =?UTF-8?B?")
                .append(Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8))).append("?=\r\n");
        m.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())).append("\r\n");
        m.append("MIME-Version: 1.0\r\n");
        m.append("Content-Type: text/plain; charset=UTF-8\r\n");
        m.append("Content-Transfer-Encoding: base64\r\n\r\n");
        // Base64 lines never start with a dot, so the body needs no dot-stuffing.
        m.append(Base64.getMimeEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8))).append("\r\n");
        return m.toString();
    }

    private static void command(OutputStream out, BufferedReader in, String line, int code) throws IOException {
        write(out, line + "\r\n");
        expect(in, code, line.startsWith("AUTH") ? "AUTH PLAIN" : line);
    }

    private static void write(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Reads one reply, continuation lines included, and throws unless its code is the one expected. */
    private static void expect(BufferedReader in, int code, String after) throws IOException {
        StringBuilder reply = new StringBuilder();
        String line;
        do {
            line = in.readLine();
            if (line == null) throw new IOException("the mail server closed the connection after " + after);
            reply.append(line).append(' ');
        } while (line.length() > 3 && line.charAt(3) == '-');
        if (!line.startsWith(String.valueOf(code))) {
            throw new IOException("the mail server refused " + after + ": " + reply.toString().trim());
        }
    }
}
