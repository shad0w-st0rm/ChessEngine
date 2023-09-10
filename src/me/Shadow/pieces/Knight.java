package me.Shadow.pieces;

import me.Shadow.Square;

public class Knight implements Piece
{
	private Square square;
	private boolean isWhite;
	private boolean isCaptured;
	private Piece piecePinning;
	
	public Knight(Square squareIn, boolean isWhiteIn)
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
	public String getName() { return "KNIGHT"; }

	@Override
	public int getValue() { return 320; }
	
	@Override
	public char getPieceSymbol() { return isWhite ? 'N' : 'n'; }
	
	@Override
	public int getZobristOffset() { return isWhite ? 8 : 9; }
	
	public void setPiecePinning(Piece piece) { piecePinning = piece; }
	
	public Piece getPiecePinning() { return piecePinning; }
	
	public void setPiecePinned(Piece piece) {}
	
	public Piece getPiecePinned() { return null; }
	
	@Override
	public int[] getPieceSquareTable(boolean endgame)
	{
		int [] table = {-50,-40,-30,-30,-30,-30,-40,-50,
						-40,-20,  0,  0,  0,  0,-20,-40,
						-30,  0, 10, 15, 15, 10,  0,-30,
						-30,  5, 15, 20, 20, 15,  5,-30,
						-30,  0, 15, 20, 20, 15,  0,-30,
						-30,  5, 10, 15, 15, 10,  5,-30,
						-40,-20,  0,  5,  5,  0,-20,-40,
						-50,-40,-30,-30,-30,-30,-40,-50};
		return table;
	}
}
