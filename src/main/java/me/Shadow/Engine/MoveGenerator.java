package me.Shadow.Engine;

import java.util.Arrays;

public class MoveGenerator
{
	private static final int MAXIMUM_LEGAL_MOVES = 218;
	public static final long ALL_ONE_BITS = ~0L;
	
	public static final long FIRST_RANK = 0xFFL;
	public static final long EIGHTH_RANK = FIRST_RANK << 56;
	public static final long FOURTH_RANK = FIRST_RANK << 24;
	public static final long FIFTH_RANK = FIRST_RANK << 32;
	public static final long A_FILE = 0x0101010101010101L;
	public static final long H_FILE = A_FILE << 7;
	
	private Board board;
	private Bitboards bitBoards;
	
	int friendlyColor;
	int enemyColor;
	int friendlyKingIndex;
	
	public boolean inCheck;
	boolean doubleCheck;
	
	long friendlyPiecesBitboard;
	long enemyPiecesBitboard;
	long allPiecesBitboard;
	long enemyAttackMap;
	long enemyPawnAttackMap;
	
	long checkRaysMask;
	long pinRaysMask;
	long filteredMovesMask;
	boolean capturesOnly;
	
	final short [] moves = new short[MAXIMUM_LEGAL_MOVES];
	int currentIndex;
	
	public MoveGenerator(Board board)
	{
		this.board = board;
		bitBoards = board.bitBoards;
	}
	
	public short[] generateMoves(boolean capturesOnly)
	{
		analyzePosition();
		
		this.capturesOnly = capturesOnly;
		if (capturesOnly) filteredMovesMask = enemyPiecesBitboard;
		else filteredMovesMask = ALL_ONE_BITS; // all bits set to 1
		
		//moves = new short[MAXIMUM_LEGAL_MOVES];
		currentIndex = 0;
		generateKingMoves();
		if (!doubleCheck)
		{
			generateSliderMoves();
			generateKnightMoves();
			generatePawnMoves();
		}
	
		return Arrays.copyOf(moves, currentIndex);	// number of moves generated
	}
	
	private void analyzePosition()
	{
		friendlyColor = board.boardInfo.isWhiteToMove() ? PieceHelper.WHITE_PIECE : PieceHelper.BLACK_PIECE;
		enemyColor = friendlyColor ^ PieceHelper.BLACK_PIECE;
		
		friendlyKingIndex = Bitboards.getLSB(bitBoards.pieceBoards[PieceHelper.KING | friendlyColor]);
		
		inCheck = doubleCheck = false;
		
		friendlyPiecesBitboard = bitBoards.colorBoards[friendlyColor >>> 3];
		enemyPiecesBitboard = bitBoards.colorBoards[enemyColor >>> 3];
		allPiecesBitboard = friendlyPiecesBitboard | enemyPiecesBitboard;
		
		checkRaysMask = pinRaysMask = 0;
		
		calculateAllAttacks();
	}
	
