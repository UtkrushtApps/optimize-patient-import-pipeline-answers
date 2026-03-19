# Solution Steps

1. Create or confirm the main Spring Boot application class so the project starts correctly (e.g., `OptimizePatientImportApplication` annotated with `@SpringBootApplication`).

2. Introduce DTOs to represent the API contract: `PatientImportRequest` for input, `PatientImportResult` for per-record output, `BatchImportResponse` for the aggregate, and an `ImportStatus` enum to indicate SUCCESS/FAILED per record.

3. Model the persistence layer with a `PatientRecord` JPA entity mapped to the existing `patient_records` table and a `PatientRecordRepository` that extends `JpaRepository` for batch database operations using `saveAll`.

4. Add logging and correlation support: implement `CorrelationIdFilter` to generate or propagate an `X-Correlation-Id` header, store it in MDC, and attach it to every request/response; add `MdcTaskDecorator` so MDC (including the correlation ID) is propagated to async threads.

5. Configure asynchronous execution via `AsyncConfig`, enabling Spring’s async support and defining an `ioExecutor` `ThreadPoolTaskExecutor` tuned for I/O-bound work, with the MDC task decorator set on the executor.

6. Implement the synchronous EMR integration layer: create `EmrPatientInfo` to represent EMR lookup results, `EmrLookupException` and `TransientEmrException` for error modeling, and an `EmrLookupService` that simulates a blocking remote call with latency and different failure modes based on the external EMR ID.

7. Create `PatientValidationService` with a `validate` method that enforces basic business rules (required names, non-null DOB, DOB not in the future) and returns a `ValidationResult` object indicating validity and an optional error message.

8. Design the core async pipeline in `PatientImportService`: inject `PatientRecordRepository`, `EmrLookupService`, `PatientValidationService`, and the `ioExecutor`, and define an internal `PatientProcessingContext` to carry per-record state through the stages.

9. In `PatientImportService.importPatients`, for each input record build a `CompletableFuture` pipeline: start from a completed context, apply a validation stage (`validationStage`), then compose an async EMR enrichment stage (`emrEnrichmentStageAsync`) that runs on `ioExecutor`, uses `performEmrLookupWithRetry` with bounded retries, applies a timeout via `orTimeout`, and captures any exceptions into the context as FAILED with a detailed failure reason.

10. After constructing the list of per-record futures, wait for completion with `CompletableFuture.allOf(...).get(...)`, then collect and sort all `PatientProcessingContext` instances, and batch-persist only successful ones via `patientRecordRepository.saveAll`, mapping generated IDs back into the contexts.

11. From the final contexts, build a list of `PatientImportResult` objects and aggregate them into a `BatchImportResponse` containing correlation ID, total count, success count, failure count, and all per-record statuses; log start and completion of the import with these summary metrics.

12. Expose the synchronous HTTP endpoint in `PatientImportController` at `POST /api/patients/import`, accepting a JSON array of `PatientImportRequest`, obtaining the current correlation ID from MDC, delegating to `PatientImportService.importPatients`, and returning the `BatchImportResponse` to the client.

13. Write an integration-style asynchronous behavior test (`PatientImportServiceAsyncTest`): mock `EmrLookupService` so each lookup sleeps for a fixed time while tracking concurrent invocations, call `patientImportService.importPatients` with a batch of requests, assert that all records complete successfully, verify that the maximum number of concurrent EMR calls is greater than one (indicating real concurrency), and assert that total processing time is significantly less than the sum of all individual EMR delays (proving async parallelism with completion semantics).

