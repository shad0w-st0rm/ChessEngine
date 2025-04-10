package me.Shadow.Engine;
import java.util.ArrayList;

public class BoardInfo
{
	public static final long WHITE_KING_CASTLING = 0b1;
	public static final long WHITE_QUEEN_CASTLING = 0b10;
	public static final long BLACK_KING_CASTLING = 0b100;
	public static final long BLACK_QUEEN_CASTLING = 0b1000;
	
	private boolean whiteToMove;	// 1 bit
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
		
	public BoardInfo()
	{
		whiteToMove = true;
		enPassantIndex = -1;
		moveNum = 0;
		halfMoves = 0;
	}
	
	public BoardInfo(BoardInfo boardInfoCopy)
	{
		whiteToMove = boardInfoCopy.whiteToMove;
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
	}
	
	public boolean isWhiteToMove() { return whiteToMove; }
	public byte getCastlingRights()
	{
		return castlingRightsByte;
	}
	public int getEnPassantIndex() { return enPassantIndex; }
	public int getWhiteMGBonus() { return whiteMGBonus; }
	public int getWhiteEGBonus() { return whiteEGBonus; }
	public int getBlackMGBonus() { return blackMGBonus; }
	public int getBlackEGBonus() { return blackEGBonus; }
	
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
	
	public void setWhiteToMove(boolean whiteToMoveIn) { whiteToMove = whiteToMoveIn; }
	public void setCastlingRights(byte castlingRightsIn)
	{
		castlingRightsByte = castlingRightsIn;
	}
	public void setEnPassantIndex(int enPassantIndexIn) { enPassantIndex = enPassantIndexIn; }
	public void setWhiteMGBonus(int whiteMGBonusIn) { whiteMGBonus = whiteMGBonusIn; }
	public void setWhiteEGBonus(int whiteEGBonusIn) { whiteEGBonus = whiteEGBonusIn; }
	public void setBlackMGBonus(int blackMGBonusIn) { blackMGBonus = blackMGBonusIn; }
	public void setBlackEGBonus(int blackEGBonusIn) { blackEGBonus = blackEGBonusIn; }
	public void setPositionList(ArrayList<Long> positionListIn) { positionList = positionListIn; }
	public void setZobristHash(long zobristIn) { zobristHash = zobristIn; }
}
