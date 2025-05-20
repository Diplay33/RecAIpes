package com.ynov.recaipes.repository;

import com.ynov.recaipes.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findByCreatedByOrderByCreatedAtDesc(String userName);
}