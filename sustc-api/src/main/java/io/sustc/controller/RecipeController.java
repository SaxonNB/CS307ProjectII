package io.sustc.controller;

import io.sustc.dto.PageResult;
import io.sustc.dto.RecipeRecord;
import io.sustc.service.CacheService;
import io.sustc.service.RecipeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/recipes")
@CrossOrigin(origins = "*")
@Slf4j
public class RecipeController {

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private CacheService cacheService;

    @GetMapping("/{recipeId}")
    public ResponseEntity<?> getRecipeById(@PathVariable long recipeId) {
        try {
            RecipeRecord recipe = cacheService.getRecipe(recipeId, RecipeRecord.class);

            if (recipe == null) {
                recipe = recipeService.getRecipeById(recipeId);
                if (recipe == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Recipe not found"));
                }
                cacheService.setRecipe(recipeId, recipe);
            }

            return ResponseEntity.ok(recipe);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting recipe by id: {}", recipeId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchRecipes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String sort) {
        try {
            @SuppressWarnings("unchecked")
            PageResult<RecipeRecord> result = (PageResult<RecipeRecord>) cacheService.getSearchResult(
                    keyword, category, minRating, page, size, sort, PageResult.class);

            if (result == null) {
                result = recipeService.searchRecipes(keyword, category, minRating, page, size, sort);
                cacheService.setSearchResult(keyword, category, minRating, page, size, sort, result);
            }

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error searching recipes", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
