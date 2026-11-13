package com.example.safeui.activities;

import android.app.AlertDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.safeui.DatabaseHelper;
import com.example.safeui.R;
import com.example.safeui.http_handler.DataSync;
import com.example.safeui.http_handler.InventoryHistoryStore;
import com.example.safeui.http_handler.InventoryStore;
import com.example.safeui.http_handler.Models;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventoryActivity extends AppCompatActivity implements DataSync.Listener {
    private DataSync sync;
    private DatabaseHelper databaseHelper;
    private InventoryHistoryStore historyStore;

    private LinearLayout layoutInventoryItems;
    private Button buttonAddItem;
    private TextView textInventoryTitle;
    private TextView textInventorySubtitle;
    private boolean showingHistory = false;

    // ClientSocket accepts one command at a time. These flags combine refresh
    // requests while an earlier command is still running.
    private boolean requestInFlight = false;
    private boolean inventoryRefreshPending = false;
    private boolean historyRefreshPending = false;
    private String pendingSuccessMessage;

    // Read history off the UI thread. A generation number prevents an older
    // read from drawing over a newer read or a different screen mode.
    private final ExecutorService historyReader = Executors.newSingleThreadExecutor();
    private int historyReadGeneration = 0;
    private boolean destroyed = false;
    private boolean databaseClosing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        Toolbar toolbarInventory = findViewById(R.id.toolbarInventory);
        layoutInventoryItems = findViewById(R.id.layoutInventoryItems);
        buttonAddItem = findViewById(R.id.buttonAddItem);
        Button buttonBackHome = findViewById(R.id.buttonBackHome);
        textInventoryTitle = findViewById(R.id.textInventoryTitle);
        textInventorySubtitle = findViewById(R.id.textInventorySubtitle);

        // Keep the existing screen's login requirement. The logged-in user is
        // not used as the actor of downloaded history: the Pi does not send one.
        String username = getIntent().getStringExtra("username");
        if (username == null || username.trim().isEmpty()) {
            Toast.makeText(this, "Unable to identify the logged-in user.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        databaseHelper = new DatabaseHelper(getApplicationContext());
        historyStore = new InventoryHistoryStore(databaseHelper);
        sync = new DataSync(this, new InventoryStore(databaseHelper), historyStore);

        toolbarInventory.inflateMenu(R.menu.inventory_menu);
        toolbarInventory.setOnMenuItemClickListener(item -> {
            if (item.getItemId() != R.id.displayHistory) {
                return false;
            }

            showingHistory = !showingHistory;
            historyReadGeneration++;
            item.setChecked(showingHistory);
            updateInventoryDisplay();

            if (showingHistory) {
                historyRefreshPending = true;
            } else {
                inventoryRefreshPending = true;
            }
            sendNextRefresh();
            return true;
        });

        buttonAddItem.setOnClickListener(v -> showAddDialog());
        buttonBackHome.setOnClickListener(v -> finish());

        // Show the saved inventory immediately, then refresh both datasets
        // sequentially so history is also available when the phone goes offline.
        updateInventoryDisplay();
        inventoryRefreshPending = true;
        historyRefreshPending = true;
        sendNextRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (layoutInventoryItems != null && databaseHelper != null) {
            updateInventoryDisplay();
        }
    }

    @Override
    public void onReply(Models.Reply reply) {
        requestInFlight = false;
        String successMessage = pendingSuccessMessage;
        pendingSuccessMessage = null;

        if (destroyed || isFinishing()) {
            closeDatabaseWhenIdle();
            return;
        }

        if (successMessage != null) {
            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
        }

        // The Pi already recorded this operation. Fetch its complete history;
        // never create a second local event or refresh again for a history reply.
        if (isInventoryChange(reply.command)) {
            historyRefreshPending = true;
        }

        updateInventoryDisplay();
        sendNextRefresh();
    }

    @Override
    public void onError(String message) {
        requestInFlight = false;
        pendingSuccessMessage = null;

        if (destroyed || isFinishing()) {
            closeDatabaseWhenIdle();
            return;
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        updateInventoryDisplay(); // Keep displaying the last saved data.
        sendNextRefresh();
    }

    private boolean isInventoryChange(String command) {
        return "create".equals(command)
                || "add".equals(command)
                || "remove".equals(command)
                || "update".equals(command)
                || "delete".equals(command);
    }

    private void sendNextRefresh() {
        if (sync == null || requestInFlight || destroyed || isFinishing()) {
            return;
        }

        // Prefer the visible dataset when both refreshes are waiting.
        if (historyRefreshPending && (showingHistory || !inventoryRefreshPending)) {
            historyRefreshPending = false;
            requestInFlight = true;
            buttonAddItem.setEnabled(false);
            sync.refreshHistory();
        } else if (inventoryRefreshPending) {
            inventoryRefreshPending = false;
            requestInFlight = true;
            buttonAddItem.setEnabled(false);
            sync.refreshAll();
        } else {
            buttonAddItem.setEnabled(true);
        }
    }

    private boolean beginInventoryRequest(String successMessage) {
        if (requestInFlight) {
            Toast.makeText(this, "Please wait for the current update.", Toast.LENGTH_SHORT).show();
            return false;
        }

        requestInFlight = true;
        pendingSuccessMessage = successMessage;
        buttonAddItem.setEnabled(false);
        return true;
    }

    private void updateInventoryDisplay() {
        buttonAddItem.setEnabled(!requestInFlight);

        if (showingHistory) {
            textInventoryTitle.setText("Inventory History");
            textInventorySubtitle.setText("Records of inventory changes");
            buttonAddItem.setVisibility(View.GONE);
            loadInventoryHistory();
        } else {
            textInventoryTitle.setText("Inventory");
            textInventorySubtitle.setText("Manage the items in your safe");
            buttonAddItem.setVisibility(View.VISIBLE);
            loadInventory();
        }
    }

    private void loadInventoryHistory() {
        layoutInventoryItems.removeAllViews();
        final int generation = ++historyReadGeneration;

        historyReader.execute(() -> {
            try {
                List<Models.InventoryHistoryItem> events = historyStore.getAll();
                runOnUiThread(() -> {
                    if (!destroyed && !isFinishing() && showingHistory
                            && generation == historyReadGeneration) {
                        displayInventoryHistory(events);
                    }
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (!destroyed && !isFinishing() && showingHistory
                            && generation == historyReadGeneration) {
                        TextView message = new TextView(this);
                        message.setText("Unable to load saved history.");
                        message.setPadding(12, 20, 12, 20);
                        layoutInventoryItems.addView(message);
                    }
                });
            }
        });
    }

    private void displayInventoryHistory(List<Models.InventoryHistoryItem> events) {
        layoutInventoryItems.removeAllViews();

        if (events.isEmpty()) {
            TextView message = new TextView(this);
            message.setText("No inventory history yet.");
            message.setTextSize(16);
            message.setPadding(12, 20, 12, 20);
            layoutInventoryItems.addView(message);
            return;
        }

        DateFormat dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM);
        for (Models.InventoryHistoryItem event : events) {
            // A delete event retains the last stock value; it does not prove
            // the physical stock became zero. Other events show remaining stock.
            String quantityLabel = "delete".equals(event.action)
                    ? "Last recorded quantity: "
                    : "Remaining quantity: ";

            TextView historyEntry = new TextView(this);
            historyEntry.setText(
                    "Item: " + event.item_name
                            + "\nAction: " + historyActionLabel(event.action)
                            + "\n" + quantityLabel + event.quantity
                            + "\nDate: " + dates.format(new Date(event.getTimestampMillis())));
            historyEntry.setTextSize(15);
            historyEntry.setTextColor(0xFF202124);
            historyEntry.setPadding(16, 16, 16, 16);
            layoutInventoryItems.addView(historyEntry);
        }
    }

    private String historyActionLabel(String action) {
        switch (action) {
            case "add":
                return "Added";
            case "remove":
                return "Removed";
            case "delete":
                return "Deleted";
            default:
                return action;
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        historyReadGeneration++;
        closeDatabaseWhenIdle();
        super.onDestroy();
    }

    private void closeDatabaseWhenIdle() {
        if (!destroyed || requestInFlight || databaseClosing) {
            return;
        }

        // Wait for a pending DataSync write and any queued history reads before
        // closing the helper. Network callbacks arrive after their cache write.
        databaseClosing = true;
        historyReader.execute(() -> {
            if (databaseHelper != null) {
                databaseHelper.close();
            }
        });
        historyReader.shutdown();
    }

    private void loadInventory() {
        layoutInventoryItems.removeAllViews();

        Cursor cursor = databaseHelper.getInventory();

        try {
            if (cursor == null || cursor.getCount() == 0) {
                TextView emptyMessage = new TextView(this);
                emptyMessage.setText("No inventory items yet.\nTap + Add Item to get started.");
                emptyMessage.setTextSize(16);
                emptyMessage.setTextColor(0xFF666666);
                emptyMessage.setGravity(Gravity.CENTER);
                emptyMessage.setPadding(16, 32, 16, 32);

                layoutInventoryItems.addView(emptyMessage);
                return;
            }

            int idColumn = cursor.getColumnIndexOrThrow("item_id");
            int nameColumn = cursor.getColumnIndexOrThrow("item_name");
            int descriptionColumn = cursor.getColumnIndexOrThrow("description");
            int quantityColumn = cursor.getColumnIndexOrThrow("quantity");

            while (cursor.moveToNext()) {
                int itemId = cursor.getInt(idColumn);
                String name = cursor.getString(nameColumn);
                String description = cursor.getString(descriptionColumn);
                int quantity = cursor.getInt(quantityColumn);

                addInventoryRow(
                        itemId,
                        name,
                        description,
                        quantity
                );
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void addInventoryRow(
            int itemId,
            String name,
            String description,
            int quantity
    ) {
        //------------------layout specs----------------
        LinearLayout itemCard = new LinearLayout(this);
        itemCard.setOrientation(LinearLayout.VERTICAL);
        itemCard.setPadding(16, 12, 16, 12);
        itemCard.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        cardParams.setMargins(0, 0, 0, 12);
        itemCard.setLayoutParams(cardParams);

        TextView textName = new TextView(this);
        textName.setText(name);
        textName.setTextSize(18);
        textName.setTextColor(0xFF202124);
        textName.setTypeface(null, android.graphics.Typeface.BOLD);
        itemCard.addView(textName);

        if (description != null && !description.trim().isEmpty()) {
            TextView textDescription = new TextView(this);
            textDescription.setText(description);
            textDescription.setTextSize(14);
            textDescription.setTextColor(0xFF666666);
            textDescription.setPadding(0, 4, 0, 4);
            itemCard.addView(textDescription);
        }

        TextView textQuantity = new TextView(this);
        textQuantity.setText("Quantity: " + quantity);
        textQuantity.setTextSize(16);
        textQuantity.setTextColor(0xFF333333);
        textQuantity.setPadding(0, 8, 0, 4);
        itemCard.addView(textQuantity);

        Button buttonRemove = new Button(this);
        buttonRemove.setText("Remove Quantity");
        buttonRemove.setAllCaps(false);
        itemCard.addView(buttonRemove);
        //------------------end of layout specs----------------

        buttonRemove.setOnClickListener(v -> showRemoveDialog(itemId, name, quantity));

        layoutInventoryItems.addView(itemCard);
    }

    private void showAddDialog() {
        //layout specifications
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(48, 16, 48, 0);

        //Description boxes for item
        EditText editName = new EditText(this);
        editName.setHint("Item name");
        editName.setSingleLine(true);
        dialogLayout.addView(editName);

        EditText editDescription = new EditText(this);
        editDescription.setHint("Description (optional)");
        dialogLayout.addView(editDescription);

        EditText editQuantity = new EditText(this);
        editQuantity.setHint("Quantity to add");
        editQuantity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        dialogLayout.addView(editQuantity);

        //Add item button
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Inventory Item")
                .setView(dialogLayout)
                .setNeutralButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();

        //When add is clicked on above, this portion adds the item to the db
        dialog.setOnShowListener(ignored ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(v -> {
                                    String name = editName.getText().toString().trim();

                                    String description = editDescription.getText().toString().trim();

                                    String quantityText = editQuantity.getText().toString().trim();

                                    if (name.isEmpty()) {
                                        editName.setError("Enter an item name.");
                                        return;
                                    }

                                    int quantity;

                                    try {
                                        quantity = Integer.parseInt(quantityText);
                                    } catch (NumberFormatException e) {
                                        editQuantity.setError("Enter a valid quantity.");
                                        return;
                                    }

                                    if (quantity <= 0) {
                                        editQuantity.setError("Quantity must be greater than zero.");
                                        return;
                                    }

                                    if (!beginInventoryRequest("Item added.")) {
                                        return;
                                    }
//----------USES create() from DataSync File-------------------
                                    sync.create(name, quantity);
                                    dialog.dismiss();

                                })
        );
        dialog.show();
    }

    private void showRemoveDialog(
            int itemId,
            String itemName,
            int currentQuantity
    ) {
        EditText editQuantity = new EditText(this);
        editQuantity.setHint("Quantity to remove");
        editQuantity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        int padding = (int) (24 * getResources().getDisplayMetrics().density);

        editQuantity.setPadding(padding, padding / 2, padding, padding / 2);

        //dialog that appears when "Remove" on an item is clicked
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Remove " + itemName)
                .setMessage("Current quantity: " + currentQuantity)
                .setView(editQuantity)
                .setNeutralButton("Cancel", null)
                .setPositiveButton("Remove", null)
                .setNegativeButton("Delete Item", null)
                .create();

        //Implement remove and delete buttons
        dialog.setOnShowListener(ignored -> {
            //remove button
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        int quantity;

                        try {
                            quantity = Integer.parseInt(editQuantity.getText().toString().trim()
                            );
                        } catch (NumberFormatException e) {
                            editQuantity.setError("Enter a valid quantity.");
                            return;
                        }

                        if (quantity <= 0) {
                            editQuantity.setError("Enter a quantity greater than zero.");
                            return;
                        }

                        if (quantity > currentQuantity) {
                            editQuantity.setError("You cannot remove more than " + currentQuantity + ".");
                            return;
                        }

                        if (!beginInventoryRequest("Quantity removed.")) {
                            return;
                        }

                        //uses DataSync command
                        sync.decrement(itemId, quantity);
                        dialog.dismiss();
                    });

            //delete button
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Permanently delete this item")
                        .setMessage("Are you sure you want to permanently delete " + itemName + "?\n\nThis action cannot be undone.")
                        .setNeutralButton("Cancel", null)
                        .setPositiveButton("Delete", (confirmDialog, which) -> {

                            if (!beginInventoryRequest(itemName + " permanently deleted.")) {
                                return;
                            }

                            //uses delete() from DataSync
                            sync.delete(itemId);

                            dialog.dismiss();
                        })
                        .show();
            });
        });
        dialog.show();
    }
}
