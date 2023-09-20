package me.Shadow;

public class Piece
{
	int pieceInfo;
	Square square;
	
	public static final int NONE = 0;
	public static final int QUEEN = 1;
	public static final int ROOK = 2;
	public static final int BISHOP = 3;
	public static final int KNIGHT = 4;
	public static final int PAWN = 5;
	public static final int KING = 6;
	
	public static final int WHITE_PIECE = 0b0000; // 0
	public static final int BLACK_PIECE = 0b1000; // 8
	
	public static final int WHITE_QUEEN = QUEEN | WHITE_PIECE;
	public static final int WHITE_ROOK = ROOK | WHITE_PIECE;
	public static final int WHITE_BISHOP = BISHOP | WHITE_PIECE;
	public static final int WHITE_KNIGHT = KNIGHT | WHITE_PIECE;
	public static final int WHITE_PAWN = PAWN | WHITE_PIECE;
	public static final int WHITE_KING = KING | WHITE_PIECE;
	
	public static final int BLACK_QUEEN = QUEEN | BLACK_PIECE;
	public static final int BLACK_ROOK = ROOK | BLACK_PIECE;
	public static final int BLACK_BISHOP = BISHOP | BLACK_PIECE;
	public static final int BLACK_KNIGHT = KNIGHT | BLACK_PIECE;
	public static final int BLACK_PAWN = PAWN | BLACK_PIECE;
	public static final int BLACK_KING = KING | BLACK_PIECE;
	
	public static final int TYPE_MASK = 0b111;
	public static final int COLOR_MASK = 0b1000;
	
	static final int [][] PIECE_SQUARE_VALUES = {
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
			0,  0,  0,  0,  0,  0,  0,  0},
			// KING
		{	-30,-40,-40,-50,-50,-40,-40,-30,
			-30,-40,-40,-50,-50,-40,-40,-30,
			-30,-40,-40,-50,-50,-40,-40,-30,
			-30,-40,-40,-50,-50,-40,-40,-30,
			-20,-30,-30,-40,-40,-30,-30,-20,
			-10,-20,-20,-20,-20,-20,-20,-10,
			20, 20,  0,  0,  0,  0, 20, 20,
			20, 30, 10,  0,  0, 10, 30, 20}
	};
	
	static final int [] KING_ENDGAME_VALUES = {
			-50,-40,-30,-20,-20,-30,-40,-50,
			-30,-20,-10,  0,  0,-10,-20,-30,
			-30,-10, 20, 30, 30, 20,-10,-30,
			-30,-10, 30, 40, 40, 30,-10,-30,
			-30,-10, 30, 40, 40, 30,-10,-30,
			-30,-10, 20, 30, 30, 20,-10,-30,
			-30,-30,  0,  0,  0,  0,-30,-30,
			-50,-30,-30,-30,-30,-30,-30,-50};
	
	public Piece(int pieceType, int color, Square square)
	{
		this(pieceType | color, square);
	}
	
	public Piece(int pieceInfo, Square square)
	{
		this.pieceInfo = pieceInfo;
		this.square = square;
	}
	
	public int getPieceType()
	{
		return pieceInfo & TYPE_MASK;
	}
	
	public void setPieceType(int pieceType)
	{
		this.pieceInfo = (this.pieceInfo & COLOR_MASK) | pieceType;
	}
	
	public boolean isWhite()
	{
		return ((pieceInfo & COLOR_MASK) == WHITE_PIECE);
	}
	public Square getSquare() { return square; }
	public void setSquare(Square square) { this.square = square; }
	
	public int getValue()
	{
		if (getPieceType() == KING) return 0;
		else if (getPieceType() == QUEEN) return 900;
		else if (getPieceType() == ROOK) return 500;
		else if (getPieceType() == BISHOP) return 330;
		else if (getPieceType() == KNIGHT) return 320;
		else if (getPieceType() == PAWN) return 100;
		
		return 0;
	}
	
	public int getZobristOffset()
	{
		int offset = pieceInfo & TYPE_MASK;
		offset += (pieceInfo & COLOR_MASK) >> 2;
		return (offset - 1);
	}
	
	public char getPieceSymbol()
	{
		char symbol = 0;
		if (getPieceType() == KING) symbol = 'K';
		else if (getPieceType() == QUEEN) symbol = 'Q';
		else if (getPieceType() == ROOK) symbol = 'R';
		else if (getPieceType() == BISHOP) symbol = 'B';
		else if (getPieceType() == KNIGHT) symbol = 'N';
		else if (getPieceType() == PAWN) symbol = 'P';
		
		if ((pieceInfo & COLOR_MASK) == WHITE_PIECE) return symbol;
		else return (char) (symbol+32);
	}
	
	public int getPieceSquareValue(int index, boolean endgame)
	{
		if ((pieceInfo & COLOR_MASK) == BLACK_PIECE) index = (7 - (index / 8))*8 + (7 - (index % 8));
		
		if (endgame && getPieceType() == Piece.KING) return KING_ENDGAME_VALUES[index];
		else return PIECE_SQUARE_VALUES[getPieceType() - 1][index];
	}
}