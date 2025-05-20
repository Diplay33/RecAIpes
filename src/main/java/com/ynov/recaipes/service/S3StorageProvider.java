package com.ynov.recaipes.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.UUID;

@Component
@ConditionalOnBean(AmazonS3.class)
public class S3StorageProvider implements StorageProvider {

    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucket:}")
    private String bucketName;

    public S3StorageProvider(AmazonS3 amazonS3) {
        this.amazonS3 = amazonS3;
    }

    @Override
    public String uploadFile(File file, String contentType) {
        String uniqueFileName = UUID.randomUUID() + "-" + file.getName();
        PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, uniqueFileName, file)
                .withCannedAcl(CannedAccessControlList.PublicRead);

        amazonS3.putObject(putObjectRequest);

        return amazonS3.getUrl(bucketName, uniqueFileName).toString();
    }

    @Override
    public String getFileUrl(String fileName) {
        return amazonS3.getUrl(bucketName, fileName).toString();
    }

    @Override
    public boolean isAvailable() {
        return amazonS3 != null && !bucketName.isEmpty();
    }
}