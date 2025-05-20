package com.ynov.recaipes.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StorageService {
    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public String uploadFile(File file, String contentType) {
        String uniqueFileName = UUID.randomUUID() + "-" + file.getName();

        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, uniqueFileName, file)
                    .withCannedAcl(CannedAccessControlList.PublicRead);

            amazonS3.putObject(putObjectRequest);

            URL s3Url = amazonS3.getUrl(bucketName, uniqueFileName);
            return s3Url.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage(), e);
        }
    }

    public String downloadImage(String imageUrl, String destinationDir) throws IOException {
        try {
            URL url = new URL(imageUrl);
            String fileName = UUID.randomUUID() + ".jpg";
            String filePath = destinationDir + File.separator + fileName;

            byte[] imageBytes = url.openStream().readAllBytes();
            Files.write(new File(filePath).toPath(), imageBytes);

            return filePath;
        } catch (Exception e) {
            throw new IOException("Failed to download image: " + e.getMessage(), e);
        }
    }
}