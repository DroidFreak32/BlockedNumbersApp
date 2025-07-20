package com.example.blockednumbers;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.BlockedNumberContract;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final List<String> allBlockedNumbers = new ArrayList<>(); // Holds all raw numbers
    private final List<DisplayItem> displayedItems = new ArrayList<>(); // Holds summarized items for display
    private EditText etPhoneNumber, etPrefix, etStartSuffix, etEndSuffix;
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
        if (position < 0 || position >= displayedItems.size()) return;

        DisplayItem itemToDelete = displayedItems.get(position);
        // This list contains one number for single items, and multiple for ranges
        List<String> numbersToUnblock = itemToDelete.originalNumbers;

        int unblockedCount = 0;
        for (String number : numbersToUnblock) {
            if (unblockNumber(number)) {
                unblockedCount++;
            }
        }

        if (unblockedCount > 0) {
            Toast.makeText(this, unblockedCount + " number(s) unblocked", Toast.LENGTH_SHORT).show();
            // Reload the entire list to re-calculate groups
            loadBlockedNumbers();
        } else {
            Toast.makeText(this, "Failed to unblock number(s)", Toast.LENGTH_SHORT).show();
        }
    }

    // Modify unblockNumber to return a boolean and not touch the adapter directly
    private boolean unblockNumber(String number) {
        try {
            String where = BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "=?";
            String[] args = new String[]{number};

            int deletedRows = getContentResolver().delete(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    where,
                    args
            );
            return deletedRows > 0;
        } catch (SecurityException e) {
            // Log the exception for debugging
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        etPhoneNumber = findViewById(R.id.etPhoneNumber);
        etPrefix = findViewById(R.id.etPrefix);
        etStartSuffix = findViewById(R.id.etStartSuffix);
        etEndSuffix = findViewById(R.id.etEndSuffix);

        btnSort = findViewById(R.id.btnSort);
        Button btnAdd = findViewById(R.id.btnAdd);
        // Set up click listeners
        btnAdd.setOnClickListener(v -> addNumberToBlocklist());
        btnSort.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ascendingSort = !isChecked;
            sortBlockedNumbers();
        });

        Button btnBlockRange = findViewById(R.id.btnBlockRange);
        btnBlockRange.setOnClickListener(v -> addRangeToBlocklist());

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
//        adapter = new BlockedNumbersAdapter(blockedNumbers, this);
        adapter = new BlockedNumbersAdapter(displayedItems, this);
        recyclerView.setAdapter(adapter);
    }

    private void setupButtons() {
        Button btnExport = findViewById(R.id.btnExport);
        Button btnImport = findViewById(R.id.btnImport);

        btnExport.setOnClickListener(v -> exportBlockedNumbers());
        btnImport.setOnClickListener(v -> importBlockedNumbers());
    }

    private void loadBlockedNumbers() {
        allBlockedNumbers.clear();
        try (Cursor cursor = getContentResolver().query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                new String[]{BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER},
                null, null, null)) {
            if (cursor != null) {
                int numberIndex = cursor.getColumnIndex(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER);
                while (cursor.moveToNext()) {
                    String number = cursor.getString(numberIndex);
                    if (number != null) {
                        allBlockedNumbers.add(number);
                    }
                }
            }
        }
        sortAndProcessBlockedNumbers();
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
        if (uri == null) {
            Toast.makeText(this, "Export failed: Invalid file path", Toast.LENGTH_SHORT).show();
            return;
        }
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(allBlockedNumbers);
            if (outputStream != null)
                outputStream.write(json.getBytes());
            else
                Toast.makeText(this, "Export failed: No data available", Toast.LENGTH_SHORT).show();
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

        if (allBlockedNumbers.contains(number)) {
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

            allBlockedNumbers.add(number);
            sortBlockedNumbers(); // Maintain current sort order
            etPhoneNumber.setText("");
            Toast.makeText(this, "Number blocked", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to block number: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void sortBlockedNumbers() {
        allBlockedNumbers.sort((n1, n2) -> {
            if (ascendingSort) {
                return n1.compareTo(n2);
            } else {
                return n2.compareTo(n1);
            }
        });
//        adapter.notifyDataSetChanged();
        sortAndProcessBlockedNumbers();
    }

    // New private helper to avoid code duplication
    private void sortAndProcessBlockedNumbers() {
        // 1. Sort the raw data
        allBlockedNumbers.sort((n1, n2) -> ascendingSort ? n1.compareTo(n2) : n2.compareTo(n1));

        // 2. Group the sorted data
        List<DisplayItem> newItems = groupAndSummarizeNumbers(allBlockedNumbers);

        // 3. Update the adapter's list
        displayedItems.clear();
        displayedItems.addAll(newItems);
        adapter.notifyDataSetChanged();
    }
    private void updateSortButtonDrawable() {
        if (btnSort.isChecked()){
            btnSort.setBackground(descendingDrawable);
        } else {
            btnSort.setBackground(ascendingDrawable);
        }
    }

    private void addRangeToBlocklist() {
        String prefix = etPrefix.getText().toString().trim();
        String startStr = etStartSuffix.getText().toString().trim();
        String endStr = etEndSuffix.getText().toString().trim();

        if (prefix.isEmpty() || startStr.isEmpty() || endStr.isEmpty()) {
            Toast.makeText(this, "All range fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            long startSuffix = Long.parseLong(startStr);
            long endSuffix = Long.parseLong(endStr);

            if (startSuffix > endSuffix) {
                Toast.makeText(this, "Start suffix cannot be greater than end suffix", Toast.LENGTH_SHORT).show();
                return;
            }

            // Add a safety limit to prevent blocking too many numbers at once and causing ANRs
            long rangeSize = endSuffix - startSuffix + 1;
            if (rangeSize > 1000) {
                Toast.makeText(this, "Range is too large. Please block up to 1000 numbers at a time.", Toast.LENGTH_LONG).show();
                return;
            }

            // Use the length of the start suffix string to determine zero-padding
            int suffixLength = startStr.length();
            String format = "%0" + suffixLength + "d";

            ArrayList<ContentValues> valuesList = new ArrayList<>();
            for (long i = startSuffix; i <= endSuffix; i++) {
                String number = prefix + String.format(format, i);
                ContentValues values = new ContentValues();
                values.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);
                valuesList.add(values);
            }

            // Use bulkInsert for efficiency
            ContentValues[] valuesArray = valuesList.toArray(new ContentValues[0]);
            int insertedRows = getContentResolver().bulkInsert(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    valuesArray
            );

            if (insertedRows > 0) {
                Toast.makeText(this, insertedRows + " numbers blocked", Toast.LENGTH_SHORT).show();
                loadBlockedNumbers(); // Refresh the list from the content provider
                etPrefix.setText("");
                etStartSuffix.setText("");
                etEndSuffix.setText("");
            } else {
                Toast.makeText(this, "Failed to block numbers", Toast.LENGTH_SHORT).show();
            }

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid start or end suffix. Please enter valid numbers.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to block range: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Groups a sorted list of numbers into DisplayItems, summarizing ranges.
     *
     * @param numbers A list of phone numbers, MUST be sorted.
     * @return A list of DisplayItems ready for the adapter.
     */
    private List<DisplayItem> groupAndSummarizeNumbers(List<String> numbers) {
        final int SUFFIX_LENGTH = 3;
        final int MIN_GROUP_SIZE = 5; // Minimum numbers in a sequence to be grouped

        List<DisplayItem> items = new ArrayList<>();
        if (numbers.isEmpty()) {
            return items;
        }

        for (int i = 0; i < numbers.size(); ) {
            String currentNumber = numbers.get(i);
            if (currentNumber.length() <= SUFFIX_LENGTH) {
                // Number is too short to have a prefix, add as single item
                items.add(new DisplayItem(currentNumber, false, Collections.singletonList(currentNumber)));
                i++;
                continue;
            }

            String prefix = currentNumber.substring(0, currentNumber.length() - SUFFIX_LENGTH);
            List<String> potentialGroup = new ArrayList<>();
            potentialGroup.add(currentNumber);

            // Look ahead to find other numbers with the same prefix
            int j = i + 1;
            while (j < numbers.size() && numbers.get(j).startsWith(prefix)) {
                potentialGroup.add(numbers.get(j));
                j++;
            }

            if (potentialGroup.size() >= MIN_GROUP_SIZE) {
                // We have enough numbers to form a group
                String displayString = prefix + "XXX";
                items.add(new DisplayItem(displayString, true, potentialGroup));
            } else {
                // Not enough numbers for a group, add them individually
                for (String num : potentialGroup) {
                    items.add(new DisplayItem(num, false, Collections.singletonList(num)));
                }
            }
            // Jump the main loop index to the end of the processed group
            i += potentialGroup.size();
        }
        return items;
    }

    // Add this new method to MainActivity.java

    public static class DisplayItem {
        public final String displayString; // e.g., "+911234567XXX" or "+15551234"
        public final boolean isRange;
        public final List<String> originalNumbers; // The actual numbers this item represents

        public DisplayItem(String displayString, boolean isRange, List<String> originalNumbers) {
            this.displayString = displayString;
            this.isRange = isRange;
            this.originalNumbers = Collections.unmodifiableList(originalNumbers);
        }
    }
}