-- =============================================================================
-- V0.1 — V006~V008 이 ALTER 하는 기반 테이블 생성 (park, 2026-09-17)
-- =============================================================================
-- 문제:
--   Flyway 는 JPA(ddl-auto=update) 보다 먼저 실행된다. 그런데 users·ingredient·market_price 는
--   지금까지 JPA 만 만들고 있어서, 빈 DB 에서는 V006(ALTER market_price)에서 멈췄다.
--   그래서 Flyway 를 꺼 두었고, 그 결과 새 환경에서는 JPA 가 price_records 를 만들어
--   unit_price_per_kg 가 GENERATED 가 아닌 일반 컬럼(항상 NULL)이 됐다.
--
-- 해결:
--   V001 보다 앞 버전(0.1)으로 세 테이블을 먼저 만든다.
--   컬럼은 Hibernate 가 만드는 DDL 을 그대로 쓰되, V006~V008 이 추가하는 컬럼
--   (market_price.source, ingredient.kamis_item_code, users.fcm_token)만 뺐다.
--   그 밖에 엔티티에만 있는 컬럼·테이블은 이후 JPA ddl-auto=update 가 채운다.
--   UK·FK 이름도 Hibernate 이름과 같게 맞춰 ddl-auto 가 중복 제약을 만들지 않게 했다.
--
-- 이미 V001~V008 이 적용된 DB:
--   spring.flyway.out-of-order=true 로 이 파일이 뒤늦게 적용되며, IF NOT EXISTS 라 아무것도 바꾸지 않는다.
-- =============================================================================

CREATE TABLE IF NOT EXISTS `users` (
  `id`         BIGINT                 NOT NULL AUTO_INCREMENT,
  `active`     BIT(1)                 NOT NULL,
  `owner_name` VARCHAR(255)           NOT NULL,
  `password`   VARCHAR(255)           NOT NULL,
  `role`       ENUM('ADMIN', 'OWNER') NOT NULL,
  `username`   VARCHAR(255)           NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `ingredient` (
  `id`                BIGINT         NOT NULL AUTO_INCREMENT,
  `base_unit`         VARCHAR(255)   NOT NULL,
  `kamis_category`    VARCHAR(20)    DEFAULT NULL,
  `name`              VARCHAR(255)   NOT NULL,
  `warning_threshold` DECIMAL(10, 3) DEFAULT NULL,
  `user_id`           BIGINT         NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKoo6a63cbx5xsxpy633jwp7l02` (`user_id`),
  CONSTRAINT `FKoo6a63cbx5xsxpy633jwp7l02` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `market_price` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `reported_date`   DATE         NOT NULL,
  `retail_price`    VARCHAR(255) DEFAULT NULL,
  `unit`            VARCHAR(255) DEFAULT NULL,
  `wholesale_price` VARCHAR(255) DEFAULT NULL,
  `ingredient_id`   BIGINT       NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKq8qbo17qicfkwgwfj31e7e5pm` (`ingredient_id`),
  CONSTRAINT `FKq8qbo17qicfkwgwfj31e7e5pm` FOREIGN KEY (`ingredient_id`) REFERENCES `ingredient` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
