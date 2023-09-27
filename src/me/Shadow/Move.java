package me.Shadow;

public class Move
{
	static final Move NULL_MOVE = new Move(0);
	
	static final int NO_FLAG = 0b0000;
	static final int EN_PASSANT_CAPTURE_FLAG = 0b0001;
	static final int PAWN_DOUBLE_PUSH_FLAG = 0b0010;
	static final int CASTLING_FLAG = 0b0011;
	static final int PROMOTION_QUEEN_FLAG = 0b0100;
	static final int PROMOTION_ROOK_FLAG = 0b0101;
	static final int PROMOTION_BISHOP_FLAG = 0b0110;
	static final int PROMOTION_KNIGHT_FLAG = 0b0111;
	
	private short data;
	
	public Move (int start, int target, int flags)
	{
		data |= (start | (target << 6) | (flags << 12));
	}
	
	public Move (int data)
	{
		this.data = (short) (data & 0xFFFF);
	}
	
	public int getStartIndex()
	{
		return (data & 0b111111); // isolate last 6 bits
	}
	
	public int getTargetIndex()
	{
		return ((data >>> 6) & 0b111111); // 64 in binary bit shifted right 6 bits
	}
	
	private int isolateFlags()
	{
		return (data >>> 12) & 15;
	}
	
	public int getEnPassantCaptureIndex()
	{
		if (isolateFlags() == EN_PASSANT_CAPTURE_FLAG)
		{
			return ((getStartIndex() / 8) * 8) + (getTargetIndex() % 8);
		}
		return -1;
	}
	
	public int getEnPassantNewIndex()
	{
		if (isolateFlags() == PAWN_DOUBLE_PUSH_FLAG)
		{
			return (getStartIndex() + getTargetIndex()) / 2;
		}
		return -1;
	}
	
	public int getPromotedPiece()
	{
		int promotion = isolateFlags();
		if (promotion >= PROMOTION_QUEEN_FLAG && promotion <= PROMOTION_KNIGHT_FLAG)
		{
			return ((promotion - PROMOTION_QUEEN_FLAG) + 1);
		}
		return 0;
	}
	
	public boolean isCastleMove()
	{
		return (isolateFlags() == CASTLING_FLAG);
	}
	
	public int getRookStartIndex()
	{
		if (!isCastleMove()) return -1;
		if (getStartIndex() < getTargetIndex()) return (getStartIndex() + 3);
		else return (getStartIndex() - 4);
	}
	
	public int getRookTargetIndex()
	{
		if (!isCastleMove()) return -1;
		if (getStartIndex() < getTargetIndex()) return (getTargetIndex() - 1);
		else return (getTargetIndex() + 1);
	}
	
	public short getData()
	{
		return data;
	}
	
	public String toString()
	{
		String notation = Utils.getSquareName(getStartIndex());
		notation += Utils.getSquareName(getTargetIndex());
		if (getPromotedPiece() != 0)
		{
			notation += Piece.getPieceSymbol(getPromotedPiece() | Piece.BLACK_PIECE);
		}
		return notation;
	}
	
	public String toStringLong()
	{
		String notation = toString();
		if (getEnPassantCaptureIndex() != -1) notation += " En Passant";
		if (getPromotedPiece() != 0) notation += " Promotion to " + getPromotedPiece();
		if (isCastleMove())
		{
			notation += " Castling: ";
			notation += Utils.getSquareName(getRookStartIndex());
			notation += Utils.getSquareName(getRookTargetIndex());
		}
		return notation;
	}
	
	@Override
	public boolean equals(Object o)
	{
		if (o == this) return true;
		if (!(o instanceof Move)) return false;
		Move move = (Move)o;
		if (this.data == move.data) return true;
		return false;
	}
	
	@Override
	public int hashCode()
	{
		return Short.hashCode(data);
	}
}
