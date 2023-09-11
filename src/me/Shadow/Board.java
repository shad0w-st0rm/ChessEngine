package me.Shadow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;

import me.Shadow.pieces.Bishop;
import me.Shadow.pieces.King;
import me.Shadow.pieces.Knight;
import me.Shadow.pieces.Pawn;
import me.Shadow.pieces.Piece;
import me.Shadow.pieces.Queen;
import me.Shadow.pieces.Rook;

public class Board
{

	ArrayList<Piece> pieces = new ArrayList<Piece>(32);
	ArrayList<Piece> promotedSliders = new ArrayList<Piece>();
	ArrayList<PinnedPiece> pinnedPieces = new ArrayList<PinnedPiece>();
	ArrayList<Square> squares = new ArrayList<Square>(64);
	BoardInfo boardInfo;
	long[] zobristHashes;

	int[] knightMoves =
	{ 17, 15, -17, -15, 10, 6, -10, -6 };
	int[] sliderMoves =
	{ 8, 1, -8, -1, 9, 7, -9, -7 };
	int[] sliderIndexes =
	{ 0, 2, 3, 5, 7, 24, 26, 27, 29, 31 };

	public Board()
	{
		this(BoardInfo.defaultFEN);
	}

	/**
	 * Constructor for Board with a specific FEN string
	 * 
	 * @param fen The given FEN string to load in
	 */
	public Board(String fen)
	{
		boardInfo = new BoardInfo();
		zobristHashes = new Random(8108415243282079581L).longs(781).toArray();
		loadFEN(fen);
	}

	/**
	 * Generate legal moves for a given side. Either all legal moves or only captures
	 * 
	 * @param whiteToMove  Boolean used to determine which side to generate moves for
	 * @param capturesOnly Generate all moves or only captures
	 * 
	 * @return ArrayList<Move> of all legal moves generated
	 */
	public ArrayList<Move> generateAllLegalMoves(boolean whiteToMove, boolean capturesOnly)
	{
		// white pieces are indices 16-31, black pieces are 0-15
		int index = whiteToMove ? 16 : 0;
		ArrayList<Move> allMoves = new ArrayList<Move>();
		for (int i = index; i < index + 16; i++)
		{
			Piece piece = pieces.get(i);
			if (!piece.isCaptured())
			{
				// For each piece generate all legal moves
				ArrayList<Move> moves = generateLegalMoves(piece);
				if (capturesOnly)
				{
					for (Move move : moves)
					{
						// if move is capture add it to legal moves to filter it
						if (squares.get(move.getTargetIndex()).hasPiece() || move.getEnPassantCaptureIndex() != -1)
							allMoves.add(move);
					}
				}
				else allMoves.addAll(moves);
			}
		}
		return allMoves;
	}
	
	
	public ArrayList<Move> generateAllPseudoLegalMoves(boolean whiteToMove, boolean capturesOnly)
	{
		// white pieces are indices 16-31, black pieces are 0-15
		int index = whiteToMove ? 16 : 0;
		ArrayList<Move> allMoves = new ArrayList<Move>();
		for (int i = index; i < index + 16; i++)
		{
			Piece piece = pieces.get(i);
			if (!piece.isCaptured())
			{
				// For each piece generate all legal moves
				ArrayList<Move> moves = generatePseudoLegalMoves(piece);
				if (capturesOnly)
				{
					for (Move move : moves)
					{
						// if move is capture add it to legal moves to filter it
						if (squares.get(move.getTargetIndex()).hasPiece() || move.getEnPassantCaptureIndex() != -1)
							allMoves.add(move);
					}
				}
				else allMoves.addAll(moves);
			}
		}
		return allMoves;
	}
	
	public boolean isMoveLegal(Move move)
	{
		Piece piece = squares.get(move.getStartIndex()).getPiece();
		PinnedPiece pinnedObj = isPiecePinned(piece);
		
		if (pinnedObj != null && isPiecePinnedAbsolutely(piece, pinnedObj.pinner)) return false; // TODO: update isPiecePinnedAbsolutely signature
		if (boardInfo.isDoubleCheck() && !(piece instanceof King)) return false;
		if (pinnedObj != null && boardInfo.getCheckPiece() != null && piece instanceof Pawn) return false;
		
		if (pinnedObj == null && boardInfo.getCheckPiece() == null && !(piece instanceof King))
		{
			if (piece instanceof Pawn && move.getEnPassantCaptureIndex() != -1)
				return isEnPassantLegal(move);
			return true;
		}
		
		if (piece instanceof King) // if moving a king
		{
			if (move.isCastleMove())
				return castleLegalityHelper(piece, move);

			boolean legal = true;
			
			Square startSquare = squares.get(move.getStartIndex());
			startSquare.removePiece();
			piece.setSquare(null);
			
			Square targetSquare = squares.get(move.getTargetIndex());
			Piece captured = targetSquare.removePiece();
			if (captured != null)
			{
				captured.setCaptured(true);
				captured.setSquare(null);
			}
			
			// runs for every enemy piece
			for (int i = (piece.isWhite() ? 0 : 16); i < (piece.isWhite() ? 16 : 32); i++)
			{
				Piece enemyPiece = pieces.get(i);
				if (enemyPiece.isCaptured()) continue;
				
				if (canPieceAttackSquare(pieces.get(i), targetSquare)) // moveLegalityHelper determines if it is legal for a given piece
				{
					legal = false;
					break;
				}
			}
			
			startSquare.addPiece(piece);
			piece.setSquare(startSquare);
			
			if (captured != null)
			{
				captured.setCaptured(false);
				captured.setSquare(targetSquare);
				targetSquare.addPiece(captured);
			}
			
			return legal;
		}
		else // not moving a king (and cant be in double check)
		{
			Piece checkPiece = boardInfo.getCheckPiece();
			if (checkPiece != null && pinnedObj == null) // extra testing because in check and not pinned
			{
				ArrayList<Square> blockSquares = getSquaresInBetween(checkPiece, pieces.get(piece.isWhite() ? 28 : 4).getSquare());
				blockSquares.add(checkPiece.getSquare());
				int captureIndex = move.getTargetIndex();
				if (move.getEnPassantCaptureIndex() != -1) captureIndex = move.getEnPassantCaptureIndex();
				
				return blockSquares.contains(squares.get(captureIndex));
			}
			else if (checkPiece == null)// extra testing because partially pinned (and not in check)
			{
				// if the pawn is pinned vertically, this will be true only for advancing, not capturing left/right
				// if it is pinned diagonally, it will only be true for the capture of the pinning piece so this works
				if (piece instanceof Pawn)
					return (move.getTargetIndex() % 8) == (pinnedObj.pinner.getSquare().getIndex() % 8);
				
				// cannot be a pawn, knight (always absolutely pinned), or king so must be a slider
				ArrayList<Square> pinSquares = getSquaresInBetween(piece,pinnedObj.pinner.getSquare());
				pinSquares.add(pinnedObj.pinner.getSquare());
				pinSquares.addAll(getSquaresInBetween(piece, pieces.get(piece.isWhite() ? 28 : 4).getSquare()));
				
				return pinSquares.contains(squares.get(move.getTargetIndex()));
			}
			else // both in check and partially pinned (getting here means piece is only a slider)
			{
				ArrayList<Square> blockSquares = getSquaresInBetween(checkPiece, pieces.get(piece.isWhite() ? 28 : 4).getSquare());
				blockSquares.add(checkPiece.getSquare());
				
				ArrayList<Square> pinSquares = getSquaresInBetween(piece, pinnedObj.pinner.getSquare());
				pinSquares.add(pinnedObj.pinner.getSquare());
				pinSquares.addAll(getSquaresInBetween(piece, pieces.get(piece.isWhite() ? 28 : 4).getSquare()));
				
				pinSquares.retainAll(blockSquares);
				return pinSquares.contains(squares.get(move.getTargetIndex()));
			}
		}
	}
	
