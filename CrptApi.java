import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;


public class CrptApi {
    private static final String CREATE_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private TimeUnit timeUnit;
    private ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;
    private final Instant[] ringBuffer;
    private volatile int cursor;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        httpClient = HttpClient.newBuilder()
//                .sslContext()
                .executor(Executors.newScheduledThreadPool(3 * requestLimit))
                .build();
        ringBuffer = new Instant[requestLimit];
        cursor = 0;
    }

    public void createDocument(Document pojo, String sign) throws JsonProcessingException {
        // preparation (no info about sign in JSON example)
        String jsonString = objectMapper.writeValueAsString(pojo);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CREATE_URL))
                .POST(HttpRequest.BodyPublishers.ofString(jsonString))
                .header("Content-Type", "application/json")
                .build();

        // await block
        Instant now;
        Instant oldestInstant;
        while (true) {
            synchronized (ringBuffer) {
                now = Instant.now();
                oldestInstant = ringBuffer[cursor];
                if (oldestInstant == null ||
                        oldestInstant
                                .plus(1, timeUnit.toChronoUnit())
                                .isBefore(now)) {
                    ringBuffer[cursor] = Instant.now();
                    cursor = (cursor + 1) % ringBuffer.length ;
                    break;
                }
            }
            try {
                Thread.sleep(oldestInstant
                        .plus(1, timeUnit.toChronoUnit())
                        .compareTo(now) * 1000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        // request block
        CompletableFuture<HttpResponse<String>> responseFuture = httpClient.sendAsync(
                request,
                HttpResponse.BodyHandlers.ofString()
                );

        // any actions with response?
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Document {
        private DocumentDescription documentDescription;
        private String docId;
        private String docStatus;
        private DocumentType documentType = DocumentType.LP_INTRODUCE_GOODS;
        private Boolean importRequest = true;
        private String ownerInn;
        private String participantInn;
        private String producerInn;
        private Date productionDate;
        private List<Product> products;
        private Date regDate;
        private String regNumber;

        private class DocumentDescription {
            private String participantInn;
        }

        private enum DocumentType {
            LP_INTRODUCE_GOODS;
        }
    }
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Product {
        private String certificateDocument;
        private Date certificateDocumentDate;
        private String certificateDocumentNumber;
        private String ownerInn;
        private String producerInn;
        private String participantInn;
        private Date productionDate;
        private String tnvedCode;
        private String uitCode;
        private String uituCode;
    }

}