package me.Shadow.pieces;

import me.Shadow.Square;

public class Pawn implements Piece
{
	private Square square;
	private boolean isWhite;
	private boolean isCaptured;
	
	public Pawn(Square squareIn, boolean isWhiteIn)
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
	public String getName() { return "PAWN"; }

	@Override
	public int getValue() { return 100; }
	
	@Override
	public char getPieceSymbol() { return isWhite ? 'P' : 'p'; }
	
	@Override
	public int getZobristOffset() { return isWhite ? 10 : 11; }
	
	@Override
	public int[] getPieceSquareTable(boolean endgame)
	{
		int [] table = {0,  0,  0,  0,  0,  0,  0,  0,
						50, 50, 50, 50, 50, 50, 50, 50,
						10, 10, 20, 30, 30, 20, 10, 10,
						5,  5, 10, 25, 25, 10,  5,  5,
						0,  0,  0, 20, 20,  0,  0,  0,
						5, -5,-10,  0,  0,-10, -5,  5,
						5, 10, 10,-20,-20, 10, 10,  5,
						0,  0,  0,  0,  0,  0,  0,  0};
		return table;
	}
}
