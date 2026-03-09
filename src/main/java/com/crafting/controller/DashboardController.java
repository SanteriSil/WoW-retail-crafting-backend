package com.crafting.controller;

import com.crafting.model.dto.DashboardResponse;
import com.crafting.service.CraftDashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final CraftDashboardService craftDashboardService;

    public DashboardController(CraftDashboardService craftDashboardService) {
        this.craftDashboardService = craftDashboardService;
    }

    @GetMapping("/crafts")
    public ResponseEntity<DashboardResponse> getDashboardCrafts(
            @RequestParam(required = false) Long characterId,
            @RequestParam(required = false) Integer professionId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "profit") String sort,
            @RequestParam(required = false, defaultValue = "desc") String direction,
            Authentication authentication) {

        Long discordId = Long.parseLong(authentication.getName());
    log.debug("GET /dashboard/crafts for discordId={} characterId={} professionId={} search='{}' sort={} direction={}",
        discordId,
        characterId,
        professionId,
        search,
        sort,
        direction);
        var params = new CraftDashboardService.DashboardFilterParams(
                characterId, professionId, search, sort, direction);
        return ResponseEntity.ok(craftDashboardService.getDashboardCrafts(discordId, params));
    }
}
