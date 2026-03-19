package com.example.optimizepatientimport.service;

import com.example.optimizepatientimport.api.dto.BatchImportResponse;
import com.example.optimizepatientimport.api.dto.ImportStatus;
import com.example.optimizepatientimport.api.dto.PatientImportRequest;
import com.example.optimizepatientimport.api.dto.PatientImportResult;
import com.example.optimizepatientimport.emr.EmrLookupException;
import com.example.optimizepatientimport.emr.EmrLookupService;
import com.example.optimizepatientimport.emr.EmrPatientInfo;
import com.example.optimizepatientimport.emr.TransientEmrException;
import com.example.optimizepatientimport.model.PatientRecord;
import com.example.optimizepatientimport.repository.PatientRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class PatientImportService {

    private static final Logger log = LoggerFactory.getLogger(PatientImportService.class);

    private final PatientRecordRepository patientRecordRepository;
    private final EmrLookupService emrLookupService;
    private final PatientValidationService validationService;
    private final TaskExecutor ioExecutor;

    private final Duration emrTimeout = Duration.ofSeconds(2);
    private static final int MAX_EMR_ATTEMPTS = 3;

    public PatientImportService(PatientRecordRepository patientRecordRepository,
                                EmrLookupService emrLookupService,
                                PatientValidationService validationService,
                                @Qualifier("ioExecutor") TaskExecutor ioExecutor) {
        this.patientRecordRepository = patientRecordRepository;
        this.emrLookupService = emrLookupService;
        this.validationService = validationService;
        this.ioExecutor = ioExecutor;
    }

    /**
     * Entry point used by the controller. Keeps the HTTP contract synchronous
     * while internally performing per-record processing concurrently.
     */
    @Transactional
    public BatchImportResponse importPatients(List<PatientImportRequest> requests, String correlationId) {
        Objects.requireNonNull(requests, "requests must not be null");
        log.info("Starting patient batch import: correlationId={}, batchSize={}", correlationId, requests.size());

        List<CompletableFuture<PatientProcessingContext>> futures = new ArrayList<>();

        for (int i = 0; i < requests.size(); i++) {
            PatientImportRequest request = requests.get(i);
            int index = i;

            CompletableFuture<PatientProcessingContext> future =
                    CompletableFuture
                            .completedFuture(new PatientProcessingContext(index, request))
                            .thenApply(this::validationStage)
                            .thenCompose(ctx -> emrEnrichmentStageAsync(ctx, correlationId));

            futures.add(future);
        }

        // Wait for all records to complete validation + EMR enrichment
        CompletableFuture<Void> allDone = CompletableFuture
                .allOf(futures.toArray(new CompletableFuture[0]));

        List<PatientProcessingContext> contexts;
        try {
            // Propagate any unexpected errors
            allDone.get(5, TimeUnit.MINUTES);
            contexts = futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparingInt(PatientProcessingContext::getIndex))
                    .collect(Collectors.toList());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Batch import interrupted: correlationId={}", correlationId, e);
            throw new IllegalStateException("Batch import interrupted", e);
        } catch (TimeoutException e) {
            log.error("Batch import timed out: correlationId={}", correlationId, e);
            throw new IllegalStateException("Batch import timed out", e);
        } catch (ExecutionException e) {
            log.error("Batch import failed with unexpected error: correlationId={}", correlationId, e.getCause());
            throw new IllegalStateException("Batch import failed", e.getCause());
        }

        // Persist successful records in a single batch
        persistSuccessfulRecords(contexts);

        List<PatientImportResult> results = contexts.stream()
                .map(ctx -> new PatientImportResult(
                        ctx.getIndex(),
                        ctx.getRequest().getExternalEmrId(),
                        ctx.getStatus(),
                        ctx.getFailureReason(),
                        ctx.getPatientId()))
                .collect(Collectors.toList());

        int successCount = (int) contexts.stream().filter(c -> c.getStatus() == ImportStatus.SUCCESS).count();
        int failureCount = contexts.size() - successCount;

        log.info("Finished patient batch import: correlationId={}, total={}, success={}, failure={}",
                correlationId, contexts.size(), successCount, failureCount);

        return new BatchImportResponse(correlationId, contexts.size(), successCount, failureCount, results);
    }

    private PatientProcessingContext validationStage(PatientProcessingContext ctx) {
        PatientValidationService.ValidationResult result = validationService.validate(ctx.getRequest());
        if (!result.isValid()) {
            ctx.markFailed("Validation failed: " + result.getErrorMessage());
            log.debug("Record {} failed validation: {}", ctx.getIndex(), result.getErrorMessage());
        }
        return ctx;
    }

    private CompletableFuture<PatientProcessingContext> emrEnrichmentStageAsync(PatientProcessingContext ctx,
                                                                                String correlationId) {
        if (ctx.getStatus() == ImportStatus.FAILED) {
            // Skip EMR lookup if validation already failed
            return CompletableFuture.completedFuture(ctx);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                EmrPatientInfo info = performEmrLookupWithRetry(ctx.getRequest(), correlationId);
                ctx.setEmrPatientInfo(info);
                // Mark as success for now; final success is determined after DB persistence
                ctx.markSuccess();
                log.debug("Record {} EMR enrichment successful", ctx.getIndex());
            } catch (Exception ex) {
                String reason = "EMR lookup failed: " + ex.getMessage();
                ctx.markFailed(reason);
                log.warn("Record {} EMR lookup failed: {}", ctx.getIndex(), ex.getMessage());
            }
            return ctx;
        }, ioExecutor).orTimeout(emrTimeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    // Handle timeout or other async-level failures
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String reason;
                    if (cause instanceof TimeoutException) {
                        reason = "EMR lookup timed out after " + emrTimeout.toMillis() + " ms";
                    } else {
                        reason = "Async EMR processing failed: " + cause.getMessage();
                    }
                    ctx.markFailed(reason);
                    log.warn("Record {} EMR async failure: {}", ctx.getIndex(), reason);
                    return ctx;
                });
    }

    private EmrPatientInfo performEmrLookupWithRetry(PatientImportRequest request, String correlationId) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                log.debug("EMR lookup attempt {} for externalId={}, correlationId={}", attempt,
                        request.getExternalEmrId(), correlationId);
                return emrLookupService.lookup(request);
            } catch (TransientEmrException ex) {
                if (attempt >= MAX_EMR_ATTEMPTS) {
                    log.warn("Transient EMR failure after {} attempts for externalId={}, correlationId={}: {}",
                            attempt, request.getExternalEmrId(), correlationId, ex.getMessage());
                    throw ex;
                }
                long backoffMillis = 100L * attempt;
                log.warn("Transient EMR error (attempt {}/{}), backing off {} ms: {}",
                        attempt, MAX_EMR_ATTEMPTS, backoffMillis, ex.getMessage());
                try {
                    Thread.sleep(backoffMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new EmrLookupException("Interrupted during EMR retry backoff", ie);
                }
            }
        }
    }

    private void persistSuccessfulRecords(List<PatientProcessingContext> contexts) {
        List<PatientProcessingContext> successfulContexts = contexts.stream()
                .filter(ctx -> ctx.getStatus() == ImportStatus.SUCCESS)
                .collect(Collectors.toList());

        if (successfulContexts.isEmpty()) {
            return;
        }

        List<PatientRecord> entitiesToSave = successfulContexts.stream()
                .map(ctx -> {
                    PatientRecord record = new PatientRecord();
                    record.setFirstName(ctx.getRequest().getFirstName());
                    record.setLastName(ctx.getRequest().getLastName());
                    record.setDateOfBirth(ctx.getRequest().getDateOfBirth());
                    if (ctx.getEmrPatientInfo() != null) {
                        record.setEmrId(ctx.getEmrPatientInfo().getEmrPatientId());
                    }
                    return record;
                })
                .collect(Collectors.toList());

        List<PatientRecord> saved = patientRecordRepository.saveAll(entitiesToSave);

        // Map generated IDs back to contexts (order is preserved by saveAll)
        IntStream.range(0, successfulContexts.size()).forEach(i -> {
            PatientProcessingContext ctx = successfulContexts.get(i);
            PatientRecord record = saved.get(i);
            ctx.setPatientId(record.getId());
        });
    }

    /**
     * Internal per-record context that flows through the processing stages.
     */
    private static class PatientProcessingContext {
        private final int index;
        private final PatientImportRequest request;
        private ImportStatus status = ImportStatus.FAILED; // default to failed until proven successful
        private String failureReason;
        private EmrPatientInfo emrPatientInfo;
        private Long patientId;

        PatientProcessingContext(int index, PatientImportRequest request) {
            this.index = index;
            this.request = request;
        }

        int getIndex() {
            return index;
        }

        PatientImportRequest getRequest() {
            return request;
        }

        ImportStatus getStatus() {
            return status;
        }

        String getFailureReason() {
            return failureReason;
        }

        void setEmrPatientInfo(EmrPatientInfo emrPatientInfo) {
            this.emrPatientInfo = emrPatientInfo;
        }

        EmrPatientInfo getEmrPatientInfo() {
            return emrPatientInfo;
        }

        Long getPatientId() {
            return patientId;
        }

        void setPatientId(Long patientId) {
            this.patientId = patientId;
        }

        void markFailed(String reason) {
            this.status = ImportStatus.FAILED;
            this.failureReason = reason;
        }

        void markSuccess() {
            this.status = ImportStatus.SUCCESS;
            this.failureReason = null;
        }
    }
}
