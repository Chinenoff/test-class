package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.example.CrptApi.Document;
import org.example.CrptApi.Document.Description;
import org.example.CrptApi.Document.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CrptApiTest {

  private ScheduledExecutorService mockScheduler;
  private Semaphore mockSemaphore;
  private CrptApi crptApi;
  private MockWebServer mockWebServer;
  private String signature;
  private Document document;

  @BeforeEach
  public void setUp() throws Exception {
    OkHttpClient realClient = new OkHttpClient(); // Создаем реальный объект OkHttpClient
    mockScheduler = mock(ScheduledExecutorService.class);
    mockSemaphore = mock(Semaphore.class);
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    String mockBaseUrl = mockWebServer.url("").toString();
    crptApi = new CrptApi(realClient, mockScheduler, mockSemaphore, TimeUnit.SECONDS, 5,
        mockBaseUrl);

    // Инициализация объектов Document и Description для повторного использования в тестах
    document = new Document();
    document.setDescription(new Description());
    document.setDocId("12345");
    document.setDocStatus("ACTIVE");
    document.setDocType("LP_INTRODUCE_GOODS");
    document.setImportRequest(true);
    document.setOwnerInn("1234567890");
    document.setParticipantInn("0987654321");
    document.setProducerInn("2345678901");
    document.setProductionDate("2020-01-23");
    document.setProductionType("TYPE_A");
    document.setProducts(Collections.singletonList(new Product()));
    document.setRegDate("2020-01-23");
    document.setRegNumber("R12345678");

    signature = "test-signature"; // Пример подписи для тестирования
  }

  @AfterEach
  public void tearDown() throws IOException {
    crptApi.shutdown();
    mockWebServer.shutdown();
  }

  @Test
  public void testCreateDocument() throws IOException, InterruptedException, URISyntaxException {

    mockWebServer.enqueue(
        new MockResponse().setBody("response body").addHeader("Content-Type", "application/json"));

// Act
    crptApi.createDocument(document, signature);

// Assert
    RecordedRequest recordedRequest = mockWebServer.takeRequest();
    assertEquals("/api/v3/lk/documents/create", recordedRequest.getPath());
    assertEquals("application/json; charset=utf-8", recordedRequest.getHeader("Content-Type"));
    assertEquals(signature, recordedRequest.getHeader("Signature"));

    verify(mockSemaphore, times(1)).acquire();
    verify(mockSemaphore, times(1)).release();
  }

  @Test
  public void testConstructorInitializesCorrectly() {
    verify(mockScheduler, times(1)).scheduleAtFixedRate(any(Runnable.class), eq(0L), anyLong(),
        eq(TimeUnit.MILLISECONDS));
  }


  @Test
  public void testShutdown() {
    crptApi.shutdown();
    verify(mockScheduler, times(1)).shutdown();
  }


}