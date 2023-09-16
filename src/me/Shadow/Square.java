package me.Shadow;

public class Square
{
  private int rank, file;
  private Piece piece;
  private boolean isDark;
  private boolean hasPiece;
  
  public Square(int rankIn, int fileIn)
  {
    rank = rankIn;
    file = fileIn;
    updateColor();
  }
  
  public String toStringLong() { return "Square Color: " + (isDark ? "DARK" : "LIGHT") + " + Location: " + (char)(file + 96) + rank + " + Piece: " + (hasPiece ? "YES" : "NO"); }
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
    hasPiece = true;
  }
  
  public Piece removePiece()
  {
    Piece removedPiece = piece;
    piece = null;
    hasPiece = false;
    return removedPiece;
  }
  
  public Piece getPiece() { return piece; }
  public boolean hasPiece()
  {
	  if(hasPiece != (piece != null)) System.out.println("something very broken");
	  return (hasPiece && piece != null);
  }
  public int getRank() { return rank; }
  public int getFile() { return file; }
  
  public void setRank(int newRank)
  {
    rank = newRank;
    updateColor();
  }
  
  public void setFile(int newFile)
  {
    file = newFile;
    updateColor();
  }
  
  public boolean isDark() { return isDark; }
  
  public int getIndex()
  {
	  int index = ((8 - rank)*8) + file - 1;
	  return index;
  }
  
  private void updateColor()
  {
    if((rank + file) % 2 == 0) isDark = true;
    else isDark = false;
  }
}