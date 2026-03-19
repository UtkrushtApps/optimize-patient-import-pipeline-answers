package com.example.optimizepatientimport.api.dto;

import java.util.List;

public class BatchImportResponse {

    private String correlationId;
    private int total;
    private int successCount;
    private int failureCount;
    private List<PatientImportResult> results;

    public BatchImportResponse() {
    }

    public BatchImportResponse(String correlationId, int total, int successCount, int failureCount,
                               List<PatientImportResult> results) {
        this.correlationId = correlationId;
        this.total = total;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.results = results;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public List<PatientImportResult> getResults() {
        return results;
    }

    public void setResults(List<PatientImportResult> results) {
        this.results = results;
    }
}