	private void generateKingMoves()
	{
		long legalMoves = PrecomputedData.KING_MOVES[friendlyKingIndex] & (~friendlyPiecesBitboard) & (~enemyAttackMap);
		legalMoves &= filteredMovesMask;
		while (legalMoves != 0)
		{
			final int targetSquare = Bitboards.getLSB(legalMoves);
			legalMoves = Bitboards.toggleBit(legalMoves, targetSquare);
			addMove(MoveHelper.createMove(friendlyKingIndex, targetSquare, MoveHelper.NO_FLAG));
		}
		
		// castling moves
		if (!(capturesOnly || inCheck))
		{
			final boolean isWhite = friendlyColor == PieceHelper.WHITE_PIECE;
			final byte castlingRights = board.boardInfo.getCastlingRights();
			
			if ((castlingRights & (isWhite ? BoardInfo.WHITE_KING_CASTLING : BoardInfo.BLACK_KING_CASTLING)) != 0)
			{
				long castleLegalMask = enemyAttackMap | allPiecesBitboard;
				castleLegalMask &= isWhite ? PrecomputedData.WHITE_KINGSIDE_CASTLING_CLEAR_MASK : PrecomputedData.BLACK_KINGSIDE_CASTLING_CLEAR_MASK;
				if (castleLegalMask == 0)
				{
					addMove(MoveHelper.createMove(friendlyKingIndex, friendlyKingIndex + 2, MoveHelper.CASTLING_FLAG));
				}
			}
			
			if ((castlingRights & (isWhite ? BoardInfo.WHITE_QUEEN_CASTLING : BoardInfo.BLACK_QUEEN_CASTLING)) != 0)
			{
				long castleLegalMask = 0;
				castleLegalMask |= allPiecesBitboard & (isWhite ? PrecomputedData.WHITE_QUEENSIDE_CASTLING_CLEAR_MASK : PrecomputedData.BLACK_QUEENSIDE_CASTLING_CLEAR_MASK);
				castleLegalMask |= enemyAttackMap & (isWhite ? PrecomputedData.WHITE_QUEENSIDE_CASTLING_SAFE_MASK : PrecomputedData.BLACK_QUEENSIDE_CASTLING_SAFE_MASK);
				if (castleLegalMask == 0)
				{
					addMove(MoveHelper.createMove(friendlyKingIndex, friendlyKingIndex - 2, MoveHelper.CASTLING_FLAG));
				}
			}
		}
	}
	
	
	private void generateSliderMoves()
	{
		long orthoSlidersBitboard = bitBoards.getOrthogonalSliders(friendlyColor);
		long diagSlidersBitboard = bitBoards.getDiagonalSliders(friendlyColor);
		final long allPossibleMoves = (~friendlyPiecesBitboard) & checkRaysMask & filteredMovesMask;
		
		if (inCheck)
		{
			orthoSlidersBitboard &= ~pinRaysMask;
			diagSlidersBitboard &= ~pinRaysMask;
		}
		
		while (orthoSlidersBitboard != 0)
		{
			final int sliderSquare = Bitboards.getLSB(orthoSlidersBitboard);
			orthoSlidersBitboard = Bitboards.toggleBit(orthoSlidersBitboard, sliderSquare);
			
			long rookMoves = PrecomputedMagicNumbers.getRookMoves(sliderSquare, allPiecesBitboard) & allPossibleMoves;
			if (isPiecePinned(sliderSquare))
			{
				rookMoves &= PrecomputedData.rayAlignMask[(sliderSquare << 6) + friendlyKingIndex];
			}
			
			while (rookMoves != 0)
			{
				final int targetSquare = Bitboards.getLSB(rookMoves);
				rookMoves = Bitboards.toggleBit(rookMoves, targetSquare);
				addMove(MoveHelper.createMove(sliderSquare, targetSquare, MoveHelper.NO_FLAG));
			}
		}
		
		while (diagSlidersBitboard != 0)
		{
			final int sliderSquare = Bitboards.getLSB(diagSlidersBitboard);
			diagSlidersBitboard = Bitboards.toggleBit(diagSlidersBitboard, sliderSquare);
			
			long bishopMoves = PrecomputedMagicNumbers.getBishopMoves(sliderSquare, allPiecesBitboard) & allPossibleMoves;
			if (isPiecePinned(sliderSquare))
			{
				bishopMoves &= PrecomputedData.rayAlignMask[(sliderSquare << 6) + friendlyKingIndex];
			}
			
			while (bishopMoves != 0)
			{
				final int targetSquare = Bitboards.getLSB(bishopMoves);
				bishopMoves = Bitboards.toggleBit(bishopMoves, targetSquare);
				addMove(MoveHelper.createMove(sliderSquare, targetSquare, MoveHelper.NO_FLAG));
			}
		}
	}
	
