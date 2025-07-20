package com.example.blockednumbers;
import android.app.role.RoleManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BlockedNumberContract;
import android.widget.Button;

import androidx.annotation.NonNull;

import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.Manifest;
import android.widget.ToggleButton;

import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity implements BlockedNumbersAdapter.OnDeleteClickListener {
    private static final int REQUEST_CODE_ROLE_DIALER = 1;
    private static final int REQUEST_CODE_PERMISSIONS = 2;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.WRITE_CALL_LOG
    };

    private static final int REQUEST_CODE_EXPORT = 3;
    private static final int REQUEST_CODE_IMPORT = 4;

    private BlockedNumbersAdapter adapter;
    private final List<String> blockedNumbers = new ArrayList<>();

    private EditText etPhoneNumber;
    private boolean ascendingSort = true;
    private ToggleButton btnSort;
    private Drawable ascendingDrawable;
    private Drawable descendingDrawable;

//    private final ActivityResultLauncher<Intent> roleRequestLauncher =
//            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
//                if (result.getResultCode() == RESULT_OK) {
//                    Toast.makeText(this, "App set as default dialer: " + result, Toast.LENGTH_SHORT).show();
//                } else
//                    Toast.makeText(this, "App unable to set as default dialer: Code: " + result.getResultCode(), Toast.LENGTH_SHORT).show();
//                Log.e("Main", "Result: " + result);
//            });

    @Override
    public void onDeleteClick(int position) {
        String numberToDelete = blockedNumbers.get(position);
        unblockNumber(numberToDelete, position);
    }

    private void unblockNumber(String number, int position) {
        try {
            // Create where clause
            String where = BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "=?";
            String[] args = new String[]{number};

            // Delete from system blocked numbers
            int deletedRows = getContentResolver().delete(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    where,
                    args
            );

            if (deletedRows > 0) {
                // Remove from local list and update UI
                blockedNumbers.remove(position);
                adapter.notifyItemRemoved(position);
                Toast.makeText(this, "Number unblocked", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to unblock number", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Toast.makeText(this, "Permission denied for unblocking", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        etPhoneNumber = findViewById(R.id.etPhoneNumber);
        btnSort = findViewById(R.id.btnSort);
        Button btnAdd = findViewById(R.id.btnAdd);
        // Set up click listeners
        btnAdd.setOnClickListener(v -> addNumberToBlocklist());
        btnSort.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ascendingSort = !isChecked;
            sortBlockedNumbers();
        });

        ascendingDrawable = ContextCompat.getDrawable(this, R.drawable.ic_sort_a);
        descendingDrawable = ContextCompat.getDrawable(this, R.drawable.ic_sort_d);

        btnSort = findViewById(R.id.btnSort);
        btnSort.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ascendingSort = !isChecked;
            updateSortButtonDrawable();
            sortBlockedNumbers();
        });

        checkAndGrantDialerRole();
    }

    private void checkAndGrantDialerRole() {
        RoleManager roleManager = (RoleManager) getSystemService(ROLE_SERVICE);
        if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER);
                startActivityForResult(intent, REQUEST_CODE_ROLE_DIALER);
//                roleRequestLauncher.launch(intent);
            } else {
                // We have the role and permissions, load numbers
                setupUI();
            }
        }
    }

    private void setupUI() {
        setupRecyclerView();
        setupButtons();
        loadBlockedNumbers();
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BlockedNumbersAdapter(blockedNumbers, this);
        recyclerView.setAdapter(adapter);
    }

    private void setupButtons() {
        Button btnExport = findViewById(R.id.btnExport);
        Button btnImport = findViewById(R.id.btnImport);

        btnExport.setOnClickListener(v -> exportBlockedNumbers());
        btnImport.setOnClickListener(v -> importBlockedNumbers());
    }

    private void loadBlockedNumbers() {
        blockedNumbers.clear();
        try (Cursor cursor = getContentResolver().query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                new String[]{BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER},
                null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    blockedNumbers.add(cursor.getString(0));
                }
                adapter.notifyDataSetChanged();
            }
        }
    }

    private void exportBlockedNumbers() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "blocked_numbers.json");
        startActivityForResult(intent, REQUEST_CODE_EXPORT);
    }

    private void importBlockedNumbers() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        startActivityForResult(intent, REQUEST_CODE_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_ROLE_DIALER:
                    setupUI();
                    break;
                case REQUEST_CODE_EXPORT:
                    handleExport(data.getData());
                    break;
                case REQUEST_CODE_IMPORT:
                    handleImport(data.getData());
                    break;
            }
        }
    }

    private void handleExport(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            String json = new Gson().toJson(blockedNumbers);
            outputStream.write(json.getBytes());
            Toast.makeText(this, "Export successful", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleImport(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder jsonBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }

            List<String> numbers = new Gson().fromJson(
                    jsonBuilder.toString(),
                    new TypeToken<List<String>>(){}.getType()
            );

            for (String number : numbers) {
                ContentValues values = new ContentValues();
                values.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);
                getContentResolver().insert(
                        BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                        values
                );
            }

            loadBlockedNumbers();
            Toast.makeText(this, "Import successful", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void addNumberToBlocklist() {
        String number = etPhoneNumber.getText().toString().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (blockedNumbers.contains(number)) {
            Toast.makeText(this, "Number already blocked", Toast.LENGTH_SHORT).show();
            return;
        }

        ContentValues values = new ContentValues();
        values.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);

        try {
            getContentResolver().insert(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    values
            );

            blockedNumbers.add(number);
            sortBlockedNumbers(); // Maintain current sort order
            etPhoneNumber.setText("");
            Toast.makeText(this, "Number blocked", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to block number: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sortBlockedNumbers() {
        Collections.sort(blockedNumbers, (n1, n2) -> {
            if (ascendingSort) {
                return n1.compareTo(n2);
            } else {
                return n2.compareTo(n1);
            }
        });
        adapter.notifyDataSetChanged();
    }

    private void updateSortButtonDrawable() {
        if (btnSort.isChecked()){
            btnSort.setBackground(descendingDrawable);
        } else {
            btnSort.setBackground(ascendingDrawable);
        }
    }
}