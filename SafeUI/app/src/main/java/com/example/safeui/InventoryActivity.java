package com.example.safeui;

import android.app.AlertDialog;
import android.database.Cursor;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class InventoryActivity extends AppCompatActivity {

    private DatabaseHelper databaseHelper;
    private String username;
    private LinearLayout layoutInventoryItems;
    private Button buttonAddItem;
    private Button buttonBackHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        databaseHelper = new DatabaseHelper(this);
        username = getIntent().getStringExtra("username");

        if (username == null || username.trim().isEmpty()) {
            Toast.makeText(this, "Unable to identify the logged-in user.", Toast.LENGTH_LONG).show();

            finish();
            return;
        }

        layoutInventoryItems = findViewById(R.id.layoutInventoryItems);
        buttonAddItem = findViewById(R.id.buttonAddItem);
        buttonBackHome = findViewById(R.id.buttonBackHome);

        buttonAddItem.setOnClickListener(v -> showAddDialog());

        buttonBackHome.setOnClickListener(v -> finish());

        loadInventory();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (layoutInventoryItems != null && databaseHelper != null) {
            loadInventory();
        }
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

            int idColumn = cursor.getColumnIndexOrThrow("id");
            int nameColumn = cursor.getColumnIndexOrThrow("name");
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

        buttonRemove.setOnClickListener(
                v -> showRemoveDialog(itemId, name, quantity)
        );

        layoutInventoryItems.addView(itemCard);
    }

    private void showAddDialog() {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(48, 16, 48, 0);

        EditText editName = new EditText(this);
        editName.setHint("Item name");
        editName.setSingleLine(true);
        dialogLayout.addView(editName);

        EditText editDescription = new EditText(this);
        editDescription.setHint("Description (optional)");
        dialogLayout.addView(editDescription);

        EditText editQuantity = new EditText(this);
        editQuantity.setHint("Quantity to add");
        editQuantity.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
        );
        dialogLayout.addView(editQuantity);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add Inventory Item")
                .setView(dialogLayout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            String name = editName.getText()
                                    .toString().trim();

                            String description = editDescription.getText()
                                    .toString().trim();

                            String quantityText = editQuantity.getText()
                                    .toString().trim();

                            if (name.isEmpty()) {
                                editName.setError(
                                        "Enter an item name."
                                );
                                return;
                            }

                            int quantity;

                            try {
                                quantity = Integer.parseInt(quantityText);
                            } catch (NumberFormatException e) {
                                editQuantity.setError(
                                        "Enter a valid quantity."
                                );
                                return;
                            }

                            if (quantity <= 0) {
                                editQuantity.setError(
                                        "Quantity must be greater than zero."
                                );
                                return;
                            }

                            boolean success =
                                    databaseHelper.addInventoryItem(
                                            name,
                                            description,
                                            quantity,
                                            username
                                    );

                            if (success) {
                                Toast.makeText(
                                        this,
                                        "Inventory updated.",
                                        Toast.LENGTH_SHORT
                                ).show();

                                dialog.dismiss();
                                loadInventory();
                            } else {
                                Toast.makeText(
                                        this,
                                        "Could not add the item. Check the item details.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
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
        editQuantity.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
        );

        int padding = (int) (24 * getResources()
                .getDisplayMetrics().density);

        editQuantity.setPadding(padding, padding / 2,
                padding, padding / 2);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Remove " + itemName)
                .setMessage(
                        "Current quantity: " + currentQuantity
                )
                .setView(editQuantity)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Remove", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(v -> {
                            int quantity;

                            try {
                                quantity = Integer.parseInt(
                                        editQuantity.getText()
                                                .toString().trim()
                                );
                            } catch (NumberFormatException e) {
                                editQuantity.setError(
                                        "Enter a valid quantity."
                                );
                                return;
                            }

                            if (quantity <= 0) {
                                editQuantity.setError(
                                        "Enter a quantity greater than zero."
                                );
                                return;
                            }

                            if (quantity > currentQuantity) {
                                editQuantity.setError(
                                        "You cannot remove more than "
                                                + currentQuantity + "."
                                );
                                return;
                            }

                            boolean success =
                                    databaseHelper.removeInventoryItem(
                                            itemId,
                                            quantity,
                                            username
                                    );

                            if (success) {
                                Toast.makeText(
                                        this,
                                        "Inventory updated.",
                                        Toast.LENGTH_SHORT
                                ).show();

                                dialog.dismiss();
                                loadInventory();
                            } else {
                                Toast.makeText(
                                        this,
                                        "Could not remove the quantity. Refresh and try again.",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        })
        );

        dialog.show();
    }
}
