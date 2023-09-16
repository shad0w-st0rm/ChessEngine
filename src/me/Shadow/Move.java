package me.Shadow;

public class Move
{
	private short data;
	private short evalGuess;
	
	public Move (int start, int target, int eval)
	{
		data |= (start | (target << 6));
		evalGuess = (short) eval;
	}
	
	public Move (int start, int target, int eval, boolean enPassantCapture, boolean enPassantNew)
	{
		this(start, target, eval);
		data |= ((enPassantCapture ? 1 : 2) << 12);
	}
	
	public Move (int start, int target, int eval, int promotedPiece)
	{
		this(start, target, eval);
		data |= ((promotedPiece + 2) << 12);
	}
	
	public Move (int start, int target, int eval, boolean castlingKingside)
	{
		this(start, target, eval);
		data |= ((castlingKingside ? 7 : 8) << 12);
	}
	
	public int getStartIndex()
	{
		return (data & 63); // isolate last 6 bits
	}
	
	public int getTargetIndex()
	{
		return ((data >>> 6) & 63); // 64 in binary bit shifted right 6 bits
	}
	
	private int isolateFlags()
	{
		return (data >>> 12) & 15;
	}
	
	public int getEnPassantCaptureIndex()
	{
		if (isolateFlags() == 1)
		{
			return ((getStartIndex() / 8) * 8) + (getTargetIndex() % 8);
		}
		return -1;
	}
	
	public int getEnPassantNewIndex()
	{
		if (isolateFlags() == 2)
		{
			return (getStartIndex() + getTargetIndex()) / 2;
		}
		return -1;
	}
	
	public int getPromotedPiece()
	{
		int promotion = isolateFlags();
		if (promotion >= 3 && promotion <= 6)
		{
			return (promotion - 2);
		}
		return 0;
	}
	
	public boolean isCastleMove()
	{
		int flags = isolateFlags();		
		return (flags == 7 || flags == 8);
	}
	
	public int getRookStartIndex()
	{
		if (!isCastleMove()) return -1;
		int flags = isolateFlags();
		if (flags == 7) return (getStartIndex() + 3);
		else return (getStartIndex() - 4);
	}
	
	public int getRookTargetIndex()
	{
		if (!isCastleMove()) return -1;
		int flags = isolateFlags();
		if (flags == 7) return (getTargetIndex() - 1);
		else return (getTargetIndex() + 1);
	}
	
	public short getData()
	{
		return data;
	}
	
	public int getEvalGuess()
	{
		return evalGuess;
	}
	public void setEvalGuess(int eval)
	{
		evalGuess = (short) eval;
	}
	
	public String toString()
	{
		String notation = ((char)((getStartIndex() % 8) + 97) + "" + (8 - (getStartIndex() / 8)));
		notation += ((char)((getTargetIndex() % 8) + 97) + "" + (8 - (getTargetIndex() / 8)));
		if (getEnPassantCaptureIndex() != -1) notation += " En Passant";
		if (getPromotedPiece() != 0) notation += " Promotion to " + getPromotedPiece();
		if (isCastleMove())
		{
			notation += " Castling: ";
			notation += ((char)((getRookStartIndex() % 8) + 97) + "" + (8 - (getRookStartIndex() / 8)));
			notation += ((char)((getRookTargetIndex() % 8) + 97) + "" + (8 - (getRookTargetIndex() / 8)));
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
}
