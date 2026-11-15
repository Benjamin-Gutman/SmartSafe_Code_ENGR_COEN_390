package com.example.safeui.http_handler;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class ClientSocket
{
    private static final String URL = "ws://100.93.188.101:8765";
    private static final long REPLY_TIMEOUT_MS = 30_000;
    private static final Gson GSON = new Gson();
    private static final ClientSocket INSTANCE = new ClientSocket();

    public interface ReplyCallback
    {
        void onReply(Models.Reply reply);
        void onFailure(Throwable error);
    }

    private final WebSocket.Factory socketFactory;
    private final Handler timeoutHandler;
    private final String url;
    private final long replyTimeoutMs;
    private WebSocket socket;
    private PendingCommand pending; // At most one request, as before.

    private static class PendingCommand
    {
        final WebSocket socket;
        final ReplyCallback callback;
        Runnable timeout;

        PendingCommand(WebSocket socket, ReplyCallback callback)
        {
            this.socket = socket;
            this.callback = callback;
        }
    }

    private ClientSocket()
    {
        this(new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(0, TimeUnit.SECONDS)
                        .pingInterval(20, TimeUnit.SECONDS)
                        .build(),
                new Handler(Looper.getMainLooper()), URL, REPLY_TIMEOUT_MS);
    }

    // Tests supply a fake socket and advance the Android clock. No real Pi or
    // background timer thread is needed to check timeout/reconnect behavior.
    ClientSocket(WebSocket.Factory socketFactory, Handler timeoutHandler,
                 String url, long replyTimeoutMs)
    {
        if (socketFactory == null || timeoutHandler == null || url == null
                || replyTimeoutMs <= 0)
            throw new IllegalArgumentException("Invalid socket configuration");
        this.socketFactory = socketFactory;
        this.timeoutHandler = timeoutHandler;
        this.url = url;
        this.replyTimeoutMs = replyTimeoutMs;
    }

    public static ClientSocket get()
    {
        return INSTANCE;
    }

    public synchronized void connect()
    {
        if (socket == null)
            socket = socketFactory.newWebSocket(
                    new Request.Builder().url(url).build(), new Listener());
    }

    public void send(String commandLine, ReplyCallback callback)
    {
        final PendingCommand command;
        try
        {
            command = beginCommand(callback);
        }
        catch (RuntimeException error)
        {
            callback.onFailure(error);
            return;
        }

        try
        {
            if (!command.socket.send(commandLine))
                handleDisconnect(command.socket, new IOException("Socket closed"));
        }
        catch (RuntimeException error)
        {
            handleDisconnect(command.socket, error);
        }
    }

    private synchronized PendingCommand beginCommand(ReplyCallback callback)
    {
        if (pending != null)
            throw new IllegalStateException("Command already in flight");
        connect();
        PendingCommand command = new PendingCommand(socket, callback);
        command.timeout = () -> expire(command);
        pending = command;
        timeoutHandler.postDelayed(command.timeout, replyTimeoutMs);
        return command;
    }

    // Called only while holding this object's lock. Detaching and cancelling
    // together prevents an older callback from clearing a newer request.
    private PendingCommand takePendingLocked()
    {
        PendingCommand command = pending;
        pending = null;
        if (command != null) timeoutHandler.removeCallbacks(command.timeout);
        return command;
    }

    private void expire(PendingCommand expected)
    {
        final PendingCommand command;
        synchronized (this)
        {
            if (pending != expected) return;
            command = takePendingLocked();
            socket = null;
        }

        // Replies contain no request ID. Discard the timed-out connection so
        // its late reply cannot be mistaken for the next command's reply.
        command.socket.cancel();
        command.callback.onFailure(new SocketTimeoutException(
                "Server reply timed out. Please retry."));
    }

    public void close()
    {
        final WebSocket old;
        final PendingCommand command;
        synchronized (this)
        {
            old = socket;
            socket = null;
            command = takePendingLocked();
        }
        if (old != null) old.close(1000, "bye");
        if (command != null)
            command.callback.onFailure(new IOException("Connection closed"));
    }

    private void handleDisconnect(WebSocket ws, Throwable error)
    {
        final PendingCommand command;
        synchronized (this)
        {
            if (ws != socket) return;
            socket = null;
            command = takePendingLocked();
        }
        ws.cancel();
        if (command != null) command.callback.onFailure(error);
    }

    private class Listener extends WebSocketListener
    {
        @Override
        public void onMessage(@NonNull WebSocket ws, @NonNull String text)
        {
            final PendingCommand command;
            synchronized (ClientSocket.this)
            {
                if (ws != socket) return; // Late message from an old connection.
                command = takePendingLocked();
            }
            if (command == null) return;

            final Models.Reply reply;
            try
            {
                reply = GSON.fromJson(text, Models.Reply.class);
                if (reply == null) throw new IOException("Empty reply");
            }
            catch (Exception error)
            {
                command.callback.onFailure(error);
                return;
            }
            command.callback.onReply(reply);
        }

        @Override
        public void onFailure(@NonNull WebSocket ws, @NonNull Throwable error,
                              Response response)
        {
            handleDisconnect(ws, error);
        }

        @Override
        public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason)
        {
            handleDisconnect(ws, new IOException("Closed: " + reason));
        }
    }
}