package com.fp.foodorderingsystem.activities.common;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.fp.foodorderingsystem.R;
import com.fp.foodorderingsystem.activities.auth.LoginActivity;
import com.fp.foodorderingsystem.activities.auth.SignUpActivity;
import com.fp.foodorderingsystem.activities.common.WelcomeActivity;
import com.fp.foodorderingsystem.utils.PreferenceUtil;

public class HomeActivity extends AppCompatActivity {
	private PreferenceUtil preferenceUtil;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		preferenceUtil = new PreferenceUtil(this);

		// If already logged in, redirect to the proper dashboard
		if (preferenceUtil.isLoggedIn()) {
			redirectToDashboard();
			return;
		}

		View loginButton = findViewById(R.id.btnLogin);
		View signUpLink = findViewById(R.id.btnCreateAccount);

		if (loginButton != null) {
			loginButton.setOnClickListener(v -> {
				startActivity(new Intent(HomeActivity.this, LoginActivity.class));
			});
		}

		if (signUpLink != null) {
			signUpLink.setOnClickListener(v -> {
				startActivity(new Intent(HomeActivity.this, SignUpActivity.class));
			});
		}
	}

	private void redirectToDashboard() {
		// Redirect to welcome page, which will then show the appropriate dashboard
		Intent intent = new Intent(this, WelcomeActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		startActivity(intent);
		finish();
	}
}
