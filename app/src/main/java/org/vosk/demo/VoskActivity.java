// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.vosk.demo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class VoskActivity extends Activity implements
        RecognitionListener {

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_DONE = 2;
    static private final int STATE_FILE = 3;
    static private final int STATE_MIC = 4;

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private static final String TAG = "VoskActivity";

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private TextView resultView;
    CountDownLatch countDownLatch = null;
    StringBuilder logBuilder = new StringBuilder();
    long startTime = 0;

    Recognizer finalRec = null;

    // 获取所有 .wav 文件
    List<File> wavFiles = new ArrayList<>();

    int currentState = STATE_START;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        // Setup layout
        resultView = findViewById(R.id.result_text);
        setUiState(STATE_START);

        findViewById(R.id.recognize_file).setOnClickListener(view -> recognizeFilesAndLog());
        findViewById(R.id.recognize_mic).setOnClickListener(view -> recognizeMicrophone());
        ((ToggleButton) findViewById(R.id.pause)).setOnCheckedChangeListener((view, isChecked) -> pause(isChecked));

        LibVosk.setLogLevel(LogLevel.INFO);

        // Check if user has given permission to record audio, init the model after permission is granted
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel();
        }

        initData();
    }

    private void initData() {
        WavFileReader wavFileReader = new WavFileReader();

        // 获取所有 .wav 文件
        wavFiles = wavFileReader.getAllWavFiles(this);

        // 打印路径并获取 InputStream
        wavFileReader.printWavFilesAndStreams(wavFiles);

    }

    private void initModel() {
        StorageService.unpack(this, "model-en-us", "model", //vosk-model-small-en-us-0.15
                (model) -> {
                    this.model = model;
                    setUiState(STATE_READY);
                },
                (exception) -> setErrorState("Failed to unpack the model" + exception.getMessage()));
    }


    private List<String> filterAssetsFiles(Context context) {
        List<String> filteredFiles = new ArrayList<>();
        try {
            // 获取 assets 文件夹下的所有文件名
            String[] files = context.getAssets().list("");
            if (files != null) {
                for (String file : files) {
                    // 检查文件名是否以 "Name_" 开头
                    if (file.startsWith("Name_")) {
                        filteredFiles.add(file);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return filteredFiles;
    }



    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
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

        if (speechStreamService != null) {
            speechStreamService.stop();
        }
    }

    @Override
    public void onResult(String hypothesis) {
        if(STATE_MIC == currentState)
            resultView.append("[onResult]\n"+hypothesis);
    }

    @Override
    public void onFinalResult(String hypothesis) {
        if(STATE_FILE == currentState){
            long duration = System.currentTimeMillis() - startTime;
            resultView.append("\n[onFinalResult]\n"+hypothesis);
            resultView.append(" | Time cost: ");
            resultView.append(duration+"");
            resultView.append("ms\n");

            if (speechStreamService != null) {
                speechStreamService.stop();
                speechStreamService = null;
            }

            if(countDownLatch!=null)
                countDownLatch.countDown();
            logBuilder.append("words:"+hypothesis);

            logBuilder.append(" | Time cost: ").append(duration).append("ms\n");
        }else if(STATE_MIC == currentState){
            setUiState(STATE_DONE);
            resultView.append("\n[onFinalResult]\n"+hypothesis);
        }



    }

    @Override
    public void onPartialResult(String hypothesis) {
        try {
            JSONObject Jbj = new JSONObject(hypothesis);

            if (!Jbj.getString("partial").isEmpty()) {
                resultView.append("[onPartialResult]:" + hypothesis + "\n");
                logBuilder.append("[onPartialResult]:" + hypothesis + "\n");

                if (speechStreamService != null) {
                    speechStreamService.stop();
                    speechStreamService = null;
                }
            }

        } catch (JSONException e) {
            throw new RuntimeException(e);
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
                resultView.setMovementMethod(new ScrollingMovementMethod());
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_READY:
                resultView.setText(R.string.ready);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_DONE:
                ((Button) findViewById(R.id.recognize_file)).setText(R.string.recognize_file);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                ((ToggleButton) findViewById(R.id.pause)).setChecked(false);
                break;
            case STATE_FILE:
                ((Button) findViewById(R.id.recognize_file)).setText(R.string.stop_file);
                resultView.setText(getString(R.string.starting));
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_MIC:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                resultView.setText(getString(R.string.say_something));
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((true));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    private void setErrorState(String message) {
        resultView.setText(message);
        ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        findViewById(R.id.recognize_file).setEnabled(false);
        findViewById(R.id.recognize_mic).setEnabled(false);
    }

    private void recognizeFile() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE);
            speechStreamService.stop();
            speechStreamService = null;
        } else {
            setUiState(STATE_FILE);
            try {
                Recognizer rec = new Recognizer(model, 16000.f );// "[\"one zero zero zero one\", " + "\"oh zero one two three four five six seven eight nine\", \"[unk]\"]"

                InputStream ais = getAssets().open(
                        "Eleanor16.wav"); // Eleanor.wav 10001-90210-01803.wav namesOf50.mp3
                if (ais.skip(44) != 44) throw new IOException("File too short");

                speechStreamService = new SpeechStreamService(rec, ais, 16000);
                speechStreamService.start(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void recognizeAssetsFilesAndLog() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE);
            speechStreamService.stop();
            speechStreamService = null;
        } else {
            setUiState(STATE_FILE);
            List<String> files = filterAssetsFiles(this); // 调用上面实现的文件筛选方法
            if (files == null || files.isEmpty()) {
                setErrorState("No matching files found in assets.");
                return;
            }
            try {
                finalRec = new Recognizer(model, 16000.f);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            new Thread(new Runnable() {
                @Override
                public void run() {

                    logBuilder.append("Recognition Log:\n\n");

                    for (String fileName : files) {
                        countDownLatch = new CountDownLatch(1);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                resultView.append("======="+fileName+"=======\n");

                            }
                        });
                        logBuilder.append("\n\n======="+fileName+"=======\n");
                        startTime = System.currentTimeMillis();
                        try {
                            InputStream ais = getAssets().open(fileName);

                            // 跳过 WAV 文件的头 44 字节（如果文件是 WAV 格式）
                            if (ais.skip(44) != 44) throw new IOException("File too short");

                            speechStreamService = new SpeechStreamService(finalRec, ais, 16000);
                            speechStreamService.start(VoskActivity.this);

                            try {
                                countDownLatch.await();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        } catch (IOException e) {
                            countDownLatch.countDown();
                            logBuilder.append("File: ").append(fileName)
                                    .append(" | Error: ").append(e.getMessage()).append("\n");
                        }
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            saveLogToFile(logBuilder.toString());
                        }
                    });
                }
            }).start();

        }


    }


    private void recognizeFilesAndLog() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE);
            speechStreamService.stop();
            speechStreamService = null;
        } else {
            setUiState(STATE_FILE);
            List<String> files = filterAssetsFiles(this); // 调用上面实现的文件筛选方法
            if (files == null || files.isEmpty()) {
                setErrorState("No matching files found in assets.");
                return;
            }
            try {
                finalRec = new Recognizer(model, 16000.f);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            new Thread(new Runnable() {
                @Override
                public void run() {

                    logBuilder.append("Recognition Log:\n\n");

                    int haveD = 0;
                    for (File wavFile : wavFiles) {
                        //if (!wavFile.getParent().contains("glass")) continue;
                        haveD++;
                        String fileName = haveD+".: "+ wavFile.getName()+" In:"+wavFile.getParent();
                        countDownLatch = new CountDownLatch(1);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                resultView.append("======="+fileName+"=======\n");

                            }
                        });
                        logBuilder.append("\n\n======="+fileName+"=======\n");
                        startTime = System.currentTimeMillis();

                        Log.d(TAG,"File Path: " + wavFile.getAbsolutePath());
                        try (InputStream ais = new FileInputStream(wavFile)) {
                            // 跳过 WAV 文件的头 44 字节（如果文件是 WAV 格式）
                            if (ais.skip(44) != 44) throw new IOException("File too short");

                            Log.d(TAG,"InputStream for file: " + wavFile.getName() + " is ready.");
                            speechStreamService = new SpeechStreamService(finalRec, ais, 16000);
                            speechStreamService.start(VoskActivity.this);
                            try {
                                countDownLatch.await();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        } catch (IOException e) {
                            Log.d(TAG,"Error reading file: " + wavFile.getAbsolutePath());
                            e.printStackTrace();

                            countDownLatch.countDown();
                            logBuilder.append("File: ").append(fileName)
                                    .append(" | Error: ").append(e.getMessage()).append("\n");
                        }

                        /*if(haveD > 20)
                            break;*/
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            saveLogToFile(logBuilder.toString());
                        }
                    });


                }
            }).start();

        }


    }

    private void saveLogToFile(String logContent) {
        File storageDir = new File(getExternalFilesDir(null), "recognition_logs");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        File logFile = new File(storageDir, "recognition_log.txt");
        try (FileWriter writer = new FileWriter(logFile, false)) {
            writer.write(logContent);
            showToast("Log saved to: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            setErrorState("Failed to save log: " + e.getMessage());
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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

}
