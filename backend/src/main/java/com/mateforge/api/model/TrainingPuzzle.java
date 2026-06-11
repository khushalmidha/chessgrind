package com.mateforge.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "training_puzzles")
public class TrainingPuzzle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TrainingMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Difficulty difficulty;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false)
    private String fen;

    @Column(nullable = false)
    private int targetMateMoves;

    @Column(nullable = false)
    private boolean dailyChallenge;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
