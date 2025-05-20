package com.ynov.recaipes.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class EnvConfig {

    @Bean
    public Dotenv dotenv() {
        return Dotenv.configure()
                .ignoreIfMissing()
                .load();
    }

    @Bean
    public String openaiApiKey(Dotenv dotenv, Environment env) {
        return dotenv.get("OPENAI_API_KEY", env.getProperty("openai.api.key", ""));
    }

    @Bean
    public String awsAccessKey(Dotenv dotenv, Environment env) {
        return dotenv.get("AWS_ACCESS_KEY", env.getProperty("aws.accessKey", ""));
    }

    @Bean
    public String awsSecretKey(Dotenv dotenv, Environment env) {
        return dotenv.get("AWS_SECRET_KEY", env.getProperty("aws.secretKey", ""));
    }

    @Bean
    public String awsS3Bucket(Dotenv dotenv, Environment env) {
        return dotenv.get("AWS_S3_BUCKET", env.getProperty("aws.s3.bucket", ""));
    }

    @Bean
    public String awsRegion(Dotenv dotenv, Environment env) {
        return dotenv.get("AWS_REGION", env.getProperty("aws.region", "eu-west-3"));
    }
}