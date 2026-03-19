package com.example.optimizepatientimport.emr;

import com.example.optimizepatientimport.api.dto.PatientImportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Synchronous service that simulates a remote EMR lookup.
 *
 * This implementation intentionally blocks for a short period to simulate
 * network latency. The import pipeline is responsible for executing these
 * calls on a dedicated I/O executor so that request threads are not blocked.
 */
@Service
public class EmrLookupService {

    private static final Logger log = LoggerFactory.getLogger(EmrLookupService.class);

    public EmrPatientInfo lookup(PatientImportRequest request) {
        try {
            // Simulate variable network latency between 50 and 150 ms
            long delay = ThreadLocalRandom.current().nextLong(50, 150);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientEmrException("EMR lookup interrupted", e);
        }

        // Simulate different EMR outcomes based on the external ID
        String externalId = request.getExternalEmrId();
        if (externalId != null) {
            if (externalId.startsWith("TRANSIENT")) {
                throw new TransientEmrException("Transient EMR failure for id=" + externalId);
            }
            if (externalId.startsWith("ERROR")) {
                throw new EmrLookupException("Non-retryable EMR failure for id=" + externalId);
            }
        }

        // For demo purposes, assume the EMR always returns some canonical ID
        String emrId = externalId != null ? externalId : "EMR-" + System.nanoTime();
        boolean existing = externalId != null;
        log.debug("EMR lookup successful for externalId={}, emrId={}", externalId, emrId);
        return new EmrPatientInfo(existing, emrId);
    }
}
