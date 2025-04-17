package me.Shadow.Engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

public class Board
{
	final static long[] zobristHashes = new Random(8108415243282079581L).longs(794).toArray();
	public final static String defaultFEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
	
	static final int ZOBRIST_COLOR_INDEX = 768;
	static final int ZOBRIST_NO_EP_INDEX = 769;
	static final int ZOBRIST_EP_INDEX = ZOBRIST_NO_EP_INDEX + 1;
	static final int ZOBRIST_CASTLING_INDEX = ZOBRIST_EP_INDEX + 8;
	
	public static final byte WHITE_KING_CASTLING = 0b1;
	public static final byte BLACK_KING_CASTLING = 0b10;
	public static final byte WHITE_QUEEN_CASTLING = 0b100;
	public static final byte BLACK_QUEEN_CASTLING = 0b1000;
	public static final byte WHITE_CASTLING = WHITE_KING_CASTLING | WHITE_QUEEN_CASTLING;
	public static final byte BLACK_CASTLING = BLACK_KING_CASTLING | BLACK_QUEEN_CASTLING;
	public static final byte [] CASTLING_MASKS = new byte[64];

	public Bitboards bitBoards;
	public byte[] squares = new byte[64];
	public byte colorToMove;
	
	public long zobristHash;
	public long pawnsHash;
	public short [] material = new short[4];
	public short halfMoves;
	private short moveNum;
	public short castlingRights;
	public short enPassantIndex;
	
	// ~100 positions for 50 move rule + about 28 depth deep search
	public long [] repetitionHistory = new long[100 + MoveSearcher.MAX_DEPTH];
	public int repetitionIndex;

	boolean cachedCheckValue = false;
	boolean isCachedCheckValid = false;

	public Board()
	{
		this(defaultFEN);
	}
	
