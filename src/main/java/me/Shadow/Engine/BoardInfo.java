package me.Shadow.Engine;
import java.util.ArrayList;

public class BoardInfo
{
	public static final long WHITE_KING_CASTLING = 0b1;
	public static final long WHITE_QUEEN_CASTLING = 0b10;
	public static final long BLACK_KING_CASTLING = 0b100;
	public static final long BLACK_QUEEN_CASTLING = 0b1000;
	
	private byte castlingRightsByte;	// 4 bits
	private int enPassantIndex;		//4 bits (could be done in 3)
	
	private int whiteMGBonus;
	private int whiteEGBonus;
	private int blackMGBonus;
	private int blackEGBonus;
	private int moveNum;	// 14 bits maximum
	private int halfMoves;	// 7 bits
	private ArrayList<Long> positionList = new ArrayList<Long>();
	//private ArrayList<Short> moveList = new ArrayList<Short>();
	private long zobristHash;
	private long pawnsHash;
		
	public BoardInfo()
	{
		enPassantIndex = -1;
		moveNum = 0;
		halfMoves = 0;
	}
	
	public BoardInfo(BoardInfo boardInfoCopy)
	{
		castlingRightsByte = boardInfoCopy.castlingRightsByte;
		enPassantIndex = boardInfoCopy.enPassantIndex;
		whiteMGBonus = boardInfoCopy.whiteMGBonus;
		whiteEGBonus = boardInfoCopy.whiteEGBonus;
		blackMGBonus = boardInfoCopy.blackMGBonus;
		blackEGBonus = boardInfoCopy.blackEGBonus;
		moveNum = boardInfoCopy.moveNum;
		halfMoves = boardInfoCopy.halfMoves;
		positionList = boardInfoCopy.positionList;
		//moveList = boardInfoCopy.moveList;
		zobristHash = boardInfoCopy.zobristHash;
		pawnsHash = boardInfoCopy.pawnsHash;
	}
	
	public byte getCastlingRights()
	{
		return castlingRightsByte;
	}
	public int getEnPassantIndex() { return enPassantIndex; }
	public int getMaterialBonus(int color, boolean endgame)
	{
		if (!endgame) return color == PieceHelper.WHITE_PIECE ? whiteMGBonus : blackMGBonus;
		else return color == PieceHelper.WHITE_PIECE ? whiteEGBonus : blackEGBonus;
	}
	
	public void setMaterialBonus(int color, boolean endgame, int value)
	{
		if (!endgame)
		{
			if (color == PieceHelper.WHITE_PIECE) whiteMGBonus = value;
			else blackMGBonus = value;
		}
		else
		{
			if (color == PieceHelper.WHITE_PIECE) whiteEGBonus = value;
			else blackEGBonus = value;
		}
	}
	
	public int getMoveNum() { return moveNum; }
	public void incrementMoveNum()
	{
		moveNum++;
	}
	public void setMoveNum(int moveNumIn)
	{
		moveNum = moveNumIn;
	}
	public int getHalfMoves() { return halfMoves; }
	public void incrementHalfMoves()
	{
		halfMoves++;
	}
	public void setHalfMoves(int halfMovesIn)
	{
		halfMoves = halfMovesIn;
	}
	
	public ArrayList<Long> getPositionList() { return positionList; }
	//public ArrayList<Short> getMoveList() { return moveList; }
	public long getZobristHash() { return zobristHash; }
	public long getPawnsHash() { return pawnsHash; }
	
	public void setCastlingRights(byte castlingRightsIn)
	{
		castlingRightsByte = castlingRightsIn;
	}
	public void setEnPassantIndex(int enPassantIndexIn) { enPassantIndex = enPassantIndexIn; }
	public void setPositionList(ArrayList<Long> positionListIn) { positionList = positionListIn; }
	public void setZobristHash(long zobristIn) { zobristHash = zobristIn; }
	public void setPawnsHash(long pawnsIn) { pawnsHash = pawnsIn; }
}
