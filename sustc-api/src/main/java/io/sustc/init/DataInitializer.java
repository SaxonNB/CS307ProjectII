package io.sustc.init;

import io.sustc.dto.RecipeRecord;
import io.sustc.dto.ReviewRecord;
import io.sustc.dto.UserRecord;
import io.sustc.service.DatabaseService;
import io.sustc.util.CsvParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private DatabaseService databaseService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users", Integer.class);
            if (count != null && count > 0) {
                log.info("Data already loaded ({} users found), skipping import.", count);
                return;
            }
        } catch (Exception e) {
            log.info("Could not check user count (tables may not exist yet): {}", e.getMessage());
        }

        log.info("Data not found, starting CSV import...");

        try {
            List<UserRecord> users;
            try (InputStream is = getClass().getResourceAsStream("/data/users.csv")) {
                if (is == null) {
                    log.error("users.csv not found on classpath");
                    return;
                }
                users = CsvParser.loadUsers(is);
            }

            List<RecipeRecord> recipes;
            try (InputStream is = getClass().getResourceAsStream("/data/recipes.csv")) {
                if (is == null) {
                    log.error("recipes.csv not found on classpath");
                    return;
                }
                recipes = CsvParser.loadRecipes(is);
            }

            List<ReviewRecord> reviews;
            try (InputStream is = getClass().getResourceAsStream("/data/reviews.csv")) {
                if (is == null) {
                    log.error("reviews.csv not found on classpath");
                    return;
                }
                reviews = CsvParser.loadReviews(is);
            }

            databaseService.importData(reviews, users, recipes);
            log.info("Import complete: {} users, {} recipes, {} reviews",
                    users.size(), recipes.size(), reviews.size());

        } catch (Exception e) {
            log.error("Failed to import CSV data on startup", e);
        }
    }
}
