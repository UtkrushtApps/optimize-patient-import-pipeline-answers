package com.example.optimizepatientimport.controller;

import com.example.optimizepatientimport.api.dto.BatchImportResponse;
import com.example.optimizepatientimport.api.dto.PatientImportRequest;
import com.example.optimizepatientimport.logging.CorrelationIdFilter;
import com.example.optimizepatientimport.service.PatientImportService;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(path = "/api/patients", produces = MediaType.APPLICATION_JSON_VALUE)
public class PatientImportController {

    private final PatientImportService patientImportService;

    public PatientImportController(PatientImportService patientImportService) {
        this.patientImportService = patientImportService;
    }

    @PostMapping(path = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BatchImportResponse importPatients(@RequestBody List<@Valid PatientImportRequest> requests) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return patientImportService.importPatients(requests, correlationId);
    }
}
