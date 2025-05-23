package com.ynov.recaipes.service;

import java.io.File;
import java.util.Map;

public interface StorageProvider {
    String uploadFile(File file, String contentType);
    String uploadFile(File file, String contentType, Map<String, String> customTags);
    String getFileUrl(String fileName);
    boolean isAvailable();
    boolean deleteFile(String fileUrl);
}