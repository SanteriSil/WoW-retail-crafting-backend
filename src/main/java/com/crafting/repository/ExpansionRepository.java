package com.crafting.repository;

import com.crafting.model.Expansion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpansionRepository extends JpaRepository<Expansion, Integer> {
    Optional<Expansion> findBySlug(String slug);
}
