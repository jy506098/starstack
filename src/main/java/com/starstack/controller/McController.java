package com.starstack.controller;

import com.starstack.dto.ContextView;
import com.starstack.service.CatalogData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** MC server status JSON. */
@RestController
public class McController {

    @Autowired
    private CatalogData catalog;

    @GetMapping("/api/mc_server_info")
    public ContextView.MCServerConfig mcServerInfo() {
        return catalog.getMcServer();
    }
}