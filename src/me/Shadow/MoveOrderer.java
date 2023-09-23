package me.Shadow;

public class MoveOrderer
{
	public static void guessMoveEvals(Board board, Move[] moves, Move firstMove)
	{
		boolean endgame = board.boardInfo.getWhiteMaterial() + board.boardInfo.getBlackMaterial() < 4000;
		int[] moveEvals = new int[moves.length];
		for (int i = 0 ; i < moves.length; i++)
		{
			Move move = moves[i];
			
			if (move.equals(firstMove))
			{
				moveEvals[i] =  1000000;
				continue;
			}
			
			int piece = board.squares[move.getStartIndex()];
			int evalGuess = 0;
			
			evalGuess -= Piece.getPieceSquareValue(piece, move.getStartIndex(), endgame);
			evalGuess += Piece.getPieceSquareValue(piece, move.getTargetIndex(), endgame);
			
			if (board.squares[move.getTargetIndex()] != Piece.NONE)
			{
				if (Piece.getPieceType(piece) == Piece.KING)
				{
					evalGuess += (100.0 * Piece.getValue(board.squares[move.getTargetIndex()])) / 100; // TODO: this is an arbitary value
				}
				else
					evalGuess += (100.0 * Piece.getValue(board.squares[move.getTargetIndex()])) / Piece.getValue(piece);
			}
			if (move.getPromotedPiece() != 0)
			{
				if (move.getPromotedPiece() == 1) evalGuess += 900;
				else if (move.getPromotedPiece() == 2) evalGuess += 500;
				else if (move.getPromotedPiece() == 3) evalGuess += 330;
				else if (move.getPromotedPiece() == 4) evalGuess += 320;
			}
			
			moveEvals[i] = evalGuess;
		}
		quicksort(moves, moveEvals, 0, moves.length-1);
	}
	
	public static void quicksort(Move[] moves, int[] scores, int low, int high)
	{
		if (low < high)
		{
			int pivotIndex = partition(moves, scores, low, high);
			quicksort(moves, scores, low, pivotIndex - 1);
			quicksort(moves, scores, pivotIndex + 1, high);
		}
	}

	public static int partition(Move[] moves, int[] scores, int low, int high)
	{
		int pivotScore = scores[high];
		int i = low - 1;

		for (int j = low; j <= high - 1; j++)
		{
			if (scores[j] > pivotScore)
			{
				i++;
				Move moveTemp = moves[i];
				moves[i] = moves[j];
				moves[j] = moveTemp;
				
				int scoreTemp = scores[i];
				scores[i] = scores[j];
				scores[j] = scoreTemp;
			}
		}
		Move moveTemp = moves[i+1];
		moves[i+1] = moves[high];
		moves[high] = moveTemp;
		
		int scoreTemp = scores[i+1];
		scores[i+1] = scores[high];
		scores[high] = scoreTemp;

		return i + 1;
	}
}
