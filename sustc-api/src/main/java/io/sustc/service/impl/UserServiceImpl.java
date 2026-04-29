package io.sustc.service.impl;

import io.sustc.dto.*;
import io.sustc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

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

    private int parseAgeFromBirthday(String birthday) {
        if (!StringUtils.hasText(birthday)) {
            return -1;
        }
        try {
            LocalDate birth = LocalDate.parse(birthday.trim());
            LocalDate now = LocalDate.now();
            if (birth.isAfter(now)) {
                return -1;
            }
            return Period.between(birth, now).getYears();
        } catch (DateTimeParseException e) {
            return -1;
        }
    }


    @Override
    public long register(RegisterUserReq req) {
        if (req == null) {
            return -1;
        }

        String name = req.getName();
        if (!StringUtils.hasText(name)) {
            return -1;
        }

        RegisterUserReq.Gender gender = req.getGender();
        if (gender == null || gender == RegisterUserReq.Gender.UNKNOWN) {
            return -1;
        }
        String genderStr = (gender == RegisterUserReq.Gender.MALE) ? "Male" : "Female";

        int age = parseAgeFromBirthday(req.getBirthday());
        if (age <= 0) {
            return -1;
        }

        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE AuthorName = ?",
                Integer.class,
                name.trim()
        );
        if (cnt != null && cnt > 0) {
            return -1;
        }

        Long newId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(AuthorId) + 1, 1) FROM users",
                Long.class
        );
        if (newId == null) {
            newId = 1L;
        }

        jdbcTemplate.update(
                "INSERT INTO users " +
                        "(AuthorId, AuthorName, Gender, Age, Password, IsDeleted) " +
                        "VALUES (?, ?, ?, ?, ?, FALSE)",
                newId,
                name.trim(),
                genderStr,
                age,
                req.getPassword()
        );

        return newId;
    }

    @Override
    public long login(AuthInfo auth) {
        if (auth == null) {
            return -1;
        }
        long userId = auth.getAuthorId();
        String password = auth.getPassword();
        if (!StringUtils.hasText(password)) {
            return -1;
        }

        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT Password, IsDeleted FROM users WHERE AuthorId = ?",
                    userId
            );
            Map<String, Object> lowerCaseRow = new HashMap<>();
            row.forEach((k, v) -> lowerCaseRow.put(k.toLowerCase(), v));

            Boolean isDeleted = (Boolean) lowerCaseRow.get("isdeleted");
            if (Boolean.TRUE.equals(isDeleted)) {
                return -1;
            }
            Object storedPwdObj = lowerCaseRow.get("password");
            String storedPwd = storedPwdObj == null ? null : storedPwdObj.toString();
            if (!StringUtils.hasText(storedPwd) || password == null) {
                return -1;
            }
            if (!storedPwd.equals(password)) {
                return -1;
            }
            return userId;
        } catch (EmptyResultDataAccessException e) {
            return -1;
        }
    }

    @Override
    public boolean deleteAccount(AuthInfo auth, long userId) {
        long operatorId = requireActiveUser(auth);

        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM users WHERE AuthorId = ?)",
                Boolean.class,
                userId
        );
        if (exists == null || !exists) {
            throw new IllegalArgumentException("target user does not exist");
        }

        if (operatorId != userId) {
            throw new SecurityException("cannot delete others' account");
        }

        Boolean isDeleted = jdbcTemplate.queryForObject(
                "SELECT IsDeleted FROM users WHERE AuthorId = ?",
                Boolean.class,
                userId
        );
        if (Boolean.TRUE.equals(isDeleted)) {
            return false;
        }

        jdbcTemplate.update(
                "UPDATE users SET IsDeleted = TRUE WHERE AuthorId = ?",
                userId
        );

        jdbcTemplate.update("DELETE FROM user_follows WHERE FollowerId = ? OR FollowingId = ?", userId, userId);

        return true;
    }

    @Override
    public boolean follow(AuthInfo auth, long followeeId) {
        long followerId = requireActiveUser(auth);

        if (followerId == followeeId) {
            throw new SecurityException("cannot follow self");
        }

        try {
            Boolean isDeleted = jdbcTemplate.queryForObject(
                    "SELECT IsDeleted FROM users WHERE AuthorId = ?",
                    Boolean.class,
                    followeeId
            );
            if (isDeleted == null || isDeleted) {
                throw new SecurityException("followee is inactive");
            }
        } catch (EmptyResultDataAccessException e) {
            throw new SecurityException("followee does not exist");
        }

        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follows WHERE FollowerId = ? AND FollowingId = ?",
                Integer.class,
                followerId,
                followeeId
        );
        boolean alreadyFollowing = cnt != null && cnt > 0;

        if (alreadyFollowing) {
            jdbcTemplate.update(
                    "DELETE FROM user_follows WHERE FollowerId = ? AND FollowingId = ?",
                    followerId,
                    followeeId
            );
            return false;
        } else {
            jdbcTemplate.update(
                    "INSERT INTO user_follows (FollowerId, FollowingId) VALUES (?, ?)",
                    followerId,
                    followeeId
            );
            return true;
        }
    }


    @Override
    public UserRecord getById(long userId) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT AuthorId, AuthorName, Gender, Age, Password, IsDeleted " +
                            "FROM users WHERE AuthorId = ?",
                    userId
            );

            Integer followers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_follows WHERE FollowingId = ?",
                    Integer.class,
                    userId
            );
            Integer following = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_follows WHERE FollowerId = ?",
                    Integer.class,
                    userId
            );

            UserRecord user = new UserRecord();
            user.setAuthorId(((Number) row.get("authorid")).longValue());
            user.setAuthorName((String) row.get("authorname"));
            user.setGender((String) row.get("gender"));
            user.setAge(((Number) row.get("age")).intValue());
            user.setPassword((String) row.get("password"));
            Object deletedObj = row.get("isdeleted");
            user.setDeleted(deletedObj != null && (Boolean) deletedObj);
            user.setFollowers(followers == null ? 0 : followers);
            user.setFollowing(following == null ? 0 : following);

            return user;
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("user not found");
        }
    }

    @Override
    public void updateProfile(AuthInfo auth, String gender, Integer age) {

        long userId = requireActiveUser(auth);

        if (gender != null) {
            String g = gender.trim();
            if (!"Male".equalsIgnoreCase(g) && !"Female".equalsIgnoreCase(g)) {
                throw new IllegalArgumentException("invalid gender");
            }
            String normalizedGender = Character.toUpperCase(g.charAt(0)) + g.substring(1).toLowerCase();
            jdbcTemplate.update(
                    "UPDATE users SET Gender = ? WHERE AuthorId = ?",
                    normalizedGender,
                    userId
            );
        }

        if (age != null) {
            if (age <= 0) {
                throw new IllegalArgumentException("invalid age");
            }
            jdbcTemplate.update(
                    "UPDATE users SET Age = ? WHERE AuthorId = ?",
                    age,
                    userId
            );
        }
    }

    @Override
    public PageResult<FeedItem> feed(AuthInfo auth, int page, int size, String category) {
        long userId = requireActiveUser(auth);

        if (page < 1) page = 1;
        if (size < 1) size = 1;
        if (size > 200) size = 200;

        List<Object> params = new ArrayList<>();
        params.add(userId);

        String baseFrom = " FROM recipes r " +
                "JOIN user_follows uf ON uf.FollowingId = r.AuthorId " +
                "JOIN users u ON u.AuthorId = r.AuthorId " +
                "WHERE uf.FollowerId = ? AND u.IsDeleted = FALSE";

        if (category != null) {
            baseFrom += " AND r.RecipeCategory = ?";
            params.add(category);
        }

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + baseFrom, params.toArray(), Long.class);
        if (total == null) total = 0L;

        int offset = (page - 1) * size;
        params.add(size);
        params.add(offset);

        //+时区调整：数据库时间加8小时
        String sql = "SELECT r.RecipeId, r.Name, r.AuthorId, u.AuthorName, " +
                "r.DatePublished, r.AggregatedRating, r.ReviewCount " +
                baseFrom +
                " ORDER BY r.DatePublished DESC NULLS LAST, r.RecipeId DESC " +
                " LIMIT ? OFFSET ?";

        List<FeedItem> items = jdbcTemplate.query(sql, params.toArray(), (rs, rowNum) -> {
            FeedItem item = new FeedItem();
            item.setRecipeId(rs.getLong("RecipeId"));
            item.setName(rs.getString("Name"));
            item.setAuthorId(rs.getLong("AuthorId"));
            item.setAuthorName(rs.getString("AuthorName"));
            Timestamp ts = rs.getTimestamp("DatePublished");
            if (ts != null) {
                long adjustedTime = ts.getTime() + 8 * 60 * 60 * 1000;
                item.setDatePublished(new Timestamp(adjustedTime).toInstant());
            } else {
                item.setDatePublished(null);
            }
            Object aggObj = rs.getObject("AggregatedRating");
            //+没有评论时返回0.0而不是null
            item.setAggregatedRating(aggObj == null ? 0.0 : ((Number) aggObj).doubleValue());
            int rc = rs.getInt("ReviewCount");
            //+没有评论时返回0而不是null
            item.setReviewCount(rs.wasNull() ? 0 : rc);
            return item;
        });

        PageResult<FeedItem> result = new PageResult<>();
        result.setItems(items);
        result.setPage(page);
        result.setSize(size);
        result.setTotal(total);
        return result;
    }

    @Override
    public Map<String, Object> getUserWithHighestFollowRatio() {
        //+使用子查询预聚合优化性能
        String sql =
                "SELECT u.AuthorId, u.AuthorName, " +
                        "       (COALESCE(fc.FollowerCount, 0) * 1.0 / fo.FollowingCount) AS Ratio " +
                        "FROM users u " +
                        "INNER JOIN ( " +
                        "    SELECT FollowerId AS AuthorId, COUNT(*) AS FollowingCount " +
                        "    FROM user_follows " +
                        "    GROUP BY FollowerId " +
                        ") fo ON fo.AuthorId = u.AuthorId " +
                        "LEFT JOIN ( " +
                        "    SELECT FollowingId AS AuthorId, COUNT(*) AS FollowerCount " +
                        "    FROM user_follows " +
                        "    GROUP BY FollowingId " +
                        ") fc ON fc.AuthorId = u.AuthorId " +
                        "WHERE u.IsDeleted = FALSE " +
                        "ORDER BY Ratio DESC, u.AuthorId ASC " +
                        "LIMIT 1";

        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("AuthorId", rs.getLong("AuthorId"));
                map.put("AuthorName", rs.getString("AuthorName"));
                map.put("Ratio", rs.getDouble("Ratio"));
                return map;
            });
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

}