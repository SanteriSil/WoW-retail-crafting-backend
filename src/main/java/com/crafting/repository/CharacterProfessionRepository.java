package com.crafting.repository;

import com.crafting.model.CharacterProfession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CharacterProfessionRepository extends JpaRepository<CharacterProfession, Long> {
}