	public boolean hasLegalMoves(boolean whiteToMove)
	{
		// white pieces are indices 16-31, black pieces are 0-15
		int index = whiteToMove ? 16 : 0;
		for (int i = index; i < index + 16; i++)
		{
			Piece piece = pieces.get(i);
			if (!piece.isCaptured())
			{
				ArrayList<Move> moves = generatePseudoLegalMoves(piece);
				for (Move move : moves)
				{
					if (isMoveLegal(move)) return true;
				}
			}
		}
		return false;
	}

	/**
	 * Generate all legal moves for a given piece. Can also specify if only captures allowed.
	 * 
	 * @param piece        The given piece to generate moves for
	 * @param capturesOnly True if generating only capture moves
	 * 
	 * @return ArrayList<Move> of all legal moves generated
	 */
	public ArrayList<Move> generateLegalMoves(Piece piece)
	{
		ArrayList<Move> moves = generatePseudoLegalMoves(piece);
		ArrayList<Move> legalMoves = new ArrayList<Move>();
		for (Move move : moves)
		{
			if (isMoveLegal(move)) legalMoves.add(move);
		}
		return legalMoves;
	}
	

	/**
	 * Used to help determine if a move was legal. Method checks if given piece can attack the square of the king
	 * 
	 * @param piece Passed in piece to check if it can attack king
	 * @param king  Square of friendly king
	 * 
	 * @return True if move is legal (piece cannot attack the king) or false if move is not legal (piece is able to attack the king)
	 */
	public boolean canPieceAttackSquare(Piece piece, Square target)
	{
		if (piece.isCaptured() || (target.hasPiece() && piece.isWhite() == target.getPiece().isWhite()))
			return false;

		// get rank and files for both squares
		int targetIndex = target.getIndex();
		int targetRank = target.getRank();
		int targetFile = target.getFile();

		int pieceIndex = piece.getSquare().getIndex();
		int pieceRank = piece.getSquare().getRank();
		int pieceFile = piece.getSquare().getFile();
		
		if (piece instanceof Rook || piece instanceof Bishop || piece instanceof Queen)
		{
			if (piece instanceof Rook && (targetRank != pieceRank && targetFile != pieceFile))
				return false;
			else if (piece instanceof Bishop && (Math.abs(targetRank - pieceRank) != Math.abs(targetFile - pieceFile)))
				return false;
			else if (piece instanceof Queen && ((targetRank != pieceRank && targetFile != pieceFile) && (Math.abs(targetRank - pieceRank) != Math.abs(targetFile - pieceFile))))
				return false;
			
			int step = 0;
			int stepCount = 0;
			// if rook or queen attacking like rook, only one condition will be true, if bishop or queen attacking like bishop, both will be true
			if (targetRank != pieceRank)
			{
				step += ((targetRank > pieceRank) ? -8 : 8);
				stepCount = Math.abs(targetRank - pieceRank);
			}
			if (targetFile != pieceFile)
			{
				step += ((targetFile > pieceFile) ? 1 : -1);
				stepCount = Math.abs(targetFile - pieceFile);
			}
			
			for (int i = 1; i < stepCount; i++)
			{
				int index = pieceIndex + i * step;
				if (squares.get(index).hasPiece())
					return false; // if any piece between them, slider is blocked so move is legal
			}
			return true; // if no piece in between, move is not legal
		}
		else if (piece instanceof Pawn)
		{
			if (target.isDark() != piece.getSquare().isDark()) return false; // pawns attack the same color square
			
			if (targetIndex == (pieceIndex + (piece.isWhite() ? -7 : 7)) || targetIndex == (pieceIndex + (piece.isWhite() ? -9 : 9)))
			{
				return true;
			}
			return false;
		}
		else if (piece instanceof King)
		{
			// if enemy king is not within one rank AND one file, move must be legal
			if (Math.abs(targetRank - pieceRank) > 1 || Math.abs(targetFile - pieceFile) > 1)
				return false;
			else
				return true; // if within that distance, move is not legal
		}
		else if (piece instanceof Knight)
		{
			if (target.isDark() == piece.getSquare().isDark()) return false; // knights cannot attack the same color square
			if (Math.abs(targetIndex - pieceIndex) > 17) return false; // maximum index movement of a knight
			
			// knightMoves[] gives the index offsets for the 8 possible knight moves
			for (int i = 0; i < knightMoves.length; i++)
			{
				int newIndex = pieceIndex + knightMoves[i];
				if (newIndex < 0 || newIndex > 63)
					continue; // skip if index is off the board
				if (Math.abs(pieceIndex % 8 - newIndex % 8) != (i < 4 ? 1 : 2))
					continue; // skip if index overflowed to next rank or file incorrectly
				if (newIndex == targetIndex) return true;
			}
			return false;
		}
		return false; // reaching here shouldnt be possible so just say cannot attack square
	}

	/**
	 * Checks if given MoveCastle move is legal
	 * 
	 * @param king The friendly king Piece object
	 * @param move The given MoveCastle move to check legality
	 * 
	 * @return True if given movecastle is legal, false if it is not legal
	 */
	public boolean castleLegalityHelper(Piece king, Move move)
	{
		if (boardInfo.getCheckPiece() != null) return false; // cannot castle while in check
				
		// both these squares must be safe
		Square kingOne = squares.get(move.getTargetIndex()); // target square for king
		Square kingTwo = squares.get((move.getTargetIndex() + move.getStartIndex()) / 2); // square in between current and target king square
		
		for (int i = (king.isWhite() ? 0 : 16); i < (king.isWhite() ? 0 : 16) + 16; i++) // iterate over all enemy pieces
		{
			if (pieces.get(i).isCaptured()) continue;

			// otherwise just check if the piece can attack either square
			if (canPieceAttackSquare(pieces.get(i), kingOne) || canPieceAttackSquare(pieces.get(i), kingTwo))
			{
				return false;
			}
		}
		return true; // castling is legal if this line is reached
	}
	
	public ArrayList<Square> getSquaresInBetween(Piece piece, Square target)
	{
		ArrayList<Square> squaresList = new ArrayList<Square>();
		if (!(piece instanceof Bishop || piece instanceof Rook || piece instanceof Queen)) return squaresList;
		
		int targetIndex = target.getIndex();
		int targetRank = target.getRank();
		int targetFile = target.getFile();
		int pieceIndex = piece.getSquare().getIndex();
		int pieceRank = piece.getSquare().getRank();
		int pieceFile = piece.getSquare().getFile();
		
		if (piece instanceof Rook && (targetRank != pieceRank && targetFile != pieceFile))
			return squaresList;
		else if (piece instanceof Bishop && (Math.abs(targetRank - pieceRank) != Math.abs(targetFile - pieceFile)))
			return squaresList;
		else if (piece instanceof Queen && ((targetRank != pieceRank && targetFile != pieceFile) && (Math.abs(targetRank - pieceRank) != Math.abs(targetFile - pieceFile))))
			return squaresList;
		
		int step = 0;
		int stepCount = 0;
		
		// if rook or queen attacking like rook, only one condition will be true, if bishop or queen attacking like bishop, both will be true
		if (targetRank != pieceRank)
		{
			step += ((targetRank > pieceRank) ? -8 : 8);
			stepCount = Math.abs(targetRank - pieceRank);
		}
		if (targetFile != pieceFile)
		{
			step += ((targetFile > pieceFile) ? 1 : -1);
			stepCount = Math.abs(targetFile - pieceFile);
		}
		
		
		for (int i = 1; i < stepCount; i++)
		{
			int index = pieceIndex + i * step;
			squaresList.add(squares.get(index));

		}
		return squaresList; // if no piece in between, move is not legal
	}
	
