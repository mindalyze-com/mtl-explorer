package com.x8ing.mtl.server.mtlserver.web.services.track;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImagePreviewServiceTest {
    private static final String JPEG = "printf '\\377\\330\\377synthetic\\377\\331'";
    private static final Path SOURCE = Path.of("/synthetic/photo.heic");
    private final MediaProcessLimiter limiter = spy(new MediaProcessLimiter(new MediaProcessProperties()));
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ImagePreviewService service = new ImagePreviewService(limiter, events);
    private final List<List<String>> commands = new ArrayList<>();

    @Test
    void nativeSuccessDoesNotInvokeOrSignalFallback() throws Exception {
        converters(JPEG, "exit 1");
        Path output;
        try (var preview = service.create(SOURCE, 4096, MediaType.IMAGE_JPEG_VALUE)) {
            assertThat(preview.processor()).isEqualTo("libvips");
            assertThat(preview.fallbackReason()).isNull();
            output = preview.output().path();
            assertThat(Files.size(output)).isEqualTo(14);
        }
        assertThat(Files.exists(output)).isFalse();
        assertThat(commands).containsExactly(List.of("mtl-vips-preview", SOURCE.toString(), "4096", "jpeg"));
        verifyNoInteractions(events);
    }

    @Test
    void unsupportedFormatRunsMagickAndSignalsTheFallback() throws Exception {
        converters("exit 64", JPEG);
        try (var preview = service.create(SOURCE, 192, MediaType.IMAGE_JPEG_VALUE)) {
            assertThat(preview.processor()).isEqualTo("imagemagick");
            assertThat(preview.fallbackReason()).isEqualTo("unsupported-format");
            assertThat(Files.size(preview.output().path())).isEqualTo(14);
        }
        assertThat(commands).hasSize(2);
        assertThat(commands.getLast()).containsExactly("magick", SOURCE.toString(), "-auto-orient",
                "-resize", "192x192>", "-quality", "92", "jpeg:-");
        verify(events).publishEvent(new MediaPreviewFallbackEvent(SOURCE, "libvips", "imagemagick", "unsupported-format"));
    }

    @Test
    void signalsAnAttemptEvenWhenMagickAlsoFails() throws Exception {
        converters("exit 64", "exit 1");
        assertThrows(ResponseStatusException.class, () -> service.create(SOURCE, null, MediaType.IMAGE_JPEG_VALUE));
        assertThat(commands).hasSize(2);
        assertThat(commands.getFirst()).contains("0");
        assertThat(commands.getLast()).doesNotContain("-resize");
        verify(events).publishEvent(any(MediaPreviewFallbackEvent.class));
    }

    @Test
    void decoderFailureDoesNotTriggerFallback() throws Exception {
        converters("printf 'damaged HEIC' >&2; exit 1", JPEG);
        assertThrows(ResponseStatusException.class, () -> service.create(SOURCE, 192, MediaType.IMAGE_JPEG_VALUE));
        assertThat(commands).hasSize(1);
        verifyNoInteractions(events);
    }

    @Test
    void incompleteOutputDoesNotTriggerFallback() throws Exception {
        converters("printf '\\377\\330\\377partial'", JPEG);
        assertThrows(ResponseStatusException.class, () -> service.create(SOURCE, 192, MediaType.IMAGE_JPEG_VALUE));
        assertThat(commands).hasSize(1);
        verifyNoInteractions(events);
    }

    @Test
    void missingConverterDoesNotTriggerFallback() throws Exception {
        doThrow(new IOException("converter unavailable")).when(limiter).start(any(ProcessBuilder.class));
        assertThrows(IOException.class, () -> service.create(SOURCE, 192, MediaType.IMAGE_JPEG_VALUE));
        verify(limiter).start(any(ProcessBuilder.class));
        verifyNoInteractions(events);
    }

    private void converters(String nativeScript, String fallbackScript) throws Exception {
        doAnswer(invocation -> {
            ProcessBuilder builder = invocation.getArgument(0);
            List<String> command = List.copyOf(builder.command());
            commands.add(command);
            builder.command("sh", "-c", command.getFirst().equals("mtl-vips-preview") ? nativeScript : fallbackScript);
            return invocation.callRealMethod();
        }).when(limiter).start(any(ProcessBuilder.class));
    }
}
