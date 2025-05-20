package com.ynov.recaipes.dto;

import lombok.Data;

@Data
public class RecipeRequest {
    private String ingredients;
    private String diet;
    private String cuisine;
    private String userName;
}