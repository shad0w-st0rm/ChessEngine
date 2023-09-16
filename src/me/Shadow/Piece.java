package me.Shadow;

public class Piece
{
	int pieceType;
	boolean isWhite;
	Square square;
	
	public static final int KING = 0;
	public static final int QUEEN = 1;
	public static final int ROOK = 2;
	public static final int BISHOP = 3;
	public static final int KNIGHT = 4;
	public static final int PAWN = 5;
	
	static final int [][] pieceSquareValues = {
			// KING
		{	-30,-40,-40,-50,-50,-40,-40,-30,
			-30,-40,-40,-50,-50,-40,-40,-30,
			-30,-40,-40,-50,-50,-40,-40,-30,
			-30,-40,-40,-50,-50,-40,-40,-30,
			-20,-30,-30,-40,-40,-30,-30,-20,
			-10,-20,-20,-20,-20,-20,-20,-10,
			20, 20,  0,  0,  0,  0, 20, 20,
			20, 30, 10,  0,  0, 10, 30, 20},
			// QUEEN
		{	-20,-10,-10, -5, -5,-10,-10,-20,
			-10,  0,  0,  0,  0,  0,  0,-10,
			-10,  0,  5,  5,  5,  5,  0,-10,
			-5,  0,  5,  5,  5,  5,  0, -5,
			0,  0,  5,  5,  5,  5,  0, -5,
			-10,  0,  5,  5,  5,  5,  0,-10,
			-10,  0,  0,  0,  0,  0,  0,-10,
			-20,-10,-10, -5, -5,-10,-10,-20},
			// ROOK
		{	0,  0,  0,  0,  0,  0,  0,  0,
		  	5, 10, 10, 10, 10, 10, 10,  5,
		  	-5,  0,  0,  0,  0,  0,  0, -5,
		  	-5,  0,  0,  0,  0,  0,  0, -5,
		  	-5,  0,  0,  0,  0,  0,  0, -5,
		  	-5,  0,  0,  0,  0,  0,  0, -5,
		  	-5,  0,  0,  0,  0,  0,  0, -5,
		  	0,  0,  0,  5,  5,  0,  0,  0},
			// BISHOP
		{	-20,-10,-10,-10,-10,-10,-10,-20,
			-10,  0,  0,  0,  0,  0,  0,-10,
			-10,  0,  5, 10, 10,  5,  0,-10,
			-10,  5,  5, 10, 10,  5,  5,-10,
			-10,  0, 10, 10, 10, 10,  0,-10,
			-10, 10, 10, 10, 10, 10, 10,-10,
			-10,  5,  0,  0,  0,  0,  5,-10,
			-20,-10,-10,-10,-10,-10,-10,-20},
			// KNIGHT
		{	-50,-40,-30,-30,-30,-30,-40,-50,
			-40,-20,  0,  0,  0,  0,-20,-40,
			-30,  0, 10, 15, 15, 10,  0,-30,
			-30,  5, 15, 20, 20, 15,  5,-30,
			-30,  0, 15, 20, 20, 15,  0,-30,
			-30,  5, 10, 15, 15, 10,  5,-30,
			-40,-20,  0,  5,  5,  0,-20,-40,
			-50,-40,-30,-30,-30,-30,-40,-50},
			// PAWN
		{	0,  0,  0,  0,  0,  0,  0,  0,
			50, 50, 50, 50, 50, 50, 50, 50,
			10, 10, 20, 30, 30, 20, 10, 10,
			5,  5, 10, 25, 25, 10,  5,  5,
			0,  0,  0, 20, 20,  0,  0,  0,
			5, -5,-10,  0,  0,-10, -5,  5,
			5, 10, 10,-20,-20, 10, 10,  5,
			0,  0,  0,  0,  0,  0,  0,  0}
	};
	
	static final int [] kingEndgameValues = {
			-50,-40,-30,-20,-20,-30,-40,-50,
			-30,-20,-10,  0,  0,-10,-20,-30,
			-30,-10, 20, 30, 30, 20,-10,-30,
			-30,-10, 30, 40, 40, 30,-10,-30,
			-30,-10, 30, 40, 40, 30,-10,-30,
			-30,-10, 20, 30, 30, 20,-10,-30,
			-30,-30,  0,  0,  0,  0,-30,-30,
			-50,-30,-30,-30,-30,-30,-30,-50};
	
	public Piece(int pieceType, boolean isWhite, Square square)
	{
		this.pieceType = pieceType;
		this.isWhite = isWhite;
		this.square = square;
	}
	
	public int getPieceType() { return pieceType; }
	
	public void setPieceType(int pieceType) { this.pieceType = pieceType; }
	
	public boolean isWhite() { return isWhite; }
	public Square getSquare() { return square; }
	public void setSquare(Square square) { this.square = square; }
	
	public int getValue()
	{
		if (pieceType == KING) return 0;
		else if (pieceType == QUEEN) return 900;
		else if (pieceType == ROOK) return 500;
		else if (pieceType == BISHOP) return 330;
		else if (pieceType == KNIGHT) return 320;
		else if (pieceType == PAWN) return 100;
		
		return 0;
	}
	
	public int getZobristOffset()
	{
		return pieceType*2 + (isWhite ? 0 : 1);
	}
	
	public char getPieceSymbol()
	{
		char symbol = 0;
		if (pieceType == KING) symbol = 'K';
		else if (pieceType == QUEEN) symbol = 'Q';
		else if (pieceType == ROOK) symbol = 'R';
		else if (pieceType == BISHOP) symbol = 'B';
		else if (pieceType == KNIGHT) symbol = 'N';
		else if (pieceType == PAWN) symbol = 'P';
		
		if (isWhite) return symbol;
		else return (char) (symbol+32);
	}
	
	public int getPieceSquareValue(int index, boolean endgame)
	{
		if (!isWhite) index = (7 - (index / 8))*8 + (7 - (index % 8));
		
		if (endgame && pieceType == Piece.KING) return kingEndgameValues[index];
		else return pieceSquareValues[pieceType][index];
	}
}