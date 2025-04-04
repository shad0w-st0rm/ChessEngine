package me.Shadow.Engine;

public class Bitboards
{
	public long [] pieceBoards;
	long [] colorBoards;
	
	public Bitboards(Board board)
	{
		createBitboards(board);
	}
	
	public void createBitboards(Board board)
	{
		pieceBoards = new long[PieceHelper.BLACK_KING + 1];
		colorBoards = new long[2];
		for (int i = 0; i < 64; i++)
		{
			int piece = board.squares[i];
			if (piece != PieceHelper.NONE)
			{
				pieceBoards[piece] |= (1L << i);
				colorBoards[piece >>> 3] |= (1L << i);
			}
		}
	}
	
	public long getAllFriendlyPieces(int pieceColor)
	{
		return colorBoards[pieceColor >>> 3];
	}
	
	public long getOrthogonalSliders(int pieceColor)
	{
		return pieceBoards[PieceHelper.ROOK | pieceColor] | pieceBoards[PieceHelper.QUEEN | pieceColor];
	}
	
	public long getDiagonalSliders(int pieceColor)
	{
		return pieceBoards[PieceHelper.BISHOP | pieceColor] | pieceBoards[PieceHelper.QUEEN | pieceColor];
	}
	
	public void toggleSquare(int pieceInfo, int square)
	{
		pieceBoards[pieceInfo] ^= (1L << square);
		colorBoards[pieceInfo >>> 3] ^= (1L << square);
	}
	
	public int getNumPawns(int pieceColor)
	{
		return Long.bitCount(pieceBoards[PieceHelper.PAWN | pieceColor]);
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
