package me.Shadow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class Board
{
	Bitboards bitBoards;
	ArrayList<Square> squares = new ArrayList<Square>(64);
	BoardInfo boardInfo;
	long[] zobristHashes;

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

	public int movePiece(Move move)
	{
		Square start = squares.get(move.getStartIndex());
		Square target = squares.get(move.getTargetIndex());
		Square capture = target;
		if (move.getEnPassantCaptureIndex() != -1)
			capture = squares.get(move.getEnPassantCaptureIndex()); // if en passant, change capture square
		int piece = start.getPiece();
		int capturedPiece = capture.getPiece();

		long newZobristHash = updateZobristHash(move);
		boardInfo.setZobristHash(newZobristHash);	// set the hash
		boardInfo.getPositionList().add(newZobristHash);
		boardInfo.getMoveList().add(move);

		// update boardInfo information
		boardInfo.setWhiteToMove(!boardInfo.isWhiteToMove());
		boardInfo.setHalfMoves(boardInfo.getHalfMoves() + 1);
		if (Piece.getPieceType(piece) == Piece.PAWN)
			boardInfo.setHalfMoves(0); // reset half moves for 50 move rule
		if (Piece.isColor(piece, Piece.BLACK_PIECE))
			boardInfo.setMoveNum(boardInfo.getMoveNum() + 1);
		
		// update the material
		boolean endgame = boardInfo.getWhiteMaterial() + boardInfo.getBlackMaterial() < 4000;
		
		int differential = Piece.getPieceSquareValue(piece, move.getTargetIndex(), endgame) - Piece.getPieceSquareValue(piece, move.getStartIndex(), endgame);
		
		if (Piece.isColor(piece, Piece.WHITE_PIECE))
			boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() + differential);
		else
			boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() + differential);

		if (capturedPiece != Piece.NONE) // if move is a capture
		{
			boardInfo.setHalfMoves(0); // reset half moves for 50 move rule
			
			// update the material
			if (Piece.isColor(capturedPiece, Piece.WHITE_PIECE))
			{
				boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() - Piece.getValue(capturedPiece));
				boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() - (Piece.getPieceSquareValue(capturedPiece, capture.getIndex(), endgame)));
			}
			else
			{
				boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() - Piece.getValue(capturedPiece));
				boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() - (Piece.getPieceSquareValue(capturedPiece, capture.getIndex(), endgame)));
			}
			
			if (Piece.getPieceType(capturedPiece) == Piece.ROOK) // update castling rights if rook was captured
			{
				boolean[] castlingRights = boardInfo.getCastlingRights();
				if (target.getIndex() == 7) castlingRights[0] = false;
				else if (target.getIndex() == 0) castlingRights[1] = false;
				else if (target.getIndex() == 63) castlingRights[2] = false;
				else if (target.getIndex() == 56) castlingRights[3] = false;
				boardInfo.setCastlingRights(castlingRights);
			}
			
			// remove captured piece from the board
			capture.setPiece(Piece.NONE);
			
			bitBoards.toggleSquare(capturedPiece, capture.getIndex());
		}

		if (move.isCastleMove()) // if move is castling
		{
			Square rookStart = squares.get(move.getRookStartIndex());
			Square rookTarget = squares.get(move.getRookTargetIndex());
			
			// move the rook
			int rook = rookStart.getPiece();
			rookStart.setPiece(Piece.NONE);
			rookTarget.setPiece(rook);

			// update castling rights
			boolean[] castlingRights = boardInfo.getCastlingRights();
			if (Piece.isColor(piece, Piece.WHITE_PIECE))
				castlingRights[0] = castlingRights[1] = false;
			else
				castlingRights[2] = castlingRights[3] = false;
			boardInfo.setCastlingRights(castlingRights);
			
			int rookDifferential = Piece.getPieceSquareValue(rook, move.getRookTargetIndex(), endgame) - Piece.getPieceSquareValue(rook, move.getRookStartIndex(), endgame);
			if (Piece.isColor(piece, Piece.WHITE_PIECE))
				boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() + rookDifferential);
			else
				boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() + rookDifferential);
			
			bitBoards.toggleSquare(rook, rookStart.getIndex());
			bitBoards.toggleSquare(rook, rookTarget.getIndex());
		}
		else if (move.getPromotedPiece() != 0) // if move is promotion
		{
			// update material
			int materialDifference = Piece.getValue(piece);
			int promotedDifferential = Piece.getPieceSquareValue(piece, target.getIndex(), endgame);
			
			bitBoards.toggleSquare(piece, start.getIndex());
			piece = (move.getPromotedPiece() | Piece.getColor(piece));
			bitBoards.toggleSquare(piece, start.getIndex());
			
			// update material
			materialDifference = Piece.getValue(piece) - materialDifference;
			if (Piece.isColor(piece, Piece.WHITE_PIECE))
				boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() + materialDifference);
			else
				boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() + materialDifference);
			
			promotedDifferential = Piece.getPieceSquareValue(piece, target.getIndex(), endgame) - promotedDifferential;
			if (Piece.isColor(piece, Piece.WHITE_PIECE))
				boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() + promotedDifferential);
			else
				boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() + promotedDifferential);
		}
		else // if it is a normal move
		{
			if (Piece.getPieceType(piece) == Piece.ROOK) // update castling rights if rook move
			{
				boolean[] castlingRights = boardInfo.getCastlingRights();
				if (move.getStartIndex() == 7) castlingRights[0] = false;
				else if (move.getStartIndex() == 0) castlingRights[1] = false;
				else if (move.getStartIndex() == 63) castlingRights[2] = false;
				else if (move.getStartIndex() == 56) castlingRights[3] = false;
				boardInfo.setCastlingRights(castlingRights);
			}

			else if (Piece.getPieceType(piece) == Piece.KING) // update castling rights if king move
			{
				boolean[] castlingRights = boardInfo.getCastlingRights();
				if (Piece.isColor(piece, Piece.WHITE_PIECE))
					castlingRights[0] = castlingRights[1] = false;
				else
					castlingRights[2] = castlingRights[3] = false;
				boardInfo.setCastlingRights(castlingRights);
			}
		}
		
		// move the piece to the new square
		start.setPiece(Piece.NONE);
		target.setPiece(piece);
		
		bitBoards.toggleSquare(piece, start.getIndex());
		bitBoards.toggleSquare(piece, target.getIndex());

		// set or reset en passant index
		boardInfo.setEnPassantIndex(move.getEnPassantNewIndex());
		
		return capturedPiece; // return the captured piece
	}

	public void moveBack(Move move, int captured, BoardInfo boardInfoOld)
	{
		boardInfo = boardInfoOld; // replace current BoardInfo object with the old BoardInfo object
		boardInfo.getPositionList().remove(boardInfo.getPositionList().size()-1); // remove the most recent position
		boardInfo.getMoveList().remove(boardInfo.getMoveList().size()-1); // remove the most recent move

		Square start = squares.get(move.getStartIndex());
		Square target = squares.get(move.getTargetIndex());
		
		// move the piece back to its original square
		int piece = target.setPiece(Piece.NONE);
		start.setPiece(piece);
		Square captureSquare = target;
		
		bitBoards.toggleSquare(piece, start.getIndex());
		bitBoards.toggleSquare(piece, target.getIndex());

		if (move.isCastleMove()) // if it was a castle move
		{
			// need to move the rook back too
			Square rookStart = squares.get(move.getRookStartIndex());
			Square rookTarget = squares.get(move.getRookTargetIndex());
			int rook = rookTarget.setPiece(Piece.NONE);
			rookStart.setPiece(rook);
			
			bitBoards.toggleSquare(rook, rookStart.getIndex());
			bitBoards.toggleSquare(rook, rookTarget.getIndex());
		}
		else if (move.getPromotedPiece() != 0) // if it was a promotion
		{
			bitBoards.toggleSquare(piece, start.getIndex());
			piece = (Piece.PAWN | Piece.getColor(piece));
			start.setPiece(piece);
			bitBoards.toggleSquare(piece, start.getIndex());
		}

		if (captured != Piece.NONE) // if there was a captured piece
		{
			if (move.getEnPassantCaptureIndex() != -1) // if en passant move
			{
				captureSquare = squares.get(move.getEnPassantCaptureIndex()); // set captureSquare to the right place
			}
			
			// put captured piece back on the board
			captureSquare.setPiece(captured);
			
			bitBoards.toggleSquare(captured, captureSquare.getIndex());
		}
	}

	public long createZobristHash()
	{
		long zobristHash = 0;
		
		for (Square square : squares)
		{
			int piece = square.getPiece();
			if (piece != Piece.NONE)
			{
				zobristHash ^= zobristHashes[(square.getIndex() * 12 + Piece.getZobristOffset(piece))];
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
		
		int piece = squares.get(move.getStartIndex()).getPiece();
		int captured = squares.get(move.getTargetIndex()).getPiece();
		int captureIndex = move.getTargetIndex();
		if (move.getEnPassantCaptureIndex() != -1)
		{
			captured = squares.get(move.getEnPassantCaptureIndex()).getPiece();
			captureIndex = move.getEnPassantCaptureIndex();
		}
		
		zobristHash ^= zobristHashes[(move.getStartIndex() * 12 + Piece.getZobristOffset(piece))]; // remove piece from start square
		zobristHash ^= zobristHashes[(move.getTargetIndex() * 12 + Piece.getZobristOffset(piece))]; // add piece to target square
		if (captured != Piece.NONE) zobristHash ^= zobristHashes[(captureIndex * 12 + Piece.getZobristOffset(captured))]; // remove captured piece if any
		
		if (boardInfo.getEnPassantIndex() != -1) zobristHash ^= zobristHashes[773 + (boardInfo.getEnPassantIndex() % 8)]; // remove old enpassant index
		if (move.getEnPassantNewIndex() != -1) zobristHash ^= zobristHashes[773 + (move.getEnPassantNewIndex() % 8)]; // add new enpassant index
		
		if (move.getPromotedPiece() != 0)
		{
			int zobristOffset = move.getPromotedPiece()*2;
			if (Piece.isColor(piece, Piece.BLACK_PIECE)) zobristOffset++;
			
			zobristHash ^= zobristHashes[(move.getTargetIndex() * 12 + Piece.getZobristOffset(piece))]; // remove old piece from target square
			zobristHash ^= zobristHashes[(move.getTargetIndex() * 12 + zobristOffset)]; // add promoted piece to target square
		}
		
		boolean [] castlingRights = boardInfo.getCastlingRights();
		
		if (captured != Piece.NONE && Piece.getPieceType(captured) == Piece.ROOK)
		{
			// remove castling rights because rook captured
			if (castlingRights[0] && move.getTargetIndex() == 7)
				zobristHash ^= zobristHashes[769];
			else if (castlingRights[1] && move.getTargetIndex() == 0)
				zobristHash ^= zobristHashes[770];
			else if (castlingRights[2] && move.getTargetIndex() == 63)
				zobristHash ^= zobristHashes[771];
			else if (castlingRights[3] && move.getTargetIndex() == 56)
				zobristHash ^= zobristHashes[772];
		}
		
		if (Piece.getPieceType(piece) == Piece.ROOK)
		{
			// remove castling rights because rook move
			if (castlingRights[0] && move.getStartIndex() == 7)
				zobristHash ^= zobristHashes[769];
			else if (castlingRights[1] && move.getStartIndex() == 0)
				zobristHash ^= zobristHashes[770];
			else if (castlingRights[2] && move.getStartIndex() == 63)
				zobristHash ^= zobristHashes[771];
			else if (castlingRights[3] && move.getStartIndex() == 56)
				zobristHash ^= zobristHashes[772];
		}
		else if (Piece.getPieceType(piece) == Piece.KING)
		{
			// remove castling rights (and removes castling rights if this is a castle move as well)
			if (Piece.isColor(piece, Piece.WHITE_PIECE))
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
			int rook = squares.get(move.getRookStartIndex()).getPiece();
			zobristHash ^= zobristHashes[(move.getRookStartIndex() * 12 + Piece.getZobristOffset(rook))]; // remove rook from start square
			zobristHash ^= zobristHashes[(move.getRookTargetIndex() * 12 + Piece.getZobristOffset(rook))]; // add rook to target square
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

		// split the fen into two strings, one for pieces information and the other for extra information
		String fenPieces = fen.substring(0, fen.indexOf(" "));
		String fenOther = fen.substring(fen.indexOf(" "));
		fenPieces.trim();
		fenOther.trim();
		int file = 1;
		int rank = 1;
		ArrayList<Square> newSquares = new ArrayList<Square>(64);
		String[] rows = fenPieces.split("/"); // split the string into each rank
		
		for (int i = 7; i >= 0; i--) // loop will run 8 times for each rank
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
					int piece = Piece.NONE;
					
					int color = Piece.BLACK_PIECE;
					if (((int) c) > 65 && ((int) c) < 90) color = Piece.WHITE_PIECE;
										
					if (c == 'k' || c == 'K')
						piece = Piece.KING | color;
					else if (c == 'q' || c == 'Q')
						piece = Piece.QUEEN | color;
					else if (c == 'r' || c == 'R')
						piece = Piece.ROOK | color;
					else if (c == 'b' || c == 'B')
						piece = Piece.BISHOP | color;
					else if (c == 'n' || c == 'N')
						piece = Piece.KNIGHT | color;
					else if (c == 'p' || c == 'P')
						piece = Piece.PAWN | color;
										
					square.setPiece(piece);
					
					newSquares.add(square); // add the square into the board
					file++; // increment file to move onto next square
				}
			}
			rank++; // decrement rank
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
					boardInfo.setEnPassantIndex(Square.getIndexFromRankFile(enPassantRank, enPassantFile)); // set the en passant index
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

		boardInfo.setWhiteMaterial(0);
		boardInfo.setBlackMaterial(0);
		
		for (Square square : squares)
		{
			int piece = square.getPiece();
			if (piece != Piece.NONE)
			{
				if (Piece.isColor(piece, Piece.WHITE_PIECE))
				{
					boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() + Piece.getValue(piece));
				}
				else
				{
					boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() + Piece.getValue(piece));
				}
			}
		}
		
		boolean endgame = boardInfo.getWhiteMaterial() + boardInfo.getBlackMaterial() < 4000;
		boardInfo.setWhiteSquareBonus(0);
		boardInfo.setBlackSquareBonus(0);
		
		for (Square square : squares)
		{
			int piece = square.getPiece();
			if (piece != Piece.NONE)
			{
				if (Piece.isColor(piece, Piece.WHITE_PIECE))
				{
					boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() + Piece.getPieceSquareValue(piece, square.getIndex(), endgame));
				}
				else
				{
					boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() + Piece.getPieceSquareValue(piece, square.getIndex(), endgame));
				}
			}
		}
		
		boardInfo.setZobristHash(createZobristHash());
		boardInfo.getPositionList().add(boardInfo.getZobristHash());
		
		bitBoards = new Bitboards(this);
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
		
		String row = "";
		ArrayList<String> rows = new ArrayList<String>();
		for (int i = 0; i < 64; i++) // iterate over all squares
		{
			if (squares.get(i).getPiece() != Piece.NONE) // if an uncaptured piece on this square
			{
				if (count != 0) // if count number of empty squares preceded this piece
				{
					row += "" + count; // add the number count to the string
					count = 0; // reset count
				}
				row += "" + Piece.getPieceSymbol(squares.get(i).getPiece()); // add the piece symbol of this piece
			}
			else
				count++; // otherwise increase count for another empty square

			if ((i + 1) % 8 == 0) // if end of a row reached
			{
				if (count != 0) // if count is not 0
				{
					row += "" + count; // add count, the number of empty squares
					count = 0;
				}
				
				if (i >= 8) row += "/"; // add a slash to signify next row
				
				rows.add(row);
				row = "";
			}
		}
		
		String newFEN = "";
		Collections.reverse(rows);
		for (String string : rows)
			newFEN += string;
		
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
			newFEN += Square.getSquareName(index);
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
		for (int rank = 8; rank > 0; rank--)
		{
			System.out.print("\n+---+---+---+---+---+---+---+---+\n| ");
			for (int file = 1; file <= 8; file++)
			{
				Square square = squares.get(Square.getIndexFromRankFile(rank, file));
				if (square.getPiece() != Piece.NONE) // if piece exists
				{
					System.out.print(Piece.getPieceSymbol(square.getPiece()) + " | "); // print the piece symbol
				}
				else
					System.out.print("  | "); // or just print an empty space
			}
		}
		System.out.println("\n+---+---+---+---+---+---+---+---+\n");
	}
}