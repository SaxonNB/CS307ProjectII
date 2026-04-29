package io.sustc.controller;

import io.sustc.dto.PageResult;
import io.sustc.dto.ReviewRecord;
import io.sustc.service.CacheService;
import io.sustc.service.ReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@CrossOrigin(origins = "*")
@Slf4j
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private CacheService cacheService;

    @GetMapping("/recipe/{recipeId}")
    public ResponseEntity<?> listByRecipe(
            @PathVariable long recipeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        try {
            @SuppressWarnings("unchecked")
            PageResult<ReviewRecord> result = (PageResult<ReviewRecord>) cacheService.getReviews(recipeId, page, size, sort, PageResult.class);

            if (result == null) {
                result = reviewService.listByRecipe(recipeId, page, size, sort);
                cacheService.setReviews(recipeId, page, size, sort, result);
            }

            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error getting reviews for recipe: {}", recipeId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