	public boolean isEnPassantLegal(Move move)
	{
		Square pawnSquare = squares.get(move.getStartIndex());
		int pawnIndex = pawnSquare.getIndex();
		Piece pawn = pawnSquare.getPiece();
		Square enemySquare = squares.get(move.getEnPassantCaptureIndex());
		Piece enemy = enemySquare.getPiece();
		
		Square king = pieces.get(pawn.isWhite() ? 28 : 4).getSquare();
		if (king.getRank() != pawnSquare.getRank()) return true;
		
		int step = (pawnIndex < king.getIndex()) ? 1 : -1;
		for (int i = (pawnIndex+step); i != king.getIndex(); i += step)
		{
			Square newSquare = squares.get(i);
			if (newSquare.getPiece() != null && newSquare.getPiece() != enemy) return true; // some piece found between pawns and king
		}
		
		// if still here, look the other way for a rook/queen that is pinning them
		int newIndex = pawnIndex - step;
		while (newIndex / 8 == pawnIndex / 8)
		{
			Square newSquare = squares.get(newIndex);
			if (newSquare.getPiece() != null && newSquare.getPiece() != enemy)
			{
				Piece piece = newSquare.getPiece();
				if (piece.isWhite() != pawn.isWhite() && (piece instanceof Rook || piece instanceof Queen)) return false;
				
				return true;
			}
			newIndex -= step;
		}
		return true;
	}

	/**
	 * Generate all pseudolegal moves for a given piece. Pseudolegal moves obey piece movement rules but ignore if king is already in check or if king will be put in discovered check
	 * 
	 * @param piece The given piece to generate all pseudolegal moves for
	 * 
	 * @return ArrayList<Move> of all pseudolegal moves for the given piece
	 */
	public ArrayList<Move> generatePseudoLegalMoves(Piece piece)
	{
		ArrayList<Move> moves = new ArrayList<Move>();
		Square start = piece.getSquare();

		int index = start.getIndex();

		boolean isWhite = piece.isWhite();

		if (piece instanceof Pawn)
		{
			int targetRankIndex = index + (isWhite ? -8 : 8);
			if (targetRankIndex > 63 || targetRankIndex < 0)
				return moves; // cannot go off the board though this should never happen
			if (!squares.get(targetRankIndex).hasPiece()) // if no piece, pawn can move forward one
			{
				if (targetRankIndex / 8 == 0 || targetRankIndex / 8 == 7)
				{
					// 4 possible moves for each promotion piece
					moves.add(new Move(index, targetRankIndex, 0, 1));
					moves.add(new Move(index, targetRankIndex, 0, 2));
					moves.add(new Move(index, targetRankIndex, 0, 3));
					moves.add(new Move(index, targetRankIndex, 0, 4));
				}
				else
					moves.add(new Move(index, targetRankIndex, 0)); // otherwise just one move for pushing pawn
				if (index / 8 == (isWhite ? 6 : 1))
				{
					// potentially a second move for moving pawn two squares if it has not moved yet
					if (!squares.get(targetRankIndex + (isWhite ? -8 : 8)).hasPiece())
						moves.add(new Move(index, targetRankIndex + (isWhite ? -8 : 8), 0, false, true));
				}
			}
			if (((targetRankIndex + 1) / 8 == targetRankIndex / 8 && (targetRankIndex + 1) < 64) && (targetRankIndex + 1 == boardInfo.getEnPassantIndex() || (squares.get(targetRankIndex + 1).hasPiece() && squares.get(targetRankIndex + 1).getPiece().isWhite() != isWhite)))
			{
				// pawn capturing to the right
				if ((targetRankIndex + 1) / 8 == 0 || (targetRankIndex + 1) / 8 == 7)
				{
					// 4 possible moves for each promotion piece
					moves.add(new Move(index, (targetRankIndex + 1), 0, 1));
					moves.add(new Move(index, (targetRankIndex + 1), 0, 2));
					moves.add(new Move(index, (targetRankIndex + 1), 0, 3));
					moves.add(new Move(index, (targetRankIndex + 1), 0, 4));
				}
				else if (targetRankIndex + 1 == boardInfo.getEnPassantIndex()) // pawn can capture enemy pawn with en passant
				{
					moves.add(new Move(index, (targetRankIndex + 1), 0,  true, false));
				}
				else
					moves.add(new Move(index, (targetRankIndex + 1), 0)); // otherwise just normal pawn capture
			}
			if (((targetRankIndex - 1) / 8 == targetRankIndex / 8 && (targetRankIndex - 1) >= 0) && (targetRankIndex - 1 == boardInfo.getEnPassantIndex() || (squares.get(targetRankIndex - 1).hasPiece() && squares.get(targetRankIndex - 1).getPiece().isWhite() != isWhite)))
			{
				// pawn capturing to the left
				if ((targetRankIndex - 1) / 8 == 0 || (targetRankIndex - 1) / 8 == 7)
				{
					// 4 possible moves for each promotion piece
					moves.add(new Move(index, (targetRankIndex - 1), 0, 1));
					moves.add(new Move(index, (targetRankIndex - 1), 0, 2));
					moves.add(new Move(index, (targetRankIndex - 1), 0, 3));
					moves.add(new Move(index, (targetRankIndex - 1), 0, 4));
				}
				else if (targetRankIndex - 1 == boardInfo.getEnPassantIndex()) // pawn can capture enemy pawn with en passant
				{
					moves.add(new Move(index, (targetRankIndex - 1), 0, true, false));
				}
				else
					moves.add(new Move(index, (targetRankIndex - 1), 0)); // otherwise just normal pawn capture
			}
		}

		else if (piece instanceof Knight)
		{
			// knightMoves[] gives the index offsets for the 8 possible knight moves
			for (int i = 0; i < knightMoves.length; i++)
			{
				int targetIndex = index + knightMoves[i];
				if (targetIndex < 0 || targetIndex > 63)
					continue; // skip if index is off the board
				if (Math.abs(index % 8 - targetIndex % 8) != (i < 4 ? 1 : 2))
					continue; // skip if index overflowed to next rank or file incorrectly
				if (squares.get(targetIndex).hasPiece() && squares.get(targetIndex).getPiece().isWhite() == isWhite)
					continue; // skip if target square is occupied by friendly piece
				moves.add(new Move(index, targetIndex, 0)); // add new knight move
			}
		}

		else if (piece instanceof Queen || piece instanceof Rook || piece instanceof Bishop)
		{
			// sets which indices of sliderMoves[] to check depending on piece;
			int sliderArrayStart = piece instanceof Bishop ? 4 : 0; // start at 4 if bishop, 0 if rook or queen
			int sliderArrayLength = piece instanceof Queen ? 8 : 4; // iterate over all 8 elements if queen, otherwise only 4 for rook and bishop

			for (int i = sliderArrayStart; i < sliderArrayStart + sliderArrayLength; i++)
			{
				boolean stop = false;
				int multiplier = 1;
				while (!stop)
				{
					int targetIndex = index + sliderMoves[i] * multiplier;
					if (targetIndex >= 0 && targetIndex < 64) // make sure index is on the board
					{
						if ((i == 1 || i == 3) && index / 8 != targetIndex / 8)
							stop = true; // if moving left/right, stop if index goes onto a different row
						else if (i > 3 && (Math.abs((targetIndex / 8) - (index / 8)) - Math.abs((targetIndex % 8) - (index % 8)) != 0))
							stop = true;
						else
						{
							if (!squares.get(targetIndex).hasPiece())
								moves.add(new Move(index, targetIndex, 0)); // if no piece, possible move to the empty square
							else
							{
								if (squares.get(targetIndex).getPiece().isWhite() != isWhite)
									moves.add(new Move(index, targetIndex, 0)); // if piece, and is enemy, this is last move
								stop = true;
							}
						}
					}
					else
						stop = true;
					multiplier++;
				}
			}
		}

		else if (piece instanceof King)
		{
			for (int i = 0; i < sliderMoves.length; i++)
			{
				// king can move in all sliderMoves[] directions but only one square
				int targetIndex = index + sliderMoves[i];
				if (targetIndex >= 0 && targetIndex < 64)
				{
					if (!((i == 1 || i == 3) && index / 8 != targetIndex / 8)) // make sure still on correct rank
					{
						if (!(i > 3 && (Math.abs((targetIndex / 8) - (index / 8)) - Math.abs((targetIndex % 8) - (index % 8)) != 0))) // make sure still on same diagonal
						{
							// add move if no piece or piece is enemy
							if (!squares.get(targetIndex).hasPiece() || squares.get(targetIndex).getPiece().isWhite() != isWhite)
								moves.add(new Move(index, targetIndex, 0));
						}
					}
				}
			}

			// add kingside castle move if castling rights and not blocked
			if (boardInfo.getCastlingRights()[(isWhite ? 0 : 2)] && !(squares.get(index + 1).hasPiece() || squares.get(index + 2).hasPiece()) && squares.get(index + 3).hasPiece() && !squares.get(index + 3).getPiece().isCaptured())
			{
				moves.add(new Move(index, (index + 2), 0, true));
			}

			// add queenside castle move if castling rights and not blocked
			if (boardInfo.getCastlingRights()[(isWhite ? 1 : 3)] && !(squares.get(index - 1).hasPiece() || squares.get(index - 2).hasPiece() || squares.get(index - 3).hasPiece()) && squares.get(index - 4).hasPiece() && !squares.get(index - 4).getPiece().isCaptured())
			{
				moves.add(new Move(index, (index - 2), 0, false));
			}
		}

		return moves; // return the list of moves
	}

