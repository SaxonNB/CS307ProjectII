package io.sustc.util;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.exceptions.CsvException;
import io.sustc.dto.RecipeRecord;
import io.sustc.dto.ReviewRecord;
import io.sustc.dto.UserRecord;

import java.io.*;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CsvParser {

    private CsvParser() {
    }

    public static List<UserRecord> loadUsers(InputStream inputStream) throws IOException, CsvException {
        List<UserRecord> users = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream))) {
            List<String[]> records = reader.readAll();
            for (int i = 1; i < records.size(); i++) {
                String[] fields = records.get(i);
                if (fields.length >= 9) {
                    UserRecord user = UserRecord.builder()
                            .authorId(parseLong(fields[0]))
                            .authorName(fields[1] != null ? fields[1].trim() : "")
                            .gender(fields[2] != null ? fields[2].trim() : "")
                            .age(parseInt(fields[3]))
                            .followerUsers(parseCsvLongList(fields[6]))
                            .followingUsers(parseCsvLongList(fields[7]))
                            .password(fields[8] != null ? fields[8].trim() : "")
                            .build();
                    users.add(user);
                }
            }
        }
        return users;
    }

    public static List<RecipeRecord> loadRecipes(InputStream inputStream) throws IOException, CsvException {
        List<RecipeRecord> recipes = new ArrayList<>();
        try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(inputStream))
                .withCSVParser(new RFC4180ParserBuilder().build())
                .build()) {
            List<String[]> records = reader.readAll();
            for (int i = 1; i < records.size(); i++) {
                String[] fields = records.get(i);
                if (fields.length >= 24) {
                    RecipeRecord recipe = RecipeRecord.builder()
                            .RecipeId(parseLong(fields[0]))
                            .name(fields[1] != null ? fields[1].trim() : "")
                            .authorId(parseLong(fields[2]))
                            .authorName(fields[3] != null ? fields[3].trim() : "")
                            .cookTime(fields[4] != null ? fields[4].trim() : "")
                            .prepTime(fields[5] != null ? fields[5].trim() : "")
                            .totalTime(fields[6] != null ? fields[6].trim() : "")
                            .datePublished(parseTimestamp(fields[7]))
                            .description(fields[8] != null ? fields[8].trim() : "")
                            .recipeCategory(fields[9] != null ? fields[9].trim() : "")
                            .recipeIngredientParts(parseCsvList(fields[10]))
                            .aggregatedRating(parseFloat(fields[11]))
                            .reviewCount((int) parseFloat(fields[12]))
                            .calories(parseFloat(fields[13]))
                            .fatContent(parseFloat(fields[14]))
                            .saturatedFatContent(parseFloat(fields[15]))
                            .cholesterolContent(parseFloat(fields[16]))
                            .sodiumContent(parseFloat(fields[17]))
                            .carbohydrateContent(parseFloat(fields[18]))
                            .fiberContent(parseFloat(fields[19]))
                            .sugarContent(parseFloat(fields[20]))
                            .proteinContent(parseFloat(fields[21]))
                            .recipeServings((int) parseFloat(fields[22]))
                            .recipeYield(fields[23] != null ? fields[23].trim() : "")
                            .build();
                    recipes.add(recipe);
                }
            }
        }
        return recipes;
    }

    public static List<ReviewRecord> loadReviews(InputStream inputStream) throws IOException, CsvException {
        List<ReviewRecord> reviews = new ArrayList<>();
        try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream))) {
            List<String[]> records = reader.readAll();
            for (int i = 1; i < records.size(); i++) {
                String[] fields = records.get(i);
                if (fields.length >= 9) {
                    ReviewRecord review = ReviewRecord.builder()
                            .reviewId(parseLong(fields[0]))
                            .recipeId(parseLong(fields[1]))
                            .authorId(parseLong(fields[2]))
                            .authorName(fields[3] != null ? fields[3].trim() : "")
                            .rating(parseFloat(fields[4]))
                            .review(fields[5] != null ? fields[5].trim() : "")
                            .dateSubmitted(parseTimestamp(fields[6]))
                            .dateModified(parseTimestamp(fields[7]))
                            .likes(parseCsvLongList(fields[8]))
                            .build();
                    reviews.add(review);
                }
            }
        }
        return reviews;
    }

    static String[] parseCsvList(String listStr) {
        if (listStr == null || listStr.trim().isEmpty() || "null".equalsIgnoreCase(listStr.trim())) {
            return new String[0];
        }
        String trimmed = listStr.trim();
        if (trimmed.startsWith("c(") && trimmed.endsWith(")")) {
            String content = trimmed.substring(2, trimmed.length() - 1).trim();
            Pattern pattern = Pattern.compile("\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(content);
            List<String> items = new ArrayList<>();
            while (matcher.find()) {
                items.add(matcher.group(1));
            }
            if (!items.isEmpty()) {
                return items.toArray(new String[0]);
            }
            return Arrays.stream(content.split("\\s*,\\s*"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }
        return Arrays.stream(trimmed.split("\\s*,\\s*"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    static long[] parseCsvLongList(String listStr) {
        if (listStr == null || listStr.trim().isEmpty() || "null".equalsIgnoreCase(listStr.trim())) {
            return new long[0];
        }
        String trimmedStr = listStr.trim();
        if (trimmedStr.length() >= 2) {
            trimmedStr = trimmedStr.substring(1, trimmedStr.length() - 1);
        }
        if (trimmedStr.isEmpty()) {
            return new long[0];
        }
        String[] stringArray = trimmedStr.split("\\s*,\\s*");
        long[] longArray = new long[stringArray.length];
        for (int i = 0; i < stringArray.length; i++) {
            try {
                longArray[i] = Long.parseLong(stringArray[i].trim());
            } catch (NumberFormatException e) {
                longArray[i] = 0L;
            }
        }
        return longArray;
    }

    static Timestamp parseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.trim().isEmpty() || "null".equalsIgnoreCase(timestampStr.trim())) {
            return null;
        }
        String[] dateFormats = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd", "MM/dd/yyyy HH:mm:ss", "MM/dd/yyyy"};
        for (String format : dateFormats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format);
                Date date = sdf.parse(timestampStr.trim());
                return new Timestamp(date.getTime());
            } catch (ParseException e) {
            }
        }
        return null;
    }

    static float parseFloat(String floatStr) {
        if (floatStr == null || floatStr.trim().isEmpty() || "null".equalsIgnoreCase(floatStr.trim())) {
            return 0.0f;
        }
        try {
            return Float.parseFloat(floatStr.trim());
        } catch (NumberFormatException e) {
            return 0.0f;
        }
    }

    static int parseInt(String intStr) {
        if (intStr == null || intStr.trim().isEmpty() || "null".equalsIgnoreCase(intStr.trim())) {
            return 0;
        }
        try {
            return Integer.parseInt(intStr.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static long parseLong(String longStr) {
        if (longStr == null || longStr.trim().isEmpty() || "null".equalsIgnoreCase(longStr.trim())) {
            return 0L;
        }
        try {
            return Long.parseLong(longStr.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
