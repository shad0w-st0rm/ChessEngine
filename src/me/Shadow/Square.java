package me.Shadow;

public class Square
{
  private int rank, file;
  private Piece piece;
  
  public Square(int rankIn, int fileIn)
  {
    rank = rankIn;
    file = fileIn;
  }
  
  public String toString()
  {
	  return ((char)(file + 96) + "" + rank);
  }
  
  public boolean equals(Square square)
  {
	  return square.getIndex() == getIndex();
  }
  
  public void addPiece(Piece pieceIn)
  {
    piece = pieceIn;
  }
  
  public Piece removePiece()
  {
    Piece removedPiece = piece;
    piece = null;
    return removedPiece;
  }
  
  public Piece getPiece() { return piece; }
  public boolean hasPiece()
  {
	  return (piece != null);
  }
  public int getRank() { return rank; }
  public int getFile() { return file; }
  
  public int getIndex()
  {
	  int index = ((rank - 1) * 8) + file - 1;
	  return index;
  }
}