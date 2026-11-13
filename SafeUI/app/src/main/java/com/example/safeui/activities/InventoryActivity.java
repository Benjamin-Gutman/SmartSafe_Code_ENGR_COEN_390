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
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.Toolbar;

import androidx.appcompat.app.AppCompatActivity;

import com.example.safeui.DatabaseHelper;
import com.example.safeui.R;
import com.example.safeui.http_handler.DataSync;
import com.example.safeui.http_handler.InventoryStore;
import com.example.safeui.http_handler.Models;

public class InventoryActivity extends AppCompatActivity implements DataSync.Listener {
    private DataSync sync;
    private DatabaseHelper databaseHelper;
    private String username;
    private LinearLayout layoutInventoryItems;
    private Button buttonAddItem;
    private Button buttonBackHome;

    private Toolbar toolbarInventory;
    private TextView textInventoryTitle;
    private TextView textInventorySubtitle;
    private boolean showingHistory = false;

    // Holds the inventory operation until the Pi confirms success.
    private String pendingHistoryAction = null;
    private String pendingHistoryItemName = null;
    private int pendingHistoryItemId = -1;
    private int pendingHistoryQuantity = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);
        toolbarInventory = findViewById(R.id.toolbarInventory);

        toolbarInventory.inflateMenu(R.menu.inventory_menu);

        toolbarInventory.setOnMenuItemClickListener(item -> {

            if (item.getItemId() == R.id.displayHistory) {

                showingHistory = !showingHistory;
                updateInventoryDisplay();

                // Change the menu label depending on the current screen.
                item.setTitle(showingHistory ? "Display Inventory" : "Display Inventory History");

                return true;
            }
            return false;
        });

        databaseHelper = new DatabaseHelper(this);
        username = getIntent().getStringExtra("username");

        if (username == null || username.trim().isEmpty()) {
            Toast.makeText(this, "Unable to identify the logged-in user.", Toast.LENGTH_LONG).show();

            finish();
            return;
        }

        sync = new DataSync(this, new InventoryStore(databaseHelper));

        layoutInventoryItems = findViewById(R.id.layoutInventoryItems);
        buttonAddItem = findViewById(R.id.buttonAddItem);
        buttonBackHome = findViewById(R.id.buttonBackHome);
        textInventoryTitle = findViewById(R.id.textInventoryTitle);
        textInventorySubtitle = findViewById(R.id.textInventorySubtitle);

        buttonAddItem.setOnClickListener(v -> showAddDialog());

        buttonBackHome.setOnClickListener(v -> finish());

        loadInventory();
        sync.refreshAll();
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
        savePendingHistory(reply);
        updateInventoryDisplay();
    }

    @Override
    public void onError(String message) {
        clearPendingHistory();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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

    private void updateInventoryDisplay() {
        // display when the user wants to see the history
        if (showingHistory) {
            textInventoryTitle.setText("Inventory History");
            textInventorySubtitle.setText("Records of inventory changes");

            buttonAddItem.setVisibility(View.GONE);

            loadInventoryHistory();
        } else {
        //display when the user wants to see the inventory
            textInventoryTitle.setText("Inventory");
            textInventorySubtitle.setText("Manage the items in your safe");

            buttonAddItem.setVisibility(View.VISIBLE);

            loadInventory();
        }
    }

    //display the history of items
    private void loadInventoryHistory() {
        layoutInventoryItems.removeAllViews();

        Cursor cursor = databaseHelper.getInventoryHistory();

        try {
            if (!cursor.moveToFirst()) {
                TextView emptyMessage = new TextView(this);
                emptyMessage.setText("No inventory history yet.");
                emptyMessage.setTextSize(16);
                emptyMessage.setPadding(12, 20, 12, 20);

                layoutInventoryItems.addView(emptyMessage);

                return;
            }

            do {
                String itemName = cursor.getString(cursor.getColumnIndexOrThrow("item_name"));

                String eventUsername = cursor.getString(cursor.getColumnIndexOrThrow("username"));

                String action = cursor.getString(cursor.getColumnIndexOrThrow("action"));

                int quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity_change"));

                String eventTime = cursor.getString(cursor.getColumnIndexOrThrow("event_time"));

                TextView historyEntry = new TextView(this);

                historyEntry.setText("Item: " + itemName + "\nAction: " + action + "\nQuantity: " + quantity + "\nUser: " + eventUsername + "\nDate: " + eventTime);

                historyEntry.setTextSize(15);
                historyEntry.setPadding(16, 16, 16, 16);

                layoutInventoryItems.addView(historyEntry);

            } while (cursor.moveToNext());
        } finally {
            cursor.close();
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
//-------------history code helpers---------------------
    private void setPendingHistory(
            String action,
            int itemId,
            String itemName,
            int quantity) {

        pendingHistoryAction = action;
        pendingHistoryItemId = itemId;
        pendingHistoryItemName = itemName;
        pendingHistoryQuantity = quantity;
    }


    // Records the operation locally after the Pi confirms success.
// TODO: Replace this SQLite history insert with a Pi history command
// once Pi-based user accounts and shared history are implemented.
    private void savePendingHistory(Models.Reply reply) {
        if (pendingHistoryAction == null) {
            return;
        }
        int itemId = pendingHistoryItemId;

        // A newly created item may receive its ID from the server response.
        if (itemId == -1) {
            if (reply.item_id != null) {
                itemId = reply.item_id;
            } else {
                Models.InventoryItem item = reply.asInventoryItem();

                if (item != null) {
                    itemId = item.item_id;
                }
            }
        }
        //uses the database helper version of get id incase pi doesn't work
        if (itemId == -1) {
            itemId = databaseHelper.getInventoryItemIdByName(pendingHistoryItemName);
        }

        String existingUsername = databaseHelper.getExistingUsername(username);

        if (existingUsername != null) {
            databaseHelper.addInventoryHistory(
                    itemId,
                    pendingHistoryItemName,
                    existingUsername,
                    pendingHistoryAction,
                    pendingHistoryQuantity
            );
        } else {
            Toast.makeText(this, "Could not find the local user for inventory history", Toast.LENGTH_SHORT).show();
        }
        clearPendingHistory();
    }

    private void clearPendingHistory() {
        pendingHistoryAction = null;
        pendingHistoryItemName = null;
        pendingHistoryItemId = -1;
        pendingHistoryQuantity = 0;
    }
    //---------------------end---------------------------
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

                                    setPendingHistory("ADDED", -1, name, quantity); //this will be changed
//----------USES create() from DataSync File-------------------
                                    sync.create(name, quantity);
                                    dialog.dismiss();

                                })
        );
        dialog.show();
    }


    //remove button for created items
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

                        //for the history
                        setPendingHistory("REMOVED", itemId, itemName, quantity);

                        //uses DataSync command
                        sync.decrement(itemId, quantity);
                        dialog.dismiss();
                        loadInventory();
                    });

            //delete button
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                new AlertDialog.Builder(this)
                        .setTitle("Permanently delete this item")
                        .setMessage("Are you sure you want to permanently delete " + itemName + "?\n\nThis action cannot be undone.")
                        .setNeutralButton("Cancel", null)
                        .setPositiveButton("Delete", (confirmDialog, which) -> {

                            //for the history
                            setPendingHistory("DELETED", itemId, itemName, currentQuantity);

                            //uses delete() from DataSync
                            sync.delete(itemId);

                            Toast.makeText(this, itemName + " permanently deleted.", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                            loadInventory();
                        })
                        .show();
            });
        });
        dialog.show();
    }
}


