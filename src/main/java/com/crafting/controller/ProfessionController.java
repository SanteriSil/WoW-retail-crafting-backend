package com.crafting.controller;

import com.crafting.model.Profession;
import com.crafting.repository.ProfessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.crafting.cache.CachedResult;

import java.util.Comparator;
import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/professions")
public class ProfessionController {

    private final ProfessionRepository professionRepository;
    private final CachedResult<List<Profession>> professionCache;

    public ProfessionController(ProfessionRepository professionRepository,
                                CachedResult<List<Profession>> professionCache) {
        this.professionRepository = professionRepository;
        this.professionCache = professionCache;
    }

    @GetMapping
    public ResponseEntity<List<Profession>> getAllProfessions() {
        List<Profession> professions = professionCache.get(() ->
            professionRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Profession::getName, String.CASE_INSENSITIVE_ORDER))
                .toList()
        );
        return ResponseEntity.ok(professions);
    }
}
