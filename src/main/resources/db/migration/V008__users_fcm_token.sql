-- V008: FCM 푸시 알림용 기기 토큰 컬럼 추가
ALTER TABLE users
    ADD COLUMN fcm_token VARCHAR(255) NULL COMMENT 'FCM 기기 토큰. 앱 실행 시 갱신.';