	public Board(String fen)
	{
		loadFEN(fen);
		
		for (int i = 0; i < 64; i++)
		{
			byte castlingRights = (byte) (WHITE_CASTLING | BLACK_CASTLING);
			if (i == 4) castlingRights &= ~WHITE_CASTLING;
			else if (i == 60) castlingRights &= ~BLACK_CASTLING;
			else if (i == 7) castlingRights &= ~WHITE_KING_CASTLING;
			else if (i == 0) castlingRights &= ~WHITE_QUEEN_CASTLING;
			else if (i == 63) castlingRights &= ~BLACK_KING_CASTLING;
			else if (i == 56) castlingRights &= ~BLACK_QUEEN_CASTLING;
			CASTLING_MASKS[i] = castlingRights;
		}
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

	private boolean isInCheck()
	{
		byte enemyColor = (byte) (colorToMove ^ PieceHelper.BLACK);

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

	public byte movePiece(final short move)
	{
		final int start = MoveHelper.getStartIndex(move);
		final int target = MoveHelper.getTargetIndex(move);

		byte piece = squares[start];

		byte enemyColor = (byte) (colorToMove ^ PieceHelper.BLACK);

		int captureIndex = target;
		if (MoveHelper.isSpecial(move))
		{
			if (MoveHelper.isCastleMove(move)) // if move is castling
			{
				final int rookStart = MoveHelper.getRookStartIndex(move);
				final int rookTarget = MoveHelper.getRookTargetIndex(move);

				// move the rook
				final byte rook = squares[rookStart];
				squares[rookStart] = PieceHelper.NONE;
				squares[rookTarget] = rook;

				bitBoards.toggleSquare(rook, rookStart);
				bitBoards.toggleSquare(rook, rookTarget);

				// remove rook from start square, add rook to target square
				zobristHash ^= zobristHashes[(rookStart * 12 + PieceHelper.getZobristOffset(rook))];
				zobristHash ^= zobristHashes[(rookTarget * 12 + PieceHelper.getZobristOffset(rook))];

				material[colorToMove*2] += PieceHelper.getPieceSquareValue(rook, rookTarget, false)
						- PieceHelper.getPieceSquareValue(rook, rookStart, false);
				material[colorToMove*2 + 1] += PieceHelper.getPieceSquareValue(rook, rookTarget, true)
						- PieceHelper.getPieceSquareValue(rook, rookStart, true);

				// castling rights get updated below
			}
			else if (MoveHelper.getPromotedPiece(move) != 0) // if move is promotion
			{
				// remove old piece from target square
				bitBoards.toggleSquare(piece, start);
				zobristHash ^= zobristHashes[(start * 12 + PieceHelper.getZobristOffset(piece))];
				pawnsHash ^= zobristHashes[(start * 12 + PieceHelper.getZobristOffset(piece))]; // always pawn promoting

				material[colorToMove*2] -= PieceHelper.getPieceSquareValue(piece, start, false);
				material[colorToMove*2 + 1] -= PieceHelper.getPieceSquareValue(piece, start, true);

				piece = (byte) (MoveHelper.getPromotedPiece(move) | PieceHelper.getColor(piece));

				// add promoted piece to target square
				bitBoards.toggleSquare(piece, start);
				zobristHash ^= zobristHashes[(start * 12 + PieceHelper.getZobristOffset(piece))];

				material[colorToMove*2] += PieceHelper.getPieceSquareValue(piece, start, false);
				material[colorToMove*2 + 1] += PieceHelper.getPieceSquareValue(piece, start, true);
			}
			else if (MoveHelper.getEnPassantCaptureIndex(move) != -1)
				captureIndex = MoveHelper.getEnPassantCaptureIndex(move); // if en passant, change capture square
		}
		
		// if ep is -1, then mod 8 is -1, so it goes down to the ZOBRIST_NO_EP_INDEX
		zobristHash ^= zobristHashes[ZOBRIST_EP_INDEX + (enPassantIndex % 8)]; // remove old enpassant index
					
		// set or reset en passant index
		enPassantIndex = (short) MoveHelper.getEnPassantNewIndex(move);
		
		// if ep is -1, then mod 8 is -1, so it goes down to the ZOBRIST_NO_EP_INDEX
		zobristHash ^= zobristHashes[ZOBRIST_EP_INDEX + (enPassantIndex % 8)]; // remove old enpassant index
		
		zobristHash ^= zobristHashes[ZOBRIST_CASTLING_INDEX + castlingRights];
		castlingRights &= CASTLING_MASKS[start] & CASTLING_MASKS[target];
		zobristHash ^= zobristHashes[ZOBRIST_CASTLING_INDEX + castlingRights];
		
		final byte capturedPiece = squares[captureIndex];
		if (capturedPiece != PieceHelper.NONE) // if move is a capture
		{
			halfMoves = 0; // reset half moves for 50 move rule
			// remove captured piece from the board
			squares[captureIndex] = PieceHelper.NONE;
			bitBoards.toggleSquare(capturedPiece, captureIndex);

			// remove captured piece
			zobristHash ^= zobristHashes[(captureIndex * 12 + PieceHelper.getZobristOffset(capturedPiece))];
			if (PieceHelper.getPieceType(capturedPiece) == PieceHelper.PAWN)
				pawnsHash ^= zobristHashes[(captureIndex * 12 + PieceHelper.getZobristOffset(capturedPiece))];
			
			material[enemyColor*2] -= PieceHelper.getPieceSquareValue(capturedPiece, captureIndex, false);
			material[enemyColor*2 + 1] -= PieceHelper.getPieceSquareValue(capturedPiece, captureIndex, true);
		}

		// move the piece to the new square
		squares[start] = PieceHelper.NONE;
		squares[target] = piece;
		bitBoards.toggleSquare(piece, start);
		bitBoards.toggleSquare(piece, target);
		
		halfMoves++;			
		moveNum++;

		// remove piece from start square, add piece to target square
		zobristHash ^= zobristHashes[(start * 12 + PieceHelper.getZobristOffset(piece))];
		zobristHash ^= zobristHashes[(target * 12 + PieceHelper.getZobristOffset(piece))];
		
		if (PieceHelper.getPieceType(piece) == PieceHelper.PAWN)
		{
			halfMoves = 0; // reset half moves for 50 move rule
			pawnsHash ^= zobristHashes[(start * 12 + PieceHelper.getZobristOffset(piece))];
			pawnsHash ^= zobristHashes[(target * 12 + PieceHelper.getZobristOffset(piece))];
		}

		material[colorToMove*2] += PieceHelper.getPieceSquareValue(piece, target, false)
				- PieceHelper.getPieceSquareValue(piece, start, false);
		material[colorToMove*2 + 1] += PieceHelper.getPieceSquareValue(piece, target, true)
				- PieceHelper.getPieceSquareValue(piece, start, true);
		
		// update side to move information
		colorToMove ^= PieceHelper.BLACK;
		zobristHash ^= zobristHashes[ZOBRIST_COLOR_INDEX]; // flip side to move

		repetitionHistory[++repetitionIndex] = zobristHash;
		
		isCachedCheckValid = false;

		return capturedPiece; // return the captured piece
	}
	
	public void moveBack(final short move, final byte captured, long zobristHash, long pawnsHash, short [] boardInfoOld)
	{
		this.zobristHash = zobristHash;
		this.pawnsHash = pawnsHash;
		unpackBoardInfo(boardInfoOld);
		colorToMove ^= PieceHelper.BLACK;
		moveNum--;
		repetitionIndex--; // remove the most recent position

		final int start = MoveHelper.getStartIndex(move);
		final int target = MoveHelper.getTargetIndex(move);

		// move the piece back to its original square
		squares[start] = squares[target];
		squares[target] = PieceHelper.NONE;
		byte piece = squares[start];
		int captureSquare = target;

		bitBoards.toggleSquare(piece, start);
		bitBoards.toggleSquare(piece, target);

		if (MoveHelper.isSpecial(move))
		{
			if (MoveHelper.isCastleMove(move)) // if it was a castle move
			{
				// need to move the rook back too
				final int rookStart = MoveHelper.getRookStartIndex(move);
				final int rookTarget = MoveHelper.getRookTargetIndex(move);

				squares[rookStart] = squares[rookTarget];
				squares[rookTarget] = PieceHelper.NONE;

				bitBoards.toggleSquare(squares[rookStart], rookStart);
				bitBoards.toggleSquare(squares[rookStart], rookTarget);
			}
			else if (MoveHelper.getPromotedPiece(move) != 0) // if it was a promotion
			{
				bitBoards.toggleSquare(piece, start);
				piece = (byte) (PieceHelper.PAWN | PieceHelper.getColor(piece));
				squares[start] = piece;
				bitBoards.toggleSquare(piece, start);
			}
			else if (MoveHelper.getEnPassantCaptureIndex(move) != -1) // if en passant move
			{
				captureSquare = MoveHelper.getEnPassantCaptureIndex(move); // set captureSquare to the right place
			}
		}
		

		if (captured != PieceHelper.NONE) // if there was a captured piece
		{
			// put captured piece back on the board
			squares[captureSquare] = captured;
			bitBoards.toggleSquare(captured, captureSquare);
		}

		isCachedCheckValid = false;
	}
	
	public short[] packBoardInfo()
	{
		return new short[] {material[0], material[1], material[2], material[3], enPassantIndex, castlingRights, halfMoves};
	}
	
	public void unpackBoardInfo(short [] boardInfo)
	{
		material[0] = boardInfo[0];
		material[1] = boardInfo[1];
		material[2] = boardInfo[2];
		material[3] = boardInfo[3];
		enPassantIndex = boardInfo[4];
		castlingRights = boardInfo[5];
		halfMoves = boardInfo[6];
	}

	public long createZobristHash()
	{
		long zobristHash = 0;

		for (int i = 0; i < 64; i++)
		{
			byte piece = squares[i];
			if (piece != PieceHelper.NONE)
			{
				zobristHash ^= zobristHashes[(i * 12 + PieceHelper.getZobristOffset(piece))];
			}
		}

		if (colorToMove == PieceHelper.BLACK)
			zobristHash ^= zobristHashes[ZOBRIST_COLOR_INDEX];
		
		zobristHash ^= zobristHashes[ZOBRIST_CASTLING_INDEX + castlingRights];

		zobristHash ^= zobristHashes[ZOBRIST_EP_INDEX + (enPassantIndex % 8)];			

		return zobristHash;
	}
	
	public long createPawnsHash()
	{
		long pawnsHash = 0;
		for (int i = 0; i < 64; i++)
		{
			byte piece = squares[i];
			if (piece != PieceHelper.NONE && PieceHelper.getPieceType(piece) == PieceHelper.PAWN)
			{
				pawnsHash ^= zobristHashes[(i * 12 + PieceHelper.getZobristOffset(piece))];
			}
		}
		
		return pawnsHash;
	}
	
	public int getMoveNum()
	{
		return moveNum / 2;
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
		squares = new byte[64];
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
					byte piece = PieceHelper.NONE;

					int color = PieceHelper.BLACK;
					if (((int) c) > 65 && ((int) c) < 90)
						color = PieceHelper.WHITE;

					if (c == 'k' || c == 'K')
						piece = (byte) (PieceHelper.KING | color);
					else if (c == 'q' || c == 'Q')
						piece = (byte) (PieceHelper.QUEEN | color);
					else if (c == 'r' || c == 'R')
						piece = (byte) (PieceHelper.ROOK | color);
					else if (c == 'b' || c == 'B')
						piece = (byte) (PieceHelper.BISHOP | color);
					else if (c == 'n' || c == 'N')
						piece = (byte) (PieceHelper.KNIGHT | color);
					else if (c == 'p' || c == 'P')
						piece = (byte) (PieceHelper.PAWN | color);

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
				colorToMove = string.equals("w") ? PieceHelper.WHITE : PieceHelper.BLACK; // sets color to
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
					moveNum = (short) (2 * Short.parseShort(string));
			}
		}

		zobristHash = createZobristHash();
		pawnsHash = createPawnsHash();
		repetitionIndex = 0;
		repetitionHistory[repetitionIndex] = zobristHash;

		for (int i = 0; i < 64; i++)
		{
			byte piece = squares[i];
			if (piece != PieceHelper.NONE)
			{
				byte color = PieceHelper.getColor(piece);
				material[color*2] += PieceHelper.getPieceSquareValue(piece, i, false);
				material[color*2 + 1] += PieceHelper.getPieceSquareValue(piece, i, true);
			}
		}
		
		if (bitBoards == null)
			bitBoards = new Bitboards(this);
		else
			bitBoards.createBitboards(this);

		isCachedCheckValid = false;
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

		newFEN += " " + (colorToMove == PieceHelper.WHITE ? "w" : "b") + " "; // color to move
		if ((castlingRights & WHITE_KING_CASTLING) != 0)
			newFEN += "K";
		if ((castlingRights & WHITE_QUEEN_CASTLING) != 0)
			newFEN += "Q";
		if ((castlingRights & BLACK_KING_CASTLING) != 0)
			newFEN += "k";
		if ((castlingRights & BLACK_QUEEN_CASTLING) != 0)
			newFEN += "q";
		if (castlingRights == 0) newFEN += "-";
		newFEN += " ";
		if (enPassantIndex == -1)
			newFEN += "- "; // add dash if no en passant index
		else
		{
			int index = enPassantIndex;
			newFEN += Utils.getSquareName(index) + " ";
		}
		if (includeMoveNums)
			newFEN += halfMoves + " " + getMoveNum(); // add moves numbers if includeMoveNums
																				// is true

		newFEN.trim();

		return newFEN; // return the created FEN string
	}
	
