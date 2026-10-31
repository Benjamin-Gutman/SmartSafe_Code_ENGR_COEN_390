package com.example.safeui.http_handler;

import androidx.annotation.NonNull;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;


public class ClientSocket
{
    private ClientSocket() {}

    private static final String URL = "ws://127.0.0.1:8765";

    public interface ReplyCallback
    {
        void onReply(Models.Reply reply);
        void onFailure(Throwable error);
    }

    private static final Gson GSON = new Gson();
    private static final ClientSocket INSTANCE = new ClientSocket();

    public static ClientSocket get()
    {
        return INSTANCE;
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)     // no read timeout on a long-lived socket
            .pingInterval(20, TimeUnit.SECONDS)   // detect dead connections
            .build();

    private WebSocket socket;
    private ReplyCallback pending; // at most one command in flight

    public synchronized void connect()
    {
        if (socket != null) return;
        Request request = new Request.Builder().url(URL).build();
        socket = client.newWebSocket(request, new Listener());
    }

    public synchronized void send(String commandLine, ReplyCallback callback)
    {
        if (pending != null)
        {
            callback.onFailure(new IllegalStateException("Command already in flight"));
            return;
        }
        connect();
        pending = callback;
        if (!socket.send(commandLine))
        {
            pending = null;
            socket = null; // next send() reconnects
            callback.onFailure(new IOException("Socket closed"));
        }
    }

    public void close()
    {
        WebSocket old;
        synchronized (this)
        {
            old = socket;
            socket = null;
        }
        if (old != null) old.close(1000, "bye");
        failPending(new IOException("Connection closed"));
    }

    private synchronized ReplyCallback takePending()
    {
        ReplyCallback cb = pending;
        pending = null;
        return cb;
    }

    private void failPending(Throwable t)
    {
        ReplyCallback cb = takePending();
        if (cb != null) cb.onFailure(t);
    }

    // Ignores events from a socket that has already been replaced or closed.
    private void handleDisconnect(WebSocket ws, Throwable t)
    {
        synchronized (this)
        {
            if (ws != socket) return;
            socket = null; // next send() reconnects
        }
        failPending(t);
    }

    private class Listener extends WebSocketListener
    {
        @Override
        public void onMessage(@NonNull WebSocket ws, @NonNull String text)
        {
            ReplyCallback cb = takePending();
            if (cb == null) return; // unexpected message, nothing waiting for it
            try
            {
                Models.Reply reply = GSON.fromJson(text, Models.Reply.class);
                if (reply == null)
                {
                    cb.onFailure(new IOException("Empty reply"));
                    return;
                }
                cb.onReply(reply);
            }
            catch (Exception e)
            {
                cb.onFailure(e);
            }
        }

        @Override
        public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, Response response)
        {
            handleDisconnect(ws, t);
        }

        @Override
        public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason)
        {
            handleDisconnect(ws, new IOException("Closed: " + reason));
        }
    }
}