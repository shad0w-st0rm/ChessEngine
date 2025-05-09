package me.Shadow.Engine;

public class Bitboards
{
	public long[] pieceBoards;
	long[] colorBoards;

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
				colorBoards[piece & PieceHelper.COLOR_MASK] |= (1L << i);
			}
		}
	}

	public long getAllPieces()
	{
		return colorBoards[0] | colorBoards[1];
	}

	public long getAllFriendlyPieces(byte pieceColor)
	{
		return colorBoards[pieceColor];
	}

	public long getOrthogonalSliders(byte pieceColor)
	{
		return pieceBoards[PieceHelper.ROOK | pieceColor] | pieceBoards[PieceHelper.QUEEN | pieceColor];
	}

	public long getDiagonalSliders(byte pieceColor)
	{
		return pieceBoards[PieceHelper.BISHOP | pieceColor] | pieceBoards[PieceHelper.QUEEN | pieceColor];
	}

	public long getXrayPieces()
	{
		return pieceBoards[PieceHelper.WHITE_QUEEN] | pieceBoards[PieceHelper.BLACK_QUEEN]
				| pieceBoards[PieceHelper.WHITE_ROOK] | pieceBoards[PieceHelper.BLACK_ROOK]
				| pieceBoards[PieceHelper.WHITE_BISHOP] | pieceBoards[PieceHelper.BLACK_BISHOP]
				| pieceBoards[PieceHelper.WHITE_PAWN] | pieceBoards[PieceHelper.BLACK_PAWN];
	}

	public long getAttacksTo(int index, long allPieces)
	{
		long knights, kings, diagSliders, orthoSliders;
		knights = (pieceBoards[PieceHelper.WHITE_KNIGHT] | pieceBoards[PieceHelper.BLACK_KNIGHT])
				& (PrecomputedData.KNIGHT_MOVES[index]);
		kings = (pieceBoards[PieceHelper.WHITE_KING] | pieceBoards[PieceHelper.BLACK_KING])
				& (PrecomputedData.KING_MOVES[index]);
		diagSliders = orthoSliders = pieceBoards[PieceHelper.WHITE_QUEEN] | pieceBoards[PieceHelper.BLACK_QUEEN];
		diagSliders |= pieceBoards[PieceHelper.WHITE_BISHOP] | pieceBoards[PieceHelper.BLACK_BISHOP];
		orthoSliders |= pieceBoards[PieceHelper.WHITE_ROOK] | pieceBoards[PieceHelper.BLACK_ROOK];
		diagSliders &= PrecomputedMagicNumbers.getBishopMoves(index, allPieces);
		orthoSliders &= PrecomputedMagicNumbers.getRookMoves(index, allPieces);

		return (knights | kings | diagSliders | orthoSliders
				| (PrecomputedData.getPawnCaptures(index, PieceHelper.WHITE) & pieceBoards[PieceHelper.BLACK_PAWN])
				| (PrecomputedData.getPawnCaptures(index, PieceHelper.BLACK) & pieceBoards[PieceHelper.WHITE_PAWN]));
	}

	public long getAttacksFrom(int index, byte piece, long allPieces)
	{
		long attacks = 0;
		int type = piece & PieceHelper.TYPE_MASK;
		if (type == PieceHelper.KNIGHT)
			attacks = PrecomputedData.KNIGHT_MOVES[index];
		if (type == PieceHelper.KING)
			attacks = PrecomputedData.KING_MOVES[index];
		if (type == PieceHelper.ROOK || type == PieceHelper.QUEEN)
			attacks |= PrecomputedMagicNumbers.getRookMoves(index, allPieces);
		if (type == PieceHelper.BISHOP || type == PieceHelper.QUEEN)
			attacks |= PrecomputedMagicNumbers.getBishopMoves(index, allPieces);
		if (type == PieceHelper.PAWN)
			attacks = PrecomputedData.getPawnCaptures(index, piece & PieceHelper.COLOR_MASK);

		return attacks;
	}

	public void toggleSquare(byte pieceInfo, int square)
	{
		pieceBoards[pieceInfo] ^= (1L << square);
		colorBoards[pieceInfo & PieceHelper.COLOR_MASK] ^= (1L << square);
	}

	public int getNumPawns(byte pieceColor)
	{
		return Long.bitCount(pieceBoards[PieceHelper.PAWN | pieceColor]);
	}

	public static long shift(long bitboard, int shiftAmount)
	{
		if (shiftAmount > 0)
			return bitboard << shiftAmount;
		else
			return bitboard >>> (-shiftAmount);
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
