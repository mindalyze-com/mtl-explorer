package com.x8ing.mtl.server.mtlserver.googlehealth.provider;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Exercise file export is separate from health data queries.
 */
@FunctionalInterface
public interface ExerciseTcxProvider {
    void downloadExerciseTcx(String resourceName, Path destination) throws IOException;
}
