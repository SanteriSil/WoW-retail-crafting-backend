package com.crafting.service;

import com.crafting.model.dto.RecipeDTO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds an .xlsx workbook for the recipe export endpoint.
 *
 * <p>Values are pre-calculated (no Excel formulas). Column headers document
 * the formula used where applicable (e.g., "Revenue (Price×Qty×0.95)").
 * Capped at 500 recipes per export (§12.5 of PLAN.md).
 */
@Service
public class ExcelExportService {

    private final RecipeService recipeService;

    public ExcelExportService(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    /**
     * Writes the workbook to {@code out}. Caller is responsible for flushing / closing the stream.
     */
    public void exportToExcel(
            Integer professionId,
            Integer expansionId,
            Long outputItemId,
            Long ingredientItemId,
            String search,
            OutputStream out
    ) throws IOException {

        List<RecipeDTO> recipes = recipeService.getRecipesForExport(
                professionId, expansionId, outputItemId, ingredientItemId, search);

        int maxIngredients = recipes.stream()
                .mapToInt(r -> r.ingredients() == null ? 0 : r.ingredients().size())
                .max()
                .orElse(0);

        // SXSSFWorkbook flushes rows to a temp file after every 100 rows to keep heap low.
        try (SXSSFWorkbook wb = new SXSSFWorkbook(100)) {
            Sheet sheet = wb.createSheet("Recipes");
            writeHeaderRow(sheet, maxIngredients);

            int rowNum = 1;
            for (RecipeDTO recipe : recipes) {
                writeDataRow(sheet.createRow(rowNum++), recipe, maxIngredients);
            }

            wb.write(out);
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void writeHeaderRow(Sheet sheet, int maxIngredients) {
        Row header = sheet.createRow(0);
        int col = 0;
        header.createCell(col++).setCellValue("Recipe Name");
        header.createCell(col++).setCellValue("Output Item");
        header.createCell(col++).setCellValue("Output Qty");
        header.createCell(col++).setCellValue("Output Price (copper)");
        header.createCell(col++).setCellValue("Revenue (Price×Qty×0.95)");
        for (int i = 1; i <= maxIngredients; i++) {
            header.createCell(col++).setCellValue("Ingredient " + i + " Name");
            header.createCell(col++).setCellValue("Ingredient " + i + " Qty");
            header.createCell(col++).setCellValue("Ingredient " + i + " Unit Price");
            header.createCell(col++).setCellValue("Ingredient " + i + " Cost (Qty×Price)");
        }
        header.createCell(col++).setCellValue("Total Ingredient Cost");
        header.createCell(col++).setCellValue("Estimated Profit");
        header.createCell(col).setCellValue("Optional Ingredients");
    }

    private void writeDataRow(Row row, RecipeDTO recipe, int maxIngredients) {
        int col = 0;

        row.createCell(col++).setCellValue(recipe.name());
        row.createCell(col++).setCellValue(recipe.outputItem() != null ? recipe.outputItem().name() : "");
        row.createCell(col++).setCellValue(recipe.outputQuantity() != null ? recipe.outputQuantity() : 1f);

        long outPrice = recipe.outputItem() != null && recipe.outputItem().currentPrice() != null
                ? recipe.outputItem().currentPrice() : 0L;
        row.createCell(col++).setCellValue(outPrice);

        long revenue = recipe.profitEstimate() != null ? recipe.profitEstimate().outputRevenue() : 0L;
        row.createCell(col++).setCellValue(revenue);

        List<RecipeDTO.IngredientView> ingredients =
                recipe.ingredients() != null ? recipe.ingredients() : List.of();
        for (int i = 0; i < maxIngredients; i++) {
            if (i < ingredients.size()) {
                RecipeDTO.IngredientView ing = ingredients.get(i);
                row.createCell(col++).setCellValue(ing.item() != null ? ing.item().name() : "");
                int qty = ing.quantity() != null ? ing.quantity() : 0;
                row.createCell(col++).setCellValue(qty);
                long unitPrice = ing.item() != null && ing.item().currentPrice() != null
                        ? ing.item().currentPrice() : 0L;
                row.createCell(col++).setCellValue(unitPrice);
                row.createCell(col++).setCellValue(unitPrice * qty);
            } else {
                col += 4; // leave cells blank for unused ingredient slots
            }
        }

        long totalCost = recipe.profitEstimate() != null ? recipe.profitEstimate().ingredientCost() : 0L;
        row.createCell(col++).setCellValue(totalCost);

        long profit = recipe.profitEstimate() != null ? recipe.profitEstimate().profit() : 0L;
        row.createCell(col++).setCellValue(profit);

        String optional = "";
        if (recipe.optionalIngredientGroups() != null) {
            optional = recipe.optionalIngredientGroups().stream()
                    .filter(g -> g.options() != null)
                    .flatMap(g -> g.options().stream())
                    .filter(o -> o.item() != null)
                    .map(o -> o.item().name())
                    .collect(Collectors.joining(", "));
        }
        row.createCell(col).setCellValue(optional);
    }
}
