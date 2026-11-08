package com.usn.labhub.user.procurement;

import com.usn.labhub.user.UsnHubApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
        classes = UsnHubApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=${USNHUB_TEST_MYSQL_URL:jdbc:h2:mem:w45-procurement-receipt-fault;MODE=MySQL;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${USNHUB_TEST_MYSQL_USERNAME:sa}",
                "spring.datasource.password=${USNHUB_TEST_MYSQL_PASSWORD:}",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false",
                "usnhub.procurement.test-fault-point=AFTER_FIRST_MOVEMENT"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestPropertySource(properties = "usnhub.procurement.test-fault-point=AFTER_FIRST_MOVEMENT")
class ProcurementReceiptFaultRollbackIntegrationTest extends ProcurementReceiptApiIntegrationTest {
    @Override
    void confirmsPartialThenFullReceiptAndSupportsExactReplay() {
    }

    @Override
    void rejectsOverQuantityWithoutInventoryFacts() {
    }

    @Override
    void enforcesReceiptWriteAndInventoryDetailPermissions() {
    }

    @Override
    void serializesConcurrentFirstBalanceCreationOnMySql() {
    }

    @Test
    void rollsBackEveryFactAtControlledFailurePoint() {
        verifyRollbackAtControlledFailurePoint();
    }
}
