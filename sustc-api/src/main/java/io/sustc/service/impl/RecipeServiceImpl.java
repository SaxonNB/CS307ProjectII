package io.sustc.service.impl;

import io.sustc.dto.*;
import io.sustc.service.RecipeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@Slf4j
public class RecipeServiceImpl implements RecipeService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long requireActiveUser(AuthInfo auth) {
        if (auth == null) {
            throw new SecurityException("auth is null");
        }
        long userId = auth.getAuthorId();
        try {
            Boolean isDeleted = jdbcTemplate.queryForObject(
                    "SELECT IsDeleted FROM users WHERE AuthorId = ?",
                    Boolean.class,
                    userId
            );
            if (isDeleted == null || isDeleted) {
                throw new SecurityException("user is inactive");
            }
            return userId;
        } catch (EmptyResultDataAccessException e) {
            throw new SecurityException("user does not exist", e);
        }
    }

    @Override
    public String getNameFromID(long id) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT Name FROM recipes WHERE RecipeId = ?",
                    String.class,
                    id
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public RecipeRecord getRecipeById(long recipeId) {
        if (recipeId <= 0) {
            throw new IllegalArgumentException("recipeId must be positive");
        }
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT * FROM recipes WHERE RecipeId = ?",
                    recipeId
            );

            RecipeRecord recipe = new RecipeRecord();
            recipe.setRecipeId(((Number) row.get("recipeid")).longValue());
            recipe.setName((String) row.get("name"));
            recipe.setAuthorId(((Number) row.get("authorid")).longValue());
            recipe.setAuthorName((String) row.get("authorname"));
            recipe.setCookTime((String) row.get("cooktime"));
            recipe.setPrepTime((String) row.get("preptime"));
            recipe.setTotalTime((String) row.get("totaltime"));
            recipe.setDatePublished((Timestamp) row.get("datepublished"));
            recipe.setDescription((String) row.get("description"));
            recipe.setRecipeCategory((String) row.get("recipecategory"));
            Object aggObj = row.get("aggregatedrating");
            recipe.setAggregatedRating(aggObj == null ? 0 : ((Number) aggObj).floatValue());
            recipe.setReviewCount(((Number) row.get("reviewcount")).intValue());
            Object caloriesObj = row.get("calories");
            recipe.setCalories(caloriesObj == null ? 0.0f : ((Number) caloriesObj).floatValue());
            Object fatObj = row.get("fatcontent");
            recipe.setFatContent(fatObj == null ? 0.0f : ((Number) fatObj).floatValue());
            Object satFatObj = row.get("saturatedfatcontent");
            recipe.setSaturatedFatContent(satFatObj == null ? 0.0f : ((Number) satFatObj).floatValue());
            Object cholObj = row.get("cholesterolcontent");
            recipe.setCholesterolContent(cholObj == null ? 0.0f : ((Number) cholObj).floatValue());
            Object sodiumObj = row.get("sodiumcontent");
            recipe.setSodiumContent(sodiumObj == null ? 0.0f : ((Number) sodiumObj).floatValue());
            Object carbObj = row.get("carbohydratecontent");
            recipe.setCarbohydrateContent(carbObj == null ? 0.0f : ((Number) carbObj).floatValue());
            Object fiberObj = row.get("fibercontent");
            recipe.setFiberContent(fiberObj == null ? 0.0f : ((Number) fiberObj).floatValue());
            Object sugarObj = row.get("sugarcontent");
            recipe.setSugarContent(sugarObj == null ? 0.0f : ((Number) sugarObj).floatValue());
            Object proteinObj = row.get("proteincontent");
            recipe.setProteinContent(proteinObj == null ? 0.0f : ((Number) proteinObj).floatValue());
            Object servingsObj = row.get("recipeservings");
            recipe.setRecipeServings(servingsObj == null ? 0 : Integer.parseInt(servingsObj.toString()));
            recipe.setRecipeYield((String) row.get("recipeyield"));

            String ingredientTags = (String) row.get("ingredienttags");
            if (ingredientTags != null && !ingredientTags.isEmpty()) {
                recipe.setRecipeIngredientParts(ingredientTags.split("\\|", -1));
            } else {
                recipe.setRecipeIngredientParts(new String[0]);
            }

            return recipe;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public PageResult<RecipeRecord> searchRecipes(String keyword, String category, Double minRating,
                                                  Integer page, Integer size, String sort) {
        if (page == null || page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (size == null || size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }

        List<Object> params = new ArrayList<>();
        StringBuilder whereClause = new StringBuilder("WHERE 1=1");

        if (keyword != null && !keyword.trim().isEmpty()) {
            whereClause.append(" AND (LOWER(r.Name) LIKE ? OR LOWER(r.Description) LIKE ?)");
            String keywordPattern = "%" + keyword.toLowerCase() + "%";
            params.add(keywordPattern);
            params.add(keywordPattern);
        }

        if (category != null && !category.trim().isEmpty()) {
            whereClause.append(" AND r.RecipeCategory = ?");
            params.add(category);
        }

        if (minRating != null) {
            whereClause.append(" AND r.AggregatedRating >= ?");
            params.add(minRating);
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recipes r " + whereClause.toString(),
                params.toArray(),
                Long.class
        );
        if (total == null) total = 0L;

        String orderBy = "ORDER BY r.RecipeId DESC";
        if (sort != null) {
            switch (sort) {
                case "rating_desc":
                    orderBy = "ORDER BY r.AggregatedRating DESC NULLS LAST, r.RecipeId DESC";
                    break;
                case "date_desc":
                    orderBy = "ORDER BY r.DatePublished DESC NULLS LAST, r.RecipeId DESC";
                    break;
                case "calories_asc":
                    orderBy = "ORDER BY r.Calories ASC NULLS LAST, r.RecipeId ASC";
                    break;
            }
        }

        int offset = (page - 1) * size;
        String sql = "SELECT * FROM recipes r " + whereClause.toString() + " " + orderBy + " LIMIT ? OFFSET ?";
        params.add(size);
        params.add(offset);

        List<RecipeRecord> recipes = jdbcTemplate.query(sql, params.toArray(), (rs, rowNum) -> {
            RecipeRecord r = new RecipeRecord();
            r.setRecipeId(rs.getLong("RecipeId"));
            r.setName(rs.getString("Name"));
            r.setAuthorId(rs.getLong("AuthorId"));
            r.setAuthorName(rs.getString("AuthorName"));
            r.setCookTime(rs.getString("CookTime"));
            r.setPrepTime(rs.getString("PrepTime"));
            r.setTotalTime(rs.getString("TotalTime"));
            r.setDatePublished(rs.getTimestamp("DatePublished"));
            r.setDescription(rs.getString("Description"));
            r.setRecipeCategory(rs.getString("RecipeCategory"));
            Object aggObj = rs.getObject("AggregatedRating");
            r.setAggregatedRating(aggObj == null ? 0 : ((Number) aggObj).floatValue());
            r.setReviewCount(rs.getInt("ReviewCount"));
            Object caloriesObj = rs.getObject("Calories");
            r.setCalories(caloriesObj == null ? 0.0f : ((Number) caloriesObj).floatValue());
            Object fatObj = rs.getObject("FatContent");
            r.setFatContent(fatObj == null ? 0.0f : ((Number) fatObj).floatValue());
            Object satFatObj = rs.getObject("SaturatedFatContent");
            r.setSaturatedFatContent(satFatObj == null ? 0.0f : ((Number) satFatObj).floatValue());
            Object cholObj = rs.getObject("CholesterolContent");
            r.setCholesterolContent(cholObj == null ? 0.0f : ((Number) cholObj).floatValue());
            Object sodiumObj = rs.getObject("SodiumContent");
            r.setSodiumContent(sodiumObj == null ? 0.0f : ((Number) sodiumObj).floatValue());
            Object carbObj = rs.getObject("CarbohydrateContent");
            r.setCarbohydrateContent(carbObj == null ? 0.0f : ((Number) carbObj).floatValue());
            Object fiberObj = rs.getObject("FiberContent");
            r.setFiberContent(fiberObj == null ? 0.0f : ((Number) fiberObj).floatValue());
            Object sugarObj = rs.getObject("SugarContent");
            r.setSugarContent(sugarObj == null ? 0.0f : ((Number) sugarObj).floatValue());
            Object proteinObj = rs.getObject("ProteinContent");
            r.setProteinContent(proteinObj == null ? 0.0f : ((Number) proteinObj).floatValue());
            Object servingsObj = rs.getObject("RecipeServings");
            r.setRecipeServings(servingsObj == null ? 0 : Integer.parseInt(servingsObj.toString()));
            r.setRecipeYield(rs.getString("RecipeYield"));

            String ingredientTags = rs.getString("IngredientTags");
            r.setRecipeIngredientParts(
                    ingredientTags != null ? ingredientTags.split("\\|", -1) : new String[0]
            );

            return r;
        });

        PageResult<RecipeRecord> result = new PageResult<>();
        result.setItems(recipes);
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        return result;
    }

    @Override
    @Transactional
    public long createRecipe(RecipeRecord dto, AuthInfo auth) {
        long authorId = requireActiveUser(auth);

        if (dto == null || dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("recipe name cannot be null or empty");
        }

        String authorName = jdbcTemplate.queryForObject(
                "SELECT AuthorName FROM users WHERE AuthorId = ?",
                String.class,
                authorId
        );

        String ingredientTags = null;
        if (dto.getRecipeIngredientParts() != null && dto.getRecipeIngredientParts().length > 0) {
            ingredientTags = String.join("|", dto.getRecipeIngredientParts());
        }

        Long newRecipeId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(RecipeId), 0) + 1 FROM recipes",
                Long.class
        );

        jdbcTemplate.update(
                "INSERT INTO recipes (RecipeId, Name, AuthorId, AuthorName, CookTime, PrepTime, TotalTime, " +
                        "DatePublished, Description, RecipeCategory, RecipeServings, RecipeYield, IngredientTags, " +
                        "AggregatedRating, ReviewCount, " +
                        "Calories, FatContent, SaturatedFatContent, CholesterolContent, SodiumContent, " +
                        "CarbohydrateContent, FiberContent, SugarContent, ProteinContent) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                newRecipeId,
                dto.getName().trim(),
                authorId,
                authorName,
                dto.getCookTime(),
                dto.getPrepTime(),
                dto.getTotalTime(),
                dto.getDatePublished() != null ? dto.getDatePublished() : new Timestamp(System.currentTimeMillis()),
                dto.getDescription(),
                dto.getRecipeCategory(),
                dto.getRecipeServings(),
                dto.getRecipeYield(),
                ingredientTags,
                dto.getAggregatedRating(),
                dto.getReviewCount(),
                dto.getCalories() > 0 ? dto.getCalories() : null,
                dto.getFatContent() > 0 ? dto.getFatContent() : null,
                dto.getSaturatedFatContent() > 0 ? dto.getSaturatedFatContent() : null,
                dto.getCholesterolContent() > 0 ? dto.getCholesterolContent() : null,
                dto.getSodiumContent() > 0 ? dto.getSodiumContent() : null,
                dto.getCarbohydrateContent() > 0 ? dto.getCarbohydrateContent() : null,
                dto.getFiberContent() > 0 ? dto.getFiberContent() : null,
                dto.getSugarContent() > 0 ? dto.getSugarContent() : null,
                dto.getProteinContent() > 0 ? dto.getProteinContent() : null
        );

        if (dto.getRecipeIngredientParts() != null && dto.getRecipeIngredientParts().length > 0) {
            Set<String> uniqueIngredients = new HashSet<>();
            for (String ing : dto.getRecipeIngredientParts()) {
                if (ing != null && !ing.trim().isEmpty()) {
                    uniqueIngredients.add(ing.trim());
                }
            }
            for (String ing : uniqueIngredients) {
                jdbcTemplate.update(
                        "INSERT INTO recipe_ingredients (RecipeId, IngredientPart) VALUES (?, ?) " +
                                "ON CONFLICT (RecipeId, IngredientPart) DO NOTHING",
                        newRecipeId,
                        ing
                );
            }
        }

        return newRecipeId;
    }

    @Override
    @Transactional
    public void deleteRecipe(long recipeId, AuthInfo auth) {
        long operatorId = requireActiveUser(auth);

        Long authorId = jdbcTemplate.queryForObject(
                "SELECT AuthorId FROM recipes WHERE RecipeId = ?",
                Long.class,
                recipeId
        );
        if (authorId == null) {
            throw new IllegalArgumentException("recipe does not exist");
        }

        if (authorId != operatorId) {
            throw new SecurityException("only recipe author can delete recipe");
        }

        jdbcTemplate.update("DELETE FROM recipes WHERE RecipeId = ?", recipeId);
    }

    @Override
    @Transactional
    public void updateTimes(AuthInfo auth, long recipeId, String cookTimeIso, String prepTimeIso) {
        long operatorId = requireActiveUser(auth);

        Long authorId = jdbcTemplate.queryForObject(
                "SELECT AuthorId FROM recipes WHERE RecipeId = ?",
                Long.class,
                recipeId
        );
        if (authorId == null) {
            throw new IllegalArgumentException("recipe does not exist");
        }
        if (authorId != operatorId) {
            throw new SecurityException("only recipe author can update times");
        }

        Duration cookDuration = null;
        Duration prepDuration = null;
        if (cookTimeIso != null) {
            try {
                cookDuration = Duration.parse(cookTimeIso);
                if (cookDuration.isNegative()) {
                    throw new IllegalArgumentException("cookTime cannot be negative");
                }
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("invalid cookTime ISO 8601 format", e);
            }
        }
        if (prepTimeIso != null) {
            try {
                prepDuration = Duration.parse(prepTimeIso);
                if (prepDuration.isNegative()) {
                    throw new IllegalArgumentException("prepTime cannot be negative");
                }
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("invalid prepTime ISO 8601 format", e);
            }
        }

        Duration totalDuration = null;
        if (cookDuration != null || prepDuration != null) {
            long cookSeconds = cookDuration != null ? cookDuration.getSeconds() : 0;
            long prepSeconds = prepDuration != null ? prepDuration.getSeconds() : 0;
            totalDuration = Duration.ofSeconds(cookSeconds + prepSeconds);
        }

        if (cookTimeIso != null) {
            jdbcTemplate.update("UPDATE recipes SET CookTime = ? WHERE RecipeId = ?", cookTimeIso, recipeId);
        }
        if (prepTimeIso != null) {
            jdbcTemplate.update("UPDATE recipes SET PrepTime = ? WHERE RecipeId = ?", prepTimeIso, recipeId);
        }
        if (totalDuration != null) {
            long totalSeconds = totalDuration.getSeconds();
            String totalIso = "PT" + totalSeconds + "S";
            if (totalSeconds >= 3600) {
                long hours = totalSeconds / 3600;
                long minutes = (totalSeconds % 3600) / 60;
                long secs = totalSeconds % 60;
                totalIso = "PT" + hours + "H" + (minutes > 0 ? minutes + "M" : "") + (secs > 0 ? secs + "S" : "");
            } else if (totalSeconds >= 60) {
                long minutes = totalSeconds / 60;
                long secs = totalSeconds % 60;
                totalIso = "PT" + minutes + "M" + (secs > 0 ? secs + "S" : "");
            }
            jdbcTemplate.update("UPDATE recipes SET TotalTime = ? WHERE RecipeId = ?", totalIso, recipeId);
        }
    }

    @Override
    public Map<String, Object> getClosestCaloriePair() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recipes WHERE Calories IS NOT NULL",
                Long.class
        );
        if (count == null || count < 2) {
            return null;
        }

        String sql = "WITH ranked_pairs AS (" +
                "    SELECT " +
                "        n1.RecipeId AS RecipeA, " +
                "        n2.RecipeId AS RecipeB, " +
                "        n1.Calories AS CaloriesA, " +
                "        n2.Calories AS CaloriesB, " +
                "        ABS(n1.Calories - n2.Calories) AS Difference " +
                "    FROM recipes n1 " +
                "    JOIN recipes n2 ON n1.RecipeId < n2.RecipeId " +
                "    WHERE n1.Calories IS NOT NULL AND n2.Calories IS NOT NULL " +
                ") " +
                "SELECT RecipeA, RecipeB, CaloriesA, CaloriesB, Difference " +
                "FROM ranked_pairs " +
                "WHERE Difference = (SELECT MIN(Difference) FROM ranked_pairs) " +
                "ORDER BY RecipeA ASC, RecipeB ASC " +
                "LIMIT 1";

        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(sql);
            if (row.isEmpty()) {
                return null;
            }
            Map<String, Object> result = new HashMap<>();
            Object recipeAObj = row.get("recipea");
            Object recipeBObj = row.get("recipeb");
            Object caloriesAObj = row.get("caloriesa");
            Object caloriesBObj = row.get("caloriesb");
            Object diffObj = row.get("difference");

            result.put("RecipeA", recipeAObj instanceof Number ? ((Number) recipeAObj).longValue() : recipeAObj);
            result.put("RecipeB", recipeBObj instanceof Number ? ((Number) recipeBObj).longValue() : recipeBObj);
            result.put("CaloriesA", caloriesAObj instanceof Number ? ((Number) caloriesAObj).doubleValue() : caloriesAObj);
            result.put("CaloriesB", caloriesBObj instanceof Number ? ((Number) caloriesBObj).doubleValue() : caloriesBObj);
            result.put("Difference", diffObj instanceof Number ? ((Number) diffObj).doubleValue() : diffObj);
            return result;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> getTop3MostComplexRecipesByIngredients() {
        String sql = "SELECT r.RecipeId, r.Name, COUNT(ri.IngredientPart) AS IngredientCount " +
                "FROM recipes r " +
                "JOIN recipe_ingredients ri ON r.RecipeId = ri.RecipeId " +
                "GROUP BY r.RecipeId, r.Name " +
                "ORDER BY IngredientCount DESC, r.RecipeId ASC " +
                "LIMIT 3";

        List<Map<String, Object>> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("RecipeId", rs.getLong("RecipeId"));
            map.put("Name", rs.getString("Name"));
            map.put("IngredientCount", rs.getInt("IngredientCount"));
            return map;
        });

        return results;
    }
}
