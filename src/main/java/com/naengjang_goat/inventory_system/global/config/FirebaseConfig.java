package com.naengjang_goat.inventory_system.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;

/**
 * Firebase Admin SDK 초기화.
 *
 * firebase-service-account.json 파일을 src/main/resources/ 에 위치시키거나
 * FIREBASE_CONFIG_PATH 환경변수로 경로 지정.
 *
 * 파일이 없으면 FCM 기능만 비활성화되고 앱은 정상 기동.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.config.path:classpath:firebase-service-account.json}")
    private Resource firebaseConfigResource;

    @Bean
    public FirebaseApp firebaseApp() {
        try {
            if (!firebaseConfigResource.exists()) {
                log.warn("[FIREBASE] 서비스 계정 파일 없음 — FCM 비활성화. 파일 위치: {}", firebaseConfigResource);
                return null;
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(firebaseConfigResource.getInputStream()))
                    .build();
            return FirebaseApp.initializeApp(options);
        } catch (IOException e) {
            log.error("[FIREBASE] 초기화 실패 — FCM 비활성화", e);
            return null;
        }
    }
}
