ALTER TABLE purchase_orders
 ADD COLUMN product_name VARCHAR(255),
 ADD COLUMN source_url VARCHAR(2048),
 ADD COLUMN expected_quantity_base DECIMAL(10,3),
 ADD COLUMN inventory_unit VARCHAR(20),
 ADD COLUMN delivery_status VARCHAR(20) NOT NULL DEFAULT 'LEGACY',
 ADD COLUMN received_at DATETIME(6),
 ADD COLUMN received_batch_id BIGINT,
 ADD UNIQUE KEY uk_purchase_receipt_batch(received_batch_id);
-- Existing orders are LEGACY: never infer receipt or create stock for them.
CREATE TABLE pos_menu_mapping (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL, pos_code VARCHAR(100) NOT NULL, menu_id BIGINT NOT NULL,
 CONSTRAINT uk_pos_mapping UNIQUE(user_id,pos_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE pos_upload (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL, business_date DATE NOT NULL,
 file_hash VARCHAR(64) NOT NULL, original_filename VARCHAR(255) NOT NULL,
 sales_json LONGTEXT NOT NULL, consumption_json LONGTEXT NOT NULL,
 created_at DATETIME(6) NOT NULL,
 CONSTRAINT uk_pos_user_date UNIQUE(user_id,business_date),
 CONSTRAINT uk_pos_user_hash UNIQUE(user_id,file_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE closing_notification (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL, event_key VARCHAR(100) NOT NULL, type VARCHAR(30) NOT NULL,
 business_date DATE NOT NULL, title VARCHAR(255) NOT NULL, body TEXT NOT NULL,
 recommendations_json LONGTEXT NOT NULL, delivery_status VARCHAR(30) NOT NULL,
 attempts INT NOT NULL, created_at DATETIME(6) NOT NULL,
 next_attempt_at DATETIME(6) NOT NULL, sent_at DATETIME(6),
 CONSTRAINT uk_closing_event UNIQUE(user_id,event_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
