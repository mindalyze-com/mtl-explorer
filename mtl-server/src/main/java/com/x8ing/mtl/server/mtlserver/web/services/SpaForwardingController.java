package com.x8ing.mtl.server.mtlserver.web.services;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards client-side Vue-Router routes to index.html so that the SPA
 * can handle routing. Without this, refreshing the browser on /login
 * would return a 404 because Spring Boot would look for a physical
 * /login resource.
 */
@Controller
public class SpaForwardingController {

    /**
     * Forward known SPA routes to index.html.
     * Add new client-side routes here when they are created.
     */
    @RequestMapping({"/login", "/about"})
    public String forward() {
        return "forward:/index.html";
    }
}
