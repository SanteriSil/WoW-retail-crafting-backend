package com.crafting.repository;

import com.crafting.model.PriceSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PriceSubmissionRepository extends JpaRepository<PriceSubmission, Long>, JpaSpecificationExecutor<PriceSubmission> {
}
