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

    public Board validatePlayableFen(String fen) {
        Board board = board(fen);
        String[] fields = fen == null ? new String[0] : fen.trim().split("\\s+");
        if (fields.length < 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid FEN");
        }
        validatePiecePlacement(fields[0]);
        legalMoves(board);
        return board;
        // FIXED: arbitrary FENs could be parsed but still contain impossible king/pawn layouts that break training sessions.
    }

    public Move parseUci(Board board, String uci) {
        if (uci == null || (uci.length() != 4 && uci.length() != 5)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Move must be UCI notation such as e2e4");
        }
        Square from;
        Square to;
        try {
            from = Square.fromValue(uci.substring(0, 2).toUpperCase());
            to = Square.fromValue(uci.substring(2, 4).toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid square in move");
            // FIXED: malformed UCI squares such as z9z1 previously escaped as server errors.
        }
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
        if (board.isMated()) {
            return "checkmate";
        }
        if (board.isStaleMate()) {
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

    public String simpleSan(Move move, Board before) {
        String suffix = "";
        Board after = board(before.getFen());
        after.doMove(move);
        if (after.isMated()) {
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

    private void validatePiecePlacement(String placement) {
        String[] ranks = placement.split("/");
        if (ranks.length != 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid FEN");
        }
        int whiteKings = 0;
        int blackKings = 0;
        int whiteKingFile = -1;
        int whiteKingRank = -1;
        int blackKingFile = -1;
        int blackKingRank = -1;
        for (int rankIndex = 0; rankIndex < ranks.length; rankIndex++) {
            int file = 0;
            for (char symbol : ranks[rankIndex].toCharArray()) {
                if (Character.isDigit(symbol)) {
                    int emptySquares = Character.digit(symbol, 10);
                    if (emptySquares < 1 || emptySquares > 8) {
                        throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid FEN");
                    }
                    file += emptySquares;
                    continue;
                }
                if (file >= 8) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid FEN");
                }
                if ((symbol == 'P' || symbol == 'p') && (rankIndex == 0 || rankIndex == 7)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid FEN: pawns cannot be on the first or eighth rank");
                }
                if (symbol == 'K') {
                    whiteKings++;
                    whiteKingFile = file;
                    whiteKingRank = rankIndex;
                } else if (symbol == 'k') {
                    blackKings++;
                    blackKingFile = file;
                    blackKingRank = rankIndex;
                } else if ("QRBNPqrbnp".indexOf(symbol) < 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid FEN");
                }
                file++;
            }
            if (file != 8) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid FEN");
            }
        }
        if (whiteKings != 1 || blackKings != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid FEN: exactly one king per side is required");
        }
        if (Math.abs(whiteKingFile - blackKingFile) <= 1 && Math.abs(whiteKingRank - blackKingRank) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid FEN: kings cannot be adjacent");
        }
    }
}
