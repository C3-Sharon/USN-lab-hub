package com.usn.labhub.user.inventory;

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
                "spring.datasource.url=${USNHUB_TEST_MYSQL_URL:jdbc:h2:mem:w46-inventory-issue-fault;MODE=MySQL;DB_CLOSE_DELAY=-1}",
                "spring.datasource.username=${USNHUB_TEST_MYSQL_USERNAME:sa}",
                "spring.datasource.password=${USNHUB_TEST_MYSQL_PASSWORD:}",
                "spring.flyway.enabled=true",
                "usnhub.iot.mqtt.enabled=false",
                "usnhub.iot.operations.timeout-enabled=false",
                "usnhub.inventory.issue.expiry-delay-ms=3600000",
                "usnhub.inventory.issue.test-fault-point=AFTER_FIRST_MOVEMENT"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestPropertySource(properties = "usnhub.inventory.issue.test-fault-point=AFTER_FIRST_MOVEMENT")
class InventoryIssueFaultRollbackIntegrationTest extends InventoryIssueApiIntegrationTest {
    @Override
    void confirmIssueConsumesAndWritesMovements() {
    }

    @Override
    void confirmIssueReplaysAndRejectsNewKey() {
    }

    @Override
    void returnConfirmsRestoresOriginalBatchAndBalance() {
    }

    @Override
    void returnRejectsQuantityExceedingIssued() {
    }

    @Override
    void returnConfirmRejectsStaleVersion() {
    }

    @Override
    void returnRejectLeavesStockUntouched() {
    }

    @Override
    void memberCannotConfirmReturn() {
    }

    @Test
    void rollsBackEveryFactAtControlledFailurePoint() {
        verifyRollbackAtControlledFailurePoint();
    }
}
