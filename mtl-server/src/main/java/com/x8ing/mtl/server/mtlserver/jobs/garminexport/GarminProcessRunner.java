package com.x8ing.mtl.server.mtlserver.jobs.garminexport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.function.Consumer;

final class GarminProcessRunner {

    private GarminProcessRunner() {
    }

    static int run(List<String> command, Consumer<String> outputConsumer)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputConsumer.accept(line);
            }
        }
        return process.waitFor();
    }
}