	/**
	 * Makes the given move on the board.
	 * 
	 * @param move The move to be played
	 * 
	 * @return The Piece that was captured or null if no piece captured
	 */
	public Piece movePiece(Move move)
	{
		Square start = squares.get(move.getStartIndex());
		Square target = squares.get(move.getTargetIndex());
		Square capture = target;
		if (move.getEnPassantCaptureIndex() != -1)
			capture = squares.get(move.getEnPassantCaptureIndex()); // if en passant, change capture square
		Piece piece = start.getPiece();
		Piece capturedPiece = null;

		long newZobristHash = updateZobristHash(move);
		boardInfo.setZobristHash(newZobristHash);	// set the hash
		boardInfo.getPositionList().add(newZobristHash);

		// update boardInfo information
		boardInfo.setWhiteToMove(!boardInfo.isWhiteToMove());
		boardInfo.setCheckPiece(null);
		boardInfo.setDoubleCheck(false);
		boardInfo.setHalfMoves(boardInfo.getHalfMoves() + 1);
		if (piece instanceof Pawn)
			boardInfo.setHalfMoves(0); // reset half moves for 50 move rule
		if (!piece.isWhite())
			boardInfo.setMoveNum(boardInfo.getMoveNum() + 1);

		boardInfo.getMoveList().add(move);
		
		// update the material
		boolean endgame = boardInfo.getWhiteMaterial() + boardInfo.getBlackMaterial() < 4000;
		if (piece.isWhite())
		{
			int newBonus = boardInfo.getWhiteSquareBonus();
			newBonus -= (piece.getPieceSquareTable(endgame)[move.getStartIndex()]);
			newBonus += (piece.getPieceSquareTable(endgame)[move.getTargetIndex()]);
			boardInfo.setWhiteSquareBonus(newBonus);
		}
		else
		{
			int newBonus = boardInfo.getBlackSquareBonus();
			newBonus -= (piece.getPieceSquareTable(endgame)[((63 - piece.getSquare().getIndex()) / 8) * 8 + (piece.getSquare().getIndex() % 8)]);
			newBonus += (piece.getPieceSquareTable(endgame)[((63 - move.getTargetIndex()) / 8) * 8 + (move.getTargetIndex() % 8)]);
			boardInfo.setBlackSquareBonus(newBonus);
		}

		if (capture.hasPiece()) // if move is a capture
		{
			boardInfo.setHalfMoves(0); // reset half moves for 50 move rule
			capturedPiece = capture.getPiece();
			
			promotedSliders.remove(capturedPiece); // remove the piece from promotedSliders if it exists in that arraylist
			
			if (capturedPiece instanceof King)
			{
				System.out.println(boardInfo.getMoveList());
				printBoard();
			}
			
			// update the material
			if (capturedPiece.isWhite())
			{
				boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() - capturedPiece.getValue());
				boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() - (capturedPiece.getPieceSquareTable(endgame)[capturedPiece.getSquare().getIndex()]));
			}
			else
			{
				boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() - capturedPiece.getValue());
				boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() - (capturedPiece.getPieceSquareTable(endgame)[((63 - capturedPiece.getSquare().getIndex()) / 8) * 8 + (capturedPiece.getSquare().getIndex() % 8)]));
			}
			
			if (capturedPiece instanceof Rook) // update castling rights if rook was captured
			{
				boolean[] castlingRights = boardInfo.getCastlingRights();
				if (!pieces.get(31).isCaptured() && target.getIndex() == pieces.get(31).getSquare().getIndex())
					castlingRights[0] = false;
				else if (!pieces.get(24).isCaptured() && target.getIndex() == pieces.get(24).getSquare().getIndex())
					castlingRights[1] = false;
				else if (!pieces.get(7).isCaptured() && target.getIndex() == pieces.get(7).getSquare().getIndex())
					castlingRights[2] = false;
				else if (!pieces.get(0).isCaptured() && target.getIndex() == pieces.get(0).getSquare().getIndex())
					castlingRights[3] = false;
				boardInfo.setCastlingRights(castlingRights);
			}
			
			// remove captured piece from the board
			capturedPiece.getSquare().removePiece();
			capturedPiece.setSquare(null);
			capturedPiece.setCaptured(true);
		}

		if (move.isCastleMove()) // if move is castling
		{
			Square rookStart = squares.get(move.getRookStartIndex());
			Square rookTarget = squares.get(move.getRookTargetIndex());
			
			// move the rook
			Piece rook = rookStart.getPiece();
			rook.setSquare(rookTarget);
			rookStart.removePiece();
			rookTarget.addPiece(rook);

			// move the king
			piece.setSquare(target);
			start.removePiece();
			target.addPiece(piece);

			// update castling rights
			boolean[] castlingRights = boardInfo.getCastlingRights();
			if (piece.isWhite())
				castlingRights[0] = castlingRights[1] = false;
			else
				castlingRights[2] = castlingRights[3] = false;
			boardInfo.setCastlingRights(castlingRights);
			
			if (piece.isWhite())
			{
				int newBonus = boardInfo.getWhiteSquareBonus();
				newBonus -= (rook.getPieceSquareTable(endgame)[rookStart.getIndex()]);
				newBonus += (rook.getPieceSquareTable(endgame)[rookTarget.getIndex()]);
				boardInfo.setWhiteSquareBonus(newBonus);
			}
			else
			{
				int newBonus = boardInfo.getBlackSquareBonus();
				newBonus -= (rook.getPieceSquareTable(endgame)[((63 - rookStart.getIndex()) / 8) * 8 + (rookStart.getIndex() % 8)]);
				newBonus += (rook.getPieceSquareTable(endgame)[((63 - rookTarget.getIndex()) / 8) * 8 + (rookTarget.getIndex() % 8)]);
				boardInfo.setBlackSquareBonus(newBonus);
			}
		}
		else if (move.getPromotedPiece() != 0) // if move is promotion
		{
			Piece promoted = null;
			
			if (move.getPromotedPiece() == 1) promoted = new Queen(null, piece.isWhite());
			else if (move.getPromotedPiece() == 2) promoted = new Rook(null, piece.isWhite());
			else if (move.getPromotedPiece() == 3) promoted = new Bishop(null, piece.isWhite());
			else if (move.getPromotedPiece() == 4) promoted = new Knight(null, piece.isWhite());
			
			// replace the pawn with the new piece
			pieces.set(pieces.indexOf(piece), promoted);
			piece.getSquare().addPiece(promoted);

			// place the new piece on the board
			promoted.setSquare(target);
			start.removePiece();
			target.addPiece(promoted);

			// if it is not a knight, add it to promotedSliders
			if (!(promoted instanceof Knight))
				promotedSliders.add(promoted);

			// update material
			if (piece.isWhite())
				boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() - piece.getValue() + promoted.getValue());
			else
				boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() - piece.getValue() + promoted.getValue());
			
			if (piece.isWhite())
			{
				int newBonus = boardInfo.getWhiteSquareBonus();
				newBonus -= (piece.getPieceSquareTable(endgame)[target.getIndex()]);
				newBonus += (promoted.getPieceSquareTable(endgame)[target.getIndex()]);
				boardInfo.setWhiteSquareBonus(newBonus);
			}
			else
			{
				int newBonus = boardInfo.getBlackSquareBonus();
				newBonus -= (piece.getPieceSquareTable(endgame)[((63 - target.getIndex()) / 8) * 8 + (target.getIndex() % 8)]);
				newBonus += (promoted.getPieceSquareTable(endgame)[((63 - target.getIndex()) / 8) * 8 + (target.getIndex() % 8)]);
				boardInfo.setBlackSquareBonus(newBonus);
			}

			// set piece to the new piece
			piece = promoted;
		}
		else // if it is a normal move
		{
			if (piece instanceof Rook) // update castling rights if rook move
			{
				boolean[] castlingRights = boardInfo.getCastlingRights();
				if (castlingRights[0] && move.getStartIndex() == 63)
					castlingRights[0] = false;
				else if (castlingRights[1] && move.getStartIndex() == 56)
					castlingRights[1] = false;
				else if (castlingRights[2] && move.getStartIndex() == 7)
					castlingRights[2] = false;
				else if (castlingRights[3] && move.getStartIndex() == 0)
					castlingRights[3] = false;
				boardInfo.setCastlingRights(castlingRights);
			}

			else if (piece instanceof King) // update castling rights if king move
			{
				boolean[] castlingRights = boardInfo.getCastlingRights();
				if (piece.isWhite())
					castlingRights[0] = castlingRights[1] = false;
				else
					castlingRights[2] = castlingRights[3] = false;
				boardInfo.setCastlingRights(castlingRights);
			}

			// move the piece to the new square
			piece.setSquare(target);
			start.removePiece();
			target.addPiece(piece);
		}

		// set or reset en passant index
		boardInfo.setEnPassantIndex(move.getEnPassantNewIndex());
		
		if (piece instanceof Knight || piece instanceof Pawn)
		{
			if (canPieceAttackSquare(piece, pieces.get((piece.isWhite() ? 4 : 28)).getSquare()))
			{
				boardInfo.setCheckPiece(piece); // set this piece as the checkPiece
			}
		}
		
		updateAllPins(move, capturedPiece);
		

		return capturedPiece; // return the captured piece
	}
	
	public void checkCreatedPin(Square start, Square king)
	{
		// get rank and files for both squares
		int targetRank = king.getRank();
		int targetFile = king.getFile();

		int startIndex = start.getIndex();
		int startRank = start.getRank();
		int startFile = start.getFile();
		
		if ((targetRank != startRank && targetFile != startFile) && (Math.abs(targetRank - startRank) != Math.abs(targetFile - startFile)))
			return;
		
		int step = 0;
		boolean diagonal = true;
		// if rook or queen attacking like rook, only one condition will be true, if bishop or queen attacking like bishop, both will be true
		if (targetRank != startRank)
		{
			step += ((targetRank > startRank) ? -8 : 8);
			diagonal = !diagonal;
		}
		if (targetFile != startFile)
		{
			step += ((targetFile > startFile) ? 1 : -1);
			diagonal = !diagonal;
		}
		
		step *= -1;
		int index = startIndex + step;
		boolean pieceFound = false;
		while (true)
		{
			if (index < 0 || index > 63) break;
			if (!diagonal && (index / 8 != startIndex / 8) && (index % 8 != startIndex % 8)) break;
			if (diagonal && (Math.abs((index / 8) - (startIndex / 8)) != Math.abs((index % 8) - (startIndex % 8)))) break;
			
			if (squares.get(index).hasPiece())
			{
				Piece piece = squares.get(index).getPiece();
				
				if (piece.isWhite() != king.getPiece().isWhite())
				{
					Piece pinned = getPiecePinnedBySlider(piece, king);
					if (pinned instanceof King)
					{
						if (boardInfo.getCheckPiece() != null && boardInfo.getCheckPiece() != piece) boardInfo.setDoubleCheck(true);
						else boardInfo.setCheckPiece(piece);
					}
					else if (pinned != null)
					{
						PinnedPiece pinnedPiece = new PinnedPiece(pinned, piece);
						pinnedPieces.add(pinnedPiece);;
					}
					return;
				}
				else
				{
					if (pieceFound) return;
					else pieceFound = true;
				}
			}
			
			index += step;
		}
	}
	
	public void updateAllPins(Move move, Piece captured)
	{
		Square start = squares.get(move.getStartIndex());
		Square target = squares.get(move.getTargetIndex());
		Piece piece = target.getPiece();
		
		pinnedPieces.removeIf(pinnedPiece -> (pinnedPiece.pinner == captured || pinnedPiece.pinner == piece || pinnedPiece.pinned == captured || pinnedPiece.pinned == piece));
		
		if (piece instanceof King)
		{
			checkAllPins();
			return;
		}
		
		checkCreatedPin(start, pieces.get(4).getSquare());
		checkCreatedPin(start, pieces.get(28).getSquare());
		
		if (move.getEnPassantCaptureIndex() != -1)
		{
			checkCreatedPin(squares.get(move.getEnPassantCaptureIndex()), pieces.get(4).getSquare());
			checkCreatedPin(squares.get(move.getEnPassantCaptureIndex()), pieces.get(28).getSquare());
		}
		
		checkCreatedPin(target, pieces.get((piece.isWhite() ? 28 : 4)).getSquare());
		
		Piece pinned = getPiecePinnedBySlider(piece, pieces.get((piece.isWhite() ? 4 : 28)).getSquare());
		if (pinned instanceof King)
		{
			if (boardInfo.getCheckPiece() != null && boardInfo.getCheckPiece() != piece) boardInfo.setDoubleCheck(true);
			else boardInfo.setCheckPiece(piece);
		}
		else if (pinned != null)
		{
			PinnedPiece pinnedPiece = new PinnedPiece(pinned, piece);
			pinnedPieces.add(pinnedPiece);
		}
		
		pinnedPieces.removeIf(pinnedPiece -> (getPiecePinnedBySlider(pinnedPiece.pinner, pieces.get((pinnedPiece.pinner.isWhite() ? 4 : 28)).getSquare()) == null));
	}
	
	public PinnedPiece isPiecePinned(Piece piece)
	{
		for (PinnedPiece pinned : pinnedPieces)
		{
			if (pinned.pinned == piece) return pinned;
		}
		return null;
	}
	
	public void checkAllPins()
	{
		// check all original slider pieces
		for (int i = 0; i < sliderIndexes.length; i++)
		{
			Piece slider = pieces.get(sliderIndexes[i]);
			if (!slider.isCaptured()) updatePin(slider, pieces.get((slider.isWhite() ? 4 : 28)).getSquare());
		}
		
		for (Piece slider : promotedSliders) // check promoted slider pieces
		{
			if (!slider.isCaptured()) updatePin(slider, pieces.get((slider.isWhite() ? 4 : 28)).getSquare());
		}
	}
	
	public void updatePin(Piece slider, Square king)
	{
		pinnedPieces.removeIf(pinnedPiece -> (pinnedPiece.pinner == slider));
		/*
		if (slider.getPiecePinned() != null)
		{
			slider.getPiecePinned().setPiecePinning(null);
			slider.setPiecePinned(null);
		}
		*/
		
		Piece pinned = getPiecePinnedBySlider(slider, king);
		if (pinned instanceof King)
		{
			if (boardInfo.getCheckPiece() != null && boardInfo.getCheckPiece() != slider) boardInfo.setDoubleCheck(true);
			else boardInfo.setCheckPiece(slider);
		}
		else if (pinned != null)
		{
			PinnedPiece pinnedPiece = new PinnedPiece(pinned, slider);
			pinnedPieces.add(pinnedPiece);
		}
	}
	
	public Piece getPiecePinnedBySlider(Piece friendlySlider, Square enemyKing)
	{
		if (!(friendlySlider instanceof Bishop || friendlySlider instanceof Rook || friendlySlider instanceof Queen)) return null;
		
		int sliderIndex = friendlySlider.getSquare().getIndex();
		int sliderRank = friendlySlider.getSquare().getRank();
		int sliderFile = friendlySlider.getSquare().getFile();
		int kingRank = enemyKing.getRank();
		int kingFile = enemyKing.getFile();
		
		if (friendlySlider instanceof Rook && (kingRank != sliderRank && kingFile != sliderFile))
			return null;
		else if (friendlySlider instanceof Bishop && (Math.abs(kingRank - sliderRank) != Math.abs(kingFile - sliderFile)))
			return null;
		else if (friendlySlider instanceof Queen && ((kingRank != sliderRank && kingFile != sliderFile) && (Math.abs(kingRank - sliderRank) != Math.abs(kingFile - sliderFile))))
			return null;
		
		int step = 0;
		int stepCount = 0;
		// if rook or queen attacking like rook, only one condition will be true, if bishop or queen attacking like bishop, both will be true
		if (kingRank != sliderRank)
		{
			step += ((kingRank > sliderRank) ? -8 : 8);
			stepCount = Math.abs(kingRank - sliderRank);
		}
		if (kingFile != sliderFile)
		{
			step += ((kingFile > sliderFile) ? 1 : -1);
			stepCount = Math.abs(kingFile - sliderFile);
		}
		
		Piece pinnedPiece = null;
		for (int i = 1; i < stepCount; i++)
		{
			int index = sliderIndex + i * step;
			if (squares.get(index).hasPiece())
			{
				if (pinnedPiece != null) return null; // found at least two pieces in between slider and king, cannot be pinned
				else pinnedPiece = squares.get(index).getPiece(); // found a potential pinned piece
			}
		}
		// if this is reached, at most one piece found between slider and king
		if (pinnedPiece == null) return enemyKing.getPiece(); // no pinned piece, king attacked directly
		else if(pinnedPiece.isWhite() == friendlySlider.isWhite()) return null; // TODO: at some point track discovered check pieces as well but for now cannot pin your own piece
		else return pinnedPiece; // return the pinned piece
	}

	/**
	 * Undo the given move and return the board to the original board state
	 * 
	 * @param move         The move to undo
	 * @param captured     The captured piece, null if no captured piece
	 * @param boardInfoOld The BoardInfo object for the original board state
	 */
	public void moveBack(Move move, Piece captured, BoardInfo boardInfoOld, ArrayList<PinnedPiece> pinnedPiecesOld)
	{
		boardInfo = boardInfoOld; // replace current BoardInfo object with the old BoardInfo object
		pinnedPieces = pinnedPiecesOld;

		Square start = squares.get(move.getStartIndex());
		Square target = squares.get(move.getTargetIndex());
		
		// move the piece back to its original square
		Piece piece = target.removePiece();
		piece.setSquare(start);
		start.addPiece(piece);
		Square captureSquare = target;

		if (move.isCastleMove()) // if it was a castle move
		{
			// need to move the rook back too
			Square rookStart = squares.get(move.getRookStartIndex());
			Square rookTarget = squares.get(move.getRookTargetIndex());
			Piece rook = rookTarget.removePiece();
			rook.setSquare(rookStart);
			rookStart.addPiece(rook);
		}
		else if (move.getPromotedPiece() != 0) // if it was a promotion
		{
			// remove piece from promotedSliders (promotedSliders remains unchanged if piece was a knight)
			if (!(piece instanceof Knight))
				promotedSliders.remove(piece);
			
			// pinnedPieces.removeIf(pinnedPiece -> (pinnedPiece.pinner == piece));
			/*
			if (piece.getPiecePinned() != null)
			{
				piece.getPiecePinned().setPiecePinning(null);
				piece.setPiecePinned(null);
			}
			*/

			Piece pawn = new Pawn(start, piece.isWhite());
			// replace promoted piece with a pawn
			pieces.set(pieces.indexOf(piece), pawn);
			piece.getSquare().removePiece();
			start.addPiece(pawn);
			
			// piece = null; // commented out because it should get garbage collected after this method finishes anyways
		}
		else if (move.getEnPassantCaptureIndex() != -1) // if en passant move
		{
			captureSquare = squares.get(move.getEnPassantCaptureIndex()); // set captureSquare to the right place
		}

		if (captured != null) // if there was a captured piece
		{
			// put captured piece back on the board
			captured.setCaptured(false);
			captured.setSquare(captureSquare);
			captureSquare.addPiece(captured);

			if (captured instanceof Queen || captured instanceof Rook || captured instanceof Bishop)
			{
				int index = pieces.indexOf(captured);
				if (index > 7 && index < 24)
				{
					promotedSliders.add(captured);
				}
			}
		}
		
		for (PinnedPiece pin : pinnedPieces)
		{
			if (pin.pinned instanceof Pawn && pieces.indexOf(pin.pinned) == -1)
			{
				pin.pinned = getPiecePinnedBySlider(pin.pinner, pieces.get((pin.pinner.isWhite() ? 4 : 28)).getSquare());
				break;
			}
		}
		
		// updateAllPins();
	}
	
	public boolean isPiecePinnedAbsolutely(Piece pinned, Piece pinner)
	{
		if (pinned instanceof Queen) return false; // quick exit to avoid calling canPieceAttackSquare
		if (pinned instanceof Knight) return true; // quick exit to avoid calling canPieceAttackSquare
		if (pinned.getClass() == pinner.getClass()) return false; // quick exit to avoid calling canPieceAttackSquare
		if (canPieceAttackSquare(pinned, pinner.getSquare())) return false; // if the pinned piece can capture the pinner, at least one legal move exists (no piece can be double pinned to the king)
		if (pinned instanceof Pawn && (pinner instanceof Rook || pinner instanceof Queen))
			return (pinned.getSquare().getFile() != pinner.getSquare().getFile());
		
		return true;
	}

	public long createZobristHash()
	{
		long zobristHash = 0;
		for (Piece piece : pieces)
		{
			if (!piece.isCaptured())
			{
				zobristHash ^= zobristHashes[(piece.getSquare().getIndex() * 12 + piece.getZobristOffset())];
			}
		}

		if (!boardInfo.isWhiteToMove())
			zobristHash ^= zobristHashes[768];
		if (boardInfo.getCastlingRights()[0])
			zobristHash ^= zobristHashes[769];
		if (boardInfo.getCastlingRights()[1])
			zobristHash ^= zobristHashes[770];
		if (boardInfo.getCastlingRights()[2])
			zobristHash ^= zobristHashes[771];
		if (boardInfo.getCastlingRights()[3])
			zobristHash ^= zobristHashes[772];
		if (boardInfo.getEnPassantIndex() != -1)
			zobristHash ^= zobristHashes[773 + (boardInfo.getEnPassantIndex() % 8)];
		
		return zobristHash;
	}
	
	public long updateZobristHash(Move move)
	{
		long zobristHash = boardInfo.getZobristHash();
		
		Piece piece = squares.get(move.getStartIndex()).getPiece();
		Piece captured = squares.get(move.getTargetIndex()).getPiece();
		if (move.getEnPassantCaptureIndex() != -1) captured = squares.get(move.getEnPassantCaptureIndex()).getPiece();
		
		zobristHash ^= zobristHashes[(move.getStartIndex() * 12 + piece.getZobristOffset())]; // remove piece from start square
		zobristHash ^= zobristHashes[(move.getTargetIndex() * 12 + piece.getZobristOffset())]; // add piece to target square
		if (captured != null) zobristHash ^= zobristHashes[(captured.getSquare().getIndex() * 12 + captured.getZobristOffset())]; // remove captured piece if any
		
		if (boardInfo.getEnPassantIndex() != -1) zobristHash ^= zobristHashes[773 + (boardInfo.getEnPassantIndex() % 8)]; // remove old enpassant index
		if (move.getEnPassantNewIndex() != -1) zobristHash ^= zobristHashes[773 + (move.getEnPassantNewIndex() % 8)]; // add new enpassant index
		
		if (move.getPromotedPiece() != 0)
		{
			int zobristOffset = move.getPromotedPiece()*2;
			if (!piece.isWhite()) zobristOffset++;
			
			zobristHash ^= zobristHashes[(move.getTargetIndex() * 12 + piece.getZobristOffset())]; // remove old piece from target square
			zobristHash ^= zobristHashes[(move.getTargetIndex() * 12 + zobristOffset)]; // add promoted piece to target square
		}
		
		boolean [] castlingRights = boardInfo.getCastlingRights();
		
		if (captured instanceof Rook)
		{
			// remove castling rights because rook captured
			if(!pieces.get(31).isCaptured() && move.getTargetIndex() == 63 && castlingRights[0]) zobristHash ^= zobristHashes[769];
			else if(!pieces.get(24).isCaptured() && move.getTargetIndex() == 56 && castlingRights[1]) zobristHash ^= zobristHashes[770];
			else if(!pieces.get(7).isCaptured() && move.getTargetIndex() == 7 && castlingRights[2]) zobristHash ^= zobristHashes[771];
			else if(!pieces.get(0).isCaptured() && move.getTargetIndex() == 0 && castlingRights[3]) zobristHash ^= zobristHashes[772];
		}
		
		if (piece instanceof Rook)
		{
			// remove castling rights because rook move
			if (castlingRights[0] && move.getStartIndex() == 63)
				zobristHash ^= zobristHashes[769];
			else if (castlingRights[1] && move.getStartIndex() == 56)
				zobristHash ^= zobristHashes[770];
			else if (castlingRights[2] && move.getStartIndex() == 7)
				zobristHash ^= zobristHashes[771];
			else if (castlingRights[3] && move.getStartIndex() == 0)
				zobristHash ^= zobristHashes[772];
		}
		else if (piece instanceof King)
		{
			// remove castling rights (and removes castling rights if this is a castle move as well)
			if (piece.isWhite())
			{
				if (castlingRights[0]) zobristHash ^= zobristHashes[769];
				if (castlingRights[1]) zobristHash ^= zobristHashes[770];
			}
			else
			{
				if (castlingRights[2]) zobristHash ^= zobristHashes[771];
				if (castlingRights[3]) zobristHash ^= zobristHashes[772];
			}
		}
		
		if (move.getRookStartIndex() != -1)
		{
			Piece rook = squares.get(move.getRookStartIndex()).getPiece();
			zobristHash ^= zobristHashes[(move.getRookStartIndex() * 12 + rook.getZobristOffset())]; // remove rook from start square
			zobristHash ^= zobristHashes[(move.getRookTargetIndex() * 12 + rook.getZobristOffset())]; // add rook to target square
		}
		
		zobristHash ^= zobristHashes[768]; // flip side to move
		
		return zobristHash;
	}

	/**
	 * Creates objects for all 32 pieces and adds them in the correct order into the pieces arraylist . All pieces start captured
	 */
	public void setupPieces()
	{
		ArrayList<Piece> newPieces = new ArrayList<Piece>(32);
		newPieces.add(new Rook(null, false));
		newPieces.add(new Knight(null, false));
		newPieces.add(new Bishop(null, false));
		newPieces.add(new Queen(null, false));
		newPieces.add(new King(null, false));
		newPieces.add(new Bishop(null, false));
		newPieces.add(new Knight(null, false));
		newPieces.add(new Rook(null, false));
		for (int i = 0; i < 8; i++)
			newPieces.add(new Pawn(null, false));
		for (int i = 0; i < 8; i++)
			newPieces.add(new Pawn(null, true));
		newPieces.add(new Rook(null, true));
		newPieces.add(new Knight(null, true));
		newPieces.add(new Bishop(null, true));
		newPieces.add(new Queen(null, true));
		newPieces.add(new King(null, true));
		newPieces.add(new Bishop(null, true));
		newPieces.add(new Knight(null, true));
		newPieces.add(new Rook(null, true));
		for (Piece piece : newPieces)
			piece.setCaptured(true);
		pieces = newPieces;
		promotedSliders.clear();
	}

	/**
	 * Load in a FEN string into the virtual board representation
	 * 
	 * @param fen The given FEN string to load in
	 */
	public void loadFEN(String fen)
	{
		setupPieces(); // create all piece objects
		boardInfo.setBoardFEN(fen); // set the fen

		// split the fen into two strings, one for pieces information and the other for extra information
		String fenPieces = fen.substring(0, fen.indexOf(" "));
		String fenOther = fen.substring(fen.indexOf(" "));
		fenPieces.trim();
		fenOther.trim();
		int file = 1;
		int rank = 8;
		ArrayList<Square> newSquares = new ArrayList<Square>(64);
		String[] rows = fenPieces.split("/"); // split the string into each rank
		for (int i = 0; i < 8; i++) // loop will run 8 times for each rank
		{
			String row = rows[i]; // get substring for current rank of the board
			for (int j = 0; j < row.length(); j++) // iterate over each character in substring
			{
				char c = row.charAt(j);
				if (c < 57 && c > 48) // if c is a number
				{
					int num = c - 48; // get the number
					while (num > 0) // loop for num times
					{
						newSquares.add(new Square(rank, file)); // add num empty squares
						file++; // increment the file
						num--;
					}
				}
				else // not a number so a piece
				{
					Square square = new Square(rank, file); // initialize the square
					for (Piece piece : pieces) // loop over all pieces
					{
						if (piece.isCaptured() && piece.getPieceSymbol() == c) // if it is not captured and matches symbol
						{
							// add the piece onto this square
							piece.setCaptured(false);
							piece.setSquare(square);
							square.addPiece(piece);
							break;
						}
					}
					if (!square.hasPiece()) // if the square still doesn't have a piece, the piece must be a promoted pawn
					{
						boolean isWhite = ((int) c) > 65 && ((int) c) < 90; // check if isWhite by looking at the case of the letter c
						for (int k = 0; i < pieces.size(); k++)
						{
							Piece pawn = pieces.get(k);
							if (pawn instanceof Pawn && pawn.isCaptured() && pawn.isWhite() == isWhite) // only check still captured pawns of the same color
							{
								// replace the pawn with the correct piece
								Piece piece = null;
								if (c == 'q' || c == 'Q')
									piece = new Queen(square, isWhite);
								else if (c == 'r' || c == 'R')
									piece = new Rook(square, isWhite);
								else if (c == 'b' || c == 'B')
									piece = new Bishop(square, isWhite);
								else if (c == 'n' || c == 'N')
									piece = new Knight(square, isWhite);

								// add the new piece onto this square and in the pieces ArrayList
								pieces.set(k, piece);
								piece.setCaptured(false);
								square.addPiece(piece);
								if (!(piece instanceof Knight))
									promotedSliders.add(piece); // if not a knight, add this piece to promotedSliders ArrayList
							}
						}
					}
					newSquares.add(square); // add the square into the board
					file++; // increment file to move onto next square
				}
			}
			rank--; // decrement rank
			file = 1; // reset file
		}
		squares = newSquares; // replace squares with filled in ArrayList

		String[] fenInfo = fenOther.split(" "); // split string by spaces to split up information
		for (int i = 0; i < fenInfo.length; i++)
		{
			String string = fenInfo[i];
			if (i == 1)
				boardInfo.setWhiteToMove(string.equals("w")); // sets color to move
			else if (i == 2) // checks castling rights
			{
				boolean[] castlingRights =
				{ false, false, false, false };
				for (int j = 0; j < string.length(); j++)
				{
					if (string.charAt(j) == 'K')
						castlingRights[0] = true;
					if (string.charAt(j) == 'Q')
						castlingRights[1] = true;
					if (string.charAt(j) == 'k')
						castlingRights[2] = true;
					if (string.charAt(j) == 'q')
						castlingRights[3] = true;
					boardInfo.setCastlingRights(castlingRights); // set the castling rights
				}
			}
			else if (i == 3) // checks en passant index
			{
				if (string.length() != 1) // if it is not a "-", en passant index exists
				{
					int enPassantRank = string.charAt(1) - 48;
					int enPassantFile = string.charAt(0) - 96;
					boardInfo.setEnPassantIndex(((8 - enPassantRank) * 8) + enPassantFile - 1); // set the en passant index
				}
			}
			else if (i == 4) // checks and sets half moves for 50 move rule
			{
				if (!string.equals("-"))
					boardInfo.setHalfMoves(string.charAt(0) - 48);
			}
			else if (i == 5) // checks and sets move number
			{
				if (!string.equals("-"))
					boardInfo.setMoveNum(string.charAt(0) - 48);
			}
		}

		// iterate over only knights and pawns to check if king is in check
		for (int i = (boardInfo.isWhiteToMove() ? 0 : 16); i < (boardInfo.isWhiteToMove() ? 0 : 16) + 16; i++)
		{
			if (!pieces.get(i).isCaptured() && (pieces.get(i) instanceof Knight || pieces.get(i) instanceof Pawn))
			{
				ArrayList<Move> enemymoves = generatePseudoLegalMoves(pieces.get(i)); // generate pseudo legal moves for piece
				for (Move enemymove : enemymoves)
				{
					// if move target square is same as square of enemy king, king is in check
					if (enemymove.getTargetIndex() == pieces.get(boardInfo.isWhiteToMove() ? 28 : 4).getSquare().getIndex())
					{
						boardInfo.setCheckPiece(pieces.get(i)); // set the check piece
						break;
					}
				}
				if (boardInfo.getCheckPiece() != null)
					break;
			}
		}
		
		checkAllPins();

		boardInfo.setWhiteMaterial(0);
		boardInfo.setBlackMaterial(0);
		boardInfo.setWhiteSquareBonus(0);
		boardInfo.setBlackSquareBonus(0);
		for (Piece piece : pieces) // iterate over all pieces and sum up material
		{
			if (!piece.isCaptured() && !(piece instanceof King))
			{
				if (piece.isWhite())
					boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() + piece.getValue());
				else
					boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() + piece.getValue());
			}
		}
		boolean endgame = boardInfo.getWhiteMaterial() + boardInfo.getBlackMaterial() < 4000;
		for (Piece piece : pieces)
		{
			if (piece.isCaptured()) continue;
			
			if (piece.isWhite())
			{
				int newBonus = boardInfo.getWhiteSquareBonus();
				newBonus += (piece.getPieceSquareTable(endgame)[piece.getSquare().getIndex()]);
				boardInfo.setWhiteSquareBonus(newBonus);
			}
			else
			{
				int newBonus = boardInfo.getBlackSquareBonus();
				newBonus += (piece.getPieceSquareTable(endgame)[((63 - piece.getSquare().getIndex()) / 8) * 8 + (piece.getSquare().getIndex() % 8)]);
				boardInfo.setBlackSquareBonus(newBonus);
			}
		}
		boardInfo.setZobristHash(createZobristHash());
		boardInfo.getPositionList().add(boardInfo.getZobristHash());
	}

	/**
	 * Creates a FEN string for the current position with or without half moves and move number included
	 * 
	 * @param includeMoveNums Boolean for whether or not to include moveNums and halfMoves in the FEN string
	 * 
	 * @return The created FEN string
	 */
	public String createFEN(boolean includeMoveNums)
	{
		int count = 0;
		String newFEN = "";
		for (int i = 0; i < 64; i++) // iterate over all squares
		{
			if (squares.get(i).hasPiece() && !squares.get(i).getPiece().isCaptured()) // if an uncaptured piece on this square
			{
				if (count != 0) // if count number of empty squares preceded this piece
				{
					newFEN += "" + count; // add the number count to the string
					count = 0; // reset count
				}
				newFEN += "" + squares.get(i).getPiece().getPieceSymbol(); // add the piece symbol of this piece
			}
			else
				count++; // otherwise increase count for another empty square

			if ((i + 1) % 8 == 0) // if end of a row reached
			{
				if (count != 0) // if count is not 0
				{
					newFEN += "" + count; // add count, the number of empty squares
					count = 0;
				}
				newFEN += "/"; // add a slash to signify next row
			}
		}

		newFEN += " " + (boardInfo.isWhiteToMove() ? "w" : "b") + " "; // color to move
		boolean[] castlingRights = boardInfo.getCastlingRights(); // set castling rights
		if (castlingRights[0])
			newFEN += "K";
		if (castlingRights[1])
			newFEN += "Q";
		if (castlingRights[2])
			newFEN += "k";
		if (castlingRights[3])
			newFEN += "q";
		newFEN += " ";
		if (boardInfo.getEnPassantIndex() == -1)
			newFEN += "- "; // add dash if no en passant index
		else
		{
			int index = boardInfo.getEnPassantIndex();
			char file = (char) ((index % 8) + 97);
			int rank = 8 - (index / 8);
			newFEN += file + "" + rank + " "; // add square of en passant index
		}
		if (includeMoveNums)
			newFEN += boardInfo.getHalfMoves() + " " + boardInfo.getMoveNum(); // add moves numbers if includeMoveNums is true

		newFEN.trim();

		return newFEN; // return the created FEN string
	}

	/**
	 * Prints the representation of the board onto the screen
	 * 
	 */
	public void printBoard()
	{
		for (int i = 0; i < 64; i++)
		{
			if (i % 8 == 0)
				System.out.print("\n+---+---+---+---+---+---+---+---+\n| ");
			Square square = squares.get(i);
			if (square.hasPiece()) // if piece exists
			{
				System.out.print(square.getPiece().getPieceSymbol() + " | "); // print the piece symbol
			}
			else
				System.out.print("  | "); // or just print an empty space
		}
		System.out.println("\n+---+---+---+---+---+---+---+---+\n");
	}
}