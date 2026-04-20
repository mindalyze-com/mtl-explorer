package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import com.x8ing.mtl.server.mtlserver.indexer.event.FileIndexerObserver;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

public class FileIndexer {

    private final PlatformTransactionManager txManager; // optional

    public FileIndexer() {
        this.txManager = null;
    }

    public FileIndexer(PlatformTransactionManager txManager) {
        this.txManager = txManager;
    }


    public void findAndIndex(String index, Path watchDirectory, IndexerRepository indexerRepository, FileIndexerObserver observer, boolean blocking) {
        findAndIndex(index, watchDirectory, indexerRepository, observer, blocking, null, null);
    }

    public void findAndIndex(String index,
                             Path watchDirectory,
                             IndexerRepository indexerRepository,
                             FileIndexerObserver observer,
                             boolean blocking,
                             List<PathMatcher> exclusionPatterns,
                             List<PathMatcher> inclusionPatterns) {
        findAndIndexInternal(index, watchDirectory, indexerRepository, observer, blocking, exclusionPatterns, inclusionPatterns, null, true);
    }

    public FileIndexerImpl findAndIndex(String index,
                                        Path watchDirectory,
                                        IndexerRepository indexerRepository,
                                        FileIndexerObserver observer,
                                        boolean blocking,
                                        List<PathMatcher> exclusionPatterns,
                                        List<PathMatcher> inclusionPatterns,
                                        FileIndexerImpl.ChangeDetectionStrategy strategy) {
        return findAndIndexInternal(index, watchDirectory, indexerRepository, observer, blocking, exclusionPatterns, inclusionPatterns, strategy, true);
    }

    public FileIndexerImpl findAndIndex(String index,
                                        Path watchDirectory,
                                        IndexerRepository indexerRepository,
                                        FileIndexerObserver observer,
                                        boolean blocking,
                                        List<PathMatcher> exclusionPatterns,
                                        List<PathMatcher> inclusionPatterns,
                                        FileIndexerImpl.ChangeDetectionStrategy strategy,
                                        boolean liveWatchEnabled) {
        return findAndIndexInternal(index, watchDirectory, indexerRepository, observer, blocking, exclusionPatterns, inclusionPatterns, strategy, liveWatchEnabled);
    }

    private FileIndexerImpl findAndIndexInternal(String index,
                                                 Path watchDirectory,
                                                 IndexerRepository indexerRepository,
                                                 FileIndexerObserver observer,
                                                 boolean blocking,
                                                 List<PathMatcher> exclusionPatterns,
                                                 List<PathMatcher> inclusionPatterns,
                                                 FileIndexerImpl.ChangeDetectionStrategy strategy,
                                                 boolean liveWatchEnabled) {
        FileIndexerImpl impl = new FileIndexerImpl(index, watchDirectory, indexerRepository, observer, txManager);
        if (exclusionPatterns != null) impl.setExclusionPatterns(exclusionPatterns);
        if (inclusionPatterns != null) impl.setInclusionPatterns(inclusionPatterns);
        if (strategy != null) impl.setChangeDetectionStrategy(strategy);
        impl.setLiveWatchEnabled(liveWatchEnabled);
        impl.start(blocking);
        return impl;
    }

}

