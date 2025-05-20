package com.ynov.recaipes.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 1000)
    private String description;

    @Column(length = 5000)
    private String ingredients;

    @Column(length = 5000)
    private String instructions;

    // Augmenter la longueur pour les URLs d'image
    @Column(length = 1000)
    private String imageUrl;

    // Également augmenter celle-ci par précaution
    @Column(length = 1000)
    private String pdfUrl;

    private String createdBy;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}