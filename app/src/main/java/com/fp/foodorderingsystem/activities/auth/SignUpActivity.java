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
    private TextInputLayout layoutFullName, layoutEmail, layoutPhone, layoutAddress, layoutPassword, layoutConfirmPassword;
    private TextInputEditText etFullName, etEmail, etPhone, etPassword, etConfirmPassword;
    private MaterialAutoCompleteTextView etAddress;
    private MaterialButton btnSignUp;
    
    private AuthService authService;
    private UserService userService;
    private PreferenceUtil preferenceUtil;
    private Set<String> allowedAddresses;
    
    // Validation states
    private boolean isValidFullName = false;
    private boolean isValidEmail = false;
    private boolean isEmailAvailable = false;
    private boolean isValidPhone = false;
    private boolean isValidAddress = false;
    private boolean isValidPassword = false;
    private boolean isPasswordMatch = false;
    
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
        layoutFullName = findViewById(R.id.layoutFullName);
        layoutEmail = findViewById(R.id.layoutEmail);
        layoutPhone = findViewById(R.id.layoutPhone);
        layoutAddress = findViewById(R.id.layoutAddress);
        layoutPassword = findViewById(R.id.layoutPassword);
        layoutConfirmPassword = findViewById(R.id.layoutConfirmPassword);
        
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etAddress = findViewById(R.id.etAddress);
        btnSignUp = findViewById(R.id.btnSignUp);

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
        for (String option : addressOptions) {
            allowedAddresses.add(option.toLowerCase(Locale.US));
        }

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
        // Full Name validation
        etFullName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateFullName();
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
        
        // Address validation
        etAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateAddress();
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
    
    private void validateFullName() {
        String name = etFullName.getText() != null ? etFullName.getText().toString() : "";
        String sanitized = ValidationUtil.sanitizeInput(name);
        
        if (!sanitized.equals(name)) {
            etFullName.setText(sanitized);
            etFullName.setSelection(sanitized.length());
        }
        
        String error = ValidationUtil.getFullNameErrorMessage(sanitized);
        isValidFullName = error == null;
        
        if (error != null) {
            layoutFullName.setError(error);
        } else {
            layoutFullName.setError(null);
            layoutFullName.setErrorEnabled(false);
        }
        
        updateSignUpButtonState();
    }
    
    private void validateEmail() {
        String email = etEmail.getText() != null ? etEmail.getText().toString() : "";
        String sanitized = ValidationUtil.sanitizeEmail(email);
        
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
        
        if (!ValidationUtil.isValidEmail(sanitized)) {
            isValidEmail = false;
            isEmailAvailable = false;
            layoutEmail.setError("Please enter a valid email address");
            updateSignUpButtonState();
            return;
        }
        
        isValidEmail = true;
        layoutEmail.setError(null);
        layoutEmail.setErrorEnabled(false);
        updateSignUpButtonState();
    }
    
    private void checkEmailAvailability() {
        if (isCheckingEmail || !isValidEmail) {
            return;
        }
        
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        if (TextUtils.isEmpty(email)) {
            return;
        }
        
        isCheckingEmail = true;
        layoutEmail.setHelperText("Checking email availability...");
        
        userService.checkEmailExists(email, new UserService.EmailCheckCallback() {
            @Override
            public void onResult(boolean exists) {
                mainHandler.post(() -> {
                    isCheckingEmail = false;
                    if (exists) {
                        isEmailAvailable = false;
                        layoutEmail.setError("This email is already registered");
                        layoutEmail.setHelperText(null);
                    } else {
                        isEmailAvailable = true;
                        layoutEmail.setError(null);
                        layoutEmail.setErrorEnabled(false);
                        layoutEmail.setHelperText("Email is available");
                    }
                    updateSignUpButtonState();
                });
            }
            
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    isCheckingEmail = false;
                    // On error, allow registration (Supabase will catch duplicates)
                    isEmailAvailable = true;
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
    
    private void validateAddress() {
        String address = etAddress.getText() != null ? etAddress.getText().toString().trim() : "";
        String error = ValidationUtil.getAddressErrorMessage(address);
        isValidAddress = error == null;
        
        if (error != null) {
            layoutAddress.setError(error);
        } else {
            layoutAddress.setError(null);
            layoutAddress.setErrorEnabled(false);
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
        boolean allValid = isValidFullName && 
                          isValidEmail && 
                          isEmailAvailable && 
                          isValidPhone && 
                          isValidAddress && 
                          isValidPassword && 
                          isPasswordMatch &&
                          !isCheckingEmail;
        
        btnSignUp.setEnabled(allValid);
        btnSignUp.setAlpha(allValid ? 1.0f : 0.5f);
    }

    private void performSignUp() {
        // Final validation before submission
        String fullName = ValidationUtil.sanitizeInput(etFullName.getText() != null ? etFullName.getText().toString() : "");
        String email = ValidationUtil.sanitizeEmail(etEmail.getText() != null ? etEmail.getText().toString() : "");
        String phone = ValidationUtil.sanitizePhone(etPhone.getText() != null ? etPhone.getText().toString() : "");
        String address = etAddress.getText() != null ? etAddress.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";
        String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";

        // Re-validate all fields
        validateFullName();
        validateEmail();
        validatePhone();
        validateAddress();
        validatePassword();
        validatePasswordMatch();
        
        if (!isValidFullName || !isValidEmail || !isEmailAvailable || !isValidPhone || 
            !isValidAddress || !isValidPassword || !isPasswordMatch) {
            Toast.makeText(this, "Please fix all validation errors before submitting", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        final String userType = "customer";
        btnSignUp.setEnabled(false);
        btnSignUp.setText("Creating account...");

        authService.signUp(email, password, fullName, phone, address, userType, new AuthService.AuthCallback() {
            @Override
            public void onSuccess(User user, String token) {
                runOnUiThread(() -> {
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
