package com.mateforge.api.service;

import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.Piece;
import com.github.bhlangonijr.chesslib.Side;
import com.github.bhlangonijr.chesslib.Square;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChessRulesService {
    public Board board(String fen) {
        Board board = new Board();
        try {
            board.loadFromFen(fen);
            return board;
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid FEN");
        }
    }

    public Move parseUci(Board board, String uci) {
        if (uci == null || (uci.length() != 4 && uci.length() != 5)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Move must be UCI notation such as e2e4");
        }
        Square from = Square.fromValue(uci.substring(0, 2).toUpperCase());
        Square to = Square.fromValue(uci.substring(2, 4).toUpperCase());
        Piece promotion = Piece.NONE;
        if (uci.length() == 5) {
            promotion = promotionPiece(board.getSideToMove(), uci.charAt(4));
        }
        return new Move(from, to, promotion);
    }

    public Move requireLegal(Board board, String uci) {
        Move move = parseUci(board, uci);
        boolean legal = legalMoves(board).stream().anyMatch(candidate -> candidate.equals(move));
        if (!legal) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Illegal move in this position");
        }
        return move;
    }

    public List<Move> legalMoves(Board board) {
        try {
            return board.legalMoves();
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Could not generate legal moves");
        }
    }

    public String apply(String fen, String uci) {
        Board board = board(fen);
        Move move = requireLegal(board, uci);
        board.doMove(move);
        return board.getFen();
    }

    public boolean isCheck(String fen) {
        return board(fen).isKingAttacked();
    }

    public String describeState(Board board) {
        if (isCheckmate(board)) {
            return "checkmate";
        }
        if (isStalemate(board)) {
            return "stalemate";
        }
        if (board.isDraw()) {
            return "draw";
        }
        if (board.isKingAttacked()) {
            return "check";
        }
        return "active";
    }

    public boolean isCheckmate(Board board) {
        return board.isKingAttacked() && legalMoves(board).isEmpty();
    }

    public boolean isStalemate(Board board) {
        return !board.isKingAttacked() && legalMoves(board).isEmpty();
    }

    public String simpleSan(Move move, Board before) {
        String suffix = "";
        Board after = board(before.getFen());
        after.doMove(move);
        if (isCheckmate(after)) {
            suffix = "#";
        } else if (after.isKingAttacked()) {
            suffix = "+";
        }
        return move.toString() + suffix;
    }

    private Piece promotionPiece(Side side, char code) {
        return switch (Character.toLowerCase(code)) {
            case 'q' -> side == Side.WHITE ? Piece.WHITE_QUEEN : Piece.BLACK_QUEEN;
            case 'r' -> side == Side.WHITE ? Piece.WHITE_ROOK : Piece.BLACK_ROOK;
            case 'b' -> side == Side.WHITE ? Piece.WHITE_BISHOP : Piece.BLACK_BISHOP;
            case 'n' -> side == Side.WHITE ? Piece.WHITE_KNIGHT : Piece.BLACK_KNIGHT;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported promotion piece");
        };
    }
}
