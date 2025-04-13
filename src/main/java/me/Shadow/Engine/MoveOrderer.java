package me.Shadow.Engine;

public class MoveOrderer
{
	public static final int million = 1000000;
	public static final int maxMoveBias = 10 * million;
	public static final int firstMoveBias = 6 * million;
	public static final int goodCaptureBias = 4 * million;
	public static final int promotingBias = 3 * million;
	public static final int killerMoveBias = 2 * million;
	public static final int badCaptureBias = 1 * million;
	public static final int noBias = 0;

	public static final int maxKillerDepth = 32;

	Board board;
	private int[] scores;
	private short[] moves;
	long[] killers;
	int[] historyHeuristic;

	public MoveOrderer(Board boardIn, short[] movesIn)
	{
		board = boardIn;
		moves = movesIn;
		scores = new int[movesIn.length];

		killers = new long[maxKillerDepth];
		historyHeuristic = new int[2 * 64 * 64];
	}

	public void guessMoveEvals(final short firstMove, final boolean inQuietSearch, final int ply, int startIndex,
			int numMoves)
	{
		float gamePhase = getGamePhase(board);
		for (int i = startIndex; i < (startIndex + numMoves); i++)
		{
			final short move = moves[i];

			if (move == firstMove)
			{
				scores[i] = firstMoveBias;
				continue;
			}
			final int start = MoveHelper.getStartIndex(move);
			final int target = MoveHelper.getTargetIndex(move);
			final int piece = board.squares[start];
			final int capturedPiece = board.squares[target];
			int evalGuess = 0;

			if (capturedPiece != PieceHelper.NONE)
			{
				// Most Valuable Victim - Least Valuable Aggresor estimation
				// capture best possible piece, with worst possible piece first
				int MVV = PieceHelper.getValue(capturedPiece, gamePhase);
				int LVA = PieceHelper.getValue(piece, gamePhase);

				if (MVV >= LVA)
				{
					evalGuess = goodCaptureBias + MVV * 100 - LVA;
				}
				else
				{
					int captureSEE = SEE(board, start, target, piece, capturedPiece, gamePhase);
					if (captureSEE >= 0)
					{
						evalGuess = goodCaptureBias + MVV * 100 - LVA;
					}
					else
					{
						evalGuess += badCaptureBias + captureSEE;
					}
				}

				scores[i] = evalGuess;
				continue;
			}

			if (MoveHelper.getPromotedPiece(move) != PieceHelper.NONE)
			{
				evalGuess += promotingBias + PieceHelper.getValue(MoveHelper.getPromotedPiece(move), gamePhase)
						- PieceHelper.getValue(piece, gamePhase);
			}

			evalGuess -= PieceHelper.getPieceSquareValue(piece, start, gamePhase);
			evalGuess += PieceHelper.getPieceSquareValue(piece, target, gamePhase);

			// not a capture move, killers rank below winning captures and killer move
			// unlikely to be losing capture
			final boolean isKillerMove = !inQuietSearch && ply < maxKillerDepth && isKiller(move, ply);
			if (isKillerMove)
				evalGuess += killerMoveBias;
			else
				evalGuess += noBias;

			// keep start/target square and add color to move for index
			int index = (move & 0xFFF) | (PieceHelper.getColor(piece) << 12);
			evalGuess += 100 * historyHeuristic[index];

			scores[i] = evalGuess;
		}
	}

	public int SEE(Board board, int start, int target, int piece, int captured, float mgWeight)
	{
		int[] gain = new int[32];
		long xrayPieces = board.bitBoards.getXrayPieces();
		long fromBitboard = 1L << start;
		long allPieces = board.bitBoards.colorBoards[PieceHelper.WHITE_PIECE]
				| board.bitBoards.colorBoards[PieceHelper.BLACK_PIECE];
		long squareAtksDefs = board.bitBoards.getAttacksTo(target, allPieces);
		int depth = 0;
		int color = piece & PieceHelper.COLOR_MASK;
		gain[depth] = PieceHelper.getValue(captured, mgWeight);
		do
		{
			depth++;
			gain[depth] = PieceHelper.getValue(piece, mgWeight) - gain[depth - 1];

			if (gain[depth] < 0 && -gain[depth - 1] < 0)
				break;

			squareAtksDefs ^= fromBitboard;
			allPieces ^= fromBitboard;
			if ((xrayPieces & fromBitboard) != 0)
			{
				squareAtksDefs |= considerXrays(board, allPieces, target);
			}
			color ^= PieceHelper.BLACK_PIECE;
			fromBitboard = getLeastValuablePiece(board, squareAtksDefs, color);
			if (fromBitboard != 0)
				piece = board.squares[Bitboards.getLSB(fromBitboard)];
		}
		while (fromBitboard != 0);

		while (depth > 1)
		{

			depth--;
			gain[depth - 1] = -Math.max(-gain[depth - 1], gain[depth]);
		}

		return gain[0];
	}

