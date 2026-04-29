package com.naengjang_goat.inventory_system.settings.domain;

/**
 * 발주·재고실사 요일 선택용 enum.
 * DB 저장: VARCHAR (name() 기준 — "MON", "TUE" …)
 */
public enum DayOfWeekType {
    MON, TUE, WED, THU, FRI, SAT, SUN;

    /**
     * java.time.DayOfWeek (1=MON … 7=SUN) 값으로 변환.
     * 알림 스케줄러에서 "다음 발주 요일까지 남은 일수" 계산에 사용.
     */
    public java.time.DayOfWeek toJavaDayOfWeek() {
        return java.time.DayOfWeek.values()[this.ordinal()]; // MON→MONDAY(ordinal 0) 동일
    }
}
