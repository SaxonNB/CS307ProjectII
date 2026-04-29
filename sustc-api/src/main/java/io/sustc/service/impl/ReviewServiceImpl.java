package io.sustc.service.impl;

import io.sustc.dto.AuthInfo;
import io.sustc.dto.PageResult;
import io.sustc.dto.RecipeRecord;
import io.sustc.dto.ReviewRecord;
import io.sustc.service.RecipeService;
import io.sustc.service.ReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RecipeService recipeService;

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

    private long requireActiveUserWithPassword(AuthInfo auth) {
        if (auth == null) {
            throw new SecurityException("auth is null");
        }
        long userId = auth.getAuthorId();
        String password = auth.getPassword();
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT Password, IsDeleted FROM users WHERE AuthorId = ?",
                    userId
            );
            Map<String, Object> lowerCaseRow = new HashMap<>();
            row.forEach((k, v) -> lowerCaseRow.put(k.toLowerCase(), v));

            Boolean isDeleted = (Boolean) lowerCaseRow.get("isdeleted");
            if (isDeleted == null || isDeleted) {
                throw new SecurityException("user is inactive");
            }
            Object storedPwdObj = lowerCaseRow.get("password");
            String storedPwd = storedPwdObj == null ? null : storedPwdObj.toString();
            if (password == null || !password.equals(storedPwd)) {
                throw new SecurityException("password mismatch");
            }
            return userId;
        } catch (EmptyResultDataAccessException e) {
            throw new SecurityException("user does not exist", e);
        }
    }

    private void updateRecipeAggregatedRating(long recipeId) {
        Map<String, Object> stats = jdbcTemplate.queryForMap(
                "SELECT " +
                        "COUNT(CASE WHEN Rating > 0 THEN 1 ELSE NULL END) AS ReviewCount, " +
                        "ROUND(AVG(CASE WHEN Rating > 0 THEN Rating ELSE NULL END)::numeric, 2) AS AvgRating " +
                        "FROM reviews WHERE RecipeId = ?",
                recipeId
        );

        Integer reviewCount = ((Number) stats.get("reviewcount")).intValue();
        Object avgRatingObj = stats.get("avgrating");

        if (reviewCount == 0 || avgRatingObj == null) {
            jdbcTemplate.update(
                    "UPDATE recipes SET AggregatedRating = NULL, ReviewCount = 0 WHERE RecipeId = ?",
                    recipeId
            );
        } else {
            Double avgRating = ((Number) avgRatingObj).doubleValue();
            jdbcTemplate.update(
                    "UPDATE recipes SET AggregatedRating = ?, ReviewCount = ? WHERE RecipeId = ?",
                    avgRating,
                    reviewCount,
                    recipeId
            );
        }
    }

    @Override
    @Transactional
    public long addReview(AuthInfo auth, long recipeId, int rating, String review) {
        long authorId = requireActiveUser(auth);

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }

        Boolean recipeExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM recipes WHERE RecipeId = ?)",
                Boolean.class,
                recipeId
        );
        if (recipeExists == null || !recipeExists) {
            throw new IllegalArgumentException("recipe does not exist");
        }

        String authorName = jdbcTemplate.queryForObject(
                "SELECT AuthorName FROM users WHERE AuthorId = ?",
                String.class,
                authorId
        );

        Long newReviewId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(ReviewId), 0) + 1 FROM reviews",
                Long.class
        );

        Timestamp now = new Timestamp(System.currentTimeMillis());

        jdbcTemplate.update(
                "INSERT INTO reviews (ReviewId, RecipeId, AuthorId, AuthorName, Rating, Review, DateSubmitted, DateModified) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                newReviewId,
                recipeId,
                authorId,
                authorName,
                rating,
                review,
                now,
                now
        );

        updateRecipeAggregatedRating(recipeId);

        return newReviewId;
    }

    @Override
    @Transactional
    public void editReview(AuthInfo auth, long recipeId, long reviewId, int rating, String review) {
        long operatorId = requireActiveUser(auth);

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }

        Long reviewAuthorId;
        try {
            reviewAuthorId = jdbcTemplate.queryForObject(
                    "SELECT AuthorId FROM reviews WHERE ReviewId = ? AND RecipeId = ?",
                    Long.class,
                    reviewId,
                    recipeId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("review does not belong to recipe");
        }

        if (reviewAuthorId != operatorId) {
            throw new SecurityException("only review author can edit review");
        }

        Timestamp now = new Timestamp(System.currentTimeMillis());
        jdbcTemplate.update(
                "UPDATE reviews SET Rating = ?, Review = ?, DateModified = ? WHERE ReviewId = ?",
                rating,
                review,
                now,
                reviewId
        );

        updateRecipeAggregatedRating(recipeId);
    }

    @Override
    @Transactional
    public void deleteReview(AuthInfo auth, long recipeId, long reviewId) {
        long operatorId = requireActiveUser(auth);

        Long reviewAuthorId;
        try {
            reviewAuthorId = jdbcTemplate.queryForObject(
                    "SELECT AuthorId FROM reviews WHERE ReviewId = ? AND RecipeId = ?",
                    Long.class,
                    reviewId,
                    recipeId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("review does not belong to recipe");
        }

        if (reviewAuthorId != operatorId) {
            throw new SecurityException("only review author can delete review");
        }

        jdbcTemplate.update("DELETE FROM review_likes WHERE ReviewId = ?", reviewId);
        jdbcTemplate.update("DELETE FROM reviews WHERE ReviewId = ?", reviewId);
        updateRecipeAggregatedRating(recipeId);
    }

    @Override
    @Transactional
    public long likeReview(AuthInfo auth, long reviewId) {
        long userId = requireActiveUserWithPassword(auth);

        Long reviewAuthorId;
        try {
            reviewAuthorId = jdbcTemplate.queryForObject(
                    "SELECT AuthorId FROM reviews WHERE ReviewId = ?",
                    Long.class,
                    reviewId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("review does not exist");
        }

        if (reviewAuthorId == userId) {
            throw new SecurityException("cannot like own review");
        }

        jdbcTemplate.update(
                "INSERT INTO review_likes (ReviewId, AuthorId) VALUES (?, ?) " +
                        "ON CONFLICT (ReviewId, AuthorId) DO NOTHING",
                reviewId,
                userId
        );

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_likes WHERE ReviewId = ?",
                Integer.class,
                reviewId
        );
        return count == null ? 0 : count;
    }

    @Override
    @Transactional
    public long unlikeReview(AuthInfo auth, long reviewId) {
        long userId = requireActiveUserWithPassword(auth);

        Boolean reviewExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM reviews WHERE ReviewId = ?)",
                Boolean.class,
                reviewId
        );
        if (reviewExists == null || !reviewExists) {
            throw new IllegalArgumentException("review does not exist");
        }

        jdbcTemplate.update(
                "DELETE FROM review_likes WHERE ReviewId = ? AND AuthorId = ?",
                reviewId,
                userId
        );

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review_likes WHERE ReviewId = ?",
                Integer.class,
                reviewId
        );
        return count == null ? 0 : count;
    }

    @Override
    public PageResult<ReviewRecord> listByRecipe(long recipeId, int page, int size, String sort) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE RecipeId = ?",
                Long.class,
                recipeId
        );
        if (total == null) total = 0L;

        String orderBy = "ORDER BY r.DateModified DESC";
        String fromClause = "FROM reviews r ";
        boolean needGroupBy = false;
        if (sort != null && "likes_desc".equals(sort)) {
            fromClause = "FROM reviews r LEFT JOIN review_likes rl ON r.ReviewId = rl.ReviewId ";
            orderBy = "ORDER BY COUNT(rl.AuthorId) DESC, r.DateModified DESC";
            needGroupBy = true;
        } else if (sort != null && "date_desc".equals(sort)) {
            orderBy = "ORDER BY r.DateModified DESC";
        }

        int offset = (page - 1) * size;
        String sql = "SELECT r.ReviewId, r.RecipeId, r.AuthorId, r.AuthorName, r.Rating, r.Review, " +
                "r.DateSubmitted, r.DateModified " +
                fromClause +
                "WHERE r.RecipeId = ? " +
                (needGroupBy ? "GROUP BY r.ReviewId, r.RecipeId, r.AuthorId, r.AuthorName, r.Rating, r.Review, r.DateSubmitted, r.DateModified " : "") +
                orderBy + " LIMIT ? OFFSET ?";

        List<ReviewRecord> reviews = jdbcTemplate.query(sql, (rs, rowNum) -> {
            ReviewRecord rec = new ReviewRecord();
            rec.setReviewId(rs.getLong("ReviewId"));
            rec.setRecipeId(rs.getLong("RecipeId"));
            rec.setAuthorId(rs.getLong("AuthorId"));
            rec.setAuthorName(rs.getString("AuthorName"));
            rec.setRating(rs.getFloat("Rating"));
            rec.setReview(rs.getString("Review"));
            rec.setDateSubmitted(rs.getTimestamp("DateSubmitted"));
            rec.setDateModified(rs.getTimestamp("DateModified"));
            return rec;
        }, recipeId, size, offset);

        if (!reviews.isEmpty()) {
            List<Long> reviewIds = reviews.stream()
                    .map(ReviewRecord::getReviewId)
                    .collect(Collectors.toList());

            String placeholders = reviewIds.stream()
                    .map(id -> "?")
                    .collect(Collectors.joining(","));

            List<Map<String, Object>> likeRows = jdbcTemplate.queryForList(
                    "SELECT ReviewId, AuthorId FROM review_likes WHERE ReviewId IN (" + placeholders + ") " +
                            "ORDER BY ReviewId, AuthorId",
                    reviewIds.toArray()
            );

            Map<Long, List<Long>> likesByReview = new HashMap<>();
            for (Map<String, Object> lr : likeRows) {
                long rid = ((Number) lr.get("reviewid")).longValue();
                long aid = ((Number) lr.get("authorid")).longValue();
                likesByReview.computeIfAbsent(rid, k -> new ArrayList<>()).add(aid);
            }

            for (ReviewRecord rec : reviews) {
                List<Long> likes = likesByReview.getOrDefault(rec.getReviewId(), Collections.emptyList());
                long[] likesArray = new long[likes.size()];
                for (int i = 0; i < likes.size(); i++) {
                    likesArray[i] = likes.get(i);
                }
                rec.setLikes(likesArray);
            }
        }

        PageResult<ReviewRecord> result = new PageResult<>();
        result.setItems(reviews);
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        return result;
    }

    @Override
    @Transactional
    public RecipeRecord refreshRecipeAggregatedRating(long recipeId) {
        Boolean recipeExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM recipes WHERE RecipeId = ?)",
                Boolean.class,
                recipeId
        );
        if (recipeExists == null || !recipeExists) {
            throw new IllegalArgumentException("recipe does not exist");
        }

        updateRecipeAggregatedRating(recipeId);
        return recipeService.getRecipeById(recipeId);
    }
}
