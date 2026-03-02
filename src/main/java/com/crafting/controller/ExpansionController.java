package com.crafting.controller;

import com.crafting.model.Expansion;
import com.crafting.repository.ExpansionRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/expansions")
public class ExpansionController {

    private final ExpansionRepository expansionRepository;

    public ExpansionController(ExpansionRepository expansionRepository) {
        this.expansionRepository = expansionRepository;
    }

    @GetMapping
    public ResponseEntity<List<Expansion>> getExpansions() {
        List<Expansion> expansions = expansionRepository.findAll().stream()
                .sorted(Comparator.comparing(Expansion::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return ResponseEntity.ok(expansions);
    }
}
