package com.mateforge.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.move.Move;
import org.junit.jupiter.api.Test;

class ChessRulesServiceTest {
    private final ChessRulesService rules = new ChessRulesService();

    @Test
    void detectsRookCheckmateAfterLegalMove() {
        Board board = rules.board("7k/8/6K1/8/8/8/8/R7 w - - 0 1");

        Move mate = rules.requireLegal(board, "a1a8");
        board.doMove(mate);

        assertThat(rules.isCheckmate(board)).isTrue();
        assertThat(rules.describeState(board)).isEqualTo("checkmate");
    }
}
