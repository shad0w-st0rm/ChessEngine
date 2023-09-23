package me.Shadow;

public class Bitboards
{
	long [] pieceBoards;
	long [] colorBoards;
	
	public Bitboards(Board board)
	{
		pieceBoards = new long[Piece.BLACK_KING + 1];
		colorBoards = new long[2];
		for (Square square : board.squares)
		{
			if (square.getPiece() != Piece.NONE)
			{
				int pieceInfo = square.getPiece();
				pieceBoards[pieceInfo] |= (1L << square.getIndex());
				colorBoards[pieceInfo >>> 3] |= (1L << square.getIndex());
			}
		}
	}
	
	public long getAllFriendlyPieces(int pieceColor)
	{
		return colorBoards[pieceColor >>> 3];
	}
	
	public long getAllEnemyPieces(int pieceColor)
	{
		return getAllFriendlyPieces(pieceColor ^ Piece.BLACK_PIECE);
	}
	
	public long getOrthogonalSliders(int pieceColor)
	{
		return pieceBoards[Piece.ROOK | pieceColor] | pieceBoards[Piece.QUEEN | pieceColor];
	}
	
	public long getDiagonalSliders(int pieceColor)
	{
		return pieceBoards[Piece.BISHOP | pieceColor] | pieceBoards[Piece.QUEEN | pieceColor];
	}
	
	public void toggleSquare(int pieceInfo, int square)
	{
		pieceBoards[pieceInfo] ^= (1L << square);
		colorBoards[pieceInfo >>> 3] ^= (1L << square);
	}
	
	public static long shift(long bitboard, int shiftAmount)
	{
		if (shiftAmount > 0) return bitboard << shiftAmount;
		else return bitboard >>> (-shiftAmount);
	}
	
	public static long toggleBit(long board, int bit)
	{
		return board ^ (1L << bit);
	}
	
	public static int getLSB(long board)
	{
		return Long.numberOfTrailingZeros(board);
	}
	
	public static int getMSB(long board)
	{
		return 63 - Long.numberOfLeadingZeros(board);
	}
}
