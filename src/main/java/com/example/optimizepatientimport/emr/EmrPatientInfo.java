package com.example.optimizepatientimport.emr;

/**
 * Result of an EMR lookup for a patient.
 */
public class EmrPatientInfo {

    private final boolean existingPatient;
    private final String emrPatientId;

    public EmrPatientInfo(boolean existingPatient, String emrPatientId) {
        this.existingPatient = existingPatient;
        this.emrPatientId = emrPatientId;
    }

    public boolean isExistingPatient() {
        return existingPatient;
    }

    public String getEmrPatientId() {
        return emrPatientId;
    }
}
