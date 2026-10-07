-- The earlier migrations ALTER these tables before Hibernate can create them.
-- Keep V001..V008 checksums unchanged. This bootstrap is for clean databases.
CREATE TABLE IF NOT EXISTS users (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(255) NOT NULL UNIQUE,
 password VARCHAR(255) NOT NULL,
 owner_name VARCHAR(255) NOT NULL,
 role VARCHAR(20) NOT NULL,
 active BIT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS ingredient (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 name VARCHAR(255) NOT NULL,
 base_unit VARCHAR(255) NOT NULL,
 warning_threshold DECIMAL(10,3),
 kamis_category VARCHAR(20),
 CONSTRAINT fk_bootstrap_ingredient_user FOREIGN KEY(user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS market_price (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 ingredient_id BIGINT NOT NULL,
 retail_price VARCHAR(255), wholesale_price VARCHAR(255), unit VARCHAR(255),
 reported_date DATE NOT NULL,
 CONSTRAINT fk_bootstrap_market_ingredient FOREIGN KEY(ingredient_id) REFERENCES ingredient(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
