package com.crafting.repository;

import com.crafting.model.AllowedUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AllowedUserRepository extends JpaRepository<AllowedUser, Long> {
}
