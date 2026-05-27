package com.naengjang_goat.inventory_system.global.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * FCM 푸시 알림 발송 서비스.
 *
 * FirebaseApp 빈이 null(서비스 계정 파일 없음)이면 발송을 skip.
 * → 개발 환경에서 파일 없이도 앱이 정상 동작.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FcmService {

    private final FirebaseApp firebaseApp;

    /**
     * 단일 기기에 푸시 알림 발송.
     *
     * @param fcmToken 대상 기기 토큰
     * @param title    알림 제목
     * @param body     알림 본문
     */
    public void send(String fcmToken, String title, String body) {
        if (firebaseApp == null) {
            log.debug("[FCM] FirebaseApp 미초기화 — 발송 skip. title={}", title);
            return;
        }
        if (fcmToken == null || fcmToken.isBlank()) {
            log.debug("[FCM] fcmToken 없음 — 발송 skip");
            return;
        }

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();

        try {
            String messageId = FirebaseMessaging.getInstance(firebaseApp).send(message);
            log.info("[FCM] 발송 성공 messageId={} title={}", messageId, title);
        } catch (FirebaseMessagingException e) {
            log.error("[FCM] 발송 실패 token={} title={}", fcmToken, title, e);
        }
    }
}
