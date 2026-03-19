package com.example.optimizepatientimport.api.dto;

public class PatientImportResult {

    private int index;
    private String externalEmrId;
    private ImportStatus status;
    private String failureReason;
    private Long patientId;

    public PatientImportResult() {
    }

    public PatientImportResult(int index, String externalEmrId, ImportStatus status, String failureReason, Long patientId) {
        this.index = index;
        this.externalEmrId = externalEmrId;
        this.status = status;
        this.failureReason = failureReason;
        this.patientId = patientId;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getExternalEmrId() {
        return externalEmrId;
    }

    public void setExternalEmrId(String externalEmrId) {
        this.externalEmrId = externalEmrId;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public void setStatus(ImportStatus status) {
        this.status = status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Long getPatientId() {
        return patientId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }
}
