package com.ilgo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(name = "ux_users_email", columnNames = "email"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String email;

    /** BCrypt 해시. 평문 저장 금지. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_output_mode", nullable = false, length = 16)
    private OutputMode defaultOutputMode = OutputMode.EASY_KOREAN;

    @Column(name = "default_lang", nullable = false, length = 8)
    private String defaultLang = "ko";

    /** 글씨 크기 배율(0.9/1.0/1.15/1.3). 기본 1.0. */
    // ColumnDefault: 기존 행이 있는 테이블에 NOT NULL 컬럼을 ddl-auto 로 추가할 때 기본값으로 백필되도록.
    @ColumnDefault("1.0")
    @Column(name = "text_scale", nullable = false)
    private double textScale = 1.0;

    /** 기한 알림 on/off. 기본 true. */
    @ColumnDefault("true")
    @Column(name = "deadline_alarm", nullable = false)
    private boolean deadlineAlarm = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public User() {
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public OutputMode getDefaultOutputMode() {
        return defaultOutputMode;
    }

    public void setDefaultOutputMode(OutputMode defaultOutputMode) {
        this.defaultOutputMode = defaultOutputMode;
    }

    public String getDefaultLang() {
        return defaultLang;
    }

    public void setDefaultLang(String defaultLang) {
        this.defaultLang = defaultLang;
    }

    public double getTextScale() {
        return textScale;
    }

    public void setTextScale(double textScale) {
        this.textScale = textScale;
    }

    public boolean isDeadlineAlarm() {
        return deadlineAlarm;
    }

    public void setDeadlineAlarm(boolean deadlineAlarm) {
        this.deadlineAlarm = deadlineAlarm;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
