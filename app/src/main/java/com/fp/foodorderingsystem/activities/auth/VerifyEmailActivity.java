package com.fp.foodorderingsystem.activities.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.activities.common.WelcomeActivity;
import com.fp.foodorderingsystem.models.User;
import com.fp.foodorderingsystem.services.AuthService;
import com.fp.foodorderingsystem.utils.PreferenceUtil;

public class VerifyEmailActivity extends AppCompatActivity {
	private EditText et1, et2, et3, et4, et5, et6;
	private String email;
	private AuthService authService;
	private PreferenceUtil preferenceUtil;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_verify_email);

		email = getIntent().getStringExtra("email");
		authService = new AuthService(this);
		preferenceUtil = new PreferenceUtil(this);

		initInputs();
		
		// Ensure verify button is enabled and clickable
		View btnVerify = findViewById(R.id.btnVerify);
		btnVerify.setEnabled(true);
		btnVerify.setClickable(true);
		btnVerify.setFocusable(true);
		btnVerify.setOnClickListener(v -> {
			android.util.Log.d("VerifyEmail", "Verify button clicked!");
			submitCode();
		});
		
		// Also add a long click listener to test if button is receiving touches
		btnVerify.setOnLongClickListener(v -> {
			android.util.Log.d("VerifyEmail", "Verify button long clicked!");
			return true;
		});
		
		findViewById(R.id.btnResend).setOnClickListener(v -> {
			// Clear OTP fields when requesting new code
			et1.setText("");
			et2.setText("");
			et3.setText("");
			et4.setText("");
			et5.setText("");
			et6.setText("");
			et1.requestFocus();
			
			// Use method for existing users (user was created during signup)
			authService.sendSignupOtpForExistingUser(email, new AuthService.SimpleCallback() {
				@Override
				public void onSuccess() {
					runOnUiThread(() -> {
						Toast.makeText(VerifyEmailActivity.this, 
							"New verification code sent to " + email, 
							Toast.LENGTH_LONG).show();
					});
				}

				@Override
				public void onError(String error) {
					runOnUiThread(() -> Toast.makeText(VerifyEmailActivity.this, error, Toast.LENGTH_LONG).show());
				}
			});
		});
	}

	private void initInputs() {
		et1 = findViewById(R.id.otp1);
		et2 = findViewById(R.id.otp2);
		et3 = findViewById(R.id.otp3);
		et4 = findViewById(R.id.otp4);
		et5 = findViewById(R.id.otp5);
		et6 = findViewById(R.id.otp6);
		TextWatcher watcher = new TextWatcher() {
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
			@Override public void afterTextChanged(Editable s) {
				View current = getCurrentFocus();
				if (current instanceof EditText && s.length() == 1) {
					View next = current.focusSearch(View.FOCUS_RIGHT);
					if (next != null) next.requestFocus();
				}
				
				// Auto-submit when all 6 digits are entered
				String code = (et1.getText().toString() + et2.getText().toString() + et3.getText().toString() +
						et4.getText().toString() + et5.getText().toString() + et6.getText().toString()).trim();
				if (code.length() == 6) {
					// Small delay to ensure last digit is set
					new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
						submitCode();
					}, 100);
				}
			}
		};
		et1.addTextChangedListener(watcher);
		et2.addTextChangedListener(watcher);
		et3.addTextChangedListener(watcher);
		et4.addTextChangedListener(watcher);
		et5.addTextChangedListener(watcher);
		et6.addTextChangedListener(watcher);
	}

	private void submitCode() {
		// Prevent multiple submissions
		View btnVerify = findViewById(R.id.btnVerify);
		if (!btnVerify.isEnabled()) {
			android.util.Log.d("VerifyEmail", "Button already disabled, ignoring submit");
			return;
		}
		
		String code = (et1.getText().toString() + et2.getText().toString() + et3.getText().toString() +
				et4.getText().toString() + et5.getText().toString() + et6.getText().toString()).trim();
		if (code.length() != 6) {
			Toast.makeText(this, "Enter 6-digit code", Toast.LENGTH_SHORT).show();
			return;
		}
		
		android.util.Log.d("VerifyEmail", "Submitting code: " + code.substring(0, 2) + "****");
		btnVerify.setEnabled(false);
		authService.verifyEmailOtp(email, code, new AuthService.AuthCallback() {
			@Override
			public void onSuccess(User user, String token) {
				runOnUiThread(() -> {
					findViewById(R.id.btnVerify).setEnabled(true);
					preferenceUtil.saveUserData(user.getId(), user.getEmail(), user.getFullName(), user.getUserType());
					// Navigate to welcome page first, then it will redirect to appropriate dashboard
					Intent intent = new Intent(VerifyEmailActivity.this, WelcomeActivity.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
					startActivity(intent);
					finish();
				});
			}

			@Override
			public void onError(String error) {
				runOnUiThread(() -> {
					findViewById(R.id.btnVerify).setEnabled(true);
					// Clear OTP fields if code expired or invalid
					if (error.contains("expired") || error.contains("invalid")) {
						et1.setText("");
						et2.setText("");
						et3.setText("");
						et4.setText("");
						et5.setText("");
						et6.setText("");
						et1.requestFocus();
						// Show helpful message to request a new code
						Toast.makeText(VerifyEmailActivity.this, 
							"Code expired. Please click 'Resend code' to get a new one.", 
							Toast.LENGTH_LONG).show();
					} else {
						Toast.makeText(VerifyEmailActivity.this, error, Toast.LENGTH_LONG).show();
					}
				});
			}
		});
	}
}
