package org.vosk.demo;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class WavFileReader {
    static final private String TAG = "WavFileReader";
    /**
     * 从 getExternalFilesDir() 目录中读取所有 .wav 文件并存储到数据结构中
     */
    public static List<File> getAllWavFiles(Context context) {
        List<File> wavFiles = new ArrayList<>();
        File externalDir = context.getExternalFilesDir(null); // 获取根目录

        if (externalDir != null && externalDir.exists()) {
            // 递归查找 .wav 文件
            findWavFiles(externalDir, wavFiles);
        }

        return wavFiles;
    }

    /**
     * 递归查找目录下的 .wav 文件
     */
    private static void findWavFiles(File directory, List<File> wavFiles) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 如果是文件夹，递归查找
                    findWavFiles(file, wavFiles);
                } else if (file.isFile() && file.getName().endsWith(".wav")) {
                    // 如果是 .wav 文件，加入列表
                    wavFiles.add(file);
                }
            }
        }
    }

    /**
     * 遍历打印文件路径，同时获取 InputStream
     */
    public static void printWavFilesAndStreams(List<File> wavFiles) {
        if (wavFiles.isEmpty()){
            Log.d(TAG,"No .wav files found.");
            return;
        }
        for (File wavFile : wavFiles) {
            Log.d(TAG,"File Path: " + wavFile.getAbsolutePath());
            try (InputStream inputStream = new FileInputStream(wavFile)) {
                Log.d(TAG,"InputStream for file: " + wavFile.getName() + " is ready.");
                // 在这里可以对 InputStream 执行处理操作
            } catch (IOException e) {
                Log.d(TAG,"Error reading file: " + wavFile.getAbsolutePath());
                e.printStackTrace();
            }
        }
    }
}
