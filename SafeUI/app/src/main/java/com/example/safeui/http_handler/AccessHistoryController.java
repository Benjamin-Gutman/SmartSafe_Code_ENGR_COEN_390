package com.example.safeui.http_handler;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Requests and callbacks run on the UI thread. Keeping the request sequence here
// also lets access-history loading be tested without a phone or Android database.
public final class AccessHistoryController {
    public interface Callback {
        void onReply(Models.Reply reply);
        void onError(String message);
    }

    public interface Sender {
        void send(String command, Callback callback);
    }

    public interface Listener {
        void onChanged();
    }

    private interface ReplyAction {
        void accept(Models.Reply reply);
    }

    private final String username;
    private final int userId;
    private final Sender sender;
    private final Listener listener;
    private boolean stopped;
    private boolean busy;
    private boolean refreshQueued;
    private boolean permissionsLoaded;
    private boolean admin;
    private long requestNumber;
    private List<AccessHistoryEntry> history = Collections.emptyList();
    private Map<Integer, String> users = Collections.emptyMap();
    private String historyStatus = "Checking account permissions...";

    public AccessHistoryController(String username, int userId, Sender sender, Listener listener) {
        this.username = username;
        this.userId = userId;
        this.sender = sender;
        this.listener = listener;
    }

    public boolean isBusy() { return busy; }
    public boolean hasPermissions() { return permissionsLoaded; }
    public boolean canViewHistory() { return permissionsLoaded && admin; }
    public List<AccessHistoryEntry> getHistory() { return history; }
    public Map<Integer, String> getUsers() { return users; }
    public String getHistoryStatus() { return historyStatus; }

    public void refresh() {
        if (stopped) return;
        if (busy) {
            refreshQueued = true;
            return;
        }
        if (userId <= 0 || username == null || username.trim().isEmpty()) {
            historyStatus = "Could not identify the logged-in user. Please sign in again.";
            listener.onChanged();
            return;
        }
        historyStatus = "Loading access history...";
        request(Commands.getUserByUsername(username), "getUserByUsername", reply -> {
            Models.User user = reply.asUser();
            if (user == null || user.user_id != userId || !username.equals(user.username))
                throw new IllegalArgumentException("Unexpected user reply");
            permissionsLoaded = true;
            admin = user.isAdmin;
            if (!admin) {
                history = Collections.emptyList();
                users = Collections.emptyMap();
                historyStatus = "Access history is available to administrators.";
                finish(true);
                return;
            }
            loadUserNames();
        }, () -> permissionLookupFailed(false), () -> permissionLookupFailed(true));
    }

    private void permissionLookupFailed(boolean connectionFailure) {
        if (connectionFailure && canViewHistory() && !history.isEmpty()) {
            historyStatus = "Could not refresh. Showing previously loaded records. Tap Refresh to retry.";
        } else {
            permissionsLoaded = false;
            admin = false;
            history = Collections.emptyList();
            users = Collections.emptyMap();
            historyStatus = "Could not verify account permissions. Tap Refresh to retry.";
        }
        finish(false);
    }

    private void loadUserNames() {
        request(Commands.listUsers(), "listUsers", reply -> {
            if (reply.users == null || !reply.users.isJsonArray())
                throw new IllegalArgumentException("Missing users list");
            Map<Integer, String> names = new HashMap<>();
            for (Models.User user : reply.asUsers()) {
                if (user != null && user.user_id > 0 && user.username != null
                        && !user.username.trim().isEmpty()) names.put(user.user_id, user.username);
            }
            users = Collections.unmodifiableMap(names);
            loadHistory();
        }, () -> {
            // A missing/deleted user must never hide an otherwise valid event.
            users = Collections.emptyMap();
            loadHistory();
        });
    }

    private void loadHistory() {
        request(Commands.listAccessHistory(), "getAccessList", reply -> {
            List<AccessHistoryEntry> updated = AccessHistoryEntry.parse(reply.access_history);
            history = updated; // Replace only after the entire snapshot validates.
            historyStatus = history.isEmpty() ? "No access history has been recorded yet."
                    : "Latest access first. Times use this phone's time zone.";
            finish(true);
        }, () -> {
            historyStatus = history.isEmpty()
                    ? "Could not load access history. Tap Refresh to retry."
                    : "Could not refresh. Showing previously loaded records. Tap Refresh to retry.";
            finish(false);
        });
    }

    public void stop() {
        stopped = true;
        requestNumber++;
        refreshQueued = false;
        busy = false;
    }

    private void request(String command, String expected, ReplyAction action, Runnable failure) {
        request(command, expected, action, failure, failure);
    }

    private void request(String command, String expected, ReplyAction action, Runnable failure,
                         Runnable connectionFailure) {
        busy = true;
        long number = ++requestNumber;
        listener.onChanged();
        try {
            sender.send(command, new Callback() {
                private boolean completed;

                private boolean accept() {
                    if (completed || stopped || requestNumber != number) return false;
                    completed = true;
                    return true;
                }

                @Override
                public void onReply(Models.Reply reply) {
                    if (!accept()) return;
                    if (reply == null || !reply.isOk() || !expected.equals(reply.command)) {
                        failure.run();
                        return;
                    }
                    try {
                        action.accept(reply);
                    } catch (RuntimeException error) {
                        failure.run();
                    }
                }

                @Override
                public void onError(String message) {
                    if (accept()) connectionFailure.run();
                }
            });
        } catch (RuntimeException error) {
            if (!stopped && requestNumber == number) connectionFailure.run();
        }
    }

    private void finish(boolean allowQueuedRefresh) {
        busy = false;
        boolean next = allowQueuedRefresh && refreshQueued;
        refreshQueued = false;
        listener.onChanged();
        if (next && !stopped) refresh();
    }
}
