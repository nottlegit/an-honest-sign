package ru.selsup.an_honest_sign;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

class CrptApiTest {

    private static final String VALID_TOKEN = "Bearer_test_token_123456789";

    @Nested
    @DisplayName("Тесты конструктора")
    class ConstructorTests {

        @Test
        @DisplayName("Ошибка при нулевом лимите")
        void shouldFailWithZeroLimit() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new CrptApi(TimeUnit.MINUTES, 0, VALID_TOKEN)
            );

            assertEquals("Request limit must be positive", exception.getMessage());
        }

        @Test
        @DisplayName("Ошибка при отрицательном лимите")
        void shouldFailWithNegativeLimit() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new CrptApi(TimeUnit.MINUTES, -5, VALID_TOKEN)
            );

            assertEquals("Request limit must be positive", exception.getMessage());
        }

        @Test
        @DisplayName("Ошибка при null токене")
        void shouldFailWithNullToken() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new CrptApi(TimeUnit.MINUTES, 100, null)
            );

            assertEquals("Auth token cannot be null or empty", exception.getMessage());
        }

        @Test
        @DisplayName("Ошибка при пустом токене")
        void shouldFailWithEmptyToken() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new CrptApi(TimeUnit.MINUTES, 100, "   ")
            );

            assertEquals("Auth token cannot be null or empty", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Тесты Document класса")
    class DocumentTests {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        @Test
        @DisplayName("Создание пустого документа")
        void shouldCreateEmptyDocument() {
            CrptApi.Document document = new CrptApi.Document();

            assertNotNull(document);
            assertEquals("LP_INTRODUCE_GOODS", document.getDocType());
            assertEquals("OWN_PRODUCTION", document.getProductionType());
            assertEquals(false, document.getImportRequest());
        }

        @Test
        @DisplayName("Заполнение полей документа")
        void shouldFillDocumentFields() {
            CrptApi.Document document = new CrptApi.Document();

            document.setDocId("TEST_DOC_001");
            document.setDocStatus("NEW");
            document.setOwnerInn("1234567890");
            document.setParticipantInn("9876543210");
            document.setProducerInn("1111111111");
            document.setProductionDate(LocalDate.parse("2025-10-31", formatter));
            document.setRegDate(LocalDate.parse("2025-11-01", formatter));
            document.setRegNumber("REG_001");

            assertEquals("TEST_DOC_001", document.getDocId());
            assertEquals("NEW", document.getDocStatus());
            assertEquals("1234567890", document.getOwnerInn());
            assertEquals("9876543210", document.getParticipantInn());
            assertEquals("1111111111", document.getProducerInn());
            assertEquals("2025-10-31", document.getProductionDate().toString());
            assertEquals("2025-11-01", document.getRegDate().toString());
            assertEquals("REG_001", document.getRegNumber());
        }

        @Test
        @DisplayName("Документ с описанием")
        void shouldCreateDocumentWithDescription() {
            CrptApi.Document document = new CrptApi.Document();
            CrptApi.Description description = new CrptApi.Description();

            description.setParticipantInn("1234567890");
            document.setDescription(description);

            assertNotNull(document.getDescription());
        }
    }

    @Nested
    @DisplayName("Тесты работы с коллекциями")
    class CollectionTests {

        @Test
        @DisplayName("Пустой список продуктов")
        void shouldHandleEmptyProductList() {
            CrptApi.Document document = new CrptApi.Document();

            document.setProducts(new ArrayList<>());

            assertNotNull(document.getProducts());
            assertEquals(0, document.getProducts().size());
            assertTrue(document.getProducts().isEmpty());
        }

        @Test
        @DisplayName("Список с одним продуктом")
        void shouldHandleSingleProduct() {
            CrptApi.Document document = new CrptApi.Document();
            CrptApi.Product product = new CrptApi.Product();
            product.setUitCode("01234567890123456789");

            document.setProducts(Arrays.asList(product));

            assertEquals(1, document.getProducts().size());
            assertEquals("01234567890123456789", document.getProducts().getFirst().getUitCode());
        }
    }
}