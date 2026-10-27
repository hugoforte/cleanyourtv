package io.github.hugoforte.morningboard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import javax.net.SocketFactory;

public class MailerTest {
    /** A stand-in SMTP server: answers each command with the next reply and records what it was sent. */
    private static final class FakeServer implements AutoCloseable {
        final ServerSocket socket = new ServerSocket(0);
        final List<String> received = Collections.synchronizedList(new ArrayList<>());
        final Thread thread;

        FakeServer(String authReply) throws IOException {
            thread = new Thread(() -> {
                try (Socket s = socket.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
                    out.print("220 fake ready\r\n");
                    out.flush();
                    String line;
                    boolean data = false;
                    while ((line = in.readLine()) != null) {
                        received.add(line);
                        if (data) {
                            if (line.equals(".")) {
                                data = false;
                                out.print("250 queued\r\n");
                            }
                        } else if (line.startsWith("EHLO")) {
                            out.print("250-fake\r\n250 AUTH PLAIN\r\n");
                        } else if (line.startsWith("AUTH")) {
                            out.print(authReply + "\r\n");
                        } else if (line.equals("DATA")) {
                            data = true;
                            out.print("354 go on\r\n");
                        } else if (line.equals("QUIT")) {
                            out.print("221 bye\r\n");
                            out.flush();
                            return;
                        } else {
                            out.print("250 ok\r\n");
                        }
                        out.flush();
                    }
                } catch (IOException ignored) {
                    // The client gave up; the test reads what arrived.
                }
            });
            thread.start();
        }

        Mailer.Account account() {
            return new Mailer.Account("127.0.0.1", socket.getLocalPort(), "me@example.com", "secret", "me@example.com");
        }

        @Override
        public void close() throws Exception {
            socket.close();
            thread.join(2000);
        }
    }

    private static final List<String> TO = Arrays.asList("a@example.com", "b@example.com");

    @Test
    public void itLogsInWithAuthPlain() throws Exception {
        try (FakeServer server = new FakeServer("235 accepted")) {
            new Mailer(SocketFactory.getDefault()).send(server.account(), TO, "Hi", "Body");
            server.thread.join(2000);
            String expected = "AUTH PLAIN " + Base64.getEncoder().encodeToString("\0me@example.com\0secret".getBytes(StandardCharsets.UTF_8));
            assertTrue(server.received.contains(expected));
        }
    }

    @Test
    public void itNamesEveryRecipient() throws Exception {
        try (FakeServer server = new FakeServer("235 accepted")) {
            new Mailer(SocketFactory.getDefault()).send(server.account(), TO, "Hi", "Body");
            server.thread.join(2000);
            assertTrue(server.received.containsAll(Arrays.asList("RCPT TO:<a@example.com>", "RCPT TO:<b@example.com>")));
        }
    }

    @Test
    public void aRefusedLoginThrowsWithTheServersReply() throws Exception {
        try (FakeServer server = new FakeServer("535 5.7.8 Username and Password not accepted")) {
            IOException e = assertThrows(IOException.class,
                    () -> new Mailer(SocketFactory.getDefault()).send(server.account(), TO, "Hi", "Body"));
            assertEquals("the mail server refused AUTH PLAIN: 535 5.7.8 Username and Password not accepted", e.getMessage());
        }
    }

    @Test
    public void theBodyIsSentAsUtf8Base64() {
        String m = Mailer.message("me@example.com", TO, "Zoë", "Café\n");
        assertTrue(m.contains("\r\n\r\n" + Base64.getMimeEncoder().encodeToString("Café\n".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void spacesInTheAppPasswordAreDropped() {
        Mailer.Account a = Mailer.Account.parse("{\"host\": \"smtp.gmail.com\", \"port\": 465, \"user\": \"me@example.com\", \"password\": \"abcd efgh ijkl mnop\"}");
        assertEquals("abcdefghijklmnop", a.password);
    }

    @Test
    public void aMailJsonWithNoPasswordIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> Mailer.Account.parse(
                "{\"host\": \"smtp.gmail.com\", \"port\": 465, \"user\": \"me@example.com\", \"password\": \"\"}"));
    }
}
