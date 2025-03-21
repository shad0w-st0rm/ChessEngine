package me.Shadow.Engine;

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
	
	public static short getMoveFromUCINotation(Board board, String uciMove)
	{
		MoveGenerator moveGen = new MoveGenerator(board);
		short [] moves = moveGen.generateMoves(false);
		
		uciMove = uciMove.replace("=", ""); // promotions sometimes have = (e.g. d7d8=q vs d7d8q)
		for (short move : moves)
		{
			if (MoveHelper.toString(move).equals(uciMove)) return move;
		}
		return MoveHelper.NULL_MOVE;
	}
	
	public static short getMoveFromAlgebraicNotation(Board board, String algebraicMove)
	{
		MoveGenerator moveGen = new MoveGenerator(board);
		short [] moves = moveGen.generateMoves(false);
		
		algebraicMove = algebraicMove.replace("-", "").replace("x", "").replace("+", "").replace("#", "");
		for (short move : moves)
		{
			if (algebraicMove.equals("OO"))
			{
				if (MoveHelper.isCastleMove(move) && MoveHelper.getRookStartIndex(move) > MoveHelper.getStartIndex(move)) return move;
			}
			else if (algebraicMove.equals("OOO"))
			{
				if (MoveHelper.isCastleMove(move) && MoveHelper.getRookStartIndex(move) < MoveHelper.getStartIndex(move)) return move;
			}
			else
			{
				int start = MoveHelper.getStartIndex(move);
				int target = MoveHelper.getTargetIndex(move);
				int piece = PieceHelper.getPieceType(board.squares[start]); // treat all pieces as white pieces
				
				char firstCharacter = algebraicMove.charAt(0);
				if (firstCharacter >= 97 && firstCharacter <= 104) // pawn move because first character is a file name
				{
					if (piece != PieceHelper.PAWN) continue;
					
					if (!Utils.getSquareName(start).contains(firstCharacter + "")) continue;
					
					if (algebraicMove.contains("="))
					{
						String squareName = algebraicMove.substring(algebraicMove.length() - 4, algebraicMove.length() - 2);
						if (!Utils.getSquareName(target).equals(squareName)) continue;
						
						char promotedSymbol = PieceHelper.getPieceSymbol(MoveHelper.getPromotedPiece(move)); // will return uppercase symbol
						if (promotedSymbol == algebraicMove.charAt(algebraicMove.length() - 1)) return move;
					}
					else
					{
						String squareName = algebraicMove.substring(algebraicMove.length() - 2);
						if (Utils.getSquareName(target).equals(squareName)) return move;
					}
				}
				else
				{
					if (firstCharacter != PieceHelper.getPieceSymbol(piece)) continue;
					
					String squareName = algebraicMove.substring(algebraicMove.length() - 2);
					if (!Utils.getSquareName(target).equals(squareName)) continue;
					
					// no specification character
					if (algebraicMove.length() != 4) return move;
					
					String specificationCharacter = algebraicMove.substring(1, 2);
					if (Utils.getSquareName(start).contains(specificationCharacter))
					{
						return move;
					}
				}
			}
		}
		
		return MoveHelper.NULL_MOVE;
	}
}
