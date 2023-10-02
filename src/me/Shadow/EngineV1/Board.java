package me.Shadow.EngineV1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class Board
{
	Bitboards bitBoards;
	public int[] squares = new int[64];
	public BoardInfo boardInfo;
	
	final static long [] zobristHashes = new Random(8108415243282079581L).longs(781).toArray();
	public final static String defaultFEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

	public Board()
	{
		this(defaultFEN);
	}

	/**
	 * Constructor for Board with a specific FEN string
	 * 
	 * @param fen The given FEN string to load in
	 */
	public Board(String fen)
	{
		boardInfo = new BoardInfo();
		loadFEN(fen);
	}
	
	public Board(Board board)
	{
		squares = Arrays.copyOf(board.squares, 64);
		bitBoards = new Bitboards(this);
		boardInfo = new BoardInfo(board.boardInfo);		
	}

	public int movePiece(short move)
	{
		int start = MoveHelper.getStartIndex(move);
		int target = MoveHelper.getTargetIndex(move);
		
		int captureIndex = target;
		if (MoveHelper.getEnPassantCaptureIndex(move) != -1)
			captureIndex = MoveHelper.getEnPassantCaptureIndex(move); // if en passant, change capture square
		
		int piece = squares[start];
		int capturedPiece = squares[captureIndex];

		long zobristHash = boardInfo.getZobristHash();

		// update boardInfo information
		boardInfo.setWhiteToMove(!boardInfo.isWhiteToMove());
		zobristHash ^= zobristHashes[768]; // flip side to move
		
		boardInfo.incrementHalfMoves();
		if (PieceHelper.getPieceType(piece) == PieceHelper.PAWN)
			boardInfo.setHalfMoves(0); // reset half moves for 50 move rule
		if (PieceHelper.isColor(piece, PieceHelper.BLACK_PIECE))
			boardInfo.incrementMoveNum();
		
		if (boardInfo.getEnPassantIndex() != -1) zobristHash ^= zobristHashes[773 + (boardInfo.getEnPassantIndex() % 8)]; // remove old enpassant index
		if (MoveHelper.getEnPassantNewIndex(move) != -1) zobristHash ^= zobristHashes[773 + (MoveHelper.getEnPassantNewIndex(move) % 8)]; // add new enpassant index
		
		// set or reset en passant index
		boardInfo.setEnPassantIndex(MoveHelper.getEnPassantNewIndex(move));
		
		// update the material
		boolean endgame = boardInfo.getWhiteMaterial() + boardInfo.getBlackMaterial() < 4000;
		
		int differential = PieceHelper.getPieceSquareValue(piece, target, endgame) - PieceHelper.getPieceSquareValue(piece, start, endgame);
		
		if (PieceHelper.isColor(piece, PieceHelper.WHITE_PIECE))
			boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() + differential);
		else
			boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() + differential);

		if (capturedPiece != PieceHelper.NONE) // if move is a capture
		{
			boardInfo.setHalfMoves(0); // reset half moves for 50 move rule
			
			// update the material
			if (PieceHelper.isColor(capturedPiece, PieceHelper.WHITE_PIECE))
			{
				boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() - PieceHelper.getValue(capturedPiece));
				boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() - (PieceHelper.getPieceSquareValue(capturedPiece, captureIndex, endgame)));
			}
			else
			{
				boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() - PieceHelper.getValue(capturedPiece));
				boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() - (PieceHelper.getPieceSquareValue(capturedPiece, captureIndex, endgame)));
			}
			
			// remove captured piece from the board
			squares[captureIndex] = PieceHelper.NONE;
			bitBoards.toggleSquare(capturedPiece, captureIndex);
			
			zobristHash ^= zobristHashes[(captureIndex * 12 + PieceHelper.getZobristOffset(capturedPiece))]; // remove captured piece
		}

		if (MoveHelper.isCastleMove(move)) // if move is castling
		{
			int rookStart = MoveHelper.getRookStartIndex(move);
			int rookTarget = MoveHelper.getRookTargetIndex(move);
			
			// move the rook
			int rook = squares[rookStart];
			squares[rookStart] = PieceHelper.NONE;
			squares[rookTarget] = rook;
			
			zobristHash ^= zobristHashes[(rookStart * 12 + PieceHelper.getZobristOffset(rook))]; // remove rook from start square
			zobristHash ^= zobristHashes[(rookTarget * 12 + PieceHelper.getZobristOffset(rook))]; // add rook to target square

			// castling rights get updated below
			
			int rookDifferential = PieceHelper.getPieceSquareValue(rook, rookTarget, endgame) - PieceHelper.getPieceSquareValue(rook, rookStart, endgame);
			if (PieceHelper.isColor(piece, PieceHelper.WHITE_PIECE))
				boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() + rookDifferential);
			else
				boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() + rookDifferential);
			
			bitBoards.toggleSquare(rook, rookStart);
			bitBoards.toggleSquare(rook, rookTarget);
		}
		else if (MoveHelper.getPromotedPiece(move) != 0) // if move is promotion
		{
			// update material
			int materialDifference = PieceHelper.getValue(piece);
			int promotedDifferential = PieceHelper.getPieceSquareValue(piece, target, endgame);
			
			bitBoards.toggleSquare(piece, start);
			zobristHash ^= zobristHashes[(target * 12 + PieceHelper.getZobristOffset(piece))]; // remove old piece from target square
			
			piece = (MoveHelper.getPromotedPiece(move) | PieceHelper.getColor(piece));
			
			bitBoards.toggleSquare(piece, start);
			zobristHash ^= zobristHashes[(target * 12 + PieceHelper.getZobristOffset(piece))]; // add promoted piece to target square
			
			// update material
			materialDifference = PieceHelper.getValue(piece) - materialDifference;
			if (PieceHelper.isColor(piece, PieceHelper.WHITE_PIECE))
				boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() + materialDifference);
			else
				boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() + materialDifference);
			
			promotedDifferential = PieceHelper.getPieceSquareValue(piece, target, endgame) - promotedDifferential;
			if (PieceHelper.isColor(piece, PieceHelper.WHITE_PIECE))
				boardInfo.setWhiteSquareBonus(boardInfo.getWhiteSquareBonus() + promotedDifferential);
			else
				boardInfo.setBlackSquareBonus(boardInfo.getBlackSquareBonus() + promotedDifferential);
		}
		
		byte oldCastlingRights = boardInfo.getCastlingRights();
		byte castlingRights = oldCastlingRights;
		if (start == 4 || start == 7 || target == 7) castlingRights &= ~BoardInfo.WHITE_KING_CASTLING;
		if (start == 4 || start == 0 || target == 0) castlingRights &= ~BoardInfo.WHITE_QUEEN_CASTLING;
		if (start == 60 || start == 63 || target == 63) castlingRights &= ~BoardInfo.BLACK_KING_CASTLING;
		if (start == 60 || start == 56 || target == 56) castlingRights &= ~BoardInfo.BLACK_QUEEN_CASTLING;
		
		int changedCastlingRights = oldCastlingRights ^ castlingRights;
		if (changedCastlingRights != 0)
		{
			if ((changedCastlingRights & BoardInfo.WHITE_KING_CASTLING) != 0) zobristHash ^= zobristHashes[769];
			if ((changedCastlingRights & BoardInfo.WHITE_QUEEN_CASTLING) != 0) zobristHash ^= zobristHashes[770];
			if ((changedCastlingRights & BoardInfo.BLACK_KING_CASTLING) != 0) zobristHash ^= zobristHashes[771];
			if ((changedCastlingRights & BoardInfo.BLACK_QUEEN_CASTLING) != 0) zobristHash ^= zobristHashes[772];
		}
		
		boardInfo.setCastlingRights(castlingRights);
		
		// move the piece to the new square
		squares[start] = PieceHelper.NONE;
		squares[target] = piece;
		bitBoards.toggleSquare(piece, start);
		bitBoards.toggleSquare(piece, target);
		
		zobristHash ^= zobristHashes[(start * 12 + PieceHelper.getZobristOffset(piece))]; // remove piece from start square
		zobristHash ^= zobristHashes[(target * 12 + PieceHelper.getZobristOffset(piece))]; // add piece to target square
		
		boardInfo.setZobristHash(zobristHash);	// set the hash
		boardInfo.getPositionList().add(zobristHash);
				
		return capturedPiece; // return the captured piece
	}

	public void moveBack(short move, int captured, BoardInfo boardInfoOld)
	{
		boardInfo = boardInfoOld; // replace current BoardInfo object with the old BoardInfo object
		boardInfo.getPositionList().remove(boardInfo.getPositionList().size()-1); // remove the most recent position

		int start = MoveHelper.getStartIndex(move);
		int target = MoveHelper.getTargetIndex(move);
		
		// move the piece back to its original square
		squares[start] = squares[target];
		squares[target] = PieceHelper.NONE;
		int piece = squares[start];
		int captureSquare = target;
		
		bitBoards.toggleSquare(piece, start);
		bitBoards.toggleSquare(piece, target);

		if (MoveHelper.isCastleMove(move)) // if it was a castle move
		{
			// need to move the rook back too
			int rookStart = MoveHelper.getRookStartIndex(move);
			int rookTarget = MoveHelper.getRookTargetIndex(move);
			
			squares[rookStart] = squares[rookTarget];
			squares[rookTarget] = PieceHelper.NONE;
			
			bitBoards.toggleSquare(squares[rookStart], rookStart);
			bitBoards.toggleSquare(squares[rookStart], rookTarget);
		}
		else if (MoveHelper.getPromotedPiece(move) != 0) // if it was a promotion
		{
			bitBoards.toggleSquare(piece, start);
			piece = (PieceHelper.PAWN | PieceHelper.getColor(piece));
			squares[start] = piece;
			bitBoards.toggleSquare(piece, start);
		}

		if (captured != PieceHelper.NONE) // if there was a captured piece
		{
			if (MoveHelper.getEnPassantCaptureIndex(move) != -1) // if en passant move
			{
				captureSquare = MoveHelper.getEnPassantCaptureIndex(move); // set captureSquare to the right place
			}
			
			// put captured piece back on the board
			squares[captureSquare] = captured;
			bitBoards.toggleSquare(captured, captureSquare);
		}
	}

	public long createZobristHash()
	{
		long zobristHash = 0;
		
		for (int i = 0; i < 64; i++)
		{
			int piece = squares[i];
			if (piece != PieceHelper.NONE)
			{
				zobristHash ^= zobristHashes[(i * 12 + PieceHelper.getZobristOffset(piece))];
			}
		}

		if (!boardInfo.isWhiteToMove())
			zobristHash ^= zobristHashes[768];
		
		byte castlingRights = boardInfo.getCastlingRights();
		if ((castlingRights & BoardInfo.WHITE_KING_CASTLING) != 0)
			zobristHash ^= zobristHashes[769];
		if ((castlingRights & BoardInfo.WHITE_QUEEN_CASTLING) != 0)
			zobristHash ^= zobristHashes[770];
		if ((castlingRights & BoardInfo.BLACK_KING_CASTLING) != 0)
			zobristHash ^= zobristHashes[771];
		if ((castlingRights & BoardInfo.BLACK_QUEEN_CASTLING) != 0)
			zobristHash ^= zobristHashes[772];
		
		if (boardInfo.getEnPassantIndex() != -1)
			zobristHash ^= zobristHashes[773 + (boardInfo.getEnPassantIndex() % 8)];
		
		return zobristHash;
	}

	/**
	 * Load in a FEN string into the virtual board representation
	 * 
	 * @param fen The given FEN string to load in
	 */
	public void loadFEN(String fen)
	{
		// split the fen into two strings, one for pieces information and the other for extra information
		String fenPieces = fen.substring(0, fen.indexOf(" "));
		String fenOther = fen.substring(fen.indexOf(" "));
		fenPieces.trim();
		fenOther.trim();
		int file = 1;
		int rank = 1;
		squares = new int[64];
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
						squares[Utils.getSquareIndexFromRankFile(rank, file)] = PieceHelper.NONE;
						file++; // increment the file
						num--;
					}
				}
				else // not a number so a piece
				{
					int piece = PieceHelper.NONE;
					
					int color = PieceHelper.BLACK_PIECE;
					if (((int) c) > 65 && ((int) c) < 90) color = PieceHelper.WHITE_PIECE;
										
					if (c == 'k' || c == 'K')
						piece = PieceHelper.KING | color;
					else if (c == 'q' || c == 'Q')
						piece = PieceHelper.QUEEN | color;
					else if (c == 'r' || c == 'R')
						piece = PieceHelper.ROOK | color;
					else if (c == 'b' || c == 'B')
						piece = PieceHelper.BISHOP | color;
					else if (c == 'n' || c == 'N')
						piece = PieceHelper.KNIGHT | color;
					else if (c == 'p' || c == 'P')
						piece = PieceHelper.PAWN | color;
										
					squares[Utils.getSquareIndexFromRankFile(rank, file)] = piece;
					file++; // increment file to move onto next square
				}
			}
			rank++; // decrement rank
			file = 1; // reset file
		}

		String[] fenInfo = fenOther.split(" "); // split string by spaces to split up information
		for (int i = 0; i < fenInfo.length; i++)
		{
			String string = fenInfo[i];
			if (i == 1)
				boardInfo.setWhiteToMove(string.equals("w")); // sets color to move
			else if (i == 2) // checks castling rights
			{
				byte castlingRights = 0;
				for (int j = 0; j < string.length(); j++)
				{
					if (string.charAt(j) == 'K')
						castlingRights |= BoardInfo.WHITE_KING_CASTLING;
					if (string.charAt(j) == 'Q')
						castlingRights |= BoardInfo.WHITE_QUEEN_CASTLING;
					if (string.charAt(j) == 'k')
						castlingRights |= BoardInfo.BLACK_KING_CASTLING;
					if (string.charAt(j) == 'q')
						castlingRights |= BoardInfo.BLACK_QUEEN_CASTLING;
					boardInfo.setCastlingRights(castlingRights); // set the castling rights
				}
			}
			else if (i == 3) // checks en passant index
			{
				if (string.length() != 1) // if it is not a "-", en passant index exists
				{
					int enPassantRank = string.charAt(1) - 48;
					int enPassantFile = string.charAt(0) - 96;
					boardInfo.setEnPassantIndex(Utils.getSquareIndexFromRankFile(enPassantRank, enPassantFile)); // set the en passant index
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
		
		for (int piece : squares)
		{
			if (piece != PieceHelper.NONE)
			{
				if (PieceHelper.isColor(piece, PieceHelper.WHITE_PIECE))
				{
					boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() + PieceHelper.getValue(piece));
				}
				else
				{
					boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() + PieceHelper.getValue(piece));
				}
			}
		}
		
		boolean endgame = boardInfo.getWhiteMaterial() + boardInfo.getBlackMaterial() < 4000;
		boardInfo.setWhiteSquareBonus(0);
		boardInfo.setBlackSquareBonus(0);
		
		for (int index = 0; index < 64; index++)
		{
			int piece = squares[index];
			if (piece != PieceHelper.NONE)
			{
				if (PieceHelper.isColor(piece, PieceHelper.WHITE_PIECE))
				{
					boardInfo.setWhiteMaterial(boardInfo.getWhiteMaterial() + PieceHelper.getPieceSquareValue(piece, index, endgame));
				}
				else
				{
					boardInfo.setBlackMaterial(boardInfo.getBlackMaterial() + PieceHelper.getPieceSquareValue(piece, index, endgame));
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
			if (squares[i] != PieceHelper.NONE) // if an uncaptured piece on this square
			{
				if (count != 0) // if count number of empty squares preceded this piece
				{
					row += "" + count; // add the number count to the string
					count = 0; // reset count
				}
				row += "" + PieceHelper.getPieceSymbol(squares[i]); // add the piece symbol of this piece
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
		byte castlingRights = boardInfo.getCastlingRights(); // set castling rights
		if ((castlingRights & BoardInfo.WHITE_KING_CASTLING) != 0)
			newFEN += "K";
		if ((castlingRights & BoardInfo.WHITE_QUEEN_CASTLING) != 0)
			newFEN += "Q";
		if ((castlingRights & BoardInfo.BLACK_KING_CASTLING) != 0)
			newFEN += "k";
		if ((castlingRights & BoardInfo.BLACK_QUEEN_CASTLING) != 0)
			newFEN += "q";
		newFEN += " ";
		if (boardInfo.getEnPassantIndex() == -1)
			newFEN += "- "; // add dash if no en passant index
		else
		{
			int index = boardInfo.getEnPassantIndex();
			newFEN += Utils.getSquareName(index);
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
				int square = Utils.getSquareIndexFromRankFile(rank, file);
				if (squares[square] != PieceHelper.NONE) // if piece exists
				{
					System.out.print(PieceHelper.getPieceSymbol(squares[square]) + " | "); // print the piece symbol
				}
				else
					System.out.print("  | "); // or just print an empty space
			}
		}
		System.out.println("\n+---+---+---+---+---+---+---+---+\n");
	}
}