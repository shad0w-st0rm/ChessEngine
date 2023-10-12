package me.Shadow.Engine;

import java.util.Arrays;

public class MoveOrderer
{
	public static final int million = 1000000;
	public static final int maxMoveBias = 10*million;
	public static final int firstMoveBias = 5*million;
	public static final int goodCaptureBias = 4*million;
	public static final int promotingBias = 3*million;
	public static final int killerMoveBias = 2*million;
	public static final int badCaptureBias = 1*million;
	public static final int noBias = 0;
	
	public static final int maxKillerDepth = 32;
	KillerMove [] killers;
	int [] historyHeuristic;
	
	public MoveOrderer()
	{
		killers = new KillerMove[maxKillerDepth];
		for (int i = 0; i < maxKillerDepth; i++)
		{
			killers[i] = new KillerMove();
		}
		historyHeuristic = new int[2*64*64];
	}
	
	public int[] guessMoveEvals(final Board board, final short[] moves, final short firstMove, final long enemyAttackMap, final long enemyPawnAttackMap, final boolean inQuietSearch, final int ply)
	{
		final boolean endgame = board.boardInfo.getWhiteMaterial() + board.boardInfo.getBlackMaterial() < 4000;
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
				final int materialDifference = PieceHelper.getValue(capturedPiece) - PieceHelper.getValue(piece);
				
				// theoretically this is not 100% accurate because the attack map is based on the position prior to the piece moving
				final boolean canOpponentRecapture = ((enemyAttackMap & (1L << target)) != 0);	// enemy attacks this square
				if (canOpponentRecapture)
				{
					evalGuess += (materialDifference >= 0 ? goodCaptureBias : badCaptureBias) + materialDifference;
				}
				else
				{
					evalGuess += goodCaptureBias + materialDifference;
				}
			}
			else if (MoveHelper.getPromotedPiece(move) == PieceHelper.QUEEN) // dont stack capture bias and promoting bias (it will always be winning capture bias if promoting)
			{
				evalGuess += promotingBias;
			}
			
			evalGuess -= PieceHelper.getPieceSquareValue(piece, start, endgame);
			evalGuess += PieceHelper.getPieceSquareValue(piece, target, endgame);
			
			if ((enemyPawnAttackMap & (1L << target)) != 0)
			{
				evalGuess -= 50;	// square is attacked by enemy pawns
			}
			else if ((enemyAttackMap & (1L << target)) != 0)
			{
				evalGuess -= 25;	// square is attacked by some enemy piece
			}
			
			
			if (capturedPiece == PieceHelper.NONE) // not a capture move, killers rank below winning captures and killer move unlikely to be losing capture
			{
				final boolean isKillerMove = !inQuietSearch && ply < maxKillerDepth && killers[ply].isKiller(move);
				if (isKillerMove) evalGuess += killerMoveBias;
				else evalGuess += noBias;
				
				// multiply the color index by 64*64 = 2^12 except only shift 9 times because color index is already shifted left 3 times
				// then add start square multiplied by 64 = 2^6
				// then add target square
				final int index = (PieceHelper.getColor(piece) << 9) | (start << 6) | target;
				evalGuess += historyHeuristic[index];
			}
			
			
			moveEvals[i] = evalGuess;
		}
		//quickSort(moves, moveEvals, 0, moveCount - 1);
		//insertionSort(moves, moveEvals);
		//binaryInsertionSort(moves, moveEvals);
		return moveEvals;
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
		// historyHeuristic = new int[2*64*64];
		// maybe this avoids some expensive garbage collecting during a search
		for (int i = 0; i < historyHeuristic.length; i++)
		{
			historyHeuristic[i] = 0;
		}
	}
	
	public static void singleSelectionSort(final short [] moves, final int [] scores, final int startIndex)
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
	
	public static void binaryInsertionSort(short [] moves, int [] scores)
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
	
	public static void insertionSort(short [] moves, int [] scores)
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
		short moveTemp = moves[i+1];
		moves[i+1] = moves[high];
		moves[high] = moveTemp;
		
		int scoreTemp = scores[i+1];
		scores[i+1] = scores[high];
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
