package com.x8ing.mtl.server.mtlserver.jobs.media.indexer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.Collections;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SupportedMediaFormatTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "photo.JPG", "phone.heic", "modern.avif", "archive.CR3", "scan.jp2", "render.exr",
            "clip.mp4", "camera.MKV", "browser.webm", "camcorder.m2ts", "professional.mxf",
            "cinema.r3d", "stream.hevc"
    })
    void acceptsBroadImageAndVideoFormats(String fileName) {
        assertThat(SupportedMediaFormat.isSupportedFileName(fileName)).isTrue();
        assertThat(SupportedMediaFormat.isSupported(Path.of(fileName))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "manual.pdf", "notes.md", "metadata.json", "subtitles.srt", "playlist.m3u8",
            "demo_photo_00001.jpg.part-203", "demo_photo_00001.jpg.tmp", "no-extension"
    })
    void rejectsDocumentsSidecarsAndTemporaryFiles(String fileName) {
        assertThat(SupportedMediaFormat.isSupportedFileName(fileName)).isFalse();
    }

    @Test
    void classifiesSupportedKindsWithoutOverlappingExtensions() {
        assertThat(SupportedMediaFormat.isVideoFileName("PHOTO.AVIF")).isFalse();
        assertThat(SupportedMediaFormat.isVideoFileName("CLIP.MKV")).isTrue();
        assertThat(Collections.disjoint(
                SupportedMediaFormat.imageExtensions(),
                SupportedMediaFormat.videoExtensions())).isTrue();
    }

    @Test
    void inclusionRegexMatchesOnlySupportedFinalExtensions() {
        Pattern pattern = Pattern.compile(SupportedMediaFormat.inclusionRegex());

        assertThat(pattern.matcher("/archive/trip/photo.JXL")).matches();
        assertThat(pattern.matcher("/archive/trip/video.WMV")).matches();
        assertThat(pattern.matcher("/archive/trip/photo.jpg.part-17").matches()).isFalse();
        assertThat(pattern.matcher("/archive/trip/document.pdf").matches()).isFalse();
    }
}
