package me.Shadow.pieces;

import me.Shadow.Square;

public class King implements Piece
{
	private Square square;
	private boolean isWhite;
	private boolean isCaptured;
	
	public King(Square squareIn, boolean isWhiteIn)
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
	public String getName() { return "KING"; }

	@Override
	public int getValue() { return 0; }
	
	@Override
	public char getPieceSymbol() { return isWhite ? 'K' : 'k'; }
	
	@Override
	public int getZobristOffset() { return isWhite ? 0 : 1; }
	
	public void setPiecePinning(Piece piece) {}
	
	public Piece getPiecePinning() { return null; }
	
	public void setPiecePinned(Piece piece) {}
	
	public Piece getPiecePinned() { return null; }
	
	@Override
	public int[] getPieceSquareTable(boolean endgame)
	{
		int [] middlegameTable = {-30,-40,-40,-50,-50,-40,-40,-30,
							-30,-40,-40,-50,-50,-40,-40,-30,
							-30,-40,-40,-50,-50,-40,-40,-30,
							-30,-40,-40,-50,-50,-40,-40,-30,
							-20,-30,-30,-40,-40,-30,-30,-20,
							-10,-20,-20,-20,-20,-20,-20,-10,
							20, 20,  0,  0,  0,  0, 20, 20,
							20, 30, 10,  0,  0, 10, 30, 20};
		if(!endgame) return middlegameTable;
		int [] endgameTable = {-50,-40,-30,-20,-20,-30,-40,-50,
					-30,-20,-10,  0,  0,-10,-20,-30,
					-30,-10, 20, 30, 30, 20,-10,-30,
					-30,-10, 30, 40, 40, 30,-10,-30,
					-30,-10, 30, 40, 40, 30,-10,-30,
					-30,-10, 20, 30, 30, 20,-10,-30,
					-30,-30,  0,  0,  0,  0,-30,-30,
					-50,-30,-30,-30,-30,-30,-30,-50};
		return endgameTable;
	}
}
