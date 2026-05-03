package com.simbridge.travel.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.simbridge.travel.R;
import com.simbridge.travel.utils.AppConfig;

public class SetupActivity extends AppCompatActivity {

    private EditText etServerUrl, etPairCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_travel);

        etServerUrl = findViewById(R.id.etServerUrl);
        etPairCode  = findViewById(R.id.etPairCode);

        etServerUrl.setText(AppConfig.getServerUrl(this));
        etPairCode.setText(AppConfig.getPairCode(this));

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            String url  = etServerUrl.getText().toString().trim();
            String code = etPairCode.getText().toString().trim();

            if (url.isEmpty() || !url.startsWith("ws")) {
                Toast.makeText(this, "Enter valid WebSocket URL (ws:// or wss://)", Toast.LENGTH_SHORT).show();
                return;
            }
            if (code.length() < 4) {
                Toast.makeText(this, "Enter the pair code from your Home app", Toast.LENGTH_SHORT).show();
                return;
            }
            AppConfig.setServerUrl(this, url);
            AppConfig.setPairCode(this, code);
            AppConfig.setSetupDone(this, true);
            Toast.makeText(this, "Saved! Connecting...", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }
}
