INSERT INTO training_puzzles (mode, difficulty, title, fen, target_mate_moves, daily_challenge) VALUES
('KING_ROOK_VS_KING', 'BEGINNER', 'Box the king', '8/8/8/8/8/4k3/8/4K2R w - - 0 1', 8, true),
('TWO_ROOKS_VS_KING', 'BEGINNER', 'Ladder mate', '8/8/8/8/8/4k3/8/R3K2R w - - 0 1', 5, false),
('QUEEN_VS_KING', 'BEGINNER', 'Queen shoulder', '8/8/8/8/8/3k4/8/4K2Q w - - 0 1', 7, false),
('TWO_BISHOPS_VS_KING', 'INTERMEDIATE', 'Drive to the corner', '8/8/8/8/3k4/8/8/2B1KB2 w - - 0 1', 16, false),
('BISHOP_KNIGHT_VS_KING', 'ADVANCED', 'Wrong corner no more', '8/8/8/8/3k4/8/8/2B1KN2 w - - 0 1', 24, false),
('TWO_PAWNS_VS_KING', 'INTERMEDIATE', 'Connected runners', '8/8/8/8/3k4/8/3PP3/4K3 w - - 0 1', 18, false)
ON CONFLICT DO NOTHING;
