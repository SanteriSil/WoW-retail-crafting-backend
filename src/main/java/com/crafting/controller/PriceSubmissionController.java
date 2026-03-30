package com.crafting.controller;

import com.crafting.model.dto.PriceSubmissionDTO;
import com.crafting.service.PriceSubmissionService;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/price-submissions")
public class PriceSubmissionController {

    private final PriceSubmissionService priceSubmissionService;

    public PriceSubmissionController(PriceSubmissionService priceSubmissionService) {
        this.priceSubmissionService = priceSubmissionService;
    }

    @GetMapping
    public ResponseEntity<Page<PriceSubmissionDTO>> getHistory(
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) Long actorDiscordId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return ResponseEntity.ok(priceSubmissionService.getHistory(itemId, actorDiscordId, source, from, to, page, size));
    }
}