	public long getLeastValuablePiece(Board board, long pieces, int color)
	{
		for (int piece = PieceHelper.PAWN + color; piece <= PieceHelper.KING + color; piece += 2)
		{
			long pieceBoard = pieces & board.bitBoards.pieceBoards[piece];
			if (pieceBoard != 0)
				return pieceBoard & -pieceBoard;
		}
		return 0L;
	}

	public long considerXrays(Board board, long allPieces, int target)
	{
		long diagSliders, orthoSliders;
		diagSliders = orthoSliders = board.bitBoards.pieceBoards[PieceHelper.WHITE_QUEEN]
				| board.bitBoards.pieceBoards[PieceHelper.BLACK_QUEEN];
		diagSliders |= board.bitBoards.pieceBoards[PieceHelper.WHITE_BISHOP]
				| board.bitBoards.pieceBoards[PieceHelper.BLACK_BISHOP];
		orthoSliders |= board.bitBoards.pieceBoards[PieceHelper.WHITE_ROOK]
				| board.bitBoards.pieceBoards[PieceHelper.BLACK_ROOK];
		diagSliders &= PrecomputedMagicNumbers.getBishopMoves(target, allPieces) & allPieces;
		orthoSliders &= PrecomputedMagicNumbers.getRookMoves(target, allPieces) & allPieces;
		return diagSliders | orthoSliders;
	}

	public float getGamePhase(Board board)
	{
		int mgPhase = 0;
		mgPhase += Long
				.bitCount(board.bitBoards.pieceBoards[PieceHelper.WHITE_QUEEN]
						| board.bitBoards.pieceBoards[PieceHelper.BLACK_QUEEN])
				* PieceHelper.getGamePhaseValue(PieceHelper.QUEEN);

		mgPhase += Long
				.bitCount(board.bitBoards.pieceBoards[PieceHelper.WHITE_ROOK]
						| board.bitBoards.pieceBoards[PieceHelper.BLACK_ROOK])
				* PieceHelper.getGamePhaseValue(PieceHelper.ROOK);

		mgPhase += Long
				.bitCount(board.bitBoards.pieceBoards[PieceHelper.WHITE_BISHOP]
						| board.bitBoards.pieceBoards[PieceHelper.BLACK_BISHOP])
				* PieceHelper.getGamePhaseValue(PieceHelper.BISHOP);

		if (mgPhase > 24)
			mgPhase = 24;
		return mgPhase / 24.0f;
	}

	public void clearKillers()
	{
		killers = new long[maxKillerDepth];
	}

	public void clearHistoryHeuristic()
	{
		historyHeuristic = new int[2 * 64 * 64];
	}

	public boolean isKiller(short move, int ply)
	{
		//return isKiller(killers[ply], move);
		return isKiller(killers[ply], move) || (ply > 2 && isKiller(killers[ply - 2], move));
	}

	public static boolean isKiller(long killerPair, short move)
	{
		return ((killerPair & 0xFFFF) == move || ((killerPair >>> 16) & 0xFFFF) == move || ((killerPair >>> 32) & 0xFFFF) == move || ((killerPair >>> 48) & 0xFFFF) == move);
	}

	public void addKiller(short move, int ply)
	{
		if (!isKiller(killers[ply], move))
		{
			killers[ply] = (killers[ply] << 16) | move;
		}
	}

	public void singleSelectionSort(final int startIndex, int lastIndexExclusive)
	{
		int bestMoveIndex = startIndex;
		int bestScore = scores[startIndex];
		for (int i = startIndex; i < lastIndexExclusive; i++)
		{
			if (scores[i] > bestScore)
			{
				bestMoveIndex = i;
				bestScore = scores[i];
			}
		}

		final short tempMove = moves[bestMoveIndex];
		moves[bestMoveIndex] = moves[startIndex];
		moves[startIndex] = tempMove;

		final int tempScore = scores[bestMoveIndex];
		scores[bestMoveIndex] = scores[startIndex];
		scores[startIndex] = tempScore;
	}
}
