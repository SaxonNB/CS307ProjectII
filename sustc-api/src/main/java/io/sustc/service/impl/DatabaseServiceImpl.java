package io.sustc.service.impl;

import io.sustc.dto.ReviewRecord;
import io.sustc.dto.UserRecord;
import io.sustc.dto.RecipeRecord;
import io.sustc.service.DatabaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

@Service
@Slf4j
public class DatabaseServiceImpl implements DatabaseService {

    @Autowired
    private DataSource dataSource;

    @Override
    public List<Integer> getGroupMembers() {
        return Arrays.asList(12412308, 12412310);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void importData(
            List<ReviewRecord> reviewRecords,
            List<UserRecord> userRecords,
            List<RecipeRecord> recipeRecords) {

        createTables();

        String[] deleteTables = {
                "review_likes",
                "reviews",
                "recipe_ingredients",
                "user_follows",
                "recipes",
                "users"
        };

        for (String tableName : deleteTables) {
            try {
                jdbcTemplate.update("DELETE FROM " + tableName);
            } catch (Exception e) {
                log.debug("Table {} may not exist, skipping delete: {}", tableName, e.getMessage());
            }
        }

        final int batchSize = 1000;

        Map<Long, String> authorIdToName = new HashMap<>();
        if (userRecords != null) {
            for (UserRecord u : userRecords) {
                if (u != null) {
                    authorIdToName.put(u.getAuthorId(), u.getAuthorName());
                }
            }
        }

        if (userRecords != null && !userRecords.isEmpty()) {
            String userSql = "INSERT INTO users " +
                    "(AuthorId, AuthorName, Gender, Age, Password, IsDeleted) " +
                    "VALUES (?, ?, ?, ?, ?, ?)";

            for (int start = 0; start < userRecords.size(); start += batchSize) {
                final int from = start;
                final int to = Math.min(from + batchSize, userRecords.size());
                jdbcTemplate.batchUpdate(userSql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        UserRecord u = userRecords.get(from + i);
                        ps.setLong(1, u.getAuthorId());
                        ps.setString(2, u.getAuthorName());
                        ps.setString(3, u.getGender());
                        ps.setInt(4, u.getAge());
                        ps.setString(5, u.getPassword());
                        ps.setBoolean(6, u.isDeleted());
                    }

                    @Override
                    public int getBatchSize() {
                        return to - from;
                    }
                });
            }
        }

