package com.example.optimizepatientimport.service;

import com.example.optimizepatientimport.api.dto.BatchImportResponse;
import com.example.optimizepatientimport.api.dto.PatientImportRequest;
import com.example.optimizepatientimport.emr.EmrLookupService;
import com.example.optimizepatientimport.emr.EmrPatientInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class PatientImportServiceAsyncTest {

    @Autowired
    private PatientImportService patientImportService;

    @MockBean
    private EmrLookupService emrLookupService;

    @Test
    void processesRecordsConcurrentlyAndWaitsForCompletion() {
        int batchSize = 10;

        List<PatientImportRequest> requests = IntStream.range(0, batchSize)
                .mapToObj(i -> {
                    PatientImportRequest r = new PatientImportRequest();
                    r.setFirstName("John" + i);
                    r.setLastName("Doe" + i);
                    r.setDateOfBirth(LocalDate.of(1990, 1, 1));
                    r.setExternalEmrId("EMR-" + i);
                    return r;
                })
                .collect(Collectors.toList());

        AtomicInteger currentConcurrent = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);

        when(emrLookupService.lookup(any()))
                .thenAnswer(invocation -> {
                    int concurrent = currentConcurrent.incrementAndGet();
                    maxConcurrent.updateAndGet(prev -> Math.max(prev, concurrent));
                    try {
                        // Simulate an I/O delay so that concurrency becomes observable
                        Thread.sleep(200L);
                    } finally {
                        currentConcurrent.decrementAndGet();
                    }
                    return new EmrPatientInfo(true, UUID.randomUUID().toString());
                });

        long start = System.currentTimeMillis();
        BatchImportResponse response = patientImportService.importPatients(requests, UUID.randomUUID().toString());
        long duration = System.currentTimeMillis() - start;

        // All records should be processed and marked as success
        assertEquals(batchSize, response.getTotal());
        assertEquals(batchSize, response.getSuccessCount());
        assertEquals(0, response.getFailureCount());

        // EMR calls should have happened concurrently (more than one at a time)
        assertTrue(maxConcurrent.get() > 1,
                "Expected EMR lookups to be performed concurrently, but maxConcurrent=" + maxConcurrent.get());

        // Total time should be significantly less than sequential processing
        long expectedSequentialMillis = 200L * batchSize;
        assertTrue(duration < expectedSequentialMillis,
                "Expected async processing to be faster than sequential: duration=" + duration
                        + "ms, expectedSequential=" + expectedSequentialMillis + "ms");
    }
}
