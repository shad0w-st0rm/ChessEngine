package me.Shadow.Engine;

import java.util.Arrays;

public class MoveOrderer
{
	public static final int million = 1000000;
	public static final int maxMoveBias = 10 * million;
	public static final int firstMoveBias = 5 * million;
	public static final int goodCaptureBias = 4 * million;
	public static final int promotingBias = 3 * million;
	public static final int killerMoveBias = 2 * million;
	public static final int badCaptureBias = 1 * million;
	public static final int noBias = 0;

	public static final int maxKillerDepth = 32;
	KillerMove[] killers;
	int[] historyHeuristic;

	public MoveOrderer()
	{
		killers = new KillerMove[maxKillerDepth];
		for (int i = 0; i < maxKillerDepth; i++)
		{
			killers[i] = new KillerMove();
		}
		historyHeuristic = new int[2 * 64 * 64];
	}

	public int[] guessMoveEvals(final Board board, final short[] moves, final short firstMove,
			final boolean inQuietSearch, final int ply)
	{
		float gamePhase = getGamePhase(board);
		final int[] moveEvals = new int[moves.length];
		for (int i = 0; i < moves.length; i++)
		{
			final short move = moves[i];

			if (move == firstMove)
			{
				moveEvals[i] = firstMoveBias;
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
					evalGuess = goodCaptureBias + MVV*2 - LVA;
				}
				else
				{
					int captureSEE = SEE(board, start, target, piece, capturedPiece, gamePhase);
					if (captureSEE >= 0)
					{
						evalGuess = goodCaptureBias + MVV*2 - LVA;
					}
					else
					{
						evalGuess += badCaptureBias + captureSEE;
					}
				}
				
				moveEvals[i] = evalGuess;
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
			final boolean isKillerMove = !inQuietSearch && ply < maxKillerDepth && killers[ply].isKiller(move);
			if (isKillerMove)
				evalGuess += killerMoveBias;
			else
				evalGuess += noBias;

			// multiply the color index by 64*64 = 2^12 except only shift 9 times because
			// color index is already shifted left 3 times
			// then add start square multiplied by 64 = 2^6
			// then add target square
			final int index = (PieceHelper.getColor(piece) << 9) | (start << 6) | target;
			evalGuess += historyHeuristic[index];

			moveEvals[i] = evalGuess;
		}
		// quickSort(moves, moveEvals, 0, moveCount - 1);
		// insertionSort(moves, moveEvals);
		// binaryInsertionSort(moves, moveEvals);
		return moveEvals;
	}

	public int SEE(Board board, int start, int target, int piece, int captured, float mgWeight)
	{
		int[] gain = new int[32];
		long xrayPieces = board.bitBoards.getXrayPieces();
		long fromBitboard = 1L << start;
		long allPieces = board.bitBoards.getAllPieces();
		long squareAtksDefs = board.bitBoards.getAttacksTo(target);
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
		killers = new KillerMove[maxKillerDepth];
		for (int i = 0; i < maxKillerDepth; i++)
		{
			killers[i] = new KillerMove();
		}
	}

	public void clearHistoryHeuristic()
	{
		historyHeuristic = new int[2 * 64 * 64];
	}

	public static void singleSelectionSort(final short[] moves, final int[] scores, final int startIndex)
	{
		final int length = moves.length;
		int bestMoveIndex = startIndex;
		int bestScore = scores[startIndex];
		for (int i = startIndex; i < length; i++)
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

	public static void binaryInsertionSort(short[] moves, int[] scores)
	{
		for (int i = 1; i < scores.length; i++)
		{
			int key = scores[i];
			short moveKey = moves[i];

			// find insert location using binary search
			int j = Math.abs(Arrays.binarySearch(scores, 0, i, key) + 1);

			// shift array one location over to the right
			System.arraycopy(scores, j, scores, j + 1, i - j);
			System.arraycopy(moves, j, moves, j + 1, i - j);

			// place element in emptied spot
			scores[j] = key;
			moves[j] = moveKey;
		}
	}

	public static void insertionSort(short[] moves, int[] scores)
	{
		int n = scores.length;
		for (int j = 1; j < n; j++)
		{
			short moveKey = moves[j];
			int key = scores[j];
			int i = j - 1;
			while (i > -1 && scores[i] < key)
			{
				moves[i + 1] = moves[i];
				scores[i + 1] = scores[i];
				i--;
			}
			scores[i + 1] = key;
			moves[i + 1] = moveKey;
		}
	}

	public static void quickSort(short[] moves, int[] scores, int low, int high)
	{
		if (low < high)
		{
			int pivotIndex = partition(moves, scores, low, high);
			quickSort(moves, scores, low, pivotIndex - 1);
			quickSort(moves, scores, pivotIndex + 1, high);
		}
	}

	public static int partition(short[] moves, int[] scores, int low, int high)
	{
		int pivotScore = scores[high];
		int i = low - 1;

		for (int j = low; j <= high - 1; j++)
		{
			if (scores[j] > pivotScore)
			{
				i++;
				short moveTemp = moves[i];
				moves[i] = moves[j];
				moves[j] = moveTemp;

				int scoreTemp = scores[i];
				scores[i] = scores[j];
				scores[j] = scoreTemp;
			}
		}
		short moveTemp = moves[i + 1];
		moves[i + 1] = moves[high];
		moves[high] = moveTemp;

		int scoreTemp = scores[i + 1];
		scores[i + 1] = scores[high];
		scores[high] = scoreTemp;

		return i + 1;
	}

	class KillerMove
	{
		short firstKiller = MoveHelper.NULL_MOVE;
		short secondKiller = MoveHelper.NULL_MOVE;

		public void addKiller(short move)
		{
			if (move != firstKiller)
			{
				secondKiller = firstKiller;
				firstKiller = move;
			}
		}

		public boolean isKiller(short move)
		{
			return (move == firstKiller || move == secondKiller);
		}
	}
}
