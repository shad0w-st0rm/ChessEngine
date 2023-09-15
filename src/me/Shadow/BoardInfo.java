package me.Shadow;
import java.util.ArrayList;

import me.Shadow.pieces.Piece;

public class BoardInfo
{
	private boolean whiteToMove;	// 1 bit
	byte castlingRightsByte;	// 4 bits
	private int enPassantIndex;		//4 bits (could be done in 3)
	private Piece checkPiece;	// 6 bits
	private boolean doubleCheck;	// 1 bit
	
	private int whiteMaterial;	// 12 bits
	private int blackMaterial;	// 12 bits
	private int whiteSquareBonus;	// 11 bits
	private int blackSquareBonus;	// 11 bits
	private int moveNum;	// 14 bits maximum
	private int halfMoves;	// 7 bits
	private ArrayList<Long> positionList = new ArrayList<Long>();
	private long zobristHash;
	
	final static String defaultFEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
  	private String boardFEN;
	
	public BoardInfo()
	{
		whiteToMove = true;
		checkPiece = null;
		enPassantIndex = -1;
		whiteMaterial = blackMaterial = 4000;
		moveNum = 0;
		halfMoves = 0;
		boardFEN = defaultFEN;
	}
	
	public BoardInfo(BoardInfo boardInfoCopy)
	{
		whiteToMove = boardInfoCopy.whiteToMove;
		checkPiece = boardInfoCopy.checkPiece;
		doubleCheck = boardInfoCopy.doubleCheck;
		castlingRightsByte = boardInfoCopy.castlingRightsByte;
		enPassantIndex = boardInfoCopy.enPassantIndex;
		whiteMaterial = boardInfoCopy.whiteMaterial;
		blackMaterial = boardInfoCopy.blackMaterial;
		whiteSquareBonus = boardInfoCopy.whiteSquareBonus;
		blackSquareBonus = boardInfoCopy.blackSquareBonus;
		moveNum = boardInfoCopy.moveNum;
		halfMoves = boardInfoCopy.halfMoves;
		positionList = (ArrayList<Long>) boardInfoCopy.positionList.clone(); // this gets cleared sometimes so cannot push and pop positions
		// boardFEN = boardInfoCopy.boardFEN;		// this probably doesnt need to be copied over
		zobristHash = boardInfoCopy.zobristHash;
	}
	
	public boolean isWhiteToMove() { return whiteToMove; }
	public Piece getCheckPiece() { return checkPiece; }
	public boolean isDoubleCheck() { return doubleCheck; }
	public boolean [] getCastlingRights()
	{
		boolean [] array = {false, false, false, false};
		for (int i = 0; i < 4; i++)
		{
			array[i] = (castlingRightsByte & (1 << i)) >>> i == 1;
		}
		return array;
	}
	public int getEnPassantIndex() { return enPassantIndex; }
	public int getWhiteMaterial() { return whiteMaterial; }
	public int getBlackMaterial() { return blackMaterial; }
	public int getWhiteSquareBonus() { return whiteSquareBonus; }
	public int getBlackSquareBonus() { return blackSquareBonus; }
	public int getMoveNum() { return moveNum; }
	public int getHalfMoves() { return halfMoves; }
	public ArrayList<Long> getPositionList() { return (ArrayList<Long>) positionList; }
	public long getZobristHash() { return zobristHash; }
	public String getBoardFEN() { return boardFEN; }
	
	public void setWhiteToMove(boolean whiteToMoveIn) { whiteToMove = whiteToMoveIn; }
	public void setCheckPiece(Piece checkPieceIn) { checkPiece = checkPieceIn; }
	public void setDoubleCheck(boolean doubleCheck) { this.doubleCheck = doubleCheck; }
	public void setCastlingRights(boolean [] castlingRightsIn)
	{
		castlingRightsByte = 0;
		for (int i = 0; i < 4; i++)
		{
			if (castlingRightsIn[i])
			{
				castlingRightsByte |= (1 << i);
			}
		}
	}
	public void setEnPassantIndex(int enPassantIndexIn) { enPassantIndex = enPassantIndexIn; }
	public void setWhiteMaterial(int whiteMaterialIn) { whiteMaterial = whiteMaterialIn; }
	public void setBlackMaterial(int blackMaterialIn) { blackMaterial = blackMaterialIn; }
	public void setWhiteSquareBonus(int whiteBonus) { whiteSquareBonus = whiteBonus; }
	public void setBlackSquareBonus(int blackBonus) { blackSquareBonus = blackBonus; }
	public void setMoveNum(int moveNumIn) { moveNum = moveNumIn; }
	public void setHalfMoves(int halfMovesIn) { halfMoves = halfMovesIn; }
	public void setPositionList(ArrayList<Long> positionListIn) { positionList = positionListIn; }
	public void setZobristHash(long zobristIn) { zobristHash = zobristIn; }
	public void setBoardFEN(String boardFENIn) { boardFEN = boardFENIn; }
}
