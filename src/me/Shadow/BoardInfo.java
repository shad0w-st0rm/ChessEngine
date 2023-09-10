package me.Shadow;
import java.util.ArrayList;

import me.Shadow.pieces.Piece;

public class BoardInfo
{
	private boolean whiteToMove;
	private Piece checkPiece;
	private boolean doubleCheck;
	private boolean [] castlingRights = {false, false, false, false};
	private int enPassantIndex;
	private int whiteMaterial;
	private int blackMaterial;
	private int whiteSquareBonus;
	private int blackSquareBonus;
	private int moveNum;
	private int halfMoves;
	private ArrayList<Move> moveList = new ArrayList<Move>();
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
		castlingRights = boardInfoCopy.castlingRights.clone();
		enPassantIndex = boardInfoCopy.enPassantIndex;
		whiteMaterial = boardInfoCopy.whiteMaterial;
		blackMaterial = boardInfoCopy.blackMaterial;
		whiteSquareBonus = boardInfoCopy.whiteSquareBonus;
		blackSquareBonus = boardInfoCopy.blackSquareBonus;
		moveNum = boardInfoCopy.moveNum;
		halfMoves = boardInfoCopy.halfMoves;
		for(Move move : boardInfoCopy.moveList)
		{
			moveList.add(move);
		}
		for(long l : boardInfoCopy.positionList)
		{
			positionList.add(l);
		}
		boardFEN = boardInfoCopy.boardFEN;
		zobristHash = boardInfoCopy.zobristHash;
	}
	
	public boolean isWhiteToMove() { return whiteToMove; }
	public Piece getCheckPiece() { return checkPiece; }
	public boolean isDoubleCheck() { return doubleCheck; }
	public boolean [] getCastlingRights() { return castlingRights; }
	public int getEnPassantIndex() { return enPassantIndex; }
	public int getWhiteMaterial() { return whiteMaterial; }
	public int getBlackMaterial() { return blackMaterial; }
	public int getWhiteSquareBonus() { return whiteSquareBonus; }
	public int getBlackSquareBonus() { return blackSquareBonus; }
	public int getMoveNum() { return moveNum; }
	public int getHalfMoves() { return halfMoves; }
	public ArrayList<Move> getMoveList() { return (ArrayList<Move>) moveList; }
	public ArrayList<Long> getPositionList() { return (ArrayList<Long>) positionList; }
	public long getZobristHash() { return zobristHash; }
	public String getBoardFEN() { return boardFEN; }
	
	public void setWhiteToMove(boolean whiteToMoveIn) { whiteToMove = whiteToMoveIn; }
	public void setCheckPiece(Piece checkPieceIn) { checkPiece = checkPieceIn; }
	public void setDoubleCheck(boolean doubleCheck) { this.doubleCheck = doubleCheck; }
	public void setCastlingRights(boolean [] castlingRightsIn) { castlingRights = castlingRightsIn; }
	public void setEnPassantIndex(int enPassantIndexIn) { enPassantIndex = enPassantIndexIn; }
	public void setWhiteMaterial(int whiteMaterialIn) { whiteMaterial = whiteMaterialIn; }
	public void setBlackMaterial(int blackMaterialIn) { blackMaterial = blackMaterialIn; }
	public void setWhiteSquareBonus(int whiteBonus) { whiteSquareBonus = whiteBonus; }
	public void setBlackSquareBonus(int blackBonus) { blackSquareBonus = blackBonus; }
	public void setMoveNum(int moveNumIn) { moveNum = moveNumIn; }
	public void setHalfMoves(int halfMovesIn) { halfMoves = halfMovesIn; }
	public void setMoveList(ArrayList<Move> moveListIn) { moveList = moveListIn; }
	public void setPositionList(ArrayList<Long> positionListIn) { positionList = positionListIn; }
	public void setZobristHash(long zobristIn) { zobristHash = zobristIn; }
	public void setBoardFEN(String boardFENIn) { boardFEN = boardFENIn; }
}
