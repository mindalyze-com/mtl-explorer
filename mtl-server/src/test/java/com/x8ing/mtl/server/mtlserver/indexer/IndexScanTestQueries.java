package com.x8ing.mtl.server.mtlserver.indexer;

import com.x8ing.mtl.server.mtlserver.db.entity.indexer.IndexedFile;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexedFileScanEntry;
import com.x8ing.mtl.server.mtlserver.db.repository.indexer.IndexerRepository;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

final class IndexScanTestQueries {
    private IndexScanTestQueries() {
    }

    static void stub(IndexerRepository repository, String index, Supplier<? extends Collection<IndexedFile>> rows) {
        when(repository.findScanEntries(eq(index), anyString(), anyCollection())).thenAnswer(call -> {
            Collection<String> names = call.getArgument(2);
            return rows.get().stream().filter(file -> Objects.equals(file.getPath(), call.getArgument(1))
                                                     && names.contains(file.getName())).map(IndexScanTestQueries::entry).toList();
        });
        when(repository.findScanEntriesAfterId(eq(index), anyLong(), any(Pageable.class))).thenAnswer(call -> {
            long after = call.getArgument(1);
            Pageable page = call.getArgument(2);
            return rows.get().stream().filter(file -> file.getId() > after).sorted(Comparator.comparing(IndexedFile::getId))
                    .limit(page.getPageSize()).map(IndexScanTestQueries::entry).toList();
        });
        when(repository.findPendingScanEntriesAfterId(eq(index), anyLong(), anyCollection(), any(Pageable.class))).thenAnswer(call -> {
            long after = call.getArgument(1);
            Collection<IndexedFile.IndexerStatus> statuses = call.getArgument(2);
            Pageable page = call.getArgument(3);
            return rows.get().stream().filter(file -> file.getId() > after && statuses.contains(file.getIndexerStatus()))
                    .sorted(Comparator.comparing(IndexedFile::getId)).limit(page.getPageSize()).map(IndexScanTestQueries::entry).toList();
        });
    }

    static IndexedFileScanEntry entry(IndexedFile file) {
        return new IndexedFileScanEntry(file.getId(), file.getName(), file.getPath(), file.getSize(), file.getLastModifiedDate(),
                file.getIndexerStatus(), file.getVersion());
    }
}
