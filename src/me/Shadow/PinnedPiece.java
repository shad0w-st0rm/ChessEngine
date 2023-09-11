package me.Shadow;

import me.Shadow.pieces.Piece;

public class PinnedPiece
{
	Piece pinned;
	Piece pinner;
	
	public PinnedPiece(Piece pinned, Piece pinner)
	{
		this.pinned = pinned;
		this.pinner = pinner;
	}
	
	public String toString()
	{
		return pinner.getPieceSymbol() + " pins " + pinned.getPieceSymbol();
	}
}