	public void flipBoard()
	{
		byte [] newSquaresArray = new byte[64];
		
		for (int i = 0; i < 64; i++)
		{
			int flippedIndex = i^56;
			newSquaresArray[flippedIndex] = squares[i];
			if (newSquaresArray[flippedIndex] != PieceHelper.NONE)
			{
				newSquaresArray[flippedIndex] ^= PieceHelper.BLACK;
			}
		}
		byte whiteCastling = (byte) (castlingRights & Board.WHITE_CASTLING);
		byte blackCastling = (byte) (castlingRights & Board.BLACK_CASTLING);
		byte newCastling = (byte) (whiteCastling << 1 | blackCastling >> 1);
		squares = newSquaresArray;
		castlingRights = newCastling;
		if (enPassantIndex != -1) enPassantIndex = (short) (enPassantIndex ^ 56);
		colorToMove = (byte) (colorToMove ^ PieceHelper.BLACK);
		
		short [] newMaterial = new short[]{material[2], material[3], material[0], material[1]};
		material = newMaterial;
		
		zobristHash = createZobristHash();
		bitBoards.createBitboards(this);
	}
	
	public String toString()
	{
		String output = "";
		for (int rank = 8; rank > 0; rank--)
		{
			output += ("\n+---+---+---+---+---+---+---+---+\n| ");
			for (int file = 1; file <= 8; file++)
			{
				int square = Utils.getSquareIndexFromRankFile(rank, file);
				if (squares[square] != PieceHelper.NONE) // if piece exists
				{
					output += (PieceHelper.getPieceSymbol(squares[square]) + " | "); // print the piece symbol
				}
				else
					output += ("  | "); // or just print an empty space
			}
		}
		output += ("\n+---+---+---+---+---+---+---+---+\n");
		return output;
	}
	
	/**
	 * Prints the representation of the board onto the screen
	 * 
	 */
	public void printBoard()
	{
		System.out.println(toString());
	}
}