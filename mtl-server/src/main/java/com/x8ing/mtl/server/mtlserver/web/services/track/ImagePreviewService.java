package com.x8ing.mtl.server.mtlserver.web.services.track;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Native libvips previews, with an observable fallback for unsupported formats.
 */
@Service
public class ImagePreviewService {
    private static final Logger log = LoggerFactory.getLogger(ImagePreviewService.class);
    static final String PROCESSOR_HEADER = "X-MTL-Image-Processor";
    static final String FALLBACK_HEADER = "X-MTL-Image-Fallback";
    static final String VIPS_COMMAND = "mtl-vips-preview";
    static final String VIPS_PROCESSOR = "libvips";
    static final String MAGICK_PROCESSOR = "imagemagick";
    static final String UNSUPPORTED_FORMAT = "unsupported-format";
    // The native adapter reserves this exit code for a missing native loader.
    static final int UNSUPPORTED_FORMAT_EXIT = 64;
    private static final String IMAGE_MAGICK_COMMAND = "magick";
    private static final String JPEG_QUALITY = "92";

    private final MediaProcessLimiter limiter;
    private final ApplicationEventPublisher events;

    public ImagePreviewService(MediaProcessLimiter limiter, ApplicationEventPublisher events) {
        this.limiter = limiter;
        this.events = events;
    }

    Preview create(Path source, Integer maxSize, String contentType) throws IOException {
        String format = MediaType.IMAGE_PNG_VALUE.equals(contentType) ? "png" : "jpeg";
        log.info("Image preview starting: file={}, processor={}, maxSize={}",
                source.getFileName(), VIPS_PROCESSOR, maxSize);
        long started = System.nanoTime();
        try {
            return generate(new ProcessBuilder(VIPS_COMMAND, source.toAbsolutePath().toString(),
                            Integer.toString(maxSize == null ? 0 : maxSize), format),
                    contentType, VIPS_PROCESSOR, null, started);
        } catch (GeneratedImageOutput.ConversionFailure e) {
            if (e.exitCode() != UNSUPPORTED_FORMAT_EXIT) throw e;
            log.warn("Image preview fallback: file={}, from={}, to={}, reason={}, exception={}: {}",
                    source.getFileName(), VIPS_PROCESSOR, MAGICK_PROCESSOR, UNSUPPORTED_FORMAT,
                    e.getClass().getSimpleName(), e.getReason());
            // Signal the attempt before ImageMagick runs, even if that also fails.
            events.publishEvent(new MediaPreviewFallbackEvent(
                    source, VIPS_PROCESSOR, MAGICK_PROCESSOR, UNSUPPORTED_FORMAT));
            ProcessBuilder fallback = imageMagickProcess(source, maxSize, format);
            // Keep this warning after event listeners and before launching the
            // fallback, so a memory-related crash still has conversion context.
            log.warn("ImageMagick fallback starting; this may use more RAM than libvips: file={}, maxSize={}, reason={}",
                    source.getFileName(), maxSize, UNSUPPORTED_FORMAT);
            return generate(fallback,
                    contentType, MAGICK_PROCESSOR, UNSUPPORTED_FORMAT, started);
        }
    }

    private Preview generate(ProcessBuilder builder, String contentType, String processor,
                             String fallbackReason, long started) throws IOException {
        GeneratedImageOutput output = GeneratedImageOutput.create(builder, limiter, contentType);
        log.info("Image preview completed: processor={}, fallback={}, durationMs={}",
                processor, fallbackReason, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        return new Preview(output, processor, fallbackReason);
    }

    private static ProcessBuilder imageMagickProcess(Path source, Integer maxSize, String format) {
        List<String> command = new ArrayList<>(List.of(IMAGE_MAGICK_COMMAND, source.toAbsolutePath().toString(),
                "-auto-orient"));
        if (maxSize != null && maxSize > 0) {
            command.addAll(List.of("-resize", maxSize + "x" + maxSize + ">"));
        }
        command.addAll(List.of("-quality", JPEG_QUALITY, format + ":-"));
        return new ProcessBuilder(command);
    }

    record Preview(GeneratedImageOutput output, String processor, String fallbackReason) implements AutoCloseable {
        @Override
        public void close() {
            output.close();
        }
    }
}
