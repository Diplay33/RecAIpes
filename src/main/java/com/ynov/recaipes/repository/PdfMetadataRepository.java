package com.ynov.recaipes.repository;

import com.ynov.recaipes.model.PdfMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PdfMetadataRepository extends JpaRepository<PdfMetadata, Long> {
    PdfMetadata findByRecipeId(Long recipeId);
}