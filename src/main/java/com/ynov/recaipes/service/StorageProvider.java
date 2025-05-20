package com.ynov.recaipes.service;

import java.io.File;
import java.io.IOException;

public interface StorageProvider {
    String uploadFile(File file, String contentType);
    String getFileUrl(String fileName);
    boolean isAvailable();
}