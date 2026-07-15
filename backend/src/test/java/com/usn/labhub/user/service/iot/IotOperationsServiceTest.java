package com.usn.labhub.user.service.iot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.usn.labhub.user.config.IotMqttProperties;
import com.usn.labhub.user.domain.dto.iot.IotCommandRequestDTO;
import com.usn.labhub.user.domain.entity.iot.IotAlertRecord;
import com.usn.labhub.user.domain.entity.iot.IotCommandRecord;
import com.usn.labhub.user.domain.entity.iot.IotRecommendationRecord;
import com.usn.labhub.user.domain.vo.iot.IotCommandVO;
import com.usn.labhub.user.mapper.IotOperationsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IotOperationsServiceTest {

    private IotOperationsMapper mapper;
    private IotCommandPublisher publisher;
    private IotOperationsService service;

    @BeforeEach
    void setUp() {
        mapper = mock(IotOperationsMapper.class);
        publisher = mock(IotCommandPublisher.class);
        IotMqttProperties properties = new IotMqttProperties();
        properties.setCommandTopic("iot/power-monitor/PM-001/command");
        service = new IotOperationsService(mapper, new ObjectMapper(), publisher, properties);
    }

    @Test
    void powerAboveThresholdCreatesOneAlertAndRecommendation() {
        when(mapper.selectOpenAlert(1L, "power", "WARNING")).thenReturn(null);
        when(mapper.insertAlert(any())).thenAnswer(invocation -> {
            IotAlertRecord alert = invocation.getArgument(0);
            alert.setId(11L);
            return 1;
        });

        service.evaluatePower(new BigDecimal("110.5"), LocalDateTime.of(2026, 7, 14, 12, 30));

        ArgumentCaptor<IotAlertRecord> alertCaptor = ArgumentCaptor.forClass(IotAlertRecord.class);
        verify(mapper).insertAlert(alertCaptor.capture());
        assertEquals("OPEN", alertCaptor.getValue().getStatus());
        assertEquals(new BigDecimal("100"), alertCaptor.getValue().getThresholdValue());

        ArgumentCaptor<IotRecommendationRecord> recommendationCaptor =
                ArgumentCaptor.forClass(IotRecommendationRecord.class);
        verify(mapper).insertRecommendation(recommendationCaptor.capture());
        assertEquals(11L, recommendationCaptor.getValue().getAlertId());
        assertEquals("PENDING", recommendationCaptor.getValue().getStatus());
    }

    @Test
    void repeatedPowerAboveThresholdUpdatesExistingOpenAlertWithoutDuplicateRecommendation() {
        IotAlertRecord open = new IotAlertRecord();
        open.setId(9L);
        when(mapper.selectOpenAlert(1L, "power", "WARNING")).thenReturn(open);

        service.evaluatePower(new BigDecimal("120"), LocalDateTime.of(2026, 7, 14, 12, 31));

        verify(mapper).updateOpenAlert(eq(9L), eq(new BigDecimal("120")), anyString(), any());
        verify(mapper, never()).insertAlert(any());
        verify(mapper, never()).insertRecommendation(any());
    }

    @Test
    void powerAtThresholdDoesNotCreateAlert() {
        service.evaluatePower(new BigDecimal("100"), LocalDateTime.now());
        verify(mapper, never()).selectOpenAlert(any(), anyString(), anyString());
        verify(mapper, never()).insertAlert(any());
    }

    @Test
    void commandIsCreatedPendingThenPublishedAndMarkedSent() {
        when(mapper.insertCommand(any())).thenAnswer(invocation -> {
            IotCommandRecord command = invocation.getArgument(0);
            command.setId(21L);
            return 1;
        });
        when(mapper.markCommandSent(anyString(), any())).thenReturn(1);
        IotCommandRequestDTO request = new IotCommandRequestDTO();
        request.setCommand("SET_SAMPLE_INTERVAL");
        request.setParams(Map.of("intervalSeconds", 5));

        IotCommandVO response = service.sendCommand(1L, request);

        assertEquals("PENDING", response.getStatus());
        assertEquals(5, response.getParams().get("intervalSeconds"));
        verify(publisher).publish(eq("iot/power-monitor/PM-001/command"), anyString());
        verify(mapper).markCommandSent(eq(response.getCommandId()), any());
        verify(mapper).insertOperationLog(any());
    }

    @Test
    void commandRejectsUnsupportedParameter() {
        IotCommandRequestDTO request = new IotCommandRequestDTO();
        request.setCommand("SET_SAMPLE_INTERVAL");
        request.setParams(Map.of("intervalSeconds", 10));

        IotApiException exception = assertThrows(IotApiException.class, () -> service.sendCommand(1L, request));
        assertEquals(400, exception.getCode());
        assertEquals("intervalSeconds 必须为 5", exception.getMessage());
        verify(mapper, never()).insertCommand(any());
    }

    @Test
    void sentCommandAcceptsAckAndLateAckIsIgnored() {
        IotCommandRecord sent = command("cmd-test", "SENT");
        when(mapper.selectCommand("cmd-test")).thenReturn(sent);
        when(mapper.applyCommandAck(eq("cmd-test"), eq("ACKED"), eq(null), any())).thenReturn(1);

        boolean updated = service.ingestAck("""
                {
                  "commandId":"cmd-test",
                  "command":"SET_SAMPLE_INTERVAL",
                  "status":"ACKED",
                  "result":{"intervalSeconds":5},
                  "ackedAt":"2026-07-14 12:30:46"
                }
                """);
        assertTrue(updated);
        verify(mapper).insertOperationLog(any());

        sent.setStatus("TIMEOUT");
        assertFalse(service.ingestAck("""
                {"commandId":"cmd-test","command":"SET_SAMPLE_INTERVAL","status":"ACKED"}
                """));
    }

    @Test
    void timeoutUsesTenSecondCutoff() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 14, 12, 31, 0);
        when(mapper.markTimedOutCommands(now.minusSeconds(10))).thenReturn(2);
        assertEquals(2, service.expireTimedOutCommands(now));
    }

    private IotCommandRecord command(String commandId, String status) {
        return new IotCommandRecord(
                1L, commandId, 1L, "PM-001", "SET_SAMPLE_INTERVAL",
                "{\"intervalSeconds\":5}", status, null, 0L,
                LocalDateTime.now(), LocalDateTime.now(), null
        );
    }
}
