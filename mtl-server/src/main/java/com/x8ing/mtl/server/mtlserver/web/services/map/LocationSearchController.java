package com.x8ing.mtl.server.mtlserver.web.services.map;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/location-search")
@JsonPropertyOrder({
        "locationSearchService"
})
public class LocationSearchController {

    private final LocationSearchService locationSearchService;

    public LocationSearchController(LocationSearchService locationSearchService) {
        this.locationSearchService = locationSearchService;
    }

    @GetMapping("/status")
    public LocationSearchStatusDto getStatus() {
        return locationSearchService.getStatus();
    }

    @GetMapping
    public LocationSearchResponseDto search(@RequestParam("q") String query,
                                            @RequestParam(value = "limit", required = false) Integer limit,
                                            @RequestParam(value = "sort", required = false) String sort,
                                            @RequestParam(value = "lat", required = false) Double lat,
                                            @RequestParam(value = "lon", required = false) Double lon) {
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter q is required.");
        }
        return locationSearchService.search(query, limit, sort, lat, lon);
    }
}
