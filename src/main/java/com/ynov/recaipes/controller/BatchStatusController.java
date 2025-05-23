package com.ynov.recaipes.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/recipes/batch")
@RequiredArgsConstructor
public class BatchStatusController {

    // Stockage en mémoire des statuts des tâches (dans un vrai système, utilisez Redis ou une BDD)
    private static final ConcurrentHashMap<String, JobStatus> jobStatuses = new ConcurrentHashMap<>();

    /**
     * Endpoint pour récupérer le statut d'une tâche
     */
    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        JobStatus status = jobStatuses.get(jobId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "status", status.getStatus(),
                "progress", status.getProgress(),
                "message", status.getMessage(),
                "error", status.getError() != null ? status.getError() : ""
        ));
    }

    /**
     * Méthode utilitaire pour créer une nouvelle tâche
     */
    public static String createNewJob() {
        String jobId = UUID.randomUUID().toString();
        jobStatuses.put(jobId, new JobStatus("running", 0, "Démarrage de la génération...", null));
        return jobId;
    }

    /**
     * Méthode utilitaire pour mettre à jour le statut d'une tâche
     */
    public static void updateJobProgress(String jobId, int progress, String message) {
        JobStatus status = jobStatuses.get(jobId);
        if (status != null) {
            status.setProgress(progress);
            status.setMessage(message);
        }
    }

    /**
     * Méthode utilitaire pour terminer une tâche
     */
    public static void completeJob(String jobId, String message) {
        JobStatus status = jobStatuses.get(jobId);
        if (status != null) {
            status.setStatus("completed");
            status.setProgress(100);
            status.setMessage(message);
        }
    }

    /**
     * Méthode utilitaire pour marquer une tâche en erreur
     */
    public static void failJob(String jobId, String error) {
        JobStatus status = jobStatuses.get(jobId);
        if (status != null) {
            status.setStatus("error");
            status.setError(error);
        }
    }

    /**
     * Classe interne pour représenter le statut d'une tâche
     */
    static class JobStatus {
        private String status; // running, completed, error
        private int progress;  // 0-100
        private String message;
        private String error;

        public JobStatus(String status, int progress, String message, String error) {
            this.status = status;
            this.progress = progress;
            this.message = message;
            this.error = error;
        }

        // Getters et Setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getProgress() { return progress; }
        public void setProgress(int progress) { this.progress = progress; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}