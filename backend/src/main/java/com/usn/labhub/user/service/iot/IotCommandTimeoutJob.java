package com.usn.labhub.user.service.iot;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "usnhub.iot.operations",
        name = "timeout-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class IotCommandTimeoutJob {

    private final IotOperationsService operationsService;

    public IotCommandTimeoutJob(IotOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @Scheduled(fixedDelayString = "${usnhub.iot.operations.timeout-check-delay-ms:1000}")
    public void expireTimedOutCommands() {
        operationsService.expireTimedOutCommands();
    }
}
