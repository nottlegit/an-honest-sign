package ru.selsup.an_honest_sign;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    private final Gson gson;
    private long lastRequestTime = 0;
    private final long minDelayMs;
    private final String authToken;
    private final HttpClient httpClient;

    public CrptApi(TimeUnit timeUnit, int requestLimit, String authToken) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("Request limit must be positive");
        }
        if (authToken == null || authToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Auth token cannot be null or empty");
        }

        this.authToken = authToken.trim();
        httpClient = HttpClient.newHttpClient();

        long timeUnitInMs = timeUnit.toMillis(1);
        this.minDelayMs = timeUnitInMs / requestLimit;

        gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new TypeAdapter<LocalDate>() {
                    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

                    @Override
                    public void write(final JsonWriter jsonWriter,
                                      final LocalDate localDate) throws IOException {
                        if (localDate == null) {
                            jsonWriter.nullValue();
                        } else {
                            jsonWriter.value(localDate.format(formatter));
                        }
                    }

                    @Override
                    public LocalDate read(final JsonReader jsonReader) throws IOException {
                        return LocalDate.parse(
                                jsonReader.nextString(),
                                formatter
                        );
                    }
                })
                .create();
    }

    public CreateDocumentResponse createDocument(Document document, String signature) throws Exception {
        waitIfNeeded();

        String documentJson = gson.toJson(document);

        String encodedDocument = org.apache.commons.codec.binary.Base64.encodeBase64String(
                documentJson.getBytes(StandardCharsets.UTF_8)
        );

        CreateDocumentRequest request = new CreateDocumentRequest(
                DocumentFormat.MANUAL,
                encodedDocument,
                "clothes",
                signature,
                "LP_INTRODUCE_GOODS"
        );

        String requestBody = gson.toJson(request);
        String responseJson = sendHttpRequest(requestBody);

        return gson.fromJson(responseJson, CreateDocumentResponse.class);
    }

    private void waitIfNeeded() throws InterruptedException {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;

        if (timeSinceLastRequest < minDelayMs) {
            long sleepTime = minDelayMs - timeSinceLastRequest;
            Thread.sleep(sleepTime);
        }

        lastRequestTime = System.currentTimeMillis();
    }

    private String sendHttpRequest(String requestBody) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "?pg=clothes"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));

        requestBuilder.header("Authorization", "Bearer " + authToken);

        HttpRequest request = requestBuilder.build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() == 401) {
            throw new RuntimeException(
                    "Ошибка авторизации (401): Bearer токен недействителен или истек (время жизни 10 часов)");
        } else if (response.statusCode() == 403) {
            throw new RuntimeException(
                    "Доступ запрещен (403): Недостаточно прав или товарная группа не подключена");
        }

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        } else {
            throw new RuntimeException("HTTP Error " + response.statusCode() + ": " + response.body());
        }
    }

    private enum DocumentFormat {
        MANUAL,
        XML,
        CSV
    }

    // =================== POJO КЛАССЫ ===================

    private static class CreateDocumentRequest {
        @SerializedName("document_format")
        private CrptApi.DocumentFormat documentFormat;
        @SerializedName("product_document")
        private String productDocument;
        @SerializedName("product_group")
        private String productGroup;
        private String signature;
        private String type;

        public CreateDocumentRequest(DocumentFormat documentFormat, String productDocument,
                                     String productGroup, String signature, String type) {
            this.documentFormat = documentFormat;
            this.productDocument = productDocument;
            this.productGroup = productGroup;
            this.signature = signature;
            this.type = type;
        }

        public DocumentFormat getDocumentFormat() {
            return documentFormat;
        }

        public void setDocumentFormat(DocumentFormat documentFormat) {
            this.documentFormat = documentFormat;
        }

        public String getProductDocument() {
            return productDocument;
        }

        public void setProductDocument(String productDocument) {
            this.productDocument = productDocument;
        }

        public String getProductGroup() {
            return productGroup;
        }

        public void setProductGroup(String productGroup) {
            this.productGroup = productGroup;
        }

        public String getSignature() {
            return signature;
        }

        public void setSignature(String signature) {
            this.signature = signature;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return "BodyRequest{" +
                    "documentFormat='" + documentFormat + '\'' +
                    ", productDocument='" + productDocument + '\'' +
                    ", productGroup='" + productGroup + '\'' +
                    ", signature='" + signature + '\'' +
                    ", type='" + type + '\'' +
                    '}';
        }
    }

    public static class Document {
        private Description description;
        @SerializedName("doc_id")
        private String docId;
        @SerializedName("doc_status")
        private String docStatus;
        @SerializedName("doc_type")
        private String docType = "LP_INTRODUCE_GOODS";
        @SerializedName("importRequest")
        private Boolean importRequest = false;
        @SerializedName("owner_inn")
        private String ownerInn;
        @SerializedName("participant_inn")
        private String participantInn;
        @SerializedName("producer_inn")
        private String producerInn;
        @SerializedName("production_date")
        private LocalDate productionDate;
        @SerializedName("production_type")
        private String productionType = "OWN_PRODUCTION"; // так как товар произведен в РФ
        private List<Product> products;
        @SerializedName("reg_date")
        private LocalDate regDate;
        @SerializedName("reg_number")
        private String regNumber;

        public Document() {}

        public Document(Description description, String docId, String docStatus, Boolean importRequest,
                        String ownerInn, String participantInn, String producerInn, LocalDate productionDate,
                        List<Product> products, LocalDate regDate, String regNumber) {
            this.description = description;
            this.docId = docId;
            this.docStatus = docStatus;
            this.importRequest = importRequest;
            this.ownerInn = ownerInn;
            this.participantInn = participantInn;
            this.producerInn = producerInn;
            this.productionDate = productionDate;
            this.products = List.copyOf(products);
            this.regDate = regDate;
            this.regNumber = regNumber;
        }

        public Description getDescription() {
            return description;
        }

        public void setDescription(Description description) {
            this.description = description;
        }

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getDocStatus() {
            return docStatus;
        }

        public void setDocStatus(String docStatus) {
            this.docStatus = docStatus;
        }

        public String getDocType() {
            return docType;
        }

        public void setDocType(String docType) {
            this.docType = docType;
        }

        public Boolean getImportRequest() {
            return importRequest;
        }

        public void setImportRequest(Boolean importRequest) {
            this.importRequest = importRequest;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public LocalDate getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(LocalDate productionDate) {
            this.productionDate = productionDate;
        }

        public String getProductionType() {
            return productionType;
        }

        public void setProductionType(String productionType) {
            this.productionType = productionType;
        }

        public List<Product> getProducts() {
            return List.copyOf(products);
        }

        public void setProducts(List<Product> products) {
            this.products = List.copyOf(products);
        }

        public LocalDate getRegDate() {
            return regDate;
        }

        public void setRegDate(LocalDate regDate) {
            this.regDate = regDate;
        }

        public String getRegNumber() {
            return regNumber;
        }

        public void setRegNumber(String regNumber) {
            this.regNumber = regNumber;
        }

        @Override
        public String toString() {
            return "Document{" +
                    "description=" + description +
                    ", docId='" + docId + '\'' +
                    ", docStatus='" + docStatus + '\'' +
                    ", docType='" + docType + '\'' +
                    ", importRequest=" + importRequest +
                    ", ownerInn='" + ownerInn + '\'' +
                    ", participantInn='" + participantInn + '\'' +
                    ", producerInn='" + producerInn + '\'' +
                    ", productionDate='" + productionDate + '\'' +
                    ", productionType='" + productionType + '\'' +
                    ", products=" + products +
                    ", regDate='" + regDate + '\'' +
                    ", regNumber='" + regNumber + '\'' +
                    '}';
        }
    }

    public static class Product {
        @SerializedName("certificate_document")
        private String certificateDocument;
        @SerializedName("certificate_document_date")
        private String certificateDocumentDate;
        @SerializedName("certificate_document_number")
        private String certificateDocumentNumber;
        @SerializedName("owner_inn")
        private String ownerInn;
        @SerializedName("producer_inn")
        private String producerInn;
        @SerializedName("production_date")
        private String productionDate;
        @SerializedName("tnved_code")
        private String tnvedCode;
        @SerializedName("uit_code")
        private String uitCode;
        @SerializedName("uitu_code")
        private String uituCode;

        public Product(String certificateDocument, String certificateDocumentDate, String certificateDocumentNumber,
                       String ownerInn, String producerInn, String productionDate, String tnvedCode,
                       String uitCode, String uituCode) {
            this.certificateDocument = certificateDocument;
            this.certificateDocumentDate = certificateDocumentDate;
            this.certificateDocumentNumber = certificateDocumentNumber;
            this.ownerInn = ownerInn;
            this.producerInn = producerInn;
            this.productionDate = productionDate;
            this.tnvedCode = tnvedCode;
            this.uitCode = uitCode;
            this.uituCode = uituCode;
        }

        public Product() {}

        public String getCertificateDocument() {
            return certificateDocument;
        }

        public void setCertificateDocument(String certificateDocument) {
            this.certificateDocument = certificateDocument;
        }

        public String getCertificateDocumentDate() {
            return certificateDocumentDate;
        }

        public void setCertificateDocumentDate(String certificateDocumentDate) {
            this.certificateDocumentDate = certificateDocumentDate;
        }

        public String getCertificateDocumentNumber() {
            return certificateDocumentNumber;
        }

        public void setCertificateDocumentNumber(String certificateDocumentNumber) {
            this.certificateDocumentNumber = certificateDocumentNumber;
        }

        public String getOwnerInn() {
            return ownerInn;
        }

        public void setOwnerInn(String ownerInn) {
            this.ownerInn = ownerInn;
        }

        public String getProducerInn() {
            return producerInn;
        }

        public void setProducerInn(String producerInn) {
            this.producerInn = producerInn;
        }

        public String getProductionDate() {
            return productionDate;
        }

        public void setProductionDate(String productionDate) {
            this.productionDate = productionDate;
        }

        public String getTnvedCode() {
            return tnvedCode;
        }

        public void setTnvedCode(String tnvedCode) {
            this.tnvedCode = tnvedCode;
        }

        public String getUitCode() {
            return uitCode;
        }

        public void setUitCode(String uitCode) {
            this.uitCode = uitCode;
        }

        public String getUituCode() {
            return uituCode;
        }

        public void setUituCode(String uituCode) {
            this.uituCode = uituCode;
        }

        @Override
        public String toString() {
            return "Product{" +
                    "certificateDocument='" + certificateDocument + '\'' +
                    ", certificateDocumentDate='" + certificateDocumentDate + '\'' +
                    ", certificateDocumentNumber='" + certificateDocumentNumber + '\'' +
                    ", ownerInn='" + ownerInn + '\'' +
                    ", producerInn='" + producerInn + '\'' +
                    ", productionDate='" + productionDate + '\'' +
                    ", tnvedCode='" + tnvedCode + '\'' +
                    ", uitCode='" + uitCode + '\'' +
                    ", uituCode='" + uituCode + '\'' +
                    '}';
        }
    }

    public static class Description {
        @SerializedName("participantInn")
        private String participantInn;

        public String getParticipantInn() {
            return participantInn;
        }

        public void setParticipantInn(String participantInn) {
            this.participantInn = participantInn;
        }
    }

    public static class CreateDocumentResponse {
        private String value;
        private String code;
        @SerializedName("error_message")
        private String errorMessage;
        private String description;

        public boolean isSuccess() {
            return value != null && !value.isEmpty();
        }

        public String getDocumentId() {
            return value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return "CreateDocumentResponse{" +
                    "value='" + value + '\'' +
                    ", code='" + code + '\'' +
                    ", errorMessage='" + errorMessage + '\'' +
                    ", description='" + description + '\'' +
                    '}';
        }
    }
}