package com.ynov.recaipes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeRequest {
    private String dishName;     // Nom du plat
    private String userName;     // Auteur de la recette

    // Constructeur de convenance pour garder la compatibilité
    public RecipeRequest(String dishName) {
        this.dishName = dishName;
        this.userName = "anonymous";
    }
}