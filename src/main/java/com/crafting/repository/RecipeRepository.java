package com.crafting.repository;

import com.crafting.model.Recipe;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long>{

    Optional<Recipe> findByIdAndDeletedFalse(Long id);

    Optional<Recipe> findByWowheadSpellId(Long wowheadSpellId);

    boolean existsByWowheadSpellId(Long wowheadSpellId);

    @Query("SELECT r.wowheadSpellId FROM Recipe r WHERE r.deleted = false AND r.wowheadSpellId IS NOT NULL" +
           " AND (:expansionId IS NULL OR r.expansion.id = :expansionId)")
    List<Long> findActiveSpellIds(@Param("expansionId") Integer expansionId);

    @Query(
	    value = """
		    SELECT DISTINCT r
		    FROM Recipe r
		    LEFT JOIN r.ingredients ri
		    WHERE r.deleted = false
		      AND (:professionId IS NULL OR r.profession.id = :professionId)
		      AND (:expansionId IS NULL OR r.expansion.id = :expansionId)
		      AND (:outputItemId IS NULL OR r.outputItem.id = :outputItemId)
		      AND (:ingredientItemId IS NULL OR ri.item.id = :ingredientItemId)
		      AND (
		            :search = ''
		            OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))
		            OR LOWER(r.outputItem.name) LIKE LOWER(CONCAT('%', :search, '%'))
		      )
		    """,
	    countQuery = """
		    SELECT COUNT(DISTINCT r.id)
		    FROM Recipe r
		    LEFT JOIN r.ingredients ri
		    WHERE r.deleted = false
		      AND (:professionId IS NULL OR r.profession.id = :professionId)
		      AND (:expansionId IS NULL OR r.expansion.id = :expansionId)
		      AND (:outputItemId IS NULL OR r.outputItem.id = :outputItemId)
		      AND (:ingredientItemId IS NULL OR ri.item.id = :ingredientItemId)
		      AND (
		            :search = ''
		            OR LOWER(r.name) LIKE LOWER(CONCAT('%', :search, '%'))
		            OR LOWER(r.outputItem.name) LIKE LOWER(CONCAT('%', :search, '%'))
		      )
		    """
    )
    Page<Recipe> findActiveRecipes(
	    @Param("professionId") Integer professionId,
	    @Param("expansionId") Integer expansionId,
	    @Param("outputItemId") Long outputItemId,
	    @Param("ingredientItemId") Long ingredientItemId,
	    @Param("search") String search,
	    Pageable pageable
    );
}
