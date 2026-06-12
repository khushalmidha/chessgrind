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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private AppUser createdBy;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 18)
    private String joinCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TournamentStatus status = TournamentStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TrainingMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Difficulty difficulty;

    @Column(nullable = false)
    private int timeLimitSeconds;

    @Column(nullable = false)
    private int maxPlayers;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
