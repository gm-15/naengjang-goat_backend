-- UC-ONBOARD: 식자재왕 레시피 공용 템플릿 라이브러리
-- sim 크롤러가 적재한 68개 메뉴 / 1,222 BOM rows 수용
-- park의 menu/recipe 테이블과 무관한 별도 라이브러리

CREATE TABLE IF NOT EXISTS recipe_template (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    category          VARCHAR(20)  NOT NULL,           -- KOREAN / WESTERN / CHINESE / JAPANESE / OTHER
    menu_name         VARCHAR(100) NOT NULL,
    source            VARCHAR(20)  NOT NULL DEFAULT '식자재왕',
    source_recipe_idx INT          NOT NULL,           -- 식자재왕 idx (재실행 시 idempotent 보장)
    image_url         TEXT,
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_idx (source, source_recipe_idx),
    INDEX idx_category (category)
);

CREATE TABLE IF NOT EXISTS recipe_template_bom (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    template_id     BIGINT        NOT NULL,
    ingredient_name VARCHAR(255)  NOT NULL,            -- 식자재왕 상품명 원문
    quantity        INT           NULL,                -- 상품명에서 파싱한 무게(g), 1인분 소모량 아님
    unit            VARCHAR(10)   NULL,                -- "g" 통일
    raw_product_gno VARCHAR(64)   NULL,                -- 식자재왕 gno
    product_price   INT           NULL,
    is_discount     TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_template (template_id),
    CONSTRAINT fk_rtb_template
        FOREIGN KEY (template_id) REFERENCES recipe_template(id) ON DELETE CASCADE
);
