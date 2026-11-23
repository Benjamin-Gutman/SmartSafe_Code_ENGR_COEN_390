package com.example.safeui.http_handler;

import android.os.Handler;
import android.os.Looper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import static org.junit.Assert.*;
import static org.robolectric.shadows.ShadowLooper.idleMainLooper;

// Use fake sockets and the Android test clock. These tests never contact a Pi.
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class ClientSocketTest
{
    private FakeFactory factory;
    private ClientSocket socket;

    @Before
    public void setUp()
    {
        factory = new FakeFactory();
        socket = new ClientSocket(factory, new Handler(Looper.getMainLooper()),
                "ws://localhost:8765", 30_000);
    }

    @Test
    public void timeoutFailsOnceAndLateMessagesCannotCompleteTheNextRequest()
    {
        Result first = new Result();
        socket.send("history", first);
        FakeSocket old = factory.latest();
        idleMainLooper(31, TimeUnit.SECONDS);
        assertEquals(1, first.failures.size());
        assertTrue(first.failures.get(0) instanceof SocketTimeoutException);
        assertTrue(old.cancelled);

        Result second = new Result();
        socket.send("history", second);
        assertEquals(2, factory.sockets.size());
        old.reply();
        old.listener.onFailure(old, new IOException("Old socket"), null);
        old.listener.onClosed(old, 1000, "Old close");
        assertEquals(0, second.replies);
        assertTrue(second.failures.isEmpty());
        factory.latest().reply();
        assertEquals(1, second.replies);
        assertEquals(1, first.failures.size());
    }

    @Test
    public void successfulReplyDoesNotLeaveATimerThatFailsTheNextCommand()
    {
        Result first = new Result();
        socket.send("history", first);
        idleMainLooper(10, TimeUnit.SECONDS);
        factory.latest().reply();
        Result second = new Result();
        socket.send("history", second);
        idleMainLooper(21, TimeUnit.SECONDS);
        assertEquals(1, first.replies);
        assertTrue(first.failures.isEmpty());
        assertTrue(second.failures.isEmpty());
        factory.latest().reply();
        idleMainLooper(31, TimeUnit.SECONDS);
        assertEquals(1, second.replies);
        assertTrue(second.failures.isEmpty());
        assertFalse(factory.latest().cancelled);
    }

    @Test
    public void busyRequestDoesNotReplaceTheOriginalCallback()
    {
        Result first = new Result();
        Result second = new Result();
        socket.send("history", first);
        socket.send("list", second);
        assertEquals(1, second.failures.size());
        assertTrue(second.failures.get(0) instanceof IllegalStateException);
        factory.latest().reply();
        assertEquals(1, first.replies);
        assertTrue(first.failures.isEmpty());
    }

    @Test
    public void rejectedSendFailsAndTheNextCommandReconnects()
    {
        factory.rejectNextSend = true;
        Result first = new Result();
        socket.send("history", first);
        assertEquals(1, first.failures.size());
        assertTrue(factory.latest().cancelled);
        Result next = new Result();
        socket.send("history", next);
        assertEquals(2, factory.sockets.size());
        factory.latest().reply();
        idleMainLooper(31, TimeUnit.SECONDS);
        assertEquals(1, next.replies);
        assertEquals(1, first.failures.size());
    }

    @Test
    public void closingFailsThePendingCommandAndCancelsItsDeadline()
    {
        Result first = new Result();
        socket.send("history", first);
        FakeSocket old = factory.latest();
        socket.close();
        idleMainLooper(31, TimeUnit.SECONDS);
        assertTrue(old.closed);
        assertEquals(1, first.failures.size());
        Result next = new Result();
        socket.send("history", next);
        old.reply();
        assertEquals(0, next.replies);
        factory.latest().reply();
        assertEquals(1, next.replies);
    }

    @Test
    public void malformedReplyFailsOnlyItsOwnCommand()
    {
        Result first = new Result();
        socket.send("history", first);
        factory.latest().listener.onMessage(factory.latest(), "{bad json");
        assertEquals(1, first.failures.size());
        Result next = new Result();
        socket.send("history", next);
        factory.latest().reply();
        idleMainLooper(31, TimeUnit.SECONDS);
        assertEquals(1, next.replies);
        assertTrue(next.failures.isEmpty());
        assertEquals(1, first.failures.size());
    }

    @Test
    public void connectionCreationFailureDoesNotLeaveACommandInFlight()
    {
        factory.failConnection = true;
        Result first = new Result();
        socket.send("history", first);
        assertEquals(1, first.failures.size());
        factory.failConnection = false;
        Result next = new Result();
        socket.send("history", next);
        factory.latest().reply();
        assertEquals(1, next.replies);
    }

    @Test
    public void disconnectFailsOnceAndAllowsAFreshConnection()
    {
        Result first = new Result();
        socket.send("history", first);
        FakeSocket old = factory.latest();
        old.listener.onFailure(old, new IOException("Offline"), null);
        old.listener.onClosed(old, 1000, "Closed");
        idleMainLooper(31, TimeUnit.SECONDS);
        assertEquals(1, first.failures.size());
        Result next = new Result();
        socket.send("history", next);
        factory.latest().reply();
        assertEquals(2, factory.sockets.size());
        assertEquals(1, next.replies);
    }

    private static class Result implements ClientSocket.ReplyCallback
    {
        int replies;
        final List<Throwable> failures = new ArrayList<>();
        public void onReply(Models.Reply reply) { replies++; }
        public void onFailure(Throwable error) { failures.add(error); }
    }

    private static class FakeFactory implements WebSocket.Factory
    {
        final List<FakeSocket> sockets = new ArrayList<>();
        boolean failConnection;
        boolean rejectNextSend;
        public WebSocket newWebSocket(Request request, WebSocketListener listener)
        {
            if (failConnection) throw new IllegalStateException("Connection failed");
            FakeSocket result = new FakeSocket(request, listener, !rejectNextSend);
            rejectNextSend = false;
            sockets.add(result);
            return result;
        }
        FakeSocket latest() { return sockets.get(sockets.size() - 1); }
    }

    private static class FakeSocket implements WebSocket
    {
        final Request request;
        final WebSocketListener listener;
        final boolean acceptsSend;
        boolean closed;
        boolean cancelled;
        FakeSocket(Request request, WebSocketListener listener, boolean acceptsSend)
        {
            this.request = request;
            this.listener = listener;
            this.acceptsSend = acceptsSend;
        }
        public Request request() { return request; }
        public long queueSize() { return 0; }
        public boolean send(String text) { return acceptsSend; }
        public boolean send(ByteString data) { return acceptsSend; }
        public boolean close(int code, String reason) { closed = true; return true; }
        public void cancel() { cancelled = true; }
        void reply()
        {
            listener.onMessage(this,
                    "{\"status\":\"ok\",\"command\":\"history\",\"history\":[]}");
        }
    }
}