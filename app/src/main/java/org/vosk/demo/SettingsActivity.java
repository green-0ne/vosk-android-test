package org.vosk.demo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.app.Activity;

public class SettingsActivity extends Activity {


    private EditText urlEditText;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "AppSettings";
    private static final String URL_KEY = "saved_url";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        urlEditText = findViewById(R.id.url_edit_text);
        Button saveButton = findViewById(R.id.save_button);

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Load saved URL if exists
        String savedUrl = sharedPreferences.getString(URL_KEY, "");
        urlEditText.setText(savedUrl);

        saveButton.setOnClickListener(view -> {
            String url = urlEditText.getText().toString();
            sharedPreferences.edit().putString(URL_KEY, url).apply();
            finish(); // Close settings activity
        });
    }
}
