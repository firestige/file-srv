package tech.icc.filesrv.core.application.entrypoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.icc.filesrv.common.exception.NotFoundException;
import tech.icc.filesrv.common.exception.validation.FileKeyTooLongException;
import tech.icc.filesrv.config.FileControllerConfig;
import tech.icc.filesrv.core.application.service.FileService;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileService service;
    @Mock
    private FileControllerConfig config;
    @InjectMocks
    private FileController controller;

    @BeforeEach
    void setUp() {
        when(config.getMaxFileKeyLength()).thenReturn(128);
    }

    @Test
    void shouldRejectFileKeyExceedingMaxFileKeyLength() {
        int fileKeyLength = 128;
        when(config.getMaxFileKeyLength()).thenReturn(fileKeyLength);
        String tooLongKey = "a".repeat(129);
        
        assertThatThrownBy(() -> controller.getFile(tooLongKey))
                .isInstanceOf(FileKeyTooLongException.class)
                .satisfies(ex -> {
                    FileKeyTooLongException exception = (FileKeyTooLongException) ex;
                    assertThat(exception.getMaxLength()).isEqualTo(128);
                    assertThat(exception.getSource()).isEqualTo(tooLongKey);
                    // 验证异常不携带 cause（无栈异常的特征之一）
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getStackTrace()).isEmpty();
                });
    }

    @Test
    void shouldRejectWhenFileKeyNotExist() {
        String invalidKey = "a";
        when(service.getFileInfo(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.getFile(invalidKey))
                .isInstanceOf(NotFoundException.FileNotFoundException.class);
    }

    @Test
    void shouldUseDefaultExpiryWhenNotProvided() {
        when(config.getDefaultPresignExpirySeconds()).thenReturn(3600L);
        when(config.getMinPresignExpirySeconds()).thenReturn(60L);
        when(config.getMaxPresignExpirySeconds()).thenReturn(604800L);
        when(service.getPresignedUrl(anyString(), any())).thenReturn("url");

        controller.getPresignedUrl("test-key", null);

        verify(service).getPresignedUrl("test-key", Duration.ofSeconds(3600));
    }
}