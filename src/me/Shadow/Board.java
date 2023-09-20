package me.Shadow;

import java.util.ArrayList;
import java.util.Random;

public class Board
{

	//ArrayList<Piece> pieces = new ArrayList<Piece>(32);
	ArrayList<Piece> whitePieces = new ArrayList<Piece>();
	ArrayList<Piece> whiteSliders = new ArrayList<Piece>();
	ArrayList<Piece> blackPieces = new ArrayList<Piece>();
	ArrayList<Piece> blackSliders = new ArrayList<Piece>();
	ArrayList<PinnedPiece> pinnedPieces = new ArrayList<PinnedPiece>();
	ArrayList<Square> squares = new ArrayList<Square>(64);
	BoardInfo boardInfo;
	Piece whiteKing;
	Piece blackKing;
	long[] zobristHashes;

	int[] knightMoves =
	{ 17, 15, -17, -15, 10, 6, -10, -6 };
	int[] sliderMoves =
	{ 8, 1, -8, -1, 9, 7, -9, -7 };

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
		ArrayList<Move> allMoves = new ArrayList<Move>();
		for (Piece piece : (whiteToMove ? whitePieces : blackPieces))
		{
			// For each piece generate all legal moves
			ArrayList<Move> moves = generateLegalMoves(piece, capturesOnly);
			allMoves.addAll(moves);
		}
		return allMoves;
	}
	
	
	public Move[] generateAllPseudoLegalMoves(boolean whiteToMove, boolean capturesOnly)
	{
		ArrayList<Move> allMoves = new ArrayList<Move>();
		for (Piece piece : (whiteToMove ? whitePieces : blackPieces))
		{
			// For each piece generate all legal moves
			ArrayList<Move> moves = generatePseudoLegalMoves(piece, capturesOnly);
			allMoves.addAll(moves);
		}
		return allMoves.toArray(new Move[0]);
	}
	
	public boolean isMoveLegal(Move move)
	{
		Piece piece = squares.get(move.getStartIndex()).getPiece();
		PinnedPiece pinnedObj = isPiecePinned(piece);
		
		if (pinnedObj != null && isPiecePinnedAbsolutely(piece, pinnedObj.pinner)) return false; // TODO: update isPiecePinnedAbsolutely signature
		if (boardInfo.isDoubleCheck() && (piece.getPieceType() != Piece.KING)) return false;
		if (pinnedObj != null && boardInfo.getCheckPiece() != null && (piece.getPieceType() != Piece.KING)) return false;
		
		if (pinnedObj == null && boardInfo.getCheckPiece() == null && (piece.getPieceType() != Piece.KING))
		{
			if ((piece.getPieceType() == Piece.PAWN) && move.getEnPassantCaptureIndex() != -1)
				return isEnPassantLegal(move);
			return true;
		}
		
		if (piece.getPieceType() == Piece.KING) // if moving a king
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
				captured.setSquare(null);
			}
			
			// runs for every enemy piece
			for (Piece enemyPiece : (piece.isWhite() ? blackPieces : whitePieces))
			{
				if (enemyPiece != captured && canPieceAttackSquare(enemyPiece.getSquare(), targetSquare, enemyPiece.pieceInfo)) // moveLegalityHelper determines if it is legal for a given piece
				{
					legal = false;
					break;
				}
			}
			
			startSquare.addPiece(piece);
			piece.setSquare(startSquare);
			
			if (captured != null)
			{
				captured.setSquare(targetSquare);
				targetSquare.addPiece(captured);
			}
			
			return legal;
		}
		else // not moving a king (and cant be in double check)
		{
			Piece checkPiece = boardInfo.getCheckPiece();
			if (checkPiece != null) // extra testing because in check and not pinned
			{
				ArrayList<Square> blockSquares = getSquaresInBetween(checkPiece, (piece.isWhite() ? whiteKing : blackKing).getSquare());
				blockSquares.add(checkPiece.getSquare());
				int captureIndex = move.getTargetIndex();
				if (move.getEnPassantCaptureIndex() != -1) captureIndex = move.getEnPassantCaptureIndex();
				
				return blockSquares.contains(squares.get(captureIndex));
			}
			else // extra testing because partially pinned (and not in check)
			{
				// if the pawn is pinned vertically, this will be true only for advancing, not capturing left/right
				// if it is pinned diagonally, it will only be true for the capture of the pinning piece so this works
				if (piece.getPieceType() == Piece.PAWN)
				{
					if (move.getStartIndex() % 8 == (pinnedObj.pinner.getSquare().getIndex() % 8))
					{
						return (move.getTargetIndex() % 8) == (pinnedObj.pinner.getSquare().getIndex() % 8);
					}
					else if (move.getTargetIndex() == pinnedObj.pinner.getSquare().getIndex()) return true;
					else if (move.getEnPassantCaptureIndex() != -1)
					{
						Square target = squares.get(move.getTargetIndex());
						if (Math.abs(pinnedObj.pinner.getSquare().getRank() - target.getRank()) == Math.abs(pinnedObj.pinner.getSquare().getFile() - target.getFile())) return true;
					}
				}
				
				// cannot be a pawn, knight (always absolutely pinned), or king so must be a slider
				ArrayList<Square> pinSquares = getSquaresInBetween(piece,pinnedObj.pinner.getSquare());
				pinSquares.add(pinnedObj.pinner.getSquare());
				pinSquares.addAll(getSquaresInBetween(piece, (piece.isWhite() ? whiteKing : blackKing).getSquare()));
				
				return pinSquares.contains(squares.get(move.getTargetIndex()));
			}
		}
	}
	
	public boolean hasLegalMoves(boolean whiteToMove)
	{
		for (Piece piece : (whiteToMove ? whitePieces : blackPieces))
		{
			ArrayList<Move> moves = generatePseudoLegalMoves(piece, false);
			for (Move move : moves)
			{
				if (isMoveLegal(move)) return true;
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
	public ArrayList<Move> generateLegalMoves(Piece piece, boolean capturesOnly)
	{
		ArrayList<Move> moves = generatePseudoLegalMoves(piece, capturesOnly);
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
	public boolean canPieceAttackSquare(Square start, Square target, int pieceInfo)
	{
		// get rank and files for both squares
		int targetIndex = target.getIndex();
		int targetRank = target.getRank();
		int targetFile = target.getFile();

		int pieceIndex = start.getIndex();
		int pieceRank = start.getRank();
		int pieceFile = start.getFile();
		
		int pieceType = pieceInfo & Piece.TYPE_MASK;
		boolean isWhite = (pieceInfo & Piece.COLOR_MASK) == Piece.WHITE_PIECE;
		
		if (pieceType == Piece.QUEEN || pieceType == Piece.ROOK || pieceType == Piece.BISHOP)
		{
			if ((pieceType == Piece.ROOK) && (targetRank != pieceRank && targetFile != pieceFile))
				return false;
			else if ((pieceType == Piece.BISHOP) && (Math.abs(targetRank - pieceRank) != Math.abs(targetFile - pieceFile)))
				return false;
			else if ((pieceType == Piece.QUEEN) && ((targetRank != pieceRank && targetFile != pieceFile) && (Math.abs(targetRank - pieceRank) != Math.abs(targetFile - pieceFile))))
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
		else if (pieceType == Piece.PAWN)
		{
			if (((targetRank + targetFile) % 2) != ((pieceRank + pieceFile) % 2)) return false; // pawns attack the same color square
			
			if (targetIndex == (pieceIndex + (isWhite ? -7 : 7)) || targetIndex == (pieceIndex + (isWhite ? -9 : 9)))
			{
				return true;
			}
			return false;
		}
		else if (pieceType == Piece.KING)
		{
			// if enemy king is not within one rank AND one file, move must be legal
			if (Math.abs(targetRank - pieceRank) > 1 || Math.abs(targetFile - pieceFile) > 1)
				return false;
			else
				return true; // if within that distance, move is not legal
		}
		else if (pieceType == Piece.KNIGHT)
		{
			if (((targetRank + targetFile) % 2) == ((pieceRank + pieceFile) % 2)) return false; // knights cannot attack the same color square
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
		
		for (Piece piece : (king.isWhite() ? blackPieces : whitePieces)) // iterate over all enemy pieces
		{
			// otherwise just check if the piece can attack either square
			if (canPieceAttackSquare(piece.getSquare(), kingOne, piece.pieceInfo) || canPieceAttackSquare(piece.getSquare(), kingTwo, piece.pieceInfo))
			{
				return false;
			}
		}
		return true; // castling is legal if this line is reached
	}
	
	public ArrayList<Square> getSquaresInBetween(Piece piece, Square target)
	{
		ArrayList<Square> squaresList = new ArrayList<Square>();
		if (piece.getPieceType() != Piece.QUEEN && piece.getPieceType() != Piece.ROOK && piece.getPieceType() != Piece.BISHOP) return squaresList;
		
		int targetRank = target.getRank();
		int targetFile = target.getFile();
		int pieceIndex = piece.getSquare().getIndex();
		int pieceRank = piece.getSquare().getRank();
		int pieceFile = piece.getSquare().getFile();
		
		if (piece.getPieceType() == Piece.ROOK && (targetRank != pieceRank && targetFile != pieceFile))
			return squaresList;
		else if (piece.getPieceType() == Piece.BISHOP && (Math.abs(targetRank - pieceRank) != Math.abs(targetFile - pieceFile)))
			return squaresList;
		else if (piece.getPieceType() == Piece.QUEEN && ((targetRank != pieceRank && targetFile != pieceFile) && (Math.abs(targetRank - pieceRank) != Math.abs(targetFile - pieceFile))))
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
		
		Square king = (pawn.isWhite() ? whiteKing : blackKing).getSquare();
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
				if (piece.isWhite() != pawn.isWhite() && (piece.getPieceType() == Piece.ROOK || piece.getPieceType() == Piece.QUEEN)) return false;
				
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
	public ArrayList<Move> generatePseudoLegalMoves(Piece piece, boolean capturesOnly)
	{
		ArrayList<Move> moves = new ArrayList<Move>();
		Square start = piece.getSquare();

		int index = start.getIndex();

		boolean isWhite = piece.isWhite();

		if (piece.getPieceType() == Piece.PAWN)
		{
			int targetRankIndex = index + (isWhite ? -8 : 8);
			if (targetRankIndex > 63 || targetRankIndex < 0)
				return moves; // cannot go off the board though this should never happen
			if (!squares.get(targetRankIndex).hasPiece() && !capturesOnly) // if no piece, pawn can move forward one
			{
				if (targetRankIndex / 8 == 0 || targetRankIndex / 8 == 7)
				{
					// 4 possible moves for each promotion piece
					moves.add(new Move(index, targetRankIndex, Move.PROMOTION_QUEEN_FLAG));
					moves.add(new Move(index, targetRankIndex, Move.PROMOTION_ROOK_FLAG));
					moves.add(new Move(index, targetRankIndex, Move.PROMOTION_BISHOP_FLAG));
					moves.add(new Move(index, targetRankIndex, Move.PROMOTION_KNIGHT_FLAG));
				}
				else
					moves.add(new Move(index, targetRankIndex, Move.NO_FLAG)); // otherwise just one move for pushing pawn
				if (index / 8 == (isWhite ? 6 : 1))
				{
					// potentially a second move for moving pawn two squares if it has not moved yet
					if (!squares.get(targetRankIndex + (isWhite ? -8 : 8)).hasPiece())
						moves.add(new Move(index, targetRankIndex + (isWhite ? -8 : 8), Move.PAWN_DOUBLE_PUSH_FLAG));
				}
			}
			if (((targetRankIndex + 1) / 8 == targetRankIndex / 8 && (targetRankIndex + 1) < 64) && (targetRankIndex + 1 == boardInfo.getEnPassantIndex() || (squares.get(targetRankIndex + 1).hasPiece() && squares.get(targetRankIndex + 1).getPiece().isWhite() != isWhite)))
			{
				// pawn capturing to the right
				if ((targetRankIndex + 1) / 8 == 0 || (targetRankIndex + 1) / 8 == 7)
				{
					// 4 possible moves for each promotion piece
					moves.add(new Move(index, targetRankIndex + 1, Move.PROMOTION_QUEEN_FLAG));
					moves.add(new Move(index, targetRankIndex + 1, Move.PROMOTION_ROOK_FLAG));
					moves.add(new Move(index, targetRankIndex + 1, Move.PROMOTION_BISHOP_FLAG));
					moves.add(new Move(index, targetRankIndex + 1, Move.PROMOTION_KNIGHT_FLAG));
				}
				else if (targetRankIndex + 1 == boardInfo.getEnPassantIndex()) // pawn can capture enemy pawn with en passant
				{
					moves.add(new Move(index, (targetRankIndex + 1), Move.EN_PASSANT_CAPTURE_FLAG));
				}
				else
					moves.add(new Move(index, (targetRankIndex + 1), Move.NO_FLAG)); // otherwise just normal pawn capture
			}
			if (((targetRankIndex - 1) / 8 == targetRankIndex / 8 && (targetRankIndex - 1) >= 0) && (targetRankIndex - 1 == boardInfo.getEnPassantIndex() || (squares.get(targetRankIndex - 1).hasPiece() && squares.get(targetRankIndex - 1).getPiece().isWhite() != isWhite)))
			{
				// pawn capturing to the left
				if ((targetRankIndex - 1) / 8 == 0 || (targetRankIndex - 1) / 8 == 7)
				{
					// 4 possible moves for each promotion piece
					moves.add(new Move(index, targetRankIndex - 1, Move.PROMOTION_QUEEN_FLAG));
					moves.add(new Move(index, targetRankIndex - 1, Move.PROMOTION_ROOK_FLAG));
					moves.add(new Move(index, targetRankIndex - 1, Move.PROMOTION_BISHOP_FLAG));
					moves.add(new Move(index, targetRankIndex - 1, Move.PROMOTION_KNIGHT_FLAG));
				}
				else if (targetRankIndex - 1 == boardInfo.getEnPassantIndex()) // pawn can capture enemy pawn with en passant
				{
					moves.add(new Move(index, (targetRankIndex - 1), Move.EN_PASSANT_CAPTURE_FLAG));
				}
				else
					moves.add(new Move(index, (targetRankIndex - 1), Move.NO_FLAG)); // otherwise just normal pawn capture
			}
		}

		else if (piece.getPieceType() == Piece.KNIGHT)
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
				if (squares.get(targetIndex).hasPiece() || !capturesOnly) moves.add(new Move(index, targetIndex, Move.NO_FLAG)); // add new knight move
			}
		}

		else if (piece.getPieceType() == Piece.QUEEN || piece.getPieceType() == Piece.ROOK || piece.getPieceType() == Piece.BISHOP)
		{
			// sets which indices of sliderMoves[] to check depending on piece;
			int sliderArrayStart = piece.getPieceType() == Piece.BISHOP ? 4 : 0; // start at 4 if bishop, 0 if rook or queen
			int sliderArrayLength = piece.getPieceType() == Piece.QUEEN ? 8 : 4; // iterate over all 8 elements if queen, otherwise only 4 for rook and bishop

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
							{
								if (!capturesOnly)
									moves.add(new Move(index, targetIndex, Move.NO_FLAG)); // if no piece, possible move to the empty square
							}
							else
							{
								if (squares.get(targetIndex).getPiece().isWhite() != isWhite)
									moves.add(new Move(index, targetIndex, Move.NO_FLAG)); // if piece, and is enemy, this is last move
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

		else if (piece.getPieceType() == Piece.KING)
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
							if (squares.get(targetIndex).hasPiece())
							{
								if (squares.get(targetIndex).getPiece().isWhite() != isWhite)
									moves.add(new Move(index, targetIndex, Move.NO_FLAG));
							}
							else if (!capturesOnly)
								moves.add(new Move(index, targetIndex, Move.NO_FLAG));
						}
					}
				}
			}
			
			if (capturesOnly) return moves;

			// add kingside castle move if castling rights and not blocked
			if (boardInfo.getCastlingRights()[(isWhite ? 0 : 2)] && !(squares.get(index + 1).hasPiece() || squares.get(index + 2).hasPiece()))
			{
				moves.add(new Move(index, (index + 2), Move.CASTLING_FLAG));
			}

			// add queenside castle move if castling rights and not blocked
			if (boardInfo.getCastlingRights()[(isWhite ? 1 : 3)] && !(squares.get(index - 1).hasPiece() || squares.get(index - 2).hasPiece() || squares.get(index - 3).hasPiece()))
			{
				moves.add(new Move(index, (index - 2), Move.CASTLING_FLAG));
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
		boardInfo.getMoveList().add(move);
		

		// update boardInfo information
		boardInfo.setWhiteToMove(!boardInfo.isWhiteToMove());
		boardInfo.setCheckPiece(null);
		boardInfo.setDoubleCheck(false);
		boardInfo.setHalfMoves(boardInfo.getHalfMoves() + 1);
		if (piece.getPieceType() == Piece.PAWN)
			boardInfo.setHalfMoves(0); // reset half moves for 50 move rule
		if (!piece.isWhite())
			boardInfo.setMoveNum(boardInfo.getMoveNum() + 1);
		
		// update the material
		boolean endgame = boardInfo.getWhiteMaterial() + boardInfo.getBlackMaterial() < 4000;
		int differential = piece.getPieceSquareValue(move.getTargetIndex(), endgame) - piece.getPieceSquareValue(move.getStartIndex(), endgame);
		
		if (piece.isWhite())
			boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() + differential);
		else
			boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() + differential);

		if (capture.hasPiece()) // if move is a capture
		{
			boardInfo.setHalfMoves(0); // reset half moves for 50 move rule
			capturedPiece = capture.getPiece();
			
			if (capturedPiece.getPieceType() == Piece.KING)
			{
				System.out.println(boardInfo.getMoveList());
				printBoard();
			}
			
			// update the material
			if (capturedPiece.isWhite())
			{
				boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() - capturedPiece.getValue());
				boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() - (capturedPiece.getPieceSquareValue(capture.getIndex(), endgame)));
			}
			else
			{
				boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() - capturedPiece.getValue());
				boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() - (capturedPiece.getPieceSquareValue(capture.getIndex(), endgame)));
			}
			
			if (capturedPiece.getPieceType() == Piece.ROOK) // update castling rights if rook was captured
			{
				boolean[] castlingRights = boardInfo.getCastlingRights();
				if (castlingRights[0] && target.getIndex() == 63)
					castlingRights[0] = false;
				else if (castlingRights[1] && target.getIndex() == 56)
					castlingRights[1] = false;
				else if (castlingRights[2] && target.getIndex() == 7)
					castlingRights[2] = false;
				else if (castlingRights[3] && target.getIndex() == 0)
					castlingRights[3] = false;
				boardInfo.setCastlingRights(castlingRights);
			}
			
			// remove captured piece from the board
			capturedPiece.getSquare().removePiece();
			capturedPiece.setSquare(null);
			
			if (capturedPiece.isWhite())
			{
				whitePieces.remove(capturedPiece);
				if (capturedPiece.getPieceType() != Piece.PAWN && capturedPiece.getPieceType() != Piece.KNIGHT)
					whiteSliders.remove(capturedPiece);
			}
			else
			{
				blackPieces.remove(capturedPiece);
				if (capturedPiece.getPieceType() != Piece.PAWN && capturedPiece.getPieceType() != Piece.KNIGHT)
					blackSliders.remove(capturedPiece);
			}
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

			// update castling rights
			boolean[] castlingRights = boardInfo.getCastlingRights();
			if (piece.isWhite())
				castlingRights[0] = castlingRights[1] = false;
			else
				castlingRights[2] = castlingRights[3] = false;
			boardInfo.setCastlingRights(castlingRights);
			
			int rookDifferential = rook.getPieceSquareValue(rookTarget.getIndex(), endgame) - rook.getPieceSquareValue(rookStart.getIndex(), endgame);
			if (piece.isWhite())
				boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() + rookDifferential);
			else
				boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() + rookDifferential);
		}
		else if (move.getPromotedPiece() != 0) // if move is promotion
		{
			// update material
			int materialDifference = piece.getValue();
			int promotedDifferential = piece.getPieceSquareValue(target.getIndex(), endgame);
			
			piece.setPieceType(move.getPromotedPiece());
			if (piece.getPieceType() != Piece.KNIGHT)
			{
				if (piece.isWhite()) whiteSliders.add(piece);
				else blackSliders.add(piece);
			}
			
			// update material
			materialDifference = piece.getValue() - materialDifference;
			if (piece.isWhite())
				boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() + materialDifference);
			else
				boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() + materialDifference);
			
			promotedDifferential = piece.getPieceSquareValue(target.getIndex(), endgame) - promotedDifferential;
			if (piece.isWhite())
				boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() + promotedDifferential);
			else
				boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() + promotedDifferential);
		}
		else // if it is a normal move
		{
			if (piece.getPieceType() == Piece.ROOK) // update castling rights if rook move
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

			else if (piece.getPieceType() == Piece.KING) // update castling rights if king move
			{
				boolean[] castlingRights = boardInfo.getCastlingRights();
				if (piece.isWhite())
					castlingRights[0] = castlingRights[1] = false;
				else
					castlingRights[2] = castlingRights[3] = false;
				boardInfo.setCastlingRights(castlingRights);
			}
		}
		
		// move the piece to the new square
		piece.setSquare(target);
		start.removePiece();
		target.addPiece(piece);

		// set or reset en passant index
		boardInfo.setEnPassantIndex(move.getEnPassantNewIndex());
		
		if (piece.getPieceType() == Piece.KNIGHT || piece.getPieceType() == Piece.PAWN)
		{
			if (canPieceAttackSquare(piece.getSquare(), (piece.isWhite() ? blackKing : whiteKing).getSquare(), piece.pieceInfo))
			{
				boardInfo.setCheckPiece(piece); // set this piece as the checkPiece
			}
		}
		
		updateAllPins(move, capturedPiece);

		return capturedPiece; // return the captured piece
	}
	
	public void updateAllPins(Move move, Piece captured)
	{
		Square start = squares.get(move.getStartIndex());
		Square target = squares.get(move.getTargetIndex());
		Piece piece = target.getPiece();
		
		pinnedPieces.removeIf(pinnedPiece -> (pinnedPiece.pinner == captured || pinnedPiece.pinner == piece || pinnedPiece.pinned == captured || pinnedPiece.pinned == piece));
		
		if (piece.getPieceType() == Piece.KING)
		{
			checkAllPins();
			return;
		}
		
		checkCreatedPin(start, blackKing.getSquare());
		checkCreatedPin(start, whiteKing.getSquare());
		
		if (move.getEnPassantCaptureIndex() != -1)
		{
			checkCreatedPin(squares.get(move.getEnPassantCaptureIndex()), blackKing.getSquare());
			checkCreatedPin(squares.get(move.getEnPassantCaptureIndex()), whiteKing.getSquare());
		}
		
		checkCreatedPin(target, (piece.isWhite() ? whiteKing : blackKing).getSquare());
		
		Piece pinned = getPiecePinnedBySlider(piece, (piece.isWhite() ? blackKing : whiteKing).getSquare());
		createPin(pinned, piece);
		
		pinnedPieces.removeIf(pinnedPiece -> (getPiecePinnedBySlider(pinnedPiece.pinner, (pinnedPiece.pinner.isWhite() ? blackKing : whiteKing).getSquare()) == null));
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
					createPin(pinned, piece);
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
		pinnedPieces.clear();
		// check all slider pieces
		whiteSliders.forEach(slider -> createPin(getPiecePinnedBySlider(slider, blackKing.getSquare()), slider));
		blackSliders.forEach(slider -> createPin(getPiecePinnedBySlider(slider, whiteKing.getSquare()), slider));
	}
	
	public void createPin(Piece pinned, Piece slider)
	{
		if (pinned != null)
		{
			if (pinned.getPieceType() == Piece.KING)
			{
				if (boardInfo.getCheckPiece() != null && boardInfo.getCheckPiece() != slider) boardInfo.setDoubleCheck(true);
				else boardInfo.setCheckPiece(slider);
			}
			else
			{
				PinnedPiece pinnedPiece = new PinnedPiece(pinned, slider);
				pinnedPieces.add(pinnedPiece);
			}
		}
	}
	
	public Piece getPiecePinnedBySlider(Piece slider, Square enemyKing)
	{
		if (slider.getPieceType() != Piece.QUEEN && slider.getPieceType() != Piece.ROOK && slider.getPieceType() != Piece.BISHOP)
			return null;
		
		int sliderIndex = slider.getSquare().getIndex();
		int sliderRank = slider.getSquare().getRank();
		int sliderFile = slider.getSquare().getFile();
		int kingRank = enemyKing.getRank();
		int kingFile = enemyKing.getFile();
		
		if (slider.getPieceType() == Piece.ROOK && (kingRank != sliderRank && kingFile != sliderFile))
			return null;
		else if (slider.getPieceType() == Piece.BISHOP && (Math.abs(kingRank - sliderRank) != Math.abs(kingFile - sliderFile)))
			return null;
		else if (slider.getPieceType() == Piece.QUEEN && ((kingRank != sliderRank && kingFile != sliderFile) && (Math.abs(kingRank - sliderRank) != Math.abs(kingFile - sliderFile))))
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
		else if(pinnedPiece.isWhite() == slider.isWhite()) return null; // TODO: at some point track discovered check pieces as well but for now cannot pin your own piece
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
		boardInfo.getPositionList().remove(boardInfo.getPositionList().size()-1); // remove the most recent position
		boardInfo.getMoveList().remove(boardInfo.getMoveList().size()-1); // remove the most recent move
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
			if (piece.getPieceType() != Piece.KNIGHT)
			{
				if (piece.isWhite()) whiteSliders.remove(piece);
				else blackSliders.remove(piece);
			}
			piece.setPieceType(Piece.PAWN);
		}
		else if (move.getEnPassantCaptureIndex() != -1) // if en passant move
		{
			captureSquare = squares.get(move.getEnPassantCaptureIndex()); // set captureSquare to the right place
		}

		if (captured != null) // if there was a captured piece
		{
			// put captured piece back on the board
			captured.setSquare(captureSquare);
			captureSquare.addPiece(captured);
			
			if (captured.isWhite())
			{
				whitePieces.add(captured);
				if (captured.getPieceType() != Piece.KNIGHT && captured.getPieceType() != Piece.PAWN) whiteSliders.add(captured);
			}
			else
			{
				blackPieces.add(captured);
				if (captured.getPieceType() != Piece.KNIGHT && captured.getPieceType() != Piece.PAWN) blackSliders.add(captured);
			}
		}
	}
	
	public boolean isPiecePinnedAbsolutely(Piece pinned, Piece pinner)
	{
		if (pinned.getPieceType() == Piece.QUEEN) return false; // quick exit to avoid calling canPieceAttackSquare
		if (pinned.getPieceType() == Piece.KNIGHT) return true; // quick exit to avoid calling canPieceAttackSquare
		if (canPieceAttackSquare(pinned.getSquare(), pinner.getSquare(), pinned.pieceInfo)) return false; // if the pinned piece can capture the pinner, at least one legal move could exists (no piece can be double pinned to the king)
		if (pinned.getPieceType() == Piece.PAWN)
		{
			if (pinned.getSquare().getRank() == pinner.getSquare().getRank()) return true;
		}
		
		if (pinned.getPieceType() == Piece.PAWN) return false;
		
		return true;
	}

	public long createZobristHash()
	{
		long zobristHash = 0;
		
		for (Piece piece : whitePieces)
			zobristHash ^= zobristHashes[(piece.getSquare().getIndex() * 12 + piece.getZobristOffset())];
		
		for (Piece piece : blackPieces)
			zobristHash ^= zobristHashes[(piece.getSquare().getIndex() * 12 + piece.getZobristOffset())];
		

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
		
		if (captured != null && captured.getPieceType() == Piece.ROOK)
		{
			// remove castling rights because rook captured
			if (castlingRights[0] && move.getTargetIndex() == 63)
				zobristHash ^= zobristHashes[769];
			else if (castlingRights[1] && move.getTargetIndex() == 56)
				zobristHash ^= zobristHashes[770];
			else if (castlingRights[2] && move.getTargetIndex() == 7)
				zobristHash ^= zobristHashes[771];
			else if (castlingRights[3] && move.getTargetIndex() == 0)
				zobristHash ^= zobristHashes[772];
		}
		
		if (piece.getPieceType() == Piece.ROOK)
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
		else if (piece.getPieceType() == Piece.KING)
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
	 * Load in a FEN string into the virtual board representation
	 * 
	 * @param fen The given FEN string to load in
	 */
	public void loadFEN(String fen)
	{
		boardInfo.setBoardFEN(fen); // set the fen
		whitePieces.clear();
		blackPieces.clear();
		whiteSliders.clear();
		blackSliders.clear();

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
					
					Piece piece = null;
					
					int color = Piece.BLACK_PIECE;
					if (((int) c) > 65 && ((int) c) < 90) color = Piece.WHITE_PIECE;
										
					if (c == 'k' || c == 'K')
						piece = new Piece(Piece.KING, color, square);
					else if (c == 'q' || c == 'Q')
						piece = new Piece(Piece.QUEEN, color, square);
					else if (c == 'r' || c == 'R')
						piece = new Piece(Piece.ROOK, color, square);
					else if (c == 'b' || c == 'B')
						piece = new Piece(Piece.BISHOP, color, square);
					else if (c == 'n' || c == 'N')
						piece = new Piece(Piece.KNIGHT, color, square);
					else if (c == 'p' || c == 'P')
						piece = new Piece(Piece.PAWN, color, square);
					
					piece.setSquare(square);
					square.addPiece(piece);
					if (color == Piece.WHITE_PIECE) whitePieces.add(piece);
					else blackPieces.add(piece);
					
					if (piece.pieceInfo == Piece.WHITE_KING)
						whiteKing = piece;
					else if (piece.pieceInfo == Piece.BLACK_KING)
						blackKing = piece;
					else if (piece.getPieceType() != Piece.KNIGHT && piece.getPieceType() != Piece.PAWN)
					{
						if (piece.isWhite()) whiteSliders.add(piece);
						else blackSliders.add(piece);
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
		for (Piece piece : (boardInfo.isWhiteToMove() ? blackPieces : whitePieces))
		{
			if (piece.getPieceType() == Piece.KNIGHT || piece.getPieceType() == Piece.PAWN)
			{
				if (canPieceAttackSquare(piece.getSquare(), (boardInfo.isWhiteToMove() ? whiteKing : blackKing).getSquare(), piece.pieceInfo))
				{
					boardInfo.setCheckPiece(piece); // set the check piece
					break;
				}
			}
		}
		
		checkAllPins();

		boardInfo.setWhiteMaterial(0);
		boardInfo.setBlackMaterial(0);
		
		for (Piece piece : whitePieces)
			boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() + piece.getValue());
		
		for (Piece piece : blackPieces)
			boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() + piece.getValue());
		
		boolean endgame = boardInfo.getWhiteMaterial() + boardInfo.getBlackMaterial() < 4000;
		boardInfo.setWhiteSquareBonus(0);
		boardInfo.setBlackSquareBonus(0);
		
		for (Piece piece : whitePieces)
			boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() + (piece.getPieceSquareValue(piece.getSquare().getIndex(), endgame)));
		
		for (Piece piece : blackPieces)
			boardInfo.setWhiteSquareBonus(boardInfo.getBlackSquareBonus() + (piece.getPieceSquareValue(piece.getSquare().getIndex(), endgame)));
		
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
			if (squares.get(i).hasPiece()) // if an uncaptured piece on this square
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