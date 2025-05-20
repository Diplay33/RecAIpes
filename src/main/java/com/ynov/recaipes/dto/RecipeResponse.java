package com.ynov.recaipes.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RecipeResponse {
    private Long id;
    private String title;
    private String description;
    private String ingredients;
    private String instructions;
    private String imageUrl;
    private String pdfUrl;
    private String createdBy;
    private LocalDateTime createdAt;
}