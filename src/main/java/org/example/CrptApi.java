package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.locks.ReentrantLock;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Represents a client for interacting with CRPT API.
 */
@Slf4j
public class CrptApi {

  private final OkHttpClient client;
  private final Semaphore semaphore;
  private long lastRequestTime;
  private static final ReentrantLock lock = new ReentrantLock();
  private final ScheduledExecutorService scheduler;
  private final String baseUrl;

  /**
   * Main constructor for CrptApi.
   *
   * @param timeUnit     The time unit for request limits.
   * @param requestLimit The limit for the number of requests.
   */
  public CrptApi(TimeUnit timeUnit, int requestLimit) {
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

    Duration duration = Duration.of(timeUnit.toSeconds(1), ChronoUnit.SECONDS);
    long interval = TimeUnit.MILLISECONDS.convert(duration.getSeconds() * requestLimit,
        TimeUnit.SECONDS);

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
   * @throws IOException          If an I/O error occurs.
   * @throws InterruptedException If interrupted while creating the document.
   * @throws URISyntaxException   If a URI syntax error occurs.
   */
  public void createDocument(Document document, String signature)
      throws IOException, InterruptedException, URISyntaxException {
    ObjectMapper objectMapper = new ObjectMapper();

    String documentJson = objectMapper.writeValueAsString(document);
    log.info("documentJson is: {}", documentJson);

    MediaType jsonMediaType = MediaType.get("application/json; charset=utf-8");

    RequestBody body = RequestBody.create(jsonMediaType, documentJson);
    String fullUrl = new URI(baseUrl).resolve("/api/v3/lk/documents/create").toString();
    Request request = new Request.Builder()
        .url(fullUrl)
        .post(body)
        .addHeader("Content-Type", "application/json")
        .addHeader("Signature", signature)
        .build();

    semaphore.acquire();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Unexpected code " + response);
      }
    } finally {
      semaphore.release();
    }
  }

  /**
   * Shuts down the scheduler.
   */
  public void shutdown() {
    scheduler.shutdown();
    log.info("Scheduler shut down.");
  }

  @Data
  static
  class Document {

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
    public static class Description {

      @JsonProperty("participant_inn")
      private String participantInn;
    }

    @Data
    public static class Product {

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
