package me.Shadow.pieces;

import me.Shadow.Square;

public class Rook implements Piece
{
	private Square square;
	private boolean isWhite;
	private boolean isCaptured;
	private Piece piecePinning;
	private Piece piecePinned;
	
	public Rook(Square squareIn, boolean isWhiteIn)
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
	public boolean isCaptured() { return isCaptured; }

	@Override
	public void setCaptured(boolean captured) { isCaptured = captured; }

	@Override
	public String getName() { return "ROOK"; }

	@Override
	public int getValue() { return 500; }
	
	@Override
	public char getPieceSymbol() { return isWhite ? 'R' : 'r'; }
	
	@Override
	public int getZobristOffset() { return isWhite ? 4 : 5; }
	
	public void setPiecePinning(Piece piece) { piecePinning = piece; }
	
	public Piece getPiecePinning() { return piecePinning; }
	
	public void setPiecePinned(Piece piece) { piecePinned = piece; }
	
	public Piece getPiecePinned() { return piecePinned; }
	
	@Override
	public int[] getPieceSquareTable(boolean endgame)
	{
		int [] table = {0,  0,  0,  0,  0,  0,  0,  0,
				  		5, 10, 10, 10, 10, 10, 10,  5,
				  		-5,  0,  0,  0,  0,  0,  0, -5,
				  		-5,  0,  0,  0,  0,  0,  0, -5,
				  		-5,  0,  0,  0,  0,  0,  0, -5,
				  		-5,  0,  0,  0,  0,  0,  0, -5,
				  		-5,  0,  0,  0,  0,  0,  0, -5,
				  		0,  0,  0,  5,  5,  0,  0,  0};
		return table;
	}
}
