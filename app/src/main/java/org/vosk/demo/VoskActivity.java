package org.vosk.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;
import org.json.JSONObject;
import org.json.JSONException;


import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class VoskActivity extends Activity implements RecognitionListener {

    // State Constants
    private static final int STATE_START = 0;
    private static final int STATE_READY = 1;
    private static final int STATE_MIC = 2;
    private static final int STATE_DONE = 3;

    // Permission request code
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private Model model;
    private SpeechService speechService;
    private TextView resultView;
    private int currentState = STATE_START; // Track UI state

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        resultView = findViewById(R.id.result_text);
        resultView.setMovementMethod(new ScrollingMovementMethod()); // Allow scrolling

        setUiState(STATE_START);

        // Microphone recognition button
        findViewById(R.id.recognize_mic).setOnClickListener(view -> recognizeMicrophone());

        // Pause toggle
        ((ToggleButton) findViewById(R.id.pause)).setOnCheckedChangeListener((view, isChecked) -> pause(isChecked));

        // Settings button to open SettingsActivity
        findViewById(R.id.settings_button).setOnClickListener(view -> {
            Intent intent = new Intent(VoskActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        LibVosk.setLogLevel(LogLevel.INFO);

        // Request microphone permissions
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel();
        }
    }

    private void initModel() {
        StorageService.unpack(this, "model-en-us", "model",
                (model) -> {
                    this.model = model;
                    setUiState(STATE_READY);
                },
                (exception) -> setErrorState("Failed to unpack the model: " + exception.getMessage()));
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            setUiState(STATE_DONE);
            speechService.stop();
            speechService = null;
        } else {
            setUiState(STATE_MIC);
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void pause(boolean checked) {
        if (speechService != null) {
            speechService.setPause(checked);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initModel();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }
    }

    @Override
    public void onResult(String hypothesis) {
        if (currentState == STATE_MIC) {
            try {
                // Parse JSON to extract only the recognized text
                JSONObject json = new JSONObject(hypothesis);
                String resultText = json.optString("text", ""); // Extract "text" field

                // Only update the resultView if there's actual text
                if (!resultText.isEmpty()) {
                    resultView.setText(resultText);
                }
            } catch (JSONException e) {
                e.printStackTrace(); // Print error if JSON parsing fails
            }
        }
    }


    @Override
    public void onFinalResult(String hypothesis) {
        if (currentState == STATE_MIC) {
            setUiState(STATE_DONE);
            resultView.append("\n[onFinalResult]\n" + hypothesis);
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        if (currentState == STATE_MIC) {
            try {
                // Parse JSON to extract only the text
                JSONObject json = new JSONObject(hypothesis);
                String partialText = json.optString("partial", ""); // Get "partial" field, default to empty

                // Update the resultView only if there's text
                if (!partialText.isEmpty()) {
                    resultView.setText(partialText);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }



    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        setUiState(STATE_DONE);
    }

    private void setUiState(int state) {
        currentState = state;
        switch (state) {
            case STATE_START:
                resultView.setText(R.string.preparing);
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.pause).setEnabled(false);
                break;
            case STATE_READY:
                resultView.setText(R.string.ready);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled(false);
                break;
            case STATE_MIC:
                resultView.setText(R.string.say_something);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled(true);
                break;
            case STATE_DONE:
                resultView.append("\nRecognition complete.\n");
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled(false);
                ((ToggleButton) findViewById(R.id.pause)).setChecked(false);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    private void setErrorState(String message) {
        resultView.setText(message);
        findViewById(R.id.recognize_mic).setEnabled(false);
    }
}
