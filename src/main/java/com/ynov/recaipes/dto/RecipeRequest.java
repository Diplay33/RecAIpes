package com.ynov.recaipes.dto;

import lombok.Data;

@Data
public class RecipeRequest {
    private String dishName;     // Nouveau champ pour le nom du plat
    private String userName;     // Conserver pour l'auteur
}