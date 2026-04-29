-- ============================================
-- SUSTC 数据库 DDL — 宽表设计（6 表）
-- 目标：读取时 0 JOIN，全部单表直出
-- ============================================

-- ============================================
-- 1. 用户表
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    AuthorId BIGINT PRIMARY KEY,
    AuthorName TEXT NOT NULL,
    Gender VARCHAR(10) CHECK (Gender IN ('Male', 'Female')),
    Age INTEGER CHECK (Age > 0),
    Password TEXT,
    IsDeleted BOOLEAN DEFAULT FALSE
);

-- ============================================
-- 2. 食谱宽表（核心）
--   合并 nutrition，冗余 AuthorName 和 IngredientTags
--   读取列表/详情均为单表查询
-- ============================================
CREATE TABLE IF NOT EXISTS recipes (
    RecipeId BIGINT PRIMARY KEY,
    Name TEXT NOT NULL,
    AuthorId BIGINT NOT NULL,
    AuthorName TEXT NOT NULL,                     -- 冗余：免 JOIN users
    CookTime TEXT,
    PrepTime TEXT,
    TotalTime TEXT,
    DatePublished TIMESTAMP,
    Description TEXT,
    RecipeCategory TEXT,
    RecipeServings INTEGER,
    RecipeYield TEXT,
    IngredientTags TEXT,                          -- 扁平化：| 分隔，"pork|cabbage|scallion"
    AggregatedRating DECIMAL(3,2) CHECK (AggregatedRating >= 0 AND AggregatedRating <= 5),
    ReviewCount INTEGER DEFAULT 0 CHECK (ReviewCount >= 0),
    -- 营养信息（合并自 nutrition 表）
    Calories NUMERIC(10, 2),
    FatContent NUMERIC(10, 2),
    SaturatedFatContent NUMERIC(10, 2),
    CholesterolContent NUMERIC(10, 2),
    SodiumContent NUMERIC(10, 2),
    CarbohydrateContent NUMERIC(10, 2),
    FiberContent NUMERIC(10, 2),
    SugarContent NUMERIC(10, 2),
    ProteinContent NUMERIC(10, 2),
    FOREIGN KEY (AuthorId) REFERENCES users(AuthorId)
);

-- ============================================
-- 3. 评论表
--   冗余 AuthorName，免 JOIN users
-- ============================================
CREATE TABLE IF NOT EXISTS reviews (
    ReviewId BIGINT PRIMARY KEY,
    RecipeId BIGINT NOT NULL,
    AuthorId BIGINT NOT NULL,
    AuthorName TEXT NOT NULL,                     -- 冗余：免 JOIN users
    Rating INTEGER NOT NULL CHECK (Rating >= 0 AND Rating <= 5),
    Review TEXT,
    DateSubmitted TIMESTAMP,
    DateModified TIMESTAMP,
    FOREIGN KEY (RecipeId) REFERENCES recipes(RecipeId) ON DELETE CASCADE,
    FOREIGN KEY (AuthorId) REFERENCES users(AuthorId)
);

-- ============================================
-- 4. 食材桥接表（用于精确筛选）
-- ============================================
CREATE TABLE IF NOT EXISTS recipe_ingredients (
    RecipeId BIGINT,
    IngredientPart TEXT,
    PRIMARY KEY (RecipeId, IngredientPart),
    FOREIGN KEY (RecipeId) REFERENCES recipes(RecipeId) ON DELETE CASCADE
);

-- ============================================
-- 5. 评论点赞表
-- ============================================
CREATE TABLE IF NOT EXISTS review_likes (
    ReviewId BIGINT,
    AuthorId BIGINT,
    PRIMARY KEY (ReviewId, AuthorId),
    FOREIGN KEY (ReviewId) REFERENCES reviews(ReviewId) ON DELETE CASCADE,
    FOREIGN KEY (AuthorId) REFERENCES users(AuthorId)
);

-- ============================================
-- 6. 用户关注表
-- ============================================
CREATE TABLE IF NOT EXISTS user_follows (
    FollowerId BIGINT,
    FollowingId BIGINT,
    PRIMARY KEY (FollowerId, FollowingId),
    FOREIGN KEY (FollowerId) REFERENCES users(AuthorId),
    FOREIGN KEY (FollowingId) REFERENCES users(AuthorId),
    CHECK (FollowerId != FollowingId)
);

-- ============================================
-- 索引
-- ============================================
CREATE INDEX IF NOT EXISTS idx_users_authorname ON users(AuthorName);
CREATE INDEX IF NOT EXISTS idx_users_isdeleted ON users(IsDeleted) WHERE IsDeleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_recipes_authorid ON recipes(AuthorId);
CREATE INDEX IF NOT EXISTS idx_recipes_category ON recipes(RecipeCategory);
CREATE INDEX IF NOT EXISTS idx_recipes_datepublished ON recipes(DatePublished DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_recipes_rating ON recipes(AggregatedRating DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_recipes_feed ON recipes(AuthorId, RecipeCategory, DatePublished DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_recipes_name_lower ON recipes(LOWER(Name));
CREATE INDEX IF NOT EXISTS idx_recipes_description_lower ON recipes(LOWER(Description));
CREATE INDEX IF NOT EXISTS idx_recipes_calories ON recipes(Calories ASC NULLS LAST);

CREATE INDEX IF NOT EXISTS idx_reviews_recipeid ON reviews(RecipeId);
CREATE INDEX IF NOT EXISTS idx_reviews_recipe_date ON reviews(RecipeId, DateModified DESC);

CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipeid ON recipe_ingredients(RecipeId);

CREATE INDEX IF NOT EXISTS idx_review_likes_reviewid ON review_likes(ReviewId);

CREATE INDEX IF NOT EXISTS idx_user_follows_followerid ON user_follows(FollowerId);
CREATE INDEX IF NOT EXISTS idx_user_follows_followingid ON user_follows(FollowingId);
