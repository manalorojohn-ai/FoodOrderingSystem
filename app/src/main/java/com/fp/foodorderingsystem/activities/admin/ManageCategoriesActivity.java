package com.fp.foodorderingsystem.activities.admin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.adapters.AdminCategoryAdapter;
import com.fp.foodorderingsystem.models.Category;
import com.fp.foodorderingsystem.services.CategoryService;
import com.fp.foodorderingsystem.services.CategoryService.CategoryCallback;
import com.fp.foodorderingsystem.services.CategoryService.CategoryListCallback;
import com.fp.foodorderingsystem.services.CategoryService.SimpleCallback;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient;
import com.fp.foodorderingsystem.services.SupabaseRealtimeClient.RealtimeListener;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManageCategoriesActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    private com.google.android.material.appbar.MaterialToolbar toolbar;
    private TextInputLayout inputNameLayout;
    private TextInputLayout inputDescriptionLayout;
    private TextInputEditText inputName;
    private TextInputEditText inputDescription;
    private MaterialButton btnSaveCategory;
    private MaterialButton btnClearForm;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView rvCategories;
    private ProgressBar progressBar;
    private TextView tvEmptyState;

    private AdminCategoryAdapter adapter;
    private CategoryService categoryService;
    private SupabaseRealtimeClient realtimeClient;
    private final List<Category> categories = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private Category editingCategory = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_categories);
        
        toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        categoryService = new CategoryService(this);
        realtimeClient = new SupabaseRealtimeClient();

        initViews();
        setupRecyclerView();
        setupListeners();

        loadCategories(true);
        subscribeToRealtimeUpdates();
    }

    private void initViews() {
        inputNameLayout = findViewById(R.id.inputNameLayout);
        inputDescriptionLayout = findViewById(R.id.inputDescriptionLayout);
        inputName = findViewById(R.id.inputName);
        inputDescription = findViewById(R.id.inputDescription);
        btnSaveCategory = findViewById(R.id.btnSaveCategory);
        btnClearForm = findViewById(R.id.btnClearForm);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        rvCategories = findViewById(R.id.rvCategories);
        progressBar = findViewById(R.id.progressBar);
        tvEmptyState = findViewById(R.id.tvEmptyState);
    }

    private void setupRecyclerView() {
        adapter = new AdminCategoryAdapter();
        adapter.setOnCategoryActionListener(new AdminCategoryAdapter.OnCategoryActionListener() {
            @Override
            public void onEdit(Category category) {
                populateFormForEdit(category);
            }

            @Override
            public void onDelete(Category category) {
                confirmDelete(category);
            }
        });

        rvCategories.setLayoutManager(new LinearLayoutManager(this));
        rvCategories.setAdapter(adapter);
    }

    private void setupListeners() {
        swipeRefreshLayout.setOnRefreshListener(this);
        btnSaveCategory.setOnClickListener(v -> saveCategory());
        btnClearForm.setOnClickListener(v -> clearForm());
    }

    private void loadCategories(boolean showSpinner) {
        if (showSpinner) {
            showLoading(true);
        }

        categoryService.getAllCategories(new CategoryListCallback() {
            @Override
            public void onSuccess(List<Category> categoryList) {
                mainHandler.post(() -> {
                    categories.clear();
                    if (categoryList != null) {
                        categories.addAll(categoryList);
                    }
                    sortCategories();
                    adapter.setItems(new ArrayList<>(categories));
                    updateEmptyState();
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageCategoriesActivity.this, error, Toast.LENGTH_SHORT).show();
                    showLoading(false);
                    swipeRefreshLayout.setRefreshing(false);
                });
            }
        });
    }

    private void saveCategory() {
        clearErrors();

        String name = getTextValue(inputName);
        String description = getTextValue(inputDescription);

        if (TextUtils.isEmpty(name)) {
            inputNameLayout.setError("Category name is required");
            return;
        }

        Category category = editingCategory != null ? editingCategory : new Category();
        category.setName(name);
        category.setDescription(TextUtils.isEmpty(description) ? null : description);

        showLoading(true);

        if (editingCategory == null) {
            categoryService.createCategory(category, new CategoryCallback() {
                @Override
                public void onSuccess(Category createdCategory) {
                    mainHandler.post(() -> {
                        Toast.makeText(ManageCategoriesActivity.this, "Category created", Toast.LENGTH_SHORT).show();
                        upsertLocalCategory(createdCategory);
                        clearForm();
                        showLoading(false);
                    });
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        Toast.makeText(ManageCategoriesActivity.this, error, Toast.LENGTH_SHORT).show();
                        showLoading(false);
                    });
                }
            });
        } else {
            categoryService.updateCategory(category, new CategoryCallback() {
                @Override
                public void onSuccess(Category updatedCategory) {
                    mainHandler.post(() -> {
                        Toast.makeText(ManageCategoriesActivity.this, "Category updated", Toast.LENGTH_SHORT).show();
                        upsertLocalCategory(updatedCategory);
                        clearForm();
                        showLoading(false);
                    });
                }

                @Override
                public void onError(String error) {
                    mainHandler.post(() -> {
                        Toast.makeText(ManageCategoriesActivity.this, error, Toast.LENGTH_SHORT).show();
                        showLoading(false);
                    });
                }
            });
        }
    }

    private void populateFormForEdit(Category category) {
        editingCategory = category;
        btnSaveCategory.setText("Update category");
        btnClearForm.setText("Cancel");
        inputName.setText(category.getName());
        inputDescription.setText(category.getDescription());
    }

    private void clearForm() {
        editingCategory = null;
        btnSaveCategory.setText("Save category");
        btnClearForm.setText("Clear");
        inputName.setText("");
        inputDescription.setText("");
        clearErrors();
    }

    private void clearErrors() {
        inputNameLayout.setError(null);
        inputDescriptionLayout.setError(null);
    }

    private void confirmDelete(Category category) {
        if (category == null) {
            return;
        }
        new AlertDialog.Builder(this)
            .setTitle("Delete category")
            .setMessage("Remove \"" + category.getName() + "\" from categories?")
            .setPositiveButton("Delete", (dialog, which) -> deleteCategory(category))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void deleteCategory(Category category) {
        showLoading(true);
        categoryService.deleteCategory(category.getId(), new SimpleCallback() {
            @Override
            public void onSuccess() {
                mainHandler.post(() -> {
                    Toast.makeText(ManageCategoriesActivity.this, "Category deleted", Toast.LENGTH_SHORT).show();
                    removeLocalCategory(category.getId());
                    showLoading(false);
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    Toast.makeText(ManageCategoriesActivity.this, error, Toast.LENGTH_SHORT).show();
                    showLoading(false);
                });
            }
        });
    }

    private void upsertLocalCategory(Category category) {
        if (category == null) {
            return;
        }
        boolean found = false;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getId() == category.getId()) {
                categories.set(i, category);
                found = true;
                break;
            }
        }
        if (!found) {
            categories.add(0, category);
        }
        sortCategories();
        adapter.setItems(new ArrayList<>(categories));
        updateEmptyState();
    }

    private void removeLocalCategory(int categoryId) {
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getId() == categoryId) {
                categories.remove(i);
                break;
            }
        }
        sortCategories();
        adapter.setItems(new ArrayList<>(categories));
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean isEmpty = categories.isEmpty();
        tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvCategories.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    private void sortCategories() {
        Collections.sort(categories, (a, b) -> {
            String dateA = a != null ? a.getCreatedAt() : null;
            String dateB = b != null ? b.getCreatedAt() : null;
            if (dateA == null && dateB == null) {
                return 0;
            }
            if (dateA == null) {
                return 1;
            }
            if (dateB == null) {
                return -1;
            }
            return dateB.compareTo(dateA);
        });
    }

    private void subscribeToRealtimeUpdates() {
        realtimeClient.subscribeToTable("public", "categories", new RealtimeListener() {
            @Override
            public void onOpen() {
                // no-op
            }

            @Override
            public void onChange(JsonObject payload) {
                handleRealtimePayload(payload);
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() ->
                    Toast.makeText(ManageCategoriesActivity.this, "Realtime error: " + error, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void handleRealtimePayload(JsonObject payload) {
        if (payload == null) {
            return;
        }
        String eventType = "";
        if (payload.has("eventType")) {
            eventType = payload.get("eventType").getAsString();
        } else if (payload.has("type")) {
            eventType = payload.get("type").getAsString();
        }

        JsonObject newRecord = null;
        if (payload.has("new")) {
            newRecord = payload.getAsJsonObject("new");
        } else if (payload.has("new_record")) {
            newRecord = payload.getAsJsonObject("new_record");
        }

        JsonObject oldRecord = null;
        if (payload.has("old")) {
            oldRecord = payload.getAsJsonObject("old");
        } else if (payload.has("old_record")) {
            oldRecord = payload.getAsJsonObject("old_record");
        }

        switch (eventType.toUpperCase()) {
            case "INSERT":
            case "UPDATE":
                if (newRecord != null) {
                    Category category = gson.fromJson(newRecord, Category.class);
                    mainHandler.post(() -> upsertLocalCategory(category));
                }
                break;
            case "DELETE":
                if (oldRecord != null && oldRecord.has("id")) {
                    int id = oldRecord.get("id").getAsInt();
                    mainHandler.post(() -> removeLocalCategory(id));
                }
                break;
            default:
                // ignore
        }
    }

    private String getTextValue(TextInputEditText editText) {
        return editText != null && editText.getText() != null
            ? editText.getText().toString().trim()
            : "";
    }

    @Override
    public void onRefresh() {
        loadCategories(false);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (realtimeClient != null) {
            realtimeClient.disconnect();
        }
    }

    @Override
    public void onBackPressed() {
        finish();
        super.onBackPressed();
    }
}
