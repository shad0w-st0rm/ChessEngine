package me.Shadow.pieces;

import me.Shadow.Square;

public class Bishop implements Piece
{
	private Square square;
	private boolean isWhite;
	private boolean isCaptured;
	
	public Bishop(Square squareIn, boolean isWhiteIn)
	{
		square = squareIn;
		isWhite = isWhiteIn;
	}
	
	@Override
	public Square getSquare() { return square; }

	@Override
	public void setSquare(Square squareIn) { square = squareIn; }

	@Override
	public boolean isWhite() { return isWhite; }

	@Override
	public String getName() { return "BISHOP"; }

	@Override
	public int getValue() { return 330; }
	
	@Override
	public char getPieceSymbol() { return isWhite ? 'B' : 'b'; }
	
	@Override
	public int getZobristOffset() { return isWhite ? 6 : 7; }
	
	@Override
	public int[] getPieceSquareTable(boolean endgame)
	{
		int [] table = {-20,-10,-10,-10,-10,-10,-10,-20,
						-10,  0,  0,  0,  0,  0,  0,-10,
						-10,  0,  5, 10, 10,  5,  0,-10,
						-10,  5,  5, 10, 10,  5,  5,-10,
						-10,  0, 10, 10, 10, 10,  0,-10,
						-10, 10, 10, 10, 10, 10, 10,-10,
						-10,  5,  0,  0,  0,  0,  5,-10,
						-20,-10,-10,-10,-10,-10,-10,-20};
		return table;
	}
}