	private void generateKnightMoves()
	{
		// ignore pinned knights because they are always absolutely pinned
		long knightsBitboard = bitBoards.pieceBoards[PieceHelper.KNIGHT | friendlyColor] & (~pinRaysMask);
		while (knightsBitboard != 0)
		{
			final int startSquare = Bitboards.getLSB(knightsBitboard);
			knightsBitboard = Bitboards.toggleBit(knightsBitboard, startSquare);
			
			long legalMoves = PrecomputedData.KNIGHT_MOVES[startSquare] & (~friendlyPiecesBitboard);
			legalMoves &= filteredMovesMask & checkRaysMask;
			
			while (legalMoves != 0)
			{
				final int targetSquare = Bitboards.getLSB(legalMoves);
				legalMoves = Bitboards.toggleBit(legalMoves, targetSquare);
				addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.NO_FLAG));
			}
		}
	}
	
	private void generatePawnMoves()
	{
		final int direction = friendlyColor == PieceHelper.WHITE_PIECE ? 1 : -1;
		
		final long promotionRank = (friendlyColor == PieceHelper.WHITE_PIECE) ? EIGHTH_RANK : FIRST_RANK;
		final long doublePushTargetRank = (friendlyColor == PieceHelper.WHITE_PIECE) ? FOURTH_RANK : FIFTH_RANK;
		
		
		final long pawnsBitboard = bitBoards.pieceBoards[PieceHelper.PAWN | friendlyColor];
		// dont AND with check mask yet because perhaps a double push is legal
		long singlePushSquares = Bitboards.shift(pawnsBitboard, (direction * 8)) & (~allPiecesBitboard);
		
		// single push square needs to be a move as well to ensure that pawn does not hop over a piece
		long doublePushSquares = Bitboards.shift(singlePushSquares, (direction * 8)) & doublePushTargetRank & (~allPiecesBitboard) & checkRaysMask;
		
		// replace single push promoting moves with promotions, and now AND with check mask
		long singlePushAndPromoting = singlePushSquares & promotionRank & checkRaysMask;
		singlePushSquares &= ~promotionRank & checkRaysMask;
		
		final long captureLeftMask = (friendlyColor == PieceHelper.WHITE_PIECE) ? ~A_FILE : ~H_FILE;
		final long captureRightMask = (friendlyColor == PieceHelper.WHITE_PIECE) ? ~H_FILE : ~A_FILE;
		
		long captureLeftSquares = Bitboards.shift(pawnsBitboard & captureLeftMask, direction * 7) & enemyPiecesBitboard & checkRaysMask;
		long captureRightSquares = Bitboards.shift(pawnsBitboard & captureRightMask, direction * 9) & enemyPiecesBitboard & checkRaysMask;
		
		long captureLeftPromoting = captureLeftSquares & promotionRank;
		long captureRightPromoting = captureRightSquares & promotionRank;
		captureLeftSquares &= ~promotionRank;
		captureRightSquares &= ~promotionRank;
		
		if (!capturesOnly)
		{
			while (singlePushSquares != 0)
			{
				final int targetSquare = Bitboards.getLSB(singlePushSquares);
				singlePushSquares = Bitboards.toggleBit(singlePushSquares, targetSquare);
				final int startSquare = targetSquare - (direction * 8);
				
				if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[(startSquare << 6) + friendlyKingIndex] == PrecomputedData.rayAlignMask[(targetSquare << 6) + friendlyKingIndex])
				{
					addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.NO_FLAG));
				}
			}
			
			while (doublePushSquares != 0)
			{
				final int targetSquare = Bitboards.getLSB(doublePushSquares);
				doublePushSquares = Bitboards.toggleBit(doublePushSquares, targetSquare);
				final int startSquare = targetSquare - (direction * 16);
				
				if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[(startSquare << 6) + friendlyKingIndex] == PrecomputedData.rayAlignMask[(targetSquare << 6) + friendlyKingIndex])
				{
					addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.PAWN_DOUBLE_PUSH_FLAG));
				}
			}
		}
		
		while (captureLeftSquares != 0)
		{
			final int targetSquare = Bitboards.getLSB(captureLeftSquares);
			captureLeftSquares = Bitboards.toggleBit(captureLeftSquares, targetSquare);
			final int startSquare = targetSquare - (direction * 7);
			
			if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[(startSquare << 6) + friendlyKingIndex] == PrecomputedData.rayAlignMask[(targetSquare << 6) + friendlyKingIndex])
			{
				addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.NO_FLAG));
			}
		}
		
		while (captureRightSquares != 0)
		{
			final int targetSquare = Bitboards.getLSB(captureRightSquares);
			captureRightSquares = Bitboards.toggleBit(captureRightSquares, targetSquare);
			final int startSquare = targetSquare - (direction * 9);
			
			if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[(startSquare << 6) + friendlyKingIndex] == PrecomputedData.rayAlignMask[(targetSquare << 6) + friendlyKingIndex])
			{
				addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.NO_FLAG));
			}
		}
		
		while (singlePushAndPromoting != 0)
		{
			final int targetSquare = Bitboards.getLSB(singlePushAndPromoting);
			singlePushAndPromoting = Bitboards.toggleBit(singlePushAndPromoting, targetSquare);
			final int startSquare = targetSquare - (direction * 8);
			
			if (!isPiecePinned(startSquare)) // if promoting straight forward, can only be absolutely pinned
			{
				addPromotionMoves(startSquare, targetSquare);
			}
		}
		
		while (captureLeftPromoting != 0)
		{
			final int targetSquare = Bitboards.getLSB(captureLeftPromoting);
			captureLeftPromoting = Bitboards.toggleBit(captureLeftPromoting, targetSquare);
			final int startSquare = targetSquare - (direction * 7);
			
			if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[(startSquare << 6) + friendlyKingIndex] == PrecomputedData.rayAlignMask[(targetSquare << 6) + friendlyKingIndex])
			{
				addPromotionMoves(startSquare, targetSquare);
			}
		}
		
		while (captureRightPromoting != 0)
		{
			final int targetSquare = Bitboards.getLSB(captureRightPromoting);
			captureRightPromoting = Bitboards.toggleBit(captureRightPromoting, targetSquare);
			final int startSquare = targetSquare - (direction * 9);
			
			if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[(startSquare << 6) + friendlyKingIndex] == PrecomputedData.rayAlignMask[(targetSquare << 6) + friendlyKingIndex])
			{
				addPromotionMoves(startSquare, targetSquare);
			}
		}
		
		if (board.boardInfo.getEnPassantIndex() != -1)
		{
			final int targetSquare = board.boardInfo.getEnPassantIndex();
			final long enPassantSquareBitboard = 1L << targetSquare;
			final int captureSquare = board.boardInfo.getEnPassantIndex() - (direction * 8);
			
			// if the enemy pawn is part of checkraysmask, either king is not in check, or this pawn is a checker
			if (((1L << captureSquare) & checkRaysMask) != 0)
			{
				long enPassantPawns = Bitboards.shift(enPassantSquareBitboard & captureRightMask, (-direction) * 7) & pawnsBitboard;
				enPassantPawns = enPassantPawns | (Bitboards.shift(enPassantSquareBitboard & captureLeftMask, (-direction) * 9) & pawnsBitboard);
				
				while (enPassantPawns != 0)
				{
					final int startSquare = Bitboards.getLSB(enPassantPawns);
					enPassantPawns = Bitboards.toggleBit(enPassantPawns, startSquare);
					if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[(startSquare << 6) + friendlyKingIndex] == PrecomputedData.rayAlignMask[(targetSquare << 6) + friendlyKingIndex])
					{
						if (isEnPassantLegal(startSquare, captureSquare))
						{
							addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.EN_PASSANT_CAPTURE_FLAG));
						}
					}
				}
			}
		}
	}
	
	private void addPromotionMoves(final int startSquare, final int targetSquare)
	{
		addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.PROMOTION_QUEEN_FLAG));
		
		if (!capturesOnly)
		{
			addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.PROMOTION_ROOK_FLAG));
			addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.PROMOTION_BISHOP_FLAG));
			addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.PROMOTION_KNIGHT_FLAG));
		}
	}
	
	private boolean isEnPassantLegal(final int startSquare, final int captureSquare)
	{
		if (friendlyKingIndex / 8 != startSquare / 8) return true; // king is not on same rank
		
		final long startSquareBitboard = 1l << startSquare;
		final long captureSquareBitboard = 1l << captureSquare;
				
		long rankMask = (FIRST_RANK << ((friendlyKingIndex / 8) * 8));	// mask of all squares on this rank
		
		long orthogonalSliders = bitBoards.getOrthogonalSliders(enemyColor) & rankMask;
		
		while (orthogonalSliders != 0)
		{
			final int sliderSquare = Bitboards.getLSB(orthogonalSliders);
			orthogonalSliders = Bitboards.toggleBit(orthogonalSliders, sliderSquare);
			
			// shift bits to clear squares beyond the king or beyond the slider including the slider and king square themselves
			rankMask = (rankMask << ((Math.min(friendlyKingIndex, sliderSquare) % 8) + 1) & (rankMask >>> (8 - Math.max(friendlyKingIndex, sliderSquare) % 8)));
			// ignore the two pawns
			rankMask &= ~(startSquareBitboard | captureSquareBitboard);
			
			
			// if no other piece on the open squares then en passant is illegal
			if ((rankMask & allPiecesBitboard) == 0)
			{
				return false;
			}
		}
		
		return true;
	}
	
	public void addMove(final short move)
	{
		moves[currentIndex++] = move;
	}
	
	private boolean isPiecePinned(final int square)
	{
		return (pinRaysMask & (1L << square)) != 0;
	}
	
	private void calculateAllAttacks()
	{
		final long enemySlidingAttackMap = calculateSliderAttacks();
		
		for (int direction = 0; direction < 8; direction++)
		{
			final boolean diagonal = direction >= 4;
			final long sliders = diagonal ? bitBoards.getDiagonalSliders(enemyColor) : bitBoards.getOrthogonalSliders(enemyColor);
						
			if ((PrecomputedData.rayDirectionMask[friendlyKingIndex*8 + direction] & sliders) == 0)
			{
				// no enemy sliders of relevant type along this ray on this specific direction from the friendly king
				continue;
			}
						
			// at least one relevant slider exists
			boolean friendlyPieceFound = false;
			long potentialPinRayMask = 0;
			final int numSquares = PrecomputedData.numSquaresToEdge[friendlyKingIndex*8 + direction];
			final int directionOffset = PrecomputedData.directionOffsets[direction];
			
			for (int i = 1; i <= numSquares; i++)
			{
				final int newIndex = friendlyKingIndex + (directionOffset * i);
				potentialPinRayMask |= (1L << newIndex);
				
				final int pieceInfo = board.squares[newIndex];
				if (pieceInfo != PieceHelper.NONE)
				{
					if (PieceHelper.isColor(pieceInfo, friendlyColor))
					{
						// possible pinned piece found or second piece found so no pin
						if (!friendlyPieceFound) friendlyPieceFound = true;
						else break;
					}
					else
					{
						// enemy piece found so if it can attack king along this ray pin or check found
						if ((diagonal && PieceHelper.isDiagonalSlider(pieceInfo)) || (!diagonal && PieceHelper.isOrthogonalSlider(pieceInfo)))
						{
							if (friendlyPieceFound)
							{
								// found a pin
								pinRaysMask |= potentialPinRayMask;
							}
							else
							{
								// found a check
								checkRaysMask |= potentialPinRayMask;
								doubleCheck = inCheck;
								inCheck = true;
							}
							break;
						}
						else
						{
							// if it cant attack the king on this ray, cant pin or check anything
							break;
						}
					}
				}
				
				// if in double check, only king moves are legal so any more pins are meaningless
				if (doubleCheck)
				{
					break;
				}
			}
		}
				
		long enemyKnightAttacks = 0;
		final long kingBitboard = 1L << friendlyKingIndex;
		long enemyKnightsBitboard = bitBoards.pieceBoards[PieceHelper.KNIGHT | enemyColor];
		
		// only possible for one knight to be checking the king
		long knightAttackingKing = PrecomputedData.KNIGHT_MOVES[friendlyKingIndex] & enemyKnightsBitboard;
		if (knightAttackingKing != 0)
		{
			// set the checking square if this piece is giving a check
			checkRaysMask |= 1L << Bitboards.getLSB(knightAttackingKing);
			doubleCheck = inCheck;
			inCheck = true;
		}
		
		// get pregenerated knight moves if 2 or fewer knights
		if (enemyKnightsBitboard != 0 && Long.bitCount(enemyKnightsBitboard) <= 2)
		{
			enemyKnightAttacks |= PrecomputedMagicNumbers.getKnightMoves(enemyKnightsBitboard);
		}
		else
		{
			while (enemyKnightsBitboard != 0)
			{
				final int knightIndex = Bitboards.getLSB(enemyKnightsBitboard);
				enemyKnightsBitboard = Bitboards.toggleBit(enemyKnightsBitboard, knightIndex);
				enemyKnightAttacks |= PrecomputedData.KNIGHT_MOVES[knightIndex];
			}
		}
		
		// pawn attacks next
		final long enemyPawnsBitboard = bitBoards.pieceBoards[PieceHelper.PAWN | enemyColor];
		enemyPawnAttackMap = 0;
		
		// mask out edge files and shift pawns according to color along the board
		if (enemyColor == PieceHelper.WHITE_PIECE)
		{
			enemyPawnAttackMap = ((enemyPawnsBitboard & (~A_FILE)) << 7) | ((enemyPawnsBitboard & (~H_FILE)) << 9);
		}
		else
		{
			enemyPawnAttackMap = ((enemyPawnsBitboard & (~A_FILE)) >>> 9) | ((enemyPawnsBitboard & (~H_FILE)) >>> 7);
		}
				
		if ((enemyPawnAttackMap & kingBitboard) != 0)
		{
			// figure out where the attack is coming from
			doubleCheck = inCheck;
			inCheck = true;
			
			long potentialPawnLocations = 0;
			// mask out edge files and pretend the king is a pawn to see which squares it could attack
			if (friendlyColor == PieceHelper.WHITE_PIECE)
			{
				potentialPawnLocations = ((kingBitboard & (~A_FILE)) << 7) | ((kingBitboard & (~H_FILE)) << 9);
			}
			else
			{
				potentialPawnLocations = ((kingBitboard & (~A_FILE)) >>> 9) | ((kingBitboard & (~H_FILE)) >>> 7);
			}
			checkRaysMask |= potentialPawnLocations & enemyPawnsBitboard;
		}
		
		final int enemyKingIndex = Bitboards.getLSB(bitBoards.pieceBoards[PieceHelper.KING | enemyColor]);
		final long enemyKingMoves = PrecomputedData.KING_MOVES[enemyKingIndex];
		
		enemyAttackMap = enemySlidingAttackMap | enemyKnightAttacks | enemyPawnAttackMap | enemyKingMoves;
				
		if (!inCheck)
		{
			// set all bits to 1
			checkRaysMask = ALL_ONE_BITS;
		}
	}
	
	private long calculateSliderAttacks()
	{
		final long orthoSliders = bitBoards.getOrthogonalSliders(enemyColor);
		final long diagSliders = bitBoards.getDiagonalSliders(enemyColor);
		long enemySlidingAttackMap = 0;
		enemySlidingAttackMap |= sliderAttacks(orthoSliders, enemyColor, true);
		enemySlidingAttackMap |= sliderAttacks(diagSliders, enemyColor, false);
		return enemySlidingAttackMap;
	}
	
	private long sliderAttacks(long pieceBoard, final int sliderColor, final boolean orthogonal)
	{
		long slidingAttacks = 0;
		final long notKingBoard = allPiecesBitboard & ~(1L << friendlyKingIndex);
		
		while (pieceBoard != 0)
		{
			// get the square of the slider
			final int sliderSquare = Bitboards.getLSB(pieceBoard);
			pieceBoard = Bitboards.toggleBit(pieceBoard, sliderSquare);
			
			slidingAttacks |= PrecomputedMagicNumbers.getSliderMoves(sliderSquare, notKingBoard, orthogonal);
		}
		
		return slidingAttacks;
	}
}
