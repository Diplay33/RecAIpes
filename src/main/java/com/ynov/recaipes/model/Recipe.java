package com.ynov.recaipes.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // URL du PDF
    @Column(length = 1000)
    private String pdfUrl;

    // URL du bucket externe
    @Column(length = 1000)
    private String externalBucketUrl;

    // ID externe pour le bucket
    private String externalId;

    private String createdBy;

    // Nouveau : Catégorie de la recette
    @Enumerated(EnumType.STRING)
    private RecipeCategory category;

    // Nouveau : Difficulté
    @Enumerated(EnumType.STRING)
    private RecipeDifficulty difficulty;

    // Nouveau : Temps de préparation (en minutes)
    private Integer preparationTime;

    // Relation avec les tags - CASCADE DELETE
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RecipeTag> tags = new ArrayList<>();

    // Relation avec les métadonnées PDF - CASCADE DELETE
    @OneToOne(mappedBy = "recipe", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private PdfMetadata pdfMetadata;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Méthodes utilitaires pour les tags
    public void addTag(String tagKey, String tagValue, String description) {
        tags.add(new RecipeTag(this, tagKey, tagValue, description));
    }

    public String getTagValue(String tagKey) {
        return tags.stream()
                .filter(tag -> tag.getTagKey().equals(tagKey))
                .findFirst()
                .map(RecipeTag::getTagValue)
                .orElse(null);
    }

    // Enums pour les nouvelles propriétés
    public enum RecipeCategory {
        ENTREE("Entrée"),
        PLAT_PRINCIPAL("Plat principal"),
        DESSERT("Dessert"),
        BOISSON("Boisson"),
        ACCOMPAGNEMENT("Accompagnement");

        private final String displayName;

        RecipeCategory(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public enum RecipeDifficulty {
        FACILE("Facile"),
        MOYEN("Moyen"),
        DIFFICILE("Difficile");

        private final String displayName;

        RecipeDifficulty(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}