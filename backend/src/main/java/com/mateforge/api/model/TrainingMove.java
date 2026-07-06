package com.mateforge.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "training_moves")
public class TrainingMove {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private TrainingSession session;

    @Column(nullable = false)
    private int ply;

    @Column(nullable = false, length = 16)
    private String uci;

    @Column(nullable = false, length = 24)
    private String san;

    @Column(nullable = false)
    private String fenAfter;

    @Column(nullable = false)
    private boolean engineMove;

    @Column(nullable = false)
    private boolean optimal;

    @Column(nullable = false, length = 260)
    private String reason;

    @Column(nullable = false)
    private Instant playedAt = Instant.now();
}
