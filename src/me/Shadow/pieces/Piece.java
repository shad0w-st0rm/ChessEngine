package me.Shadow.pieces;

import me.Shadow.Square;


public interface Piece
{
	public Square getSquare();
	public void setSquare(Square squareIn);
	public boolean isWhite();
	public boolean isCaptured();
	public void setCaptured(boolean captured);
	public String toString();
	public String getName();
	public int getValue();
	public char getPieceSymbol();
	public int getZobristOffset();
	//public Piece getPiecePinning();
	//public void setPiecePinning(Piece piece);
	//public Piece getPiecePinned();
	//public void setPiecePinned(Piece piece);
	public int[] getPieceSquareTable(boolean endgame);
}
