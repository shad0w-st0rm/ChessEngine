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
	
	private int whiteMaterial;	// 12 bits
	private int blackMaterial;	// 12 bits
	private int whiteSquareBonus;	// 11 bits
	private int blackSquareBonus;	// 11 bits
	private int moveNum;	// 14 bits maximum
	private int halfMoves;	// 7 bits
	private ArrayList<Long> positionList = new ArrayList<Long>();
	//private ArrayList<Short> moveList = new ArrayList<Short>();
	private long zobristHash;
		
	public BoardInfo()
	{
		whiteToMove = true;
		enPassantIndex = -1;
		whiteMaterial = blackMaterial = 4000;
		moveNum = 0;
		halfMoves = 0;
	}
	
	public BoardInfo(BoardInfo boardInfoCopy)
	{
		whiteToMove = boardInfoCopy.whiteToMove;
		castlingRightsByte = boardInfoCopy.castlingRightsByte;
		enPassantIndex = boardInfoCopy.enPassantIndex;
		whiteMaterial = boardInfoCopy.whiteMaterial;
		blackMaterial = boardInfoCopy.blackMaterial;
		whiteSquareBonus = boardInfoCopy.whiteSquareBonus;
		blackSquareBonus = boardInfoCopy.blackSquareBonus;
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
	public int getWhiteMaterial() { return whiteMaterial; }
	public int getBlackMaterial() { return blackMaterial; }
	public int getWhiteSquareBonus() { return whiteSquareBonus; }
	public int getBlackSquareBonus() { return blackSquareBonus; }
	
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
	public void setWhiteMaterial(int whiteMaterialIn) { whiteMaterial = whiteMaterialIn; }
	public void setBlackMaterial(int blackMaterialIn) { blackMaterial = blackMaterialIn; }
	public void setWhiteSquareBonus(int whiteBonus) { whiteSquareBonus = whiteBonus; }
	public void setBlackSquareBonus(int blackBonus) { blackSquareBonus = blackBonus; }
	public void setPositionList(ArrayList<Long> positionListIn) { positionList = positionListIn; }
	public void setZobristHash(long zobristIn) { zobristHash = zobristIn; }
}
