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
@Table(name = "training_sessions")
public class TrainingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "puzzle_id")
    private TrainingPuzzle puzzle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TrainingMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TimerMode timerMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private SessionStatus status = SessionStatus.ACTIVE;

    @Column(nullable = false)
    private String startFen;

    @Column(nullable = false)
    private String currentFen;

    @Column(nullable = false)
    private int timeLimitSeconds;

    @Column(nullable = false)
    private int incrementSeconds;

    @Column(nullable = false)
    private int remainingSeconds;

    @Column(nullable = false)
    private boolean userTurn;

    @Column(nullable = false)
    private boolean hintsEnabled;

    @Column(nullable = false)
    private boolean takebacksEnabled;

    @Column(nullable = false)
    private int hintsUsed;

    @Column(nullable = false)
    private int mistakes;

    @Column(nullable = false)
    private double accuracy;

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant endedAt;
}
