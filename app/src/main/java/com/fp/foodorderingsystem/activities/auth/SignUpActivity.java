package com.fp.foodorderingsystem.activities.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.activities.admin.AdminDashboardActivity;
import com.fp.foodorderingsystem.activities.common.HomeActivity;
import com.fp.foodorderingsystem.activities.customer.CustomerDashboardActivity;
import com.fp.foodorderingsystem.models.User;
import com.fp.foodorderingsystem.services.AuthService;
import com.fp.foodorderingsystem.services.UserService;
import com.fp.foodorderingsystem.utils.PreferenceUtil;
import com.fp.foodorderingsystem.utils.ValidationUtil;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class SignUpActivity extends AppCompatActivity {
    private TextInputLayout layoutFirstName, layoutLastName, layoutEmail, layoutPhone, layoutAddress, layoutPassword, layoutConfirmPassword;
    private TextInputEditText etFirstName, etLastName, etEmail, etPhone, etPassword, etConfirmPassword;
    private MaterialAutoCompleteTextView etAddress;
    private MaterialButton btnSignUp;
    
    private AuthService authService;
    private UserService userService;
    private PreferenceUtil preferenceUtil;
    private Set<String> allowedAddresses;
    
    // Validation states
    private boolean isValidFirstName = false;
    private boolean isValidLastName = false;
    private boolean isValidEmail = false;
    private boolean isEmailAvailable = false;
    private boolean isValidPhone = false;
    private boolean isValidAddress = true; // address validation disabled per request
    private boolean isValidPassword = false;
    private boolean isPasswordMatch = false;
    private boolean isSubmitting = false;
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isCheckingEmail = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        authService = new AuthService(this);
        userService = new UserService(this);
        preferenceUtil = new PreferenceUtil(this);

        initViews();
        setupClickListeners();
        setupRealTimeValidation();
    }

    private void initViews() {
        layoutFirstName = findViewById(R.id.layoutFirstName);
        layoutLastName = findViewById(R.id.layoutLastName);
        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPhone = findViewById(R.id.layoutPhone);
        layoutAddress = findViewById(R.id.layoutAddress);
        layoutPassword = findViewById(R.id.layoutPassword);
        layoutConfirmPassword = findViewById(R.id.layoutConfirmPassword);
        
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etAddress = findViewById(R.id.etAddress);
        btnSignUp = findViewById(R.id.btnSignUp);

        // Force password toggle eye icon in case XML style is overridden
        if (layoutPassword != null) {
            layoutPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        }
        if (layoutConfirmPassword != null) {
            layoutConfirmPassword.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        }

        // Set up toolbar back button
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setNavigationOnClickListener(v -> {
                Intent intent = new Intent(SignUpActivity.this, HomeActivity.class);
                startActivity(intent);
                finish();
            });
        }

        String[] addressOptions = getResources().getStringArray(R.array.address_options);
        allowedAddresses = new HashSet<>();
        // Address validation removed; dropdown kept optional for user convenience
        if (etAddress != null) {
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                addressOptions
            );
            etAddress.setAdapter(adapter);
            etAddress.setOnClickListener(v -> etAddress.showDropDown());
            etAddress.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    etAddress.showDropDown();
                }
            });
        }
        
        // Initially disable sign up button
        updateSignUpButtonState();
    }

    private void setupClickListeners() {
        btnSignUp.setOnClickListener(v -> performSignUp());
        findViewById(R.id.tvLoginLink).setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        });
    }
    
    private void setupRealTimeValidation() {
        // First Name validation
        etFirstName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateFirstName();
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Last Name validation
        etLastName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateLastName();
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Email validation with duplicate check
        etEmail.addTextChangedListener(new TextWatcher() {
            private Handler handler = new Handler(Looper.getMainLooper());
            private Runnable emailCheckRunnable;
            
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Reset availability on every change to avoid using stale "available" state
                isEmailAvailable = false;
                layoutEmail.setHelperText(null);
                layoutEmail.setError(null);
                updateSignUpButtonState();

                // Cancel previous check
                if (emailCheckRunnable != null) {
                    handler.removeCallbacks(emailCheckRunnable);
                }
                
                // Debounce email check by 500ms
                emailCheckRunnable = () -> {
                    validateEmail();
                    checkEmailAvailability();
                };
                handler.postDelayed(emailCheckRunnable, 500);
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Phone validation
        etPhone.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePhone();
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Password validation
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePassword();
                validatePasswordMatch();
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
        
        // Confirm Password validation
        etConfirmPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validatePasswordMatch();
            }
            
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }
    
    private void validateFirstName() {
        String name = etFirstName.getText() != null ? etFirstName.getText().toString() : "";
        String sanitized = sanitizeSimpleName(name);

        if (!sanitized.equals(name)) {
            etFirstName.setText(sanitized);
            etFirstName.setSelection(sanitized.length());
        }

        String error = getSimpleNameErrorMessage(sanitized);
        isValidFirstName = error == null;
        
        if (error != null) {
            layoutFirstName.setError(error);
        } else {
            layoutFirstName.setError(null);
            layoutFirstName.setErrorEnabled(false);
        }
        
        updateSignUpButtonState();
    }
    
    private void validateLastName() {
        String name = etLastName.getText() != null ? etLastName.getText().toString() : "";
        String sanitized = sanitizeSimpleName(name);

        if (!sanitized.equals(name)) {
            etLastName.setText(sanitized);
            etLastName.setSelection(sanitized.length());
        }

        String error = getSimpleNameErrorMessage(sanitized);
        isValidLastName = error == null;
        
        if (error != null) {
            layoutLastName.setError(error);
        } else {
            layoutLastName.setError(null);
            layoutLastName.setErrorEnabled(false);
        }
        
        updateSignUpButtonState();
    }

    /**
     * Simple name rules for first/last name:
     * - Only letters (A–Z, a–z)
     * - No spaces or numbers
     * - Length 2–20
     * - No more than 3 of the same letter in a row (prevents JOOOHHHN)
     */
    private String sanitizeSimpleName(String input) {
        if (input == null) return "";
        // Keep only letters
        String lettersOnly = input.replaceAll("[^A-Za-z]", "");
        // Collapse repeated same char >3
        return lettersOnly.replaceAll("(.)\\1{3,}", "$1$1$1");
    }

    private String getSimpleNameErrorMessage(String name) {
        if (name == null || name.isEmpty()) {
            return "Name cannot be empty";
        }
        if (name.length() < 2) {
            return "Name must be at least 2 letters";
        }
        if (name.length() > 20) {
            return "Name must be at most 20 letters";
        }
        if (!name.matches("^[A-Za-z]+$")) {
            return "Name can contain letters only";
        }
        if (name.matches("(?i).*(.)\\1{3,}.*")) {
            return "Name has too many repeated letters";
        }
        return null;
    }
    
    private void validateEmail() {
        String email = etEmail.getText() != null ? etEmail.getText().toString() : "";
        String sanitized = ValidationUtil.sanitizeEmail(email).toLowerCase(Locale.US).trim();

        if (!sanitized.equals(email)) {
            etEmail.setText(sanitized);
            etEmail.setSelection(sanitized.length());
        }

        if (TextUtils.isEmpty(sanitized)) {
            isValidEmail = false;
            isEmailAvailable = false;
            layoutEmail.setError("Email cannot be empty");
            updateSignUpButtonState();
            return;
        }

        // Use Android built-in pattern and domain guard similar to reference
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(sanitized).matches()) {
            isValidEmail = false;
            isEmailAvailable = false;
            layoutEmail.setError("Use a valid email address");
            updateSignUpButtonState();
            return;
        }

        String[] allowedDomains = {"gmail.com", "yahoo.com", "outlook.com", "hotmail.com"};
        String domain = sanitized.contains("@") ? sanitized.substring(sanitized.indexOf('@') + 1) : "";
        String localPart = sanitized.contains("@") ? sanitized.substring(0, sanitized.indexOf('@')) : "";
        boolean domainOk = false;
        for (String d : allowedDomains) {
            if (d.equalsIgnoreCase(domain)) {
                domainOk = true;
                break;
            }
        }
        if (!domainOk || localPart.length() < 3) {
            isValidEmail = false;
            isEmailAvailable = false;
            layoutEmail.setError("Allowed: gmail, yahoo, outlook, hotmail (min 3 chars before @)");
            updateSignUpButtonState();
            return;
        }

        isValidEmail = true;
        // Availability must be re-checked for this sanitized email
        isEmailAvailable = false;
        layoutEmail.setError(null);
        layoutEmail.setErrorEnabled(false);
        layoutEmail.setHelperText("Checking email availability...");
        updateSignUpButtonState();
    }
    
    private void checkEmailAvailability() {
        if (isCheckingEmail || !isValidEmail) {
            return;
        }
        
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim().toLowerCase(Locale.US) : "";
        if (TextUtils.isEmpty(email)) {
            return;
        }
        
        isCheckingEmail = true;
        isEmailAvailable = false;
        layoutEmail.setError(null);
        layoutEmail.setErrorEnabled(false);
        layoutEmail.setHelperText("Checking email availability...");
        updateSignUpButtonState();
        
        userService.checkEmailExists(email, new UserService.EmailCheckCallback() {
            @Override
            public void onResult(boolean exists) {
                mainHandler.post(() -> {
                    isCheckingEmail = false;
                    if (exists) {
                        isEmailAvailable = false;
                        layoutEmail.setError("Email already exists. Cannot use this email.");
                        layoutEmail.setHelperText(null);
                    } else {
                        isEmailAvailable = true;
                        layoutEmail.setError(null);
                        layoutEmail.setErrorEnabled(false);
                        layoutEmail.setHelperText(null); // no "available" helper
                    }
                    updateSignUpButtonState();
                });
            }
            
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    isCheckingEmail = false;
                    isEmailAvailable = false;
                    layoutEmail.setError(error != null ? error : "Unable to verify email. Please try again.");
                    layoutEmail.setHelperText(null);
                    updateSignUpButtonState();
                });
            }
        });
    }
    
    private void validatePhone() {
        String phone = etPhone.getText() != null ? etPhone.getText().toString() : "";
        String sanitized = ValidationUtil.sanitizePhone(phone);
        
        if (!sanitized.equals(phone)) {
            etPhone.setText(sanitized);
            etPhone.setSelection(sanitized.length());
        }
        
        String error = ValidationUtil.getPhoneErrorMessage(sanitized);
        isValidPhone = error == null;
        
        if (error != null) {
            layoutPhone.setError(error);
        } else {
            layoutPhone.setError(null);
            layoutPhone.setErrorEnabled(false);
        }
        
        updateSignUpButtonState();
    }
    
    private void validatePassword() {
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String error = ValidationUtil.getPasswordErrorMessage(password);
        isValidPassword = error == null;
        
        if (error != null) {
            layoutPassword.setError(error);
        } else {
            layoutPassword.setError(null);
            layoutPassword.setErrorEnabled(false);
            layoutPassword.setHelperText("Password meets requirements");
        }
        
        updateSignUpButtonState();
    }
    
    private void validatePasswordMatch() {
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";
        
        if (TextUtils.isEmpty(confirmPassword)) {
            isPasswordMatch = false;
            layoutConfirmPassword.setError(null);
            layoutConfirmPassword.setErrorEnabled(false);
            updateSignUpButtonState();
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            isPasswordMatch = false;
            layoutConfirmPassword.setError("Passwords do not match");
        } else {
            isPasswordMatch = true;
            layoutConfirmPassword.setError(null);
            layoutConfirmPassword.setErrorEnabled(false);
            layoutConfirmPassword.setHelperText("Passwords match");
        }
        
        updateSignUpButtonState();
    }
    
    private void updateSignUpButtonState() {
        boolean allValid = isValidFirstName && 
                          isValidLastName &&
                          isValidEmail && 
                          isEmailAvailable && 
                          isValidPhone && 
                          isValidPassword && 
                          isPasswordMatch &&
                          !isCheckingEmail;
        
        btnSignUp.setEnabled(allValid);
        btnSignUp.setAlpha(allValid ? 1.0f : 0.5f);
    }

    private void performSignUp() {
        // Final validation before submission
        String firstName = ValidationUtil.sanitizeInput(etFirstName.getText() != null ? etFirstName.getText().toString() : "");
        String lastName = ValidationUtil.sanitizeInput(etLastName.getText() != null ? etLastName.getText().toString() : "");
        String fullName = (firstName + " " + lastName).trim();
        String email = ValidationUtil.sanitizeEmail(etEmail.getText() != null ? etEmail.getText().toString() : "");
        String phone = ValidationUtil.sanitizePhone(etPhone.getText() != null ? etPhone.getText().toString() : "");
        String address = etAddress.getText() != null ? etAddress.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";

        // Re-validate all fields
        validateFirstName();
        validateLastName();
        validateEmail();
        validatePhone();
        validatePassword();
        validatePasswordMatch();
        
        if (!isEmailAvailable) {
            Toast.makeText(this, "Email is already used!", Toast.LENGTH_LONG).show();
            layoutEmail.setError("Email already exists. Cannot use this email.");
            return;
        }

        if (!isValidFirstName || !isValidLastName || !isValidEmail || !isEmailAvailable || !isValidPhone || 
            !isValidPassword || !isPasswordMatch) {
            Toast.makeText(this, "Please fix all validation errors before submitting", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        final String userType = "customer";
        if (isSubmitting) return;
        isSubmitting = true;
        btnSignUp.setEnabled(false);
        btnSignUp.setText("Checking email...");

        // Final real-time check against Supabase users before sign-up
        userService.checkEmailExists(email, new UserService.EmailCheckCallback() {
            @Override
            public void onResult(boolean exists) {
                runOnUiThread(() -> {
                    if (exists) {
                        isSubmitting = false;
                        btnSignUp.setEnabled(true);
                        btnSignUp.setText("Sign Up");
                        layoutEmail.setError("Email already exists. Cannot use this email.");
                        Toast.makeText(SignUpActivity.this, "Email is already used!", Toast.LENGTH_LONG).show();
                        return;
                    }
                    proceedSignUp(fullName, phone, address, email, password, userType);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isSubmitting = false;
                    btnSignUp.setEnabled(true);
                    btnSignUp.setText("Sign Up");
                    layoutEmail.setError(error != null ? error : "Unable to verify email. Please try again.");
                    Toast.makeText(SignUpActivity.this, "Unable to verify email. Please try again.", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void proceedSignUp(String fullName, String phone, String address, String email, String password, String userType) {
        btnSignUp.setText("Creating account...");
        authService.signUp(email, password, fullName, phone, address, userType, new AuthService.AuthCallback() {
            @Override
            public void onSuccess(User user, String token) {
                runOnUiThread(() -> {
                    isSubmitting = false;
                    preferenceUtil.saveTempSignupData(fullName, phone, address, userType);
                    btnSignUp.setEnabled(true);
                    btnSignUp.setText("Sign Up");
                    Toast.makeText(SignUpActivity.this,
                        "Check your email for the verification code. If you don't see it, click 'Resend code'.",
                        Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(SignUpActivity.this, VerifyEmailActivity.class);
                    intent.putExtra("email", email);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    isSubmitting = false;
                    btnSignUp.setEnabled(true);
                    btnSignUp.setText("Sign Up");
                    showError(error);
                    updateSignUpButtonState();
                });
            }
        });
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
