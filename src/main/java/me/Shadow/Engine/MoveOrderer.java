package me.Shadow.Engine;

public class MoveOrderer
{
	public static final int MILLION = 1000000;
	
	public static final int MAX_MOVE_BIAS = 2100 * MILLION;
	public static final int HASH_MOVE_BIAS = 2050 * MILLION;
	public static final int PV_MOVE_BIAS = 2000 * MILLION;
	public static final int GOOD_CAPTURE_BIAS = 1900 * MILLION;
	public static final int PROMOTING_BIAS = 1850 * MILLION;
	public static final int KILLER_MOVE_BIAS = 1800 * MILLION;
	public static final int BAD_CAPTURE_BIAS = 1700 * MILLION;
	public static final int NO_BIAS = 850 * MILLION;

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
		//historyHeuristic = new int[2 * 64 * 64];
		historyHeuristic = new int[2 * 8 * 64];
	}

	public void guessMoveEvals(short pvMove, short hashMove, final boolean inQSearch, final int ply, int startIndex, int numMoves)
	{
		float gamePhase = getGamePhase(board);
		for (int i = startIndex; i < (startIndex + numMoves); i++)
		{
			final short move = moves[i];

			
			if (move == hashMove)
			{
				scores[i] = HASH_MOVE_BIAS;
				continue;
			}
			else if (move == pvMove)
			{
				System.out.println(MoveHelper.toString(move));
				scores[i] = PV_MOVE_BIAS;
				continue;
			}
			
			final int start = MoveHelper.getStartIndex(move);
			final int target = MoveHelper.getTargetIndex(move);
			final byte piece = board.squares[start];
			final byte capturedPiece = board.squares[target];
			int evalGuess = 0;

			if (capturedPiece != PieceHelper.NONE)
			{
				// Most Valuable Victim - Least Valuable Aggresor estimation
				// capture best possible piece, with worst possible piece first
				int MVV = PieceHelper.getValue(capturedPiece, gamePhase);
				int LVA = PieceHelper.getValue(piece, gamePhase);

				if (MVV >= LVA)
				{
					evalGuess = GOOD_CAPTURE_BIAS + MVV * 100 - LVA;
				}
				else if(!inQSearch)
				{
					int captureSEE = SEE(board, move, gamePhase);
					if (captureSEE >= 0)
					{
						evalGuess = GOOD_CAPTURE_BIAS + MVV * 100 - LVA;
					}
					else
					{
						evalGuess += BAD_CAPTURE_BIAS + captureSEE;
					}
				}
				else
				{
					evalGuess += BAD_CAPTURE_BIAS;
				}

				scores[i] = evalGuess;
				continue;
			}

			if (MoveHelper.getPromotedPiece(move) != PieceHelper.NONE)
			{
				evalGuess += PROMOTING_BIAS + PieceHelper.getValue(MoveHelper.getPromotedPiece(move), gamePhase)
						- PieceHelper.getValue(piece, gamePhase);
			}

			evalGuess -= PieceHelper.getPieceSquareValue(piece, start, gamePhase);
			evalGuess += PieceHelper.getPieceSquareValue(piece, target, gamePhase);

			// not a capture move, killers rank below winning captures and killer move
			// unlikely to be losing capture
			final boolean isKillerMove = !inQSearch && ply < maxKillerDepth && isKiller(move, ply);
			if (isKillerMove)
			{
				evalGuess += KILLER_MOVE_BIAS;
			}
			else
			{
				evalGuess += NO_BIAS;
				// keep start/target square and add color to move for index
				//int index = (move & 0xFFF) | (PieceHelper.getColor(piece) << 12);
				int index = (piece << 6) | target;
				evalGuess += 50 * historyHeuristic[index];
			}
			
			scores[i] = evalGuess;
		}
	}
	
	public int SEE(Board board, short move, float mgWeight)
	{
		int start = MoveHelper.getStartIndex(move);
		byte piece = board.squares[start];
		int seeSquare = MoveHelper.getTargetIndex(move);
		int capturePieceValue = PieceHelper.getValue(board.squares[seeSquare], mgWeight);
		if (MoveHelper.getEPCaptureIndex(move) != -1)
		{
			capturePieceValue = PieceHelper.getValue(PieceHelper.PAWN, mgWeight);
		}
		
		return SEE(board, start, seeSquare, piece, capturePieceValue, mgWeight);
	}

	public int SEE(Board board, int start, int target, byte piece, int trophyValue, float mgWeight)
	{
		int[] gain = new int[32];
		long xrayPieces = board.bitBoards.getXrayPieces();
		long fromBitboard = 1L << start;
		long allPieces = board.bitBoards.colorBoards[PieceHelper.WHITE]
				| board.bitBoards.colorBoards[PieceHelper.BLACK];
		long squareAtksDefs = board.bitBoards.getAttacksTo(target, allPieces);
		
		int depth = 0;
		int color = piece & PieceHelper.COLOR_MASK;
		gain[depth] = trophyValue;
		do
		{
			depth++;
			
			if (PieceHelper.getPieceType(piece) == PieceHelper.PAWN && (target >= 56 || target < 8))
			{
				gain[depth-1] += PieceHelper.getValue(PieceHelper.QUEEN, mgWeight) - PieceHelper.getValue(PieceHelper.PAWN, mgWeight);
				gain[depth] = PieceHelper.getValue(PieceHelper.QUEEN, mgWeight) - gain[depth - 1];
			}
			else
			{
				gain[depth] = PieceHelper.getValue(piece, mgWeight) - gain[depth - 1];
			}

			
			if (gain[depth] < 0 && -gain[depth - 1] < 0)
				break;
				

			squareAtksDefs &= ~fromBitboard;
			allPieces &= ~fromBitboard;
			if ((xrayPieces & fromBitboard) != 0)
			{
				squareAtksDefs |= considerXrays(board, allPieces, target);
			}
			color ^= PieceHelper.BLACK;
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
		// return isKiller(killers[ply], move);
		return isKiller(killers[ply], move) || (ply > 2 && isKiller(killers[ply - 2], move));
	}

	public static boolean isKiller(long killerPair, short move)
	{
		return ((killerPair & 0xFFFF) == move || ((killerPair >>> 16) & 0xFFFF) == move
				|| ((killerPair >>> 32) & 0xFFFF) == move || ((killerPair >>> 48) & 0xFFFF) == move);
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
