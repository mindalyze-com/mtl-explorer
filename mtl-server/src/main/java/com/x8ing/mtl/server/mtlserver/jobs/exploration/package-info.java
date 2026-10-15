/**
 * Exploration Score background job.
 *
 * <p>Computes the {@code exploration_score} for each GPS track — a value between 0.0 and 1.0
 * expressing what fraction of the track covered territory the user had never visited before,
 * based on all prior tracks (ordered by {@code start_date}).
 *
 * <p>See {@code documentation/architecture/analysis/exploration.md} for full documentation.
 */
package com.x8ing.mtl.server.mtlserver.jobs.exploration;
