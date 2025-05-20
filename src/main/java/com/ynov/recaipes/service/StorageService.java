package com.ynov.recaipes.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class StorageService {
    private final AmazonS3 amazonS3;
    private final String bucketName;
    private final boolean s3Enabled;

    @Value("${pdf.storage.local.path}")
    private String localStoragePath;

    @Autowired
    public StorageService(AmazonS3 amazonS3, @Autowired String awsS3Bucket) {
        this.amazonS3 = amazonS3;
        this.bucketName = awsS3Bucket;
        this.s3Enabled = (amazonS3 != null && !bucketName.isEmpty());
    }

    // Mode hybride: upload sur S3 si disponible, sinon stockage local uniquement
    public String uploadFile(File file, String contentType) {
        // S'assurer que le chemin local existe
        try {
            Path localPath = Paths.get(localStoragePath);
            if (!Files.exists(localPath)) {
                Files.createDirectories(localPath);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create local storage directory", e);
        }

        // Vérifier si S3 est disponible
        if (s3Enabled) {
            String uniqueFileName = UUID.randomUUID() + "-" + file.getName();
            try {
                PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, uniqueFileName, file)
                        .withCannedAcl(CannedAccessControlList.PublicRead);

                amazonS3.putObject(putObjectRequest);
                URL s3Url = amazonS3.getUrl(bucketName, uniqueFileName);
                return s3Url.toString();
            } catch (Exception e) {
                System.out.println("Warning: S3 upload failed, falling back to local storage: " + e.getMessage());
                // Fallback to local storage
            }
        }

        // Mode local: retourne une URL "file://" locale
        return "file://" + file.getAbsolutePath();
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