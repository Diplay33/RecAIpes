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
public class RecipeTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "recipe_id")
    private Recipe recipe;

    @Column(nullable = false)
    private String tagKey;   // tag1, tag2, tag3

    @Column(nullable = false)
    private String tagValue; // valeur du tag

    @Column(length = 500)
    private String description; // description du tag

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Constructeur utilitaire
    public RecipeTag(Recipe recipe, String tagKey, String tagValue, String description) {
        this.recipe = recipe;
        this.tagKey = tagKey;
        this.tagValue = tagValue;
        this.description = description;
    }
}