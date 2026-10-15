package com.x8ing.mtl.server.mtlserver.web.services.logs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class StackFingerprintTest {
    @Test
    void ignoresExceptionHeadersAndFormattingWhilePreservingAllFrames() {
        String frames = "at a (app.js:1:2)\nat b (app.js:7:4)";
        assertThat(StackFingerprint.of("TypeError: first reason\n " + frames))
                .isEqualTo(StackFingerprint.of("Error: another reason\r\nwith multiple description lines\r\n\tat a (app.js:1:2)  \r\n\tat b (app.js:7:4)\r\n"))
                .isEqualTo(StackFingerprint.of(frames))
                .isEqualTo("dd7a634e6a465292399cf7a7a9bd0305f0f4299b5f48374c67ac11ddc88b4435");
    }

    @Test
    void distinguishesEveryFrameBeyondTheDefaultBrowserDepthAndTheirOrder() {
        String frames = IntStream.range(0, 15).mapToObj(index -> "at f" + index + " (app.js:" + index + ":2)")
                .collect(Collectors.joining("\n"));
        String fingerprint = StackFingerprint.of(frames);
        for (String changed : new String[]{frames.replace("f14", "other"), frames.replace("app.js:14:2", "other.js:14:2"),
                frames.replace("app.js:14:2", "app.js:15:2"), frames.replace("app.js:14:2", "app.js:14:3"),
                frames.replace("at f14", "at async f14"), frames + "\nat nativeCall (native)",
                "at caller (caller.js:1:1)\n" + frames, frames.lines().sorted(java.util.Comparator.reverseOrder()).collect(Collectors.joining("\n"))}) {
            assertThat(StackFingerprint.of(changed)).isNotEqualTo(fingerprint);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"load@app.js:1:2\nglobal code@app.js:7:4", "@app.js:1:2\nload@[native code]", "anonymous@\nglobal code@app.js:7:4"})
    void retainsFirefoxAndSafariStacksWithoutDiscardingTheirFirstFrame(String frames) {
        assertThat(StackFingerprint.of(frames)).matches("[a-f0-9]{64}")
                .isEqualTo(StackFingerprint.of("TypeError: variable description\n" + frames))
                .isNotEqualTo(StackFingerprint.of(frames.substring(frames.indexOf('\n') + 1)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  \r\n\t", "Error: header only", "TypeError: user@example.test\nno captured frames"})
    void leavesMissingOrUnrecognizedStacksUnfingerprinted(String stack) {
        assertThat(StackFingerprint.of(stack)).isNull();
    }
}
