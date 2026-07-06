package com.mateforge.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String username;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private int streakDays;

    @Column(nullable = false)
    private int totalCompleted;

    @Column(columnDefinition = "TEXT")
    private String profileReportJson;

    @Column(length = 120)
    private String profileReportFingerprint;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
