package me.Shadow.pieces;

import me.Shadow.Square;

public class Queen implements Piece
{
	private Square square;
	private boolean isWhite;
	private boolean isCaptured;
	
	public Queen(Square squareIn, boolean isWhiteIn)
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
	public String getName() { return "QUEEN"; }

	@Override
	public int getValue() { return 900; }
	
	@Override
	public char getPieceSymbol() { return isWhite ? 'Q' : 'q'; }
	
	@Override
	public int getZobristOffset() { return isWhite ? 2 : 3; }
	
	@Override
	public int[] getPieceSquareTable(boolean endgame)
	{
		int [] table = {-20,-10,-10, -5, -5,-10,-10,-20,
						-10,  0,  0,  0,  0,  0,  0,-10,
						-10,  0,  5,  5,  5,  5,  0,-10,
						-5,  0,  5,  5,  5,  5,  0, -5,
						0,  0,  5,  5,  5,  5,  0, -5,
						-10,  0,  5,  5,  5,  5,  0,-10,
						-10,  0,  0,  0,  0,  0,  0,-10,
						-20,-10,-10, -5, -5,-10,-10,-20};
		return table;
	}
}
