package com.crafting.controller;

import com.crafting.model.Profession;
import com.crafting.repository.ProfessionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/professions")
public class ProfessionController {

    private final ProfessionRepository professionRepository;

    public ProfessionController(ProfessionRepository professionRepository) {
        this.professionRepository = professionRepository;
    }

    @GetMapping
    public ResponseEntity<List<Profession>> getAllProfessions() {
        List<Profession> professions = professionRepository.findAll()
            .stream()
            .sorted(Comparator.comparing(Profession::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        return ResponseEntity.ok(professions);
    }
}
