package me.Shadow.Engine;

public class Evaluation
{
	Board board;
	TranspositionTable transpositionTable;
	boolean tableEnabled;
	
	public Evaluation(Board board)
	{
		this.board = board;
		tableEnabled = false;
	}
	
	public Evaluation(Board board, TranspositionTable tt)
	{
		this.board = board;
		this.transpositionTable = tt;
		tableEnabled = true;
	}
	
	public int staticEvaluation()
	{
		float evaluation = 0;

		float gamePhase = getGamePhase();
		evaluation = evaluateMaterial(gamePhase);
		
		
		final int wkIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[PieceHelper.WHITE_KING]);
		final int bkIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[PieceHelper.BLACK_KING]);

		evaluation += forceKingToCorner(wkIndex, bkIndex, 1 - (gamePhase * (24 / 5.0f)));
		evaluation -= forceKingToCorner(bkIndex, wkIndex, 1 - (gamePhase * (24 / 5.0f)));

		evaluation += evaluatePawns();

		evaluation *= (board.colorToMove == PieceHelper.WHITE ? 1 : -1);

		return (int) evaluation;
	}
	
	public float evaluateMaterial(float gamePhase)
	{
		int mgScore = board.material[PieceHelper.WHITE * 2] - board.material[PieceHelper.BLACK * 2];
		int egScore = board.material[PieceHelper.WHITE * 2 + 1] - board.material[PieceHelper.BLACK * 2 + 1];
		return mgScore * gamePhase + egScore * (1 - gamePhase);
	}

	public float getGamePhase()
	{
		int mgPhase = 0;
		mgPhase += Long
				.bitCount(board.bitBoards.pieceBoards[PieceHelper.WHITE_QUEEN]
						| board.bitBoards.pieceBoards[PieceHelper.BLACK_QUEEN])
				* PieceHelper.getGamePhaseValue(PieceHelper.QUEEN);

		mgPhase += Long
				.bitCount(board.bitBoards.pieceBoards[PieceHelper.WHITE_ROOK]
						| board.bitBoards.pieceBoards[PieceHelper.BLACK_ROOK])
				* PieceHelper.getGamePhaseValue(PieceHelper.QUEEN);

		mgPhase += Long
				.bitCount(board.bitBoards.pieceBoards[PieceHelper.WHITE_BISHOP]
						| board.bitBoards.pieceBoards[PieceHelper.BLACK_BISHOP])
				* PieceHelper.getGamePhaseValue(PieceHelper.QUEEN);

		if (mgPhase > 24)
			mgPhase = 24;
		return mgPhase / 24.0f;
	}

	public int evaluatePawns()
	{
		long pawnsHash = board.pawnsHash;
		int pawnsEval;
		if (tableEnabled && (pawnsEval = transpositionTable.getPawnsEval(pawnsHash)) != TranspositionTable.LOOKUP_FAILED)
		{
			return pawnsEval;
		}
		else
		{
			pawnsEval = evaluatePawns(PieceHelper.WHITE, PieceHelper.BLACK);
			pawnsEval -= evaluatePawns(PieceHelper.BLACK, PieceHelper.WHITE);
			if (tableEnabled) transpositionTable.storePawnsEvaluation(pawnsHash, pawnsEval);
			return pawnsEval;
		}
	}

	public int evaluatePawns(int friendlyColor, int enemyColor)
	{
		final int[] passedPawnsBonus = { 0, 10, 15, 25, 40, 60, 100, 0 };
		boolean whitePieces = friendlyColor == PieceHelper.WHITE;
		int evaluation = 0;

		long pawnsBitboard = board.bitBoards.pieceBoards[friendlyColor | PieceHelper.PAWN];
		long friendlyPawns = pawnsBitboard;
		long enemyPawns = board.bitBoards.pieceBoards[enemyColor | PieceHelper.PAWN];

		while (pawnsBitboard != 0)
		{
			int pawnIndex = Bitboards.getLSB(pawnsBitboard);
			pawnsBitboard = Bitboards.toggleBit(pawnsBitboard, pawnIndex);

			int rank = pawnIndex & 56;
			long ranksAheadMask = -1; // All 1 bits
			ranksAheadMask = whitePieces ? (ranksAheadMask << (rank + 8)) : (ranksAheadMask >>> ((rank ^ 56) + 8));

			int file = pawnIndex & 0x7;
			long fileMask = MoveGenerator.A_FILE << (file); // mask last 3 bits, aka mod 8
			long tripleFileMask = fileMask;
			if (file > 0)
				tripleFileMask |= (tripleFileMask >>> 1); // left file
			if (file < 7)
				tripleFileMask |= (tripleFileMask << 1); // right file

			long passedPawnMask = ranksAheadMask & tripleFileMask & enemyPawns;
			if (passedPawnMask == 0)
			{
				evaluation += passedPawnsBonus[whitePieces ? (pawnIndex / 8) : (7 - (pawnIndex / 8))];
			}
				

			long friendlyPawnsAhead = (ranksAheadMask & fileMask & friendlyPawns);
			evaluation -= 10 * (Long.bitCount(friendlyPawnsAhead) * Long.bitCount(friendlyPawnsAhead));

			
			// next/prev rank are swapped for white/black pawns
			long nextRankMask = MoveGenerator.FIRST_RANK << ((pawnIndex & ~7) + (whitePieces ? 8 : -8));
			long prevRankMask = MoveGenerator.FIRST_RANK << ((pawnIndex & ~7) - (whitePieces ? 8 : -8));
			long friendlyPawnsSupported = tripleFileMask & ~fileMask & nextRankMask & friendlyPawns;
			long friendlyPawnsSupporting = tripleFileMask & ~fileMask & prevRankMask & friendlyPawns;
			evaluation += 5 * Long.bitCount(friendlyPawnsSupported) * Long.bitCount(friendlyPawnsSupported) + 5;
			evaluation += 5 * Long.bitCount(friendlyPawnsSupporting) * Long.bitCount(friendlyPawnsSupporting) + 5;
			 
		}

		return evaluation;
	}

	public float forceKingToCorner(final int friendlyKingIndex, final int enemyKingIndex, final float endgameWeight)
	{
		if (endgameWeight < 0)
			return 0;

		float evaluation = (PrecomputedData.distToCenter[enemyKingIndex] * 15)
				+ (PrecomputedData.orthoSquaresDist[(friendlyKingIndex << 6) | enemyKingIndex] * 8);

		return evaluation * endgameWeight;
	}
}
