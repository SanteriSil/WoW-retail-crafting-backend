package com.crafting.controller;

import com.crafting.model.Expansion;
import com.crafting.model.Profession;
import com.crafting.repository.ExpansionRepository;
import com.crafting.repository.ProfessionRepository;
import com.crafting.service.ExcelExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/export")
public class ExportController {

    private final ExcelExportService excelExportService;
    private final ProfessionRepository professionRepository;
    private final ExpansionRepository expansionRepository;

    public ExportController(
            ExcelExportService excelExportService,
            ProfessionRepository professionRepository,
            ExpansionRepository expansionRepository
    ) {
        this.excelExportService = excelExportService;
        this.professionRepository = professionRepository;
        this.expansionRepository = expansionRepository;
    }

    /**
     * GET /export/recipes/excel — ALLOWED_USER+ (enforced by SecurityConfig).
     *
     * <p>Same filter params as GET /recipes (no pagination). Capped at 500 recipes.
     * Returns an .xlsx workbook as a binary attachment.
     */
    @GetMapping("/recipes/excel")
    public void exportRecipesExcel(
            @RequestParam(required = false) Integer professionId,
            @RequestParam(required = false) Integer expansionId,
            @RequestParam(required = false) Long outputItemId,
            @RequestParam(required = false) Long ingredientItemId,
            @RequestParam(required = false) String search,
            HttpServletResponse response
    ) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + buildFilename(professionId, expansionId) + "\"");

        excelExportService.exportToExcel(
                professionId, expansionId, outputItemId, ingredientItemId, search,
                response.getOutputStream()
        );
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Produces a filename like {@code recipes-blacksmithing-midnight.xlsx} when IDs are given,
     * falling back to {@code recipes.xlsx} when neither is present.
     */
    private String buildFilename(Integer professionId, Integer expansionId) {
        StringBuilder sb = new StringBuilder("recipes");
        if (professionId != null) {
            professionRepository.findById(professionId)
                    .map(Profession::getName)
                    .ifPresent(name -> sb.append("-").append(name.toLowerCase().replace(" ", "-")));
        }
        if (expansionId != null) {
            expansionRepository.findById(expansionId)
                    .map(Expansion::getSlug)
                    .ifPresent(slug -> sb.append("-").append(slug));
        }
        sb.append(".xlsx");
        return sb.toString();
    }
}
