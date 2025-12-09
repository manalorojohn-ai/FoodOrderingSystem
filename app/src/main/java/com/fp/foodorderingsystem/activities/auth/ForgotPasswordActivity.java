package com.fp.foodorderingsystem.activities.auth;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.services.AuthService;
import com.google.android.material.textfield.TextInputEditText;

public class ForgotPasswordActivity extends AppCompatActivity {
    private TextInputEditText etEmail;
    private AuthService authService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);
        authService = new AuthService(this);
        etEmail = findViewById(R.id.etEmail);
        Button btnSend = findViewById(R.id.btnSend);
        btnSend.setOnClickListener(v -> {
            String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email", Toast.LENGTH_SHORT).show();
                return;
            }
            btnSend.setEnabled(false);
            // Use 6-digit OTP flow for password reset
            authService.sendRecoveryOtp(email, new AuthService.SimpleCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        btnSend.setEnabled(true);
                        Toast.makeText(ForgotPasswordActivity.this, "Code sent. Check your email.", Toast.LENGTH_LONG).show();
                        ResetPasswordActivity.start(ForgotPasswordActivity.this, email);
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        btnSend.setEnabled(true);
                        Toast.makeText(ForgotPasswordActivity.this, error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }
}


