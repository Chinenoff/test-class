package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a client for interacting with CRPT API.
 */
@Slf4j
public class CrptApi {

    private final OkHttpClient client;
    private final Semaphore semaphore;
    private long lastRequestTime;
    private static final Lock lock = new ReentrantLock();
    private final ScheduledExecutorService scheduler;
    private final String baseUrl;

    /**
     * Main constructor for CrptApi.
     *
     * @param timeUnit     The time unit for request limits.
     * @param requestLimit The limit for the number of requests.
     */
    CrptApi(TimeUnit timeUnit, int requestLimit) {
        this(new OkHttpClient(), Executors.newScheduledThreadPool(1), new Semaphore(requestLimit),
                timeUnit, requestLimit, "https://ismp.crpt.ru");
    }

    /**
     * Constructor for tests CrptApi.
     */
    CrptApi(OkHttpClient client, ScheduledExecutorService scheduler, Semaphore semaphore,
            TimeUnit timeUnit, int requestLimit, String baseUrl) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be positive");
        }

        this.client = client;
        this.scheduler = scheduler;
        this.semaphore = semaphore;
        this.baseUrl = baseUrl;

        long timeInMilliseconds = Duration.of(timeUnit.toMillis(1), ChronoUnit.MILLIS).getSeconds();
        long interval = (long) Math.ceil((double) requestLimit / timeInMilliseconds);

        log.debug("Interval set to {} milliseconds", interval);

        scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastRequestTime >= interval) {
                    lastRequestTime = currentTime;
                    semaphore.release(requestLimit);
                }
            } finally {
                lock.unlock();
            }
        }, 0, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * Creates a document with the given Product body and signature.
     *
     * @param document  The object Document class of the document.
     * @param signature The signature of the document.
     * @throws IOException If an I/O error occurs.
     */
    public void createDocument(Document document, String signature)
            throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        String documentJson = objectMapper.writeValueAsString(document);
        log.debug("documentJson is: {}", documentJson);

        MediaType jsonMediaType = MediaType.get("application/json; charset=utf-8");

        RequestBody body = RequestBody.create(jsonMediaType, documentJson);

        String fullUrl;
        try {
            fullUrl = new URI(baseUrl).resolve("/api/v3/lk/documents/create").toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException("Invalid URL syntax", e); // Перехват и повторное выбрасывание RuntimeException
        }

        Request request = new Request.Builder()
                .url(fullUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Signature", signature) //
                .build();

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response.code());
            }
        } finally {
            semaphore.release(); //
        }
    }

    /**
     * Shuts down the scheduler.
     */
    public void shutdown() {
        scheduler.shutdown();
        log.debug("Scheduler shut down.");
    }

    @Data
    static class Document {

        @JsonProperty("description")
        private Description description;

        @JsonProperty("doc_id")
        private String docId;

        @JsonProperty("doc_status")
        private String docStatus;

        @JsonProperty("doc_type")
        private String docType;

        @JsonProperty("import_request")
        private Boolean importRequest;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("participant_inn")
        private String participantInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        private String productionDate;

        @JsonProperty("production_type")
        private String productionType;

        @JsonProperty("products")
        private List<Product> products;

        @JsonProperty("reg_date")
        private String regDate;

        @JsonProperty("reg_number")
        private String regNumber;

        @Data
        static class Description {

            @JsonProperty("participant_inn")
            private String participantInn;
        }

        @Data
        static class Product {

            @JsonProperty("certificate_document")
            private String certificateDocument;

            @JsonProperty("certificate_document_date")
            private String certificateDocumentDate;

            @JsonProperty("certificate_document_number")
            private String certificateDocumentNumber;

            @JsonProperty("owner_inn")
            private String ownerInn;

            @JsonProperty("producer_inn")
            private String producerInn;

            @JsonProperty("production_date")
            private String productionDate;

            @JsonProperty("tnved_code")
            private String tnvedCode;

            @JsonProperty("uit_code")
            private String uitCode;

            @JsonProperty("uitu_code")
            private String uituCode;
        }
    }


}



