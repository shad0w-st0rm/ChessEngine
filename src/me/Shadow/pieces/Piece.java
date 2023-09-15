package me.Shadow.pieces;

import me.Shadow.Square;


public interface Piece
{
	public Square getSquare();
	public void setSquare(Square squareIn);
	public boolean isWhite();
	public String toString();
	public String getName();
	public int getValue();
	public char getPieceSymbol();
	public int getZobristOffset();
	public int[] getPieceSquareTable(boolean endgame);
}
