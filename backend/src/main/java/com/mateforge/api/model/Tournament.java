package com.mateforge.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tournaments")
public class Tournament {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_user_id", nullable = false)
    private AppUser host;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TrainingMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Difficulty difficulty;

    @Column(nullable = false)
    private int rounds;

    @Column(nullable = false)
    private Instant scheduledStartAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
