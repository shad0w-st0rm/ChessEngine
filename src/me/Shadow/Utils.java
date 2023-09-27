package me.Shadow;

public class Utils
{
	public static int getSquareRank(int index)
	{
		return (index / 8) + 1;
	}

	public static int getSquareFile(int index)
	{
		return (index % 8) + 1;
	}

	public static int getSquareIndexFromRankFile(int rank, int file)
	{
		int index = ((rank - 1) * 8) + file - 1;
		return index;
	}
	
	public static boolean isValidSquare(int rank, int file)
	{
		return (rank >= 1) && (rank <= 8) && (file >= 1) && (file <= 8);
	}

	public static String getSquareName(int rank, int file)
	{
		return getSquareName(getSquareIndexFromRankFile(rank, file));
	}

	public static String getSquareName(int index)
	{
		return ((char) ((index % 8) + 97) + "" + ((index / 8) + 1));
	}
	
	public static Move getMoveFromUCINotation(Board board, String uciMove)
	{
		MoveGenerator moveGen = new MoveGenerator(board);
		Move [] moves = moveGen.generateMoves(false);
		uciMove = uciMove.replace("=", ""); // promotions sometimes have = (e.g. d7d8=q vs d7d8q)
		for (Move move : moves)
		{
			if (move.toString().equals(uciMove)) return move;
		}
		return Move.NULL_MOVE;
	}
	
	public static Move getMoveFromAlgebraicNotation(Board board, String algebraicMove)
	{
		MoveGenerator moveGen = new MoveGenerator(board);
		Move [] moves = moveGen.generateMoves(false);
		algebraicMove = algebraicMove.replace("-", "").replace("x", "").replace("+", "").replace("#", "");
		for (Move move : moves)
		{
			if (algebraicMove.equals("OO"))
			{
				if (move.isCastleMove() && move.getRookStartIndex() > move.getStartIndex()) return move;
			}
			else if (algebraicMove.equals("OOO"))
			{
				if ((move.isCastleMove() && move.getRookStartIndex() < move.getStartIndex())) return move;
			}
			else
			{
				int piece = Piece.getPieceType(board.squares[move.getStartIndex()]); // treat all pieces as white pieces
				
				char firstCharacter = algebraicMove.charAt(0);
				if (firstCharacter >= 97 && firstCharacter <= 104) // pawn move because first character is a file name
				{
					if (piece != Piece.PAWN) continue;
					
					if (!Utils.getSquareName(move.getStartIndex()).contains(firstCharacter + "")) continue;
					
					if (algebraicMove.contains("="))
					{
						String squareName = algebraicMove.substring(algebraicMove.length() - 4, algebraicMove.length() - 2);
						if (!Utils.getSquareName(move.getTargetIndex()).equals(squareName)) continue;
						
						if (move.isCastleMove() && move.getRookStartIndex() < move.getStartIndex()) return move;
					}
					else
					{
						String squareName = algebraicMove.substring(algebraicMove.length() - 2);
						if (Utils.getSquareName(move.getTargetIndex()).equals(squareName)) return move;
					}
				}
				else
				{
					if (firstCharacter != Piece.getPieceSymbol(piece)) continue;
					
					String squareName = algebraicMove.substring(algebraicMove.length() - 2);
					if (!Utils.getSquareName(move.getTargetIndex()).equals(squareName)) continue;
					
					// no specification character
					if (algebraicMove.length() != 4) return move;
					
					String specificationCharacter = algebraicMove.substring(1, 2);
					if (Utils.getSquareName(move.getStartIndex()).contains(specificationCharacter))
					{
						return move;
					}
				}
			}
		}
		
		return Move.NULL_MOVE;
	}
}
