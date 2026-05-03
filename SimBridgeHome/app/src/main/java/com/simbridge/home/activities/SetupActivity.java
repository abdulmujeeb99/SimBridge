package com.simbridge.home.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.simbridge.home.R;
import com.simbridge.home.utils.AppConfig;

public class SetupActivity extends AppCompatActivity {

    private EditText etServerUrl, etPairCode, etSimLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        etServerUrl = findViewById(R.id.etServerUrl);
        etPairCode  = findViewById(R.id.etPairCode);
        etSimLabel  = findViewById(R.id.etSimLabel);

        etServerUrl.setText(AppConfig.getServerUrl(this));
        etPairCode.setText(AppConfig.getPairCode(this));
        etSimLabel.setText(AppConfig.getSimLabel(this));

        findViewById(R.id.btnGenerateCode).setOnClickListener(v -> {
            String code = String.valueOf((int)(Math.random() * 900000) + 100000);
            etPairCode.setText(code);
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            String url   = etServerUrl.getText().toString().trim();
            String code  = etPairCode.getText().toString().trim();
            String label = etSimLabel.getText().toString().trim();

            if (url.isEmpty() || !url.startsWith("ws")) {
                Toast.makeText(this, "Enter a valid WebSocket URL (ws:// or wss://)", Toast.LENGTH_SHORT).show();
                return;
            }
            if (code.length() < 4) {
                Toast.makeText(this, "Pair code must be at least 4 digits", Toast.LENGTH_SHORT).show();
                return;
            }
            if (label.isEmpty()) {
                Toast.makeText(this, "Enter a SIM label e.g. Karachi SIM", Toast.LENGTH_SHORT).show();
                return;
            }

            AppConfig.setServerUrl(this, url);
            AppConfig.setPairCode(this, code);
            AppConfig.setSimLabel(this, label);
            AppConfig.setSetupDone(this, true);
            Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
