package me.Shadow.Engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

public class Board
{
	final static long[] zobristHashes = new Random(8108415243282079581L).longs(781).toArray();
	public final static String defaultFEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
	
	static final long WHITE_KING_CASTLING = 0b1;
	static final long WHITE_QUEEN_CASTLING = 0b10;
	static final long BLACK_KING_CASTLING = 0b100;
	static final long BLACK_QUEEN_CASTLING = 0b1000;

	public Bitboards bitBoards;
	public int[] squares = new int[64];
	public int colorToMove;
	
	private long zobristHash;
	private long pawnsHash;
	long material;
	private short halfMoves;
	private short moveNum;
	byte castlingRights;
	short enPassantIndex;
	public ArrayList<Long> positionList = new ArrayList<Long>();

	boolean cachedCheckValue = false;
	boolean isCachedCheckValid = false;

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
		loadFEN(fen);
	}

	public boolean inCheck()
	{
		if (isCachedCheckValid)
			return cachedCheckValue;
		else
		{
			cachedCheckValue = isInCheck();
			isCachedCheckValid = true;
			return cachedCheckValue;
		}
	}

	boolean isInCheck()
	{
		int enemyColor = colorToMove ^ PieceHelper.BLACK_PIECE;

		int friendlyKingIndex = Bitboards.getLSB(bitBoards.pieceBoards[PieceHelper.KING | colorToMove]);

		long friendlyPiecesBitboard = bitBoards.colorBoards[colorToMove];
		long enemyPiecesBitboard = bitBoards.colorBoards[enemyColor];
		long allPiecesBitboard = friendlyPiecesBitboard | enemyPiecesBitboard;

		long orthoSlidersBitboard = bitBoards.getOrthogonalSliders(enemyColor);
		long diagSlidersBitboard = bitBoards.getDiagonalSliders(enemyColor);

		if (orthoSlidersBitboard != 0 && (PrecomputedMagicNumbers.getRookMoves(friendlyKingIndex, allPiecesBitboard)
				& orthoSlidersBitboard) != 0)
			return true;

		if (diagSlidersBitboard != 0 && (PrecomputedMagicNumbers.getBishopMoves(friendlyKingIndex, allPiecesBitboard)
				& diagSlidersBitboard) != 0)
			return true;

		long enemyKnightsBitboard = bitBoards.pieceBoards[PieceHelper.KNIGHT | enemyColor];

		if ((PrecomputedData.KNIGHT_MOVES[friendlyKingIndex] & enemyKnightsBitboard) != 0)
			return true;

		long potentialPawnLocations = PrecomputedData.getPawnCaptures(friendlyKingIndex, colorToMove);
		long enemyPawnsBitboard = bitBoards.pieceBoards[PieceHelper.PAWN | enemyColor];

		if ((potentialPawnLocations & enemyPawnsBitboard) != 0)
			return true;

		return false;
	}

	public int movePiece(final short move)
	{
		final int start = MoveHelper.getStartIndex(move);
		final int target = MoveHelper.getTargetIndex(move);

		int captureIndex = target;
		if (MoveHelper.getEnPassantCaptureIndex(move) != -1)
			captureIndex = MoveHelper.getEnPassantCaptureIndex(move); // if en passant, change capture square

		int piece = squares[start];
		final int capturedPiece = squares[captureIndex];
		
		int color = colorToMove;
		int enemyColor = color ^ PieceHelper.BLACK_PIECE;
		int friendlyMGPieceBonus = Board.getMaterial(color, false, material);
		int friendlyEGPieceBonus = Board.getMaterial(color, true, material);
		int enemyMGPieceBonus = Board.getMaterial(enemyColor, false, material);
		int enemyEGPieceBonus = Board.getMaterial(enemyColor, true, material);

		// update boardInfo information
		colorToMove ^= PieceHelper.BLACK_PIECE;
		zobristHash ^= zobristHashes[768]; // flip side to move
		
		halfMoves++;
		if (PieceHelper.getPieceType(piece) == PieceHelper.PAWN)
			halfMoves = 0; // reset half moves for 50 move rule
		if (color == PieceHelper.BLACK_PIECE)
			moveNum++;

		if (enPassantIndex != -1)
			zobristHash ^= zobristHashes[773 + (enPassantIndex % 8)]; // remove old enpassant index
		if (MoveHelper.getEnPassantNewIndex(move) != -1)
			zobristHash ^= zobristHashes[773 + (MoveHelper.getEnPassantNewIndex(move) % 8)]; // add new enpassant index

		// set or reset en passant index
		enPassantIndex = (short) MoveHelper.getEnPassantNewIndex(move);

		if (capturedPiece != PieceHelper.NONE) // if move is a capture
		{
			halfMoves = 0; // reset half moves for 50 move rule
			// remove captured piece from the board
			squares[captureIndex] = PieceHelper.NONE;
			bitBoards.toggleSquare(capturedPiece, captureIndex);
			// bitBoards.setAttacksFrom(captureIndex, 0);

			// remove captured piece
			zobristHash ^= zobristHashes[(captureIndex * 12 + PieceHelper.getZobristOffset(capturedPiece))];
			if (PieceHelper.getPieceType(capturedPiece) == PieceHelper.PAWN)
				pawnsHash ^= zobristHashes[(captureIndex * 12 + PieceHelper.getZobristOffset(capturedPiece))];

			enemyMGPieceBonus -= PieceHelper.getPieceSquareValue(capturedPiece, captureIndex, false);
			enemyEGPieceBonus -= PieceHelper.getPieceSquareValue(capturedPiece, captureIndex, true);
		}

		if (MoveHelper.isCastleMove(move)) // if move is castling
		{
			final int rookStart = MoveHelper.getRookStartIndex(move);
			final int rookTarget = MoveHelper.getRookTargetIndex(move);

			// move the rook
			final int rook = squares[rookStart];
			squares[rookStart] = PieceHelper.NONE;
			squares[rookTarget] = rook;

			bitBoards.toggleSquare(rook, rookStart);
			bitBoards.toggleSquare(rook, rookTarget);
			// bitBoards.setAttacksFrom(rookStart, 0);
			// bitBoards.createAttacksFrom(rookTarget, rook);

			// remove rook from start square, add rook to target square
			zobristHash ^= zobristHashes[(rookStart * 12 + PieceHelper.getZobristOffset(rook))];
			zobristHash ^= zobristHashes[(rookTarget * 12 + PieceHelper.getZobristOffset(rook))];

			friendlyMGPieceBonus += PieceHelper.getPieceSquareValue(rook, rookTarget, false)
					- PieceHelper.getPieceSquareValue(rook, rookStart, false);
			friendlyEGPieceBonus += PieceHelper.getPieceSquareValue(rook, rookTarget, true)
					- PieceHelper.getPieceSquareValue(rook, rookStart, true);

			// castling rights get updated below
		}
		else if (MoveHelper.getPromotedPiece(move) != 0) // if move is promotion
		{
			// remove old piece from target square
			bitBoards.toggleSquare(piece, start);
			zobristHash ^= zobristHashes[(start * 12 + PieceHelper.getZobristOffset(piece))];
			pawnsHash ^= zobristHashes[(start * 12 + PieceHelper.getZobristOffset(piece))]; // always pawn promoting

			friendlyMGPieceBonus -= PieceHelper.getPieceSquareValue(piece, start, false);
			friendlyEGPieceBonus -= PieceHelper.getPieceSquareValue(piece, start, true);

			piece = (MoveHelper.getPromotedPiece(move) | PieceHelper.getColor(piece));

			// add promoted piece to target square
			bitBoards.toggleSquare(piece, start);
			zobristHash ^= zobristHashes[(start * 12 + PieceHelper.getZobristOffset(piece))];

			friendlyMGPieceBonus += PieceHelper.getPieceSquareValue(piece, start, false);
			friendlyEGPieceBonus += PieceHelper.getPieceSquareValue(piece, start, true);
		}

		final byte oldCastlingRights = castlingRights;
		if (start == 4 || start == 7 || target == 7)
			castlingRights &= ~WHITE_KING_CASTLING;
		if (start == 4 || start == 0 || target == 0)
			castlingRights &= ~WHITE_QUEEN_CASTLING;
		if (start == 60 || start == 63 || target == 63)
			castlingRights &= ~BLACK_KING_CASTLING;
		if (start == 60 || start == 56 || target == 56)
			castlingRights &= ~BLACK_QUEEN_CASTLING;

		final int changedCastlingRights = oldCastlingRights ^ castlingRights;
		if (changedCastlingRights != 0)
		{
			if ((changedCastlingRights & WHITE_KING_CASTLING) != 0)
				zobristHash ^= zobristHashes[769];
			if ((changedCastlingRights & WHITE_QUEEN_CASTLING) != 0)
				zobristHash ^= zobristHashes[770];
			if ((changedCastlingRights & BLACK_KING_CASTLING) != 0)
				zobristHash ^= zobristHashes[771];
			if ((changedCastlingRights & BLACK_QUEEN_CASTLING) != 0)
				zobristHash ^= zobristHashes[772];
		}

		// move the piece to the new square
		squares[start] = PieceHelper.NONE;
		squares[target] = piece;
		bitBoards.toggleSquare(piece, start);
		bitBoards.toggleSquare(piece, target);
		// bitBoards.setAttacksFrom(start, 0);
		// bitBoards.createAttacksFrom(target, piece);

		// remove piece from start square, add piece to target square
		zobristHash ^= zobristHashes[(start * 12 + PieceHelper.getZobristOffset(piece))];
		zobristHash ^= zobristHashes[(target * 12 + PieceHelper.getZobristOffset(piece))];
		
		if (PieceHelper.getPieceType(piece) == PieceHelper.PAWN)
		{
			pawnsHash ^= zobristHashes[(start * 12 + PieceHelper.getZobristOffset(piece))];
			pawnsHash ^= zobristHashes[(target * 12 + PieceHelper.getZobristOffset(piece))];
		}

		friendlyMGPieceBonus += PieceHelper.getPieceSquareValue(piece, target, false)
				- PieceHelper.getPieceSquareValue(piece, start, false);
		friendlyEGPieceBonus += PieceHelper.getPieceSquareValue(piece, target, true)
				- PieceHelper.getPieceSquareValue(piece, start, true);

		positionList.add(zobristHash);
		
		material = Board.createMaterial(friendlyMGPieceBonus, friendlyEGPieceBonus, enemyMGPieceBonus, enemyEGPieceBonus, color);

		isCachedCheckValid = false;

		return capturedPiece; // return the captured piece
	}

	public void moveBack(final short move, final int captured, long [] boardInfoOld)
	{
		unpackBoardInfo(boardInfoOld);
		colorToMove ^= PieceHelper.BLACK_PIECE;
		positionList.removeLast(); // remove the most recent position

		final int start = MoveHelper.getStartIndex(move);
		final int target = MoveHelper.getTargetIndex(move);

		// move the piece back to its original square
		squares[start] = squares[target];
		squares[target] = PieceHelper.NONE;
		int piece = squares[start];
		int captureSquare = target;

		bitBoards.toggleSquare(piece, start);
		bitBoards.toggleSquare(piece, target);

		// bitBoards.setAttacksFrom(target, 0);
		// bitBoards.createAttacksFrom(start, piece);

		if (MoveHelper.isCastleMove(move)) // if it was a castle move
		{
			// need to move the rook back too
			final int rookStart = MoveHelper.getRookStartIndex(move);
			final int rookTarget = MoveHelper.getRookTargetIndex(move);

			squares[rookStart] = squares[rookTarget];
			squares[rookTarget] = PieceHelper.NONE;

			bitBoards.toggleSquare(squares[rookStart], rookStart);
			bitBoards.toggleSquare(squares[rookStart], rookTarget);

			// bitBoards.setAttacksFrom(rookTarget, 0);
			// bitBoards.createAttacksFrom(rookStart, squares[rookStart]);
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
			// bitBoards.createAttacksFrom(captureSquare, captured);
		}

		isCachedCheckValid = false;
	}
	
	public long getZobristHash()
	{
		return zobristHash;
	}
	
	public long getPawnsHash()
	{
		return pawnsHash;
	}
	
	public long[] packBoardInfo()
	{
		long info = (long)enPassantIndex << 48;
		info |= (long)castlingRights << 32;
		info |= moveNum << 16L;
		info |= halfMoves;
		
		return new long[] {zobristHash, pawnsHash, material, info};
	}
	
	public void unpackBoardInfo(long [] boardInfo)
	{
		zobristHash = boardInfo[0];
		pawnsHash = boardInfo[1];
		material = boardInfo[2];
		enPassantIndex = (short) (boardInfo[3] >> 48);	// take advantage of sign extending
		castlingRights = (byte) ((boardInfo[3] >>> 32) & 0xFFFF);
		moveNum = (short) ((boardInfo[3] >>> 16) & 0xFFFF);
		halfMoves = (short) (boardInfo[3] & 0xFFFF);
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

		if (colorToMove == PieceHelper.BLACK_PIECE)
			zobristHash ^= zobristHashes[768];

		if ((castlingRights & WHITE_KING_CASTLING) != 0)
			zobristHash ^= zobristHashes[769];
		if ((castlingRights & WHITE_QUEEN_CASTLING) != 0)
			zobristHash ^= zobristHashes[770];
		if ((castlingRights & BLACK_KING_CASTLING) != 0)
			zobristHash ^= zobristHashes[771];
		if ((castlingRights & BLACK_QUEEN_CASTLING) != 0)
			zobristHash ^= zobristHashes[772];

		if (enPassantIndex != -1)
			zobristHash ^= zobristHashes[773 + (enPassantIndex % 8)];

		return zobristHash;
	}
	
	public long createPawnsHash()
	{
		long pawnsHash = 0;
		for (int i = 0; i < 64; i++)
		{
			int piece = squares[i];
			if (piece != PieceHelper.NONE && PieceHelper.getPieceType(piece) == PieceHelper.PAWN)
			{
				pawnsHash ^= zobristHashes[(i * 12 + PieceHelper.getZobristOffset(piece))];
			}
		}
		
		return pawnsHash;
	}

	/**
	 * Load in a FEN string into the virtual board representation
	 * 
	 * @param fen The given FEN string to load in
	 */
	public void loadFEN(String fen)
	{
		// split the fen into two strings, one for pieces information and the other for
		// extra information
		String fenPieces = fen.substring(0, fen.indexOf(" "));
		String fenOther = fen.substring(fen.indexOf(" "));
		fenPieces.trim();
		fenOther.trim();
		int file = 1;
		int rank = 1;
		squares = new int[64];
		halfMoves = moveNum = castlingRights = 0;
		enPassantIndex = -1;
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
					if (((int) c) > 65 && ((int) c) < 90)
						color = PieceHelper.WHITE_PIECE;

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
			String string = fenInfo[i].trim();
			if (i == 1)
				colorToMove = string.equals("w") ? PieceHelper.WHITE_PIECE : PieceHelper.BLACK_PIECE; // sets color to
																										// move
			else if (i == 2) // checks castling rights
			{
				castlingRights = 0;
				for (int j = 0; j < string.length(); j++)
				{
					if (string.charAt(j) == 'K')
						castlingRights |= WHITE_KING_CASTLING;
					if (string.charAt(j) == 'Q')
						castlingRights |= WHITE_QUEEN_CASTLING;
					if (string.charAt(j) == 'k')
						castlingRights |= BLACK_KING_CASTLING;
					if (string.charAt(j) == 'q')
						castlingRights |= BLACK_QUEEN_CASTLING;
				}
			}
			else if (i == 3) // checks en passant index
			{
				if (string.length() != 1) // if it is not a "-", en passant index exists
				{
					int enPassantRank = string.charAt(1) - 48;
					int enPassantFile = string.charAt(0) - 96;
					enPassantIndex = (short) Utils.getSquareIndexFromRankFile(enPassantRank, enPassantFile);																													// the																													// passant
				}
			}
			else if (i == 4) // checks and sets half moves for 50 move rule
			{
				if (!string.equals("-"))
					halfMoves = Short.parseShort(string);
			}
			else if (i == 5) // checks and sets move number
			{
				if (!string.equals("-"))
					moveNum = Short.parseShort(string);
			}
		}

		zobristHash = createZobristHash();
		pawnsHash = createPawnsHash();
		positionList.clear();
		positionList.add(zobristHash);

		int whiteMGBonus = 0;
		int whiteEGBonus = 0;
		int blackMGBonus = 0;
		int blackEGBonus = 0;
		for (int i = 0; i < 64; i++)
		{
			int piece = squares[i];
			if (piece != PieceHelper.NONE)
			{
				if ((piece & PieceHelper.COLOR_MASK) == PieceHelper.WHITE_PIECE)
				{
					whiteMGBonus += PieceHelper.getPieceSquareValue(piece, i, false);
					whiteEGBonus += PieceHelper.getPieceSquareValue(piece, i, true);
				}
				else
				{
					blackMGBonus += PieceHelper.getPieceSquareValue(piece, i, false);
					blackEGBonus += PieceHelper.getPieceSquareValue(piece, i, true);
				}
			}
		}
		
		material = Board.createMaterial(whiteMGBonus, whiteEGBonus, blackMGBonus, blackEGBonus, PieceHelper.WHITE_PIECE);

		if (bitBoards == null)
			bitBoards = new Bitboards(this);
		else
			bitBoards.createBitboards(this);

		isCachedCheckValid = false;
	}
	
	public int getHalfMoves()
	{
		return halfMoves;
	}
	
	public int getMoveNum()
	{
		return moveNum;
	}
	
	public static short getMaterial(int color, boolean endgame, long material)
	{
		if (color == PieceHelper.BLACK_PIECE) material = material >>> 32;
		if (endgame) material = material >>> 16;
		return (short) material;
	}
	
	public static long createMaterial(int wMG, int wEG, int bMG, int bEG, int color)
	{
		// if color perspective is black, flip black and white to keep white as lower order bits
		if (color == PieceHelper.WHITE_PIECE)
			return (wMG & 0xFFFFFFFF) | (wEG & 0xFFFFFFFF) << 16 | (bMG & 0xFFFFFFFF) << 32 | (bEG & 0xFFFFFFFF) << 48;
		else
			return (bMG & 0xFFFFFFFF) | (bEG & 0xFFFFFFFF) << 16 | (wMG & 0xFFFFFFFF) << 32 | (wEG & 0xFFFFFFFF) << 48;
	}

	/**
	 * Creates a FEN string for the current position with or without half moves and
	 * move number included
	 * 
	 * @param includeMoveNums Boolean for whether or not to include moveNums and
	 *                        halfMoves in the FEN string
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

				if (i >= 8)
					row += "/"; // add a slash to signify next row

				rows.add(row);
				row = "";
			}
		}

		String newFEN = "";
		Collections.reverse(rows);
		for (String string : rows)
			newFEN += string;

		newFEN += " " + (colorToMove == PieceHelper.WHITE_PIECE ? "w" : "b") + " "; // color to move
		if ((castlingRights & WHITE_KING_CASTLING) != 0)
			newFEN += "K";
		if ((castlingRights & WHITE_QUEEN_CASTLING) != 0)
			newFEN += "Q";
		if ((castlingRights & BLACK_KING_CASTLING) != 0)
			newFEN += "k";
		if ((castlingRights & BLACK_QUEEN_CASTLING) != 0)
			newFEN += "q";
		newFEN += " ";
		if (enPassantIndex == -1)
			newFEN += "- "; // add dash if no en passant index
		else
		{
			int index = enPassantIndex;
			newFEN += Utils.getSquareName(index);
		}
		if (includeMoveNums)
			newFEN += halfMoves + " " + moveNum; // add moves numbers if includeMoveNums
																				// is true

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