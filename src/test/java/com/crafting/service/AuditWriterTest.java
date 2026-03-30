package com.crafting.service;

import com.crafting.model.AuditEvent;
import com.crafting.repository.AuditEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditWriterTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditWriter auditWriter;

    @Test
    @DisplayName("maps write request fields into persisted AuditEvent")
    void mapsRequestToAuditEvent() {
        when(auditEventRepository.save(org.mockito.ArgumentMatchers.any(AuditEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        auditWriter.write(new AuditWriter.AuditWriteRequest(
                4242L,
                "create",
                "recipe",
                "123",
                "success",
                "source=manual"
        ));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());
        AuditEvent saved = captor.getValue();

        assertThat(saved.getActorDiscordId()).isEqualTo(4242L);
        assertThat(saved.getAction()).isEqualTo("CREATE");
        assertThat(saved.getEntity()).isEqualTo("RECIPE");
        assertThat(saved.getEntityId()).isEqualTo("123");
        assertThat(saved.getResult()).isEqualTo("SUCCESS");
        assertThat(saved.getMetadata()).isEqualTo("source=manual");
    }
}
