package com.fp.foodorderingsystem.activities.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.services.AuthService;

public class ResetPasswordActivity extends AppCompatActivity {
    private static final String EXTRA_EMAIL = "email";
    private EditText otp1, otp2, otp3, otp4, otp5, otp6;
    private EditText etNewPassword;
    private Button btnResend;
    private Button btnConfirm;
    private String email;
    private AuthService authService;

    public static void start(Context ctx, String email) {
        Intent i = new Intent(ctx, ResetPasswordActivity.class);
        i.putExtra(EXTRA_EMAIL, email);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);
        email = getIntent().getStringExtra(EXTRA_EMAIL);
        authService = new AuthService(this);

        otp1 = findViewById(R.id.otp1);
        otp2 = findViewById(R.id.otp2);
        otp3 = findViewById(R.id.otp3);
        otp4 = findViewById(R.id.otp4);
        otp5 = findViewById(R.id.otp5);
        otp6 = findViewById(R.id.otp6);
        etNewPassword = findViewById(R.id.etNewPassword);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnResend = findViewById(R.id.btnResendCode);

        setupOtpInputs();

        btnConfirm.setOnClickListener(v -> doReset());
        if (btnResend != null) {
            btnResend.setOnClickListener(v -> resendCode());
        }
    }

    private void setupOtpInputs() {
        setupOtpMove(otp1, otp2);
        setupOtpMove(otp2, otp3);
        setupOtpMove(otp3, otp4);
        setupOtpMove(otp4, otp5);
        setupOtpMove(otp5, otp6);

        // Handle backspace to go to previous
        setupOtpBackspace(otp2, otp1);
        setupOtpBackspace(otp3, otp2);
        setupOtpBackspace(otp4, otp3);
        setupOtpBackspace(otp5, otp4);
        setupOtpBackspace(otp6, otp5);
    }

    private void setupOtpMove(final EditText current, final EditText next) {
        current.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 1 && next != null) {
                    next.requestFocus();
                }
            }
        });
    }

    private void setupOtpBackspace(final EditText current, final EditText previous) {
        current.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN &&
                        keyCode == KeyEvent.KEYCODE_DEL &&
                        current.getText().length() == 0 &&
                        previous != null) {
                    previous.requestFocus();
                    previous.setSelection(previous.getText().length());
                    return true;
                }
                return false;
            }
        });
    }

    private void doReset() {
        String code = otp1.getText().toString()+otp2.getText().toString()+otp3.getText().toString()+
                otp4.getText().toString()+otp5.getText().toString()+otp6.getText().toString();
        String newPass = etNewPassword.getText().toString();
        if (code.length() != 6) {
            Toast.makeText(this, "Enter 6-digit code", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newPass.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
            return;
        }

        authService.verifyRecoveryOtp(email, code, new AuthService.TokenCallback() {
            @Override
            public void onSuccess(String accessToken) {
                authService.updatePasswordWithToken(accessToken, newPass, new AuthService.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            Toast.makeText(ResetPasswordActivity.this, "Password updated. Please log in.", Toast.LENGTH_LONG).show();
                            finish();
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(ResetPasswordActivity.this, error, Toast.LENGTH_LONG).show());
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ResetPasswordActivity.this, error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void resendCode() {
        if (email == null || email.isEmpty()) {
            Toast.makeText(this, "Missing email address", Toast.LENGTH_SHORT).show();
            return;
        }

        if (btnResend != null) {
            btnResend.setEnabled(false);
        }

        authService.sendRecoveryOtp(email, new AuthService.SimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    if (btnResend != null) {
                        btnResend.setEnabled(true);
                    }
                    Toast.makeText(ResetPasswordActivity.this, "Verification code resent", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (btnResend != null) {
                        btnResend.setEnabled(true);
                    }
                    Toast.makeText(ResetPasswordActivity.this, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}


