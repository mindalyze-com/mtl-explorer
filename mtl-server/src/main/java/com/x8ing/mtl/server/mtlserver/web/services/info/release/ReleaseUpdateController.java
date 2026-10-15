package com.x8ing.mtl.server.mtlserver.web.services.info.release;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/info/updates")
public class ReleaseUpdateController {
    private final ReleaseUpdateService service;

    public ReleaseUpdateController(ReleaseUpdateService service) {
        this.service = service;
    }

    @GetMapping
    public ReleaseUpdateResponse getUpdateStatus(@RequestParam(defaultValue = "false") boolean check) {
        return service.getStatus(check);
    }
}