        if (recipeRecords != null && !recipeRecords.isEmpty()) {
            String recipeSql = "INSERT INTO recipes " +
                    "(RecipeId, Name, AuthorId, AuthorName, CookTime, PrepTime, TotalTime, DatePublished, " +
                    "Description, RecipeCategory, RecipeServings, RecipeYield, IngredientTags, " +
                    "AggregatedRating, ReviewCount, " +
                    "Calories, FatContent, SaturatedFatContent, CholesterolContent, SodiumContent, " +
                    "CarbohydrateContent, FiberContent, SugarContent, ProteinContent) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            for (int start = 0; start < recipeRecords.size(); start += batchSize) {
                final int from = start;
                final int to = Math.min(from + batchSize, recipeRecords.size());
                jdbcTemplate.batchUpdate(recipeSql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        RecipeRecord r = recipeRecords.get(from + i);
                        ps.setLong(1, r.getRecipeId());
                        ps.setString(2, r.getName());
                        ps.setLong(3, r.getAuthorId());
                        ps.setString(4, authorIdToName.getOrDefault(r.getAuthorId(), ""));
                        ps.setString(5, r.getCookTime());
                        ps.setString(6, r.getPrepTime());
                        ps.setString(7, r.getTotalTime());
                        ps.setTimestamp(8, r.getDatePublished());
                        ps.setString(9, r.getDescription());
                        ps.setString(10, r.getRecipeCategory());
                        Object servings = r.getRecipeServings();
                        if (servings instanceof String) {
                            try {
                                ps.setInt(11, Integer.parseInt((String) servings));
                            } catch (NumberFormatException e) {
                                ps.setNull(11, java.sql.Types.INTEGER);
                            }
                        } else if (servings instanceof Number) {
                            ps.setInt(11, ((Number) servings).intValue());
                        } else {
                            ps.setNull(11, java.sql.Types.INTEGER);
                        }
                        ps.setString(12, r.getRecipeYield());
                        String[] parts = r.getRecipeIngredientParts();
                        String ingredientTags = (parts != null && parts.length > 0)
                                ? String.join("|", parts)
                                : null;
                        ps.setString(13, ingredientTags);
                        Object aggRating = r.getAggregatedRating();
                        ps.setObject(14, aggRating);
                        ps.setInt(15, r.getReviewCount());
                        ps.setObject(16, r.getCalories() > 0 ? r.getCalories() : null);
                        ps.setObject(17, r.getFatContent() > 0 ? r.getFatContent() : null);
                        ps.setObject(18, r.getSaturatedFatContent() > 0 ? r.getSaturatedFatContent() : null);
                        ps.setObject(19, r.getCholesterolContent() > 0 ? r.getCholesterolContent() : null);
                        ps.setObject(20, r.getSodiumContent() > 0 ? r.getSodiumContent() : null);
                        ps.setObject(21, r.getCarbohydrateContent() > 0 ? r.getCarbohydrateContent() : null);
                        ps.setObject(22, r.getFiberContent() > 0 ? r.getFiberContent() : null);
                        ps.setObject(23, r.getSugarContent() > 0 ? r.getSugarContent() : null);
                        ps.setObject(24, r.getProteinContent() > 0 ? r.getProteinContent() : null);
                    }

                    @Override
                    public int getBatchSize() {
                        return to - from;
                    }
                });
            }
        }

        if (recipeRecords != null && !recipeRecords.isEmpty()) {
            Map<Long, Set<String>> recipeIngredientsMap = new HashMap<>();
            for (RecipeRecord recipe : recipeRecords) {
                if (recipe != null && recipe.getRecipeIngredientParts() != null) {
                    long recipeId = recipe.getRecipeId();
                    Set<String> ingredients = recipeIngredientsMap.computeIfAbsent(recipeId, k -> new LinkedHashSet<>());
                    for (String ingredient : recipe.getRecipeIngredientParts()) {
                        if (ingredient != null && !ingredient.trim().isEmpty()) {
                            ingredients.add(ingredient.trim());
                        }
                    }
                }
            }

            List<Object[]> ingredientBatch = new ArrayList<>();
            for (Map.Entry<Long, Set<String>> entry : recipeIngredientsMap.entrySet()) {
                long recipeId = entry.getKey();
                for (String ingredient : entry.getValue()) {
                    ingredientBatch.add(new Object[]{recipeId, ingredient});
                }
            }

            if (!ingredientBatch.isEmpty()) {
                String ingredientSql = "INSERT INTO recipe_ingredients (RecipeId, IngredientPart) " +
                        "VALUES (?, ?) ON CONFLICT (RecipeId, IngredientPart) DO NOTHING";

                for (int start = 0; start < ingredientBatch.size(); start += batchSize) {
                    final int from = start;
                    final int to = Math.min(from + batchSize, ingredientBatch.size());
                    jdbcTemplate.batchUpdate(ingredientSql, new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            Object[] row = ingredientBatch.get(from + i);
                            ps.setLong(1, ((Number) row[0]).longValue());
                            ps.setString(2, (String) row[1]);
                        }

                        @Override
                        public int getBatchSize() {
                            return to - from;
                        }
                    });
                }
            }
        }

        Set<Long> validReviewIds = new HashSet<>();

        if (reviewRecords != null && !reviewRecords.isEmpty()) {
            List<ReviewRecord> validReviews = new ArrayList<>();
            for (ReviewRecord r : reviewRecords) {
                if (r != null) {
                    float rating = r.getRating();
                    if (rating < 0.0f) {
                        ReviewRecord modified = ReviewRecord.builder()
                                .reviewId(r.getReviewId())
                                .recipeId(r.getRecipeId())
                                .authorId(r.getAuthorId())
                                .rating(0.0f)
                                .review(r.getReview())
                                .dateSubmitted(r.getDateSubmitted())
                                .dateModified(r.getDateModified())
                                .build();
                        validReviews.add(modified);
                    } else if (rating > 5.0f) {
                        ReviewRecord modified = ReviewRecord.builder()
                                .reviewId(r.getReviewId())
                                .recipeId(r.getRecipeId())
                                .authorId(r.getAuthorId())
                                .rating(5.0f)
                                .review(r.getReview())
                                .dateSubmitted(r.getDateSubmitted())
                                .dateModified(r.getDateModified())
                                .build();
                        validReviews.add(modified);
                    } else {
                        validReviews.add(r);
                    }
                    validReviewIds.add(r.getReviewId());
                }
            }

            if (!validReviews.isEmpty()) {
                String reviewSql = "INSERT INTO reviews " +
                        "(ReviewId, RecipeId, AuthorId, AuthorName, Rating, Review, DateSubmitted, DateModified) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (ReviewId) DO NOTHING";

                for (int start = 0; start < validReviews.size(); start += batchSize) {
                    final int from = start;
                    final int to = Math.min(from + batchSize, validReviews.size());
                    jdbcTemplate.batchUpdate(reviewSql, new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            ReviewRecord r = validReviews.get(from + i);
                            ps.setLong(1, r.getReviewId());
                            ps.setLong(2, r.getRecipeId());
                            ps.setLong(3, r.getAuthorId());
                            ps.setString(4, authorIdToName.getOrDefault(r.getAuthorId(), ""));
                            float ratingFloat = r.getRating();
                            int rating = Math.round(ratingFloat);
                            if (rating < 0) rating = 0;
                            if (rating > 5) rating = 5;
                            ps.setInt(5, rating);
                            ps.setString(6, r.getReview());
                            ps.setTimestamp(7, r.getDateSubmitted());
                            ps.setTimestamp(8, r.getDateModified());
                        }

                        @Override
                        public int getBatchSize() {
                            return to - from;
                        }
                    });
                }
            }
        }

        if (reviewRecords != null && !reviewRecords.isEmpty() && !validReviewIds.isEmpty()) {
            List<Object[]> likeBatch = new ArrayList<>();
            for (ReviewRecord review : reviewRecords) {
                if (review != null && review.getLikes() != null) {
                    long reviewId = review.getReviewId();
                    if (validReviewIds.contains(reviewId)) {
                        for (long authorId : review.getLikes()) {
                            likeBatch.add(new Object[]{reviewId, authorId});
                        }
                    }
                }
            }

            if (!likeBatch.isEmpty()) {
                String likeSql = "INSERT INTO review_likes (ReviewId, AuthorId) " +
                        "VALUES (?, ?) ON CONFLICT (ReviewId, AuthorId) DO NOTHING";

                for (int start = 0; start < likeBatch.size(); start += batchSize) {
                    final int from = start;
                    final int to = Math.min(from + batchSize, likeBatch.size());
                    try {
                        jdbcTemplate.batchUpdate(likeSql, new BatchPreparedStatementSetter() {
                            @Override
                            public void setValues(PreparedStatement ps, int i) throws SQLException {
                                Object[] row = likeBatch.get(from + i);
                                ps.setLong(1, ((Number) row[0]).longValue());
                                ps.setLong(2, ((Number) row[1]).longValue());
                            }

                            @Override
                            public int getBatchSize() {
                                return to - from;
                            }
                        });
                    } catch (Exception e) {
                        log.warn("Failed to insert some review_likes (foreign key constraint): {}", e.getMessage());
                    }
                }
            }
        }

        if (userRecords != null && !userRecords.isEmpty()) {
            List<Object[]> followBatch = new ArrayList<>();
            for (UserRecord user : userRecords) {
                if (user != null) {
                    long userId = user.getAuthorId();
                    if (user.getFollowerUsers() != null) {
                        for (long followerId : user.getFollowerUsers()) {
                            if (followerId != userId) {
                                followBatch.add(new Object[]{followerId, userId});
                            }
                        }
                    }
                    if (user.getFollowingUsers() != null) {
                        for (long followingId : user.getFollowingUsers()) {
                            if (followingId != userId) {
                                followBatch.add(new Object[]{userId, followingId});
                            }
                        }
                    }
                }
            }

            if (!followBatch.isEmpty()) {
                String followSql = "INSERT INTO user_follows (FollowerId, FollowingId) " +
                        "VALUES (?, ?) ON CONFLICT (FollowerId, FollowingId) DO NOTHING";

                for (int start = 0; start < followBatch.size(); start += batchSize) {
                    final int from = start;
                    final int to = Math.min(from + batchSize, followBatch.size());
                    jdbcTemplate.batchUpdate(followSql, new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement ps, int i) throws SQLException {
                            Object[] row = followBatch.get(from + i);
                            ps.setLong(1, ((Number) row[0]).longValue());
                            ps.setLong(2, ((Number) row[1]).longValue());
                        }

                        @Override
                        public int getBatchSize() {
                            return to - from;
                        }
                    });
                }
            }
        }
    }

    private void createTables() {
        String[] dropTableSQLs = {
                "DROP TABLE IF EXISTS review_likes CASCADE",
                "DROP TABLE IF EXISTS reviews CASCADE",
                "DROP TABLE IF EXISTS recipe_ingredients CASCADE",
                "DROP TABLE IF EXISTS user_follows CASCADE",
                "DROP TABLE IF EXISTS recipes CASCADE",
                "DROP TABLE IF EXISTS users CASCADE"
        };

        for (String sql : dropTableSQLs) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.debug("Drop table error (may not exist): {}", e.getMessage());
            }
        }

        String[] createTableSQLs = {
                "CREATE TABLE IF NOT EXISTS users (" +
                        "    AuthorId BIGINT PRIMARY KEY, " +
                        "    AuthorName TEXT NOT NULL, " +
                        "    Gender VARCHAR(10) CHECK (Gender IN ('Male', 'Female')), " +
                        "    Age INTEGER CHECK (Age > 0), " +
                        "    Password TEXT, " +
                        "    IsDeleted BOOLEAN DEFAULT FALSE" +
                        ")",

                "CREATE TABLE IF NOT EXISTS recipes (" +
                        "    RecipeId BIGINT PRIMARY KEY, " +
                        "    Name TEXT NOT NULL, " +
                        "    AuthorId BIGINT NOT NULL, " +
                        "    AuthorName TEXT NOT NULL, " +
                        "    CookTime TEXT, " +
                        "    PrepTime TEXT, " +
                        "    TotalTime TEXT, " +
                        "    DatePublished TIMESTAMP, " +
                        "    Description TEXT, " +
                        "    RecipeCategory TEXT, " +
                        "    RecipeServings INTEGER, " +
                        "    RecipeYield TEXT, " +
                        "    IngredientTags TEXT, " +
                        "    AggregatedRating DECIMAL(3,2) CHECK (AggregatedRating >= 0 AND AggregatedRating <= 5), " +
                        "    ReviewCount INTEGER DEFAULT 0 CHECK (ReviewCount >= 0), " +
                        "    Calories NUMERIC(10, 2), " +
                        "    FatContent NUMERIC(10, 2), " +
                        "    SaturatedFatContent NUMERIC(10, 2), " +
                        "    CholesterolContent NUMERIC(10, 2), " +
                        "    SodiumContent NUMERIC(10, 2), " +
                        "    CarbohydrateContent NUMERIC(10, 2), " +
                        "    FiberContent NUMERIC(10, 2), " +
                        "    SugarContent NUMERIC(10, 2), " +
                        "    ProteinContent NUMERIC(10, 2), " +
                        "    FOREIGN KEY (AuthorId) REFERENCES users(AuthorId)" +
                        ")",

                "CREATE TABLE IF NOT EXISTS reviews (" +
                        "    ReviewId BIGINT PRIMARY KEY, " +
                        "    RecipeId BIGINT NOT NULL, " +
                        "    AuthorId BIGINT NOT NULL, " +
                        "    AuthorName TEXT NOT NULL, " +
                        "    Rating INTEGER NOT NULL CHECK (Rating >= 0 AND Rating <= 5), " +
                        "    Review TEXT, " +
                        "    DateSubmitted TIMESTAMP, " +
                        "    DateModified TIMESTAMP, " +
                        "    FOREIGN KEY (RecipeId) REFERENCES recipes(RecipeId) ON DELETE CASCADE, " +
                        "    FOREIGN KEY (AuthorId) REFERENCES users(AuthorId)" +
                        ")",

                "CREATE TABLE IF NOT EXISTS recipe_ingredients (" +
                        "    RecipeId BIGINT, " +
                        "    IngredientPart TEXT, " +
                        "    PRIMARY KEY (RecipeId, IngredientPart), " +
                        "    FOREIGN KEY (RecipeId) REFERENCES recipes(RecipeId) ON DELETE CASCADE" +
                        ")",

                "CREATE TABLE IF NOT EXISTS review_likes (" +
                        "    ReviewId BIGINT, " +
                        "    AuthorId BIGINT, " +
                        "    PRIMARY KEY (ReviewId, AuthorId), " +
                        "    FOREIGN KEY (ReviewId) REFERENCES reviews(ReviewId) ON DELETE CASCADE, " +
                        "    FOREIGN KEY (AuthorId) REFERENCES users(AuthorId)" +
                        ")",

                "CREATE TABLE IF NOT EXISTS user_follows (" +
                        "    FollowerId BIGINT, " +
                        "    FollowingId BIGINT, " +
                        "    PRIMARY KEY (FollowerId, FollowingId), " +
                        "    FOREIGN KEY (FollowerId) REFERENCES users(AuthorId), " +
                        "    FOREIGN KEY (FollowingId) REFERENCES users(AuthorId), " +
                        "    CHECK (FollowerId != FollowingId)" +
                        ")"
        };

        for (String sql : createTableSQLs) {
            try {
                jdbcTemplate.execute(sql);
                log.debug("Table created successfully");
            } catch (Exception e) {
                log.warn("Table creation error (may already exist): {}", e.getMessage());
            }
        }

        try {
            createIndexes();
        } catch (Exception e) {
            log.warn("Index creation failed, but tables are created: {}", e.getMessage());
        }
    }

    private void createIndexes() {
        String[] createIndexSQLs = {
                "CREATE INDEX IF NOT EXISTS idx_users_authorname ON users(AuthorName)",
                "CREATE INDEX IF NOT EXISTS idx_users_isdeleted ON users(IsDeleted) WHERE IsDeleted = FALSE",

                "CREATE INDEX IF NOT EXISTS idx_recipes_authorid ON recipes(AuthorId)",
                "CREATE INDEX IF NOT EXISTS idx_recipes_category ON recipes(RecipeCategory)",
                "CREATE INDEX IF NOT EXISTS idx_recipes_datepublished ON recipes(DatePublished DESC NULLS LAST)",
                "CREATE INDEX IF NOT EXISTS idx_recipes_rating ON recipes(AggregatedRating DESC NULLS LAST)",
                "CREATE INDEX IF NOT EXISTS idx_recipes_feed ON recipes(AuthorId, RecipeCategory, DatePublished DESC NULLS LAST)",
                "CREATE INDEX IF NOT EXISTS idx_recipes_name_lower ON recipes(LOWER(Name))",
                "CREATE INDEX IF NOT EXISTS idx_recipes_description_lower ON recipes(LOWER(Description))",
                "CREATE INDEX IF NOT EXISTS idx_recipes_calories ON recipes(Calories ASC NULLS LAST)",

                "CREATE INDEX IF NOT EXISTS idx_reviews_recipeid ON reviews(RecipeId)",
                "CREATE INDEX IF NOT EXISTS idx_reviews_recipe_date ON reviews(RecipeId, DateModified DESC)",

                "CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipeid ON recipe_ingredients(RecipeId)",

                "CREATE INDEX IF NOT EXISTS idx_review_likes_reviewid ON review_likes(ReviewId)",

                "CREATE INDEX IF NOT EXISTS idx_user_follows_followerid ON user_follows(FollowerId)",
                "CREATE INDEX IF NOT EXISTS idx_user_follows_followingid ON user_follows(FollowingId)"
        };

        for (String sql : createIndexSQLs) {
            try {
                jdbcTemplate.execute(sql);
            } catch (Exception e) {
                log.debug("Index creation skipped (may already exist): {}", e.getMessage());
            }
        }
    }

    @Override
    public void drop() {
        String sql = "DO $$\n" +
                "DECLARE\n" +
                "    tables CURSOR FOR\n" +
                "        SELECT tablename\n" +
                "        FROM pg_tables\n" +
                "        WHERE schemaname = 'public';\n" +
                "BEGIN\n" +
                "    FOR t IN tables\n" +
                "    LOOP\n" +
                "        EXECUTE 'DROP TABLE IF EXISTS ' || QUOTE_IDENT(t.tablename) || ' CASCADE;';\n" +
                "    END LOOP;\n" +
                "END $$;\n";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer sum(int a, int b) {
        String sql = "SELECT ?+?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, a);
            stmt.setInt(2, b);
            log.info("SQL: {}", stmt);

            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
