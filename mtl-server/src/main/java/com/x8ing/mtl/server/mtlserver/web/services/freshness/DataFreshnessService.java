package com.x8ing.mtl.server.mtlserver.web.services.freshness;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.x8ing.mtl.server.mtlserver.db.repository.freshness.DataFreshnessRepository;
import com.x8ing.mtl.server.mtlserver.utils.Base58BitcoinFlavor;
import com.x8ing.mtl.server.mtlserver.web.services.config.ServerIdentityService;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

@Service
@JsonPropertyOrder({
        "dataFreshnessRepository",
        "serverIdentityService"
})
public class DataFreshnessService {

    private static final String TOKEN_ITEM_SEPARATOR = "|";
    private static final String TOKEN_KEY_REVISION_SEPARATOR = ":";
    private static final String TOKEN_MINI_HASH_SEPARATOR = "__|";
    private static final String TOKEN_SERVER_ID_KEY = "server_id";
    private static final int TOKEN_MINI_HASH_LENGTH = 6;
    private static final int CRC32_BYTE_COUNT = Integer.BYTES;

    private final DataFreshnessRepository dataFreshnessRepository;
    private final ServerIdentityService serverIdentityService;

    public DataFreshnessService(
            DataFreshnessRepository dataFreshnessRepository,
            ServerIdentityService serverIdentityService
    ) {
        this.dataFreshnessRepository = dataFreshnessRepository;
        this.serverIdentityService = serverIdentityService;
    }

    public DataFreshnessResponseDto getDataFreshness() {
        String serverId = serverIdentityService.getServerId();

        List<DataFreshnessItemDto> items = dataFreshnessRepository.findAllByOrderByDomainKeyAsc().stream()
                .map(row -> new DataFreshnessItemDto(row.getDomainKey(), row.getRevision(), row.getChangedAt()))
                .toList();

        String domainToken = items.stream()
                .map(item -> item.key() + TOKEN_KEY_REVISION_SEPARATOR + item.revision())
                .collect(Collectors.joining(TOKEN_ITEM_SEPARATOR));
        String readableToken = TOKEN_SERVER_ID_KEY + TOKEN_KEY_REVISION_SEPARATOR + serverId;
        if (!domainToken.isBlank()) {
            readableToken += TOKEN_ITEM_SEPARATOR + domainToken;
        }
        String token = miniHash(readableToken) + TOKEN_MINI_HASH_SEPARATOR + readableToken;

        Date changedAt = items.stream()
                .map(DataFreshnessItemDto::changedAt)
                .max(Comparator.nullsLast(Date::compareTo))
                .orElse(null);

        return new DataFreshnessResponseDto(token, changedAt, items);
    }

    private static String miniHash(String token) {
        CRC32 crc32 = new CRC32();
        crc32.update(token.getBytes(StandardCharsets.UTF_8));
        byte[] crcBytes = ByteBuffer.allocate(CRC32_BYTE_COUNT).putInt((int) crc32.getValue()).array();
        String encoded = Base58BitcoinFlavor.encode(crcBytes);
        if (encoded.length() >= TOKEN_MINI_HASH_LENGTH) {
            return encoded.substring(encoded.length() - TOKEN_MINI_HASH_LENGTH);
        }
        return String.valueOf(Base58BitcoinFlavor.ALPHABET[0]).repeat(TOKEN_MINI_HASH_LENGTH - encoded.length()) + encoded;
    }
}
