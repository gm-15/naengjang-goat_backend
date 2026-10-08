-- FCM 토큰은 대소문자를 구별하는 값이며 한 계정에만 연결한다.
ALTER TABLE users MODIFY COLUMN fcm_token VARCHAR(255)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL;

UPDATE users SET fcm_token = NULL WHERE TRIM(fcm_token) = '';

-- 이전 등록 시각은 기록되지 않았으므로 기존 중복은 가장 큰 사용자 ID만 유지한다.
UPDATE users u
JOIN (
    SELECT fcm_token, MAX(id) AS keep_id
    FROM users
    WHERE fcm_token IS NOT NULL
    GROUP BY fcm_token
    HAVING COUNT(*) > 1
) duplicates ON u.fcm_token = duplicates.fcm_token
SET u.fcm_token = NULL
WHERE u.id <> duplicates.keep_id;

CREATE UNIQUE INDEX uk_users_fcm_token ON users (fcm_token);
