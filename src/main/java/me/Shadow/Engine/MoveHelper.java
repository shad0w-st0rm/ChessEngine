package me.Shadow.Engine;

public class MoveHelper
{
	public static final short NULL_MOVE = 0;

	public static final int NO_FLAG = 0b0000;
	public static final int EN_PASSANT_CAPTURE_FLAG = 0b0001;
	public static final int PAWN_DOUBLE_PUSH_FLAG = 0b0010;
	public static final int CASTLING_FLAG = 0b0011;
	
	public static final int PROMOTION_KNIGHT_FLAG = 0b0100;
	public static final int PROMOTION_BISHOP_FLAG = 0b0101;
	public static final int PROMOTION_ROOK_FLAG = 0b0110;
	public static final int PROMOTION_QUEEN_FLAG = 0b0111;

	public static short createMove(int start, int target, int flags)
	{
		return (short) (start | (target << 6) | (flags << 12));
	}

	public static int getStartIndex(short move)
	{
		return (move & 0b111111); // isolate last 6 bits
	}

	public static int getTargetIndex(short move)
	{
		return ((move >>> 6) & 0b111111); // shift down 6 bits and then isolate last 6 bits
	}
	
	public static boolean isSpecial(short move)
	{
		return isolateFlags(move) != NO_FLAG;
	}

	public static int getEPCaptureIndex(short move)
	{
		if (isolateFlags(move) == EN_PASSANT_CAPTURE_FLAG)
		{
			return ((getStartIndex(move) / 8) * 8) + (getTargetIndex(move) % 8);
		}
		return -1;
	}
	
	public static int getNewEPIndex(short move)
	{
		if (isolateFlags(move) == PAWN_DOUBLE_PUSH_FLAG)
		{
			return (getStartIndex(move) + getTargetIndex(move)) / 2;
		}
		return -1;
	}

	public static byte getPromotedPiece(short move)
	{
		int promotion = isolateFlags(move);
		if (promotion >= PROMOTION_KNIGHT_FLAG && promotion <= PROMOTION_QUEEN_FLAG)
		{
			return (byte) ((promotion - 2) << 1);
		}
		return 0;
	}

	public static boolean isCastleMove(short move)
	{
		return (isolateFlags(move) == CASTLING_FLAG);
	}

	public static int getRookStartIndex(short move)
	{
		if (!isCastleMove(move))
			return -1;
		if (getStartIndex(move) < getTargetIndex(move))
			return (getStartIndex(move) + 3);
		else
			return (getStartIndex(move) - 4);
	}

	public static int getRookTargetIndex(short move)
	{
		if (!isCastleMove(move))
			return -1;
		if (getStartIndex(move) < getTargetIndex(move))
			return (getTargetIndex(move) - 1);
		else
			return (getTargetIndex(move) + 1);
	}

	public static int isolateFlags(short move)
	{
		return (move >>> 12) & 15;
	}

	public static String toString(short move)
	{
		String notation = Utils.getSquareName(getStartIndex(move));
		notation += Utils.getSquareName(getTargetIndex(move));
		if (getPromotedPiece(move) != 0)
		{
			notation += PieceHelper.getPieceSymbol((byte) (getPromotedPiece(move) | PieceHelper.BLACK));
		}
		return notation;
	}
	
	public static boolean isCapture(Board board, short move)
	{
		if (board.squares[getTargetIndex(move)] != PieceHelper.NONE) return true;
		return false;
	}
}
