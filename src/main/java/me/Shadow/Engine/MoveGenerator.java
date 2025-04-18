package me.Shadow.Engine;

public class MoveGenerator
{
	public static final int MAXIMUM_LEGAL_MOVES = 218;
	public static final long ALL_ONE_BITS = ~0L;
	
	public static final long FIRST_RANK = 0xFFL;
	public static final long EIGHTH_RANK = FIRST_RANK << 56;
	public static final long FOURTH_RANK = FIRST_RANK << 24;
	public static final long FIFTH_RANK = FIRST_RANK << 32;
	public static final long A_FILE = 0x0101010101010101L;
	public static final long H_FILE = A_FILE << 7;
	
	public static final long CAPTURES_ONLY = 0;
	public static final long ALL_MOVES = ALL_ONE_BITS;
	static final long VALID_MOVE_FLAG = 1L;
	
	private final Board board;
	private final Bitboards bitBoards;
	
	byte friendlyColor;
	byte enemyColor;
	int friendlyKingIndex;
	long friendlyKingBoard;
	
	long numChecks;
	
	long friendlyPiecesBitboard;
	long enemyPiecesBitboard;
	long allPiecesBitboard;
	long enemyAttackMap;
	long enemyPawnAttackMap;
	
	long checkRaysMask;
	long pinRaysMask;
	final long [] pinTable;
	long filteredMovesMask;
	long capturesOnly;
	
	final short [] moves;
	int currentIndex;
	
	public MoveGenerator(Board board, short [] movesList)
	{
		this.board = board;
		bitBoards = board.bitBoards;
		moves = movesList;
		pinTable = new long[64];
	}
	
	public int generateMoves(long capturesOnly, int startIndex)
	{
		analyzePosition();
		
		this.capturesOnly = capturesOnly;
		
		filteredMovesMask = enemyPiecesBitboard | capturesOnly;
		
		currentIndex = startIndex;
		generateKingMoves();
		if (numChecks < 2)
		{
			generateSliderMoves();
			generateKnightMoves();
			generatePawnMoves();
		}
	
		return currentIndex - startIndex;	// number of moves generated
	}
	
	private void analyzePosition()
	{
		friendlyColor = board.colorToMove;
		enemyColor = (byte) (friendlyColor ^ PieceHelper.BLACK);
		friendlyKingBoard = bitBoards.pieceBoards[PieceHelper.KING + friendlyColor];
		friendlyKingIndex = Bitboards.getLSB(friendlyKingBoard);
		
		numChecks = 0;
		
		friendlyPiecesBitboard = bitBoards.colorBoards[friendlyColor];
		enemyPiecesBitboard = bitBoards.colorBoards[enemyColor];
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
			addMove(MoveHelper.createMove(friendlyKingIndex, targetSquare, MoveHelper.NO_FLAG), VALID_MOVE_FLAG);
		}
		
		// castling moves
		if ((board.castlingRights & (Board.WHITE_CASTLING << friendlyColor)) != 0 && !(capturesOnly == 0 || numChecks != 0))
		{			
			if ((board.castlingRights & (Board.WHITE_KING_CASTLING << friendlyColor)) != 0)
			{
				long castleLegalMask = enemyAttackMap | allPiecesBitboard;
				castleLegalMask &= PrecomputedData.WHITE_KINGSIDE_CASTLING_CLEAR_MASK << 56*friendlyColor;
				addMove(MoveHelper.createMove(friendlyKingIndex, friendlyKingIndex + 2, MoveHelper.CASTLING_FLAG), ((castleLegalMask | -castleLegalMask) >>> 63) ^ 1);
			}
			
			if ((board.castlingRights & (Board.WHITE_QUEEN_CASTLING << friendlyColor)) != 0)
			{
				long castleLegalMask = 0;
				castleLegalMask |= allPiecesBitboard & (PrecomputedData.WHITE_QUEENSIDE_CASTLING_CLEAR_MASK << 56*friendlyColor);
				castleLegalMask |= enemyAttackMap & (PrecomputedData.WHITE_QUEENSIDE_CASTLING_SAFE_MASK << 56*friendlyColor);		
				addMove(MoveHelper.createMove(friendlyKingIndex, friendlyKingIndex - 2, MoveHelper.CASTLING_FLAG), ((castleLegalMask | -castleLegalMask) >>> 63) ^ 1);
			}
		}
	}
	
	
	private void generateSliderMoves()
	{
		long orthoSlidersBitboard = bitBoards.getOrthogonalSliders(friendlyColor);
		long diagSlidersBitboard = bitBoards.getDiagonalSliders(friendlyColor);
		final long allPossibleMoves = (~friendlyPiecesBitboard) & checkRaysMask & filteredMovesMask;
				
		while (orthoSlidersBitboard != 0)
		{
			final int sliderSquare = Bitboards.getLSB(orthoSlidersBitboard);
			orthoSlidersBitboard = Bitboards.toggleBit(orthoSlidersBitboard, sliderSquare);
			
			long rookMoves = PrecomputedMagicNumbers.getRookMoves(sliderSquare, allPiecesBitboard) & allPossibleMoves;
			
			// check if pinned, and AND with pinTable if so in one go
			rookMoves &= (~(~((pinRaysMask >>> sliderSquare) & 1L) + 1) | pinTable[sliderSquare]);
			
			while (rookMoves != 0)
			{
				final int targetSquare = Bitboards.getLSB(rookMoves);
				rookMoves = Bitboards.toggleBit(rookMoves, targetSquare);
				addMove(MoveHelper.createMove(sliderSquare, targetSquare, MoveHelper.NO_FLAG), VALID_MOVE_FLAG);
			}
		}
		
		while (diagSlidersBitboard != 0)
		{
			final int sliderSquare = Bitboards.getLSB(diagSlidersBitboard);
			diagSlidersBitboard = Bitboards.toggleBit(diagSlidersBitboard, sliderSquare);
			
			long bishopMoves = PrecomputedMagicNumbers.getBishopMoves(sliderSquare, allPiecesBitboard) & allPossibleMoves;
			
			// check if pinned, and AND with pinTable if so in one go
			bishopMoves &= (~(~((pinRaysMask >>> sliderSquare) & 1L) + 1) | pinTable[sliderSquare]);
			
			while (bishopMoves != 0)
			{
				final int targetSquare = Bitboards.getLSB(bishopMoves);
				bishopMoves = Bitboards.toggleBit(bishopMoves, targetSquare);
				addMove(MoveHelper.createMove(sliderSquare, targetSquare, MoveHelper.NO_FLAG), VALID_MOVE_FLAG);
			}
		}
	}
	
	private void generateKnightMoves()
	{
		// ignore pinned knights because they are always absolutely pinned
		long knightsBitboard = bitBoards.pieceBoards[PieceHelper.KNIGHT + friendlyColor] & (~pinRaysMask);
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
				addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.NO_FLAG), VALID_MOVE_FLAG);
			}
		}
	}
	
	private void generatePawnMoves()
	{
		final long promotionRank = EIGHTH_RANK >>> 56*friendlyColor;
		final long doublePushTargetRank = FOURTH_RANK << 8*friendlyColor;
		
		final long pawnsBitboard = bitBoards.pieceBoards[PieceHelper.PAWN + friendlyColor];
		
		// dont AND with check mask yet because perhaps a double push is legal
		long singlePushSquares = Long.rotateLeft(pawnsBitboard, 8 - 16*friendlyColor) & (~allPiecesBitboard);
		
		// single push square needs to be a move as well to ensure that pawn does not hop over a piece
		long doublePushSquares = Long.rotateLeft(singlePushSquares, 8 - 16*friendlyColor) & doublePushTargetRank & (~allPiecesBitboard) & checkRaysMask & filteredMovesMask;
		
		// replace single push promoting moves with promotions, and now AND with check mask
		long singlePushAndPromoting = singlePushSquares & promotionRank & checkRaysMask;
		singlePushSquares &= ~promotionRank & checkRaysMask & filteredMovesMask;
		
		final long captureLeftMask = ~(A_FILE << friendlyColor * 7);
		final long captureRightMask = ~(H_FILE >>> friendlyColor * 7);
		
		long captureLeftSquares = Long.rotateLeft(pawnsBitboard & captureLeftMask, 7 - 14*friendlyColor) & enemyPiecesBitboard & checkRaysMask;
		long captureRightSquares = Long.rotateLeft(pawnsBitboard & captureRightMask, 9 - 18*friendlyColor) & enemyPiecesBitboard & checkRaysMask;
		
		long captureLeftPromoting = captureLeftSquares & promotionRank;
		long captureRightPromoting = captureRightSquares & promotionRank;
		captureLeftSquares &= ~promotionRank;
		captureRightSquares &= ~promotionRank;
		
		while (singlePushSquares != 0)
		{
			final int targetSquare = Bitboards.getLSB(singlePushSquares);
			singlePushSquares = Bitboards.toggleBit(singlePushSquares, targetSquare);
			final int startSquare = targetSquare - 8 + 16 * friendlyColor;
			
			addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.NO_FLAG), isValidPinnedMove(startSquare, targetSquare));
		}
		
		while (doublePushSquares != 0)
		{
			final int targetSquare = Bitboards.getLSB(doublePushSquares);
			doublePushSquares = Bitboards.toggleBit(doublePushSquares, targetSquare);
			final int startSquare = targetSquare - 16 + 32 * friendlyColor;
			
			addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.PAWN_DOUBLE_PUSH_FLAG), isValidPinnedMove(startSquare, targetSquare));
		}
		
		while (captureLeftSquares != 0)
		{
			final int targetSquare = Bitboards.getLSB(captureLeftSquares);
			captureLeftSquares = Bitboards.toggleBit(captureLeftSquares, targetSquare);
			final int startSquare = targetSquare - 7 + 14 * friendlyColor;
			
			addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.NO_FLAG), isValidPinnedMove(startSquare, targetSquare));
		}
		
		while (captureRightSquares != 0)
		{
			final int targetSquare = Bitboards.getLSB(captureRightSquares);
			captureRightSquares = Bitboards.toggleBit(captureRightSquares, targetSquare);
			final int startSquare = targetSquare - 9 + 18 * friendlyColor;
			
			addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.NO_FLAG), isValidPinnedMove(startSquare, targetSquare));
		}
		
		while (singlePushAndPromoting != 0)
		{
			final int targetSquare = Bitboards.getLSB(singlePushAndPromoting);
			singlePushAndPromoting = Bitboards.toggleBit(singlePushAndPromoting, targetSquare);
			final int startSquare = targetSquare - 8 + 16 * friendlyColor;
			
			// if promoting straight forward, can only be absolutely pinned
			addPromotionMoves(startSquare, targetSquare, ((pinRaysMask >>> startSquare) & 1) - 1);
		}
		
		while (captureLeftPromoting != 0)
		{
			final int targetSquare = Bitboards.getLSB(captureLeftPromoting);
			captureLeftPromoting = Bitboards.toggleBit(captureLeftPromoting, targetSquare);
			final int startSquare = targetSquare - 7 + 14 * friendlyColor;
			
			addPromotionMoves(startSquare, targetSquare, isValidPinnedMove(startSquare, targetSquare));
		}
		
		while (captureRightPromoting != 0)
		{
			final int targetSquare = Bitboards.getLSB(captureRightPromoting);
			captureRightPromoting = Bitboards.toggleBit(captureRightPromoting, targetSquare);
			final int startSquare = targetSquare - 9 + 18 * friendlyColor;
			
			addPromotionMoves(startSquare, targetSquare, isValidPinnedMove(startSquare, targetSquare));
		}
		
		if (board.enPassantIndex != -1)
		{
			final int targetSquare = board.enPassantIndex;
			final long enPassantSquareBitboard = 1L << targetSquare;
			final int captureSquare = board.enPassantIndex - 8 + 16 * friendlyColor;
			
			// if the enemy pawn is part of checkraysmask, either king is not in check, or this pawn is a checker
			if (((1L << captureSquare) & checkRaysMask) != 0)
			{
				long enPassantPawns = Bitboards.shift(enPassantSquareBitboard & captureRightMask, -7 + 14*friendlyColor) & pawnsBitboard;
				enPassantPawns = enPassantPawns | (Bitboards.shift(enPassantSquareBitboard & captureLeftMask, -9 + 18*friendlyColor) & pawnsBitboard);
				
				while (enPassantPawns != 0)
				{
					final int startSquare = Bitboards.getLSB(enPassantPawns);
					enPassantPawns = Bitboards.toggleBit(enPassantPawns, startSquare);
					
					if (isValidPinnedMove(startSquare, targetSquare) != 0 && isEnPassantLegal(startSquare, captureSquare))
					{
						addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.EN_PASSANT_CAPTURE_FLAG), VALID_MOVE_FLAG);
					}
				}
			}
		}
	}
	
	private void addPromotionMoves(final int startSquare, final int targetSquare, long validityFlags)
	{
		addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.PROMOTION_QUEEN_FLAG), validityFlags);
		
		addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.PROMOTION_ROOK_FLAG), validityFlags & capturesOnly);
		addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.PROMOTION_BISHOP_FLAG), validityFlags & capturesOnly);
		addMove(MoveHelper.createMove(startSquare, targetSquare, MoveHelper.PROMOTION_KNIGHT_FLAG), validityFlags & capturesOnly);
	}
	
	private boolean isEnPassantLegal(final int startSquare, final int captureSquare)
	{
		if ((friendlyKingIndex & 56) != (startSquare & 56)) return true; // king is not on same rank
		
		final long startSquareBitboard = 1l << startSquare;
		final long captureSquareBitboard = 1l << captureSquare;
				
		long rankMask = (FIRST_RANK << (friendlyKingIndex & 56));	// mask of all squares on this rank
		
		long orthoSliders = bitBoards.getOrthogonalSliders(enemyColor) & rankMask;
		if (orthoSliders == 0) return true;
		
		long blockers = (allPiecesBitboard ^ (startSquareBitboard | captureSquareBitboard)) & rankMask;
		
		long squaresBelowMask = friendlyKingBoard ^ (friendlyKingBoard - 1);
		
		long firstRightBlocker = Long.lowestOneBit(blockers & ~squaresBelowMask);
		long firstLeftBlocker = Long.highestOneBit(blockers & squaresBelowMask & ~friendlyKingBoard);
		return (((firstRightBlocker | firstLeftBlocker) & orthoSliders) == 0);
	}
	
	public void addMove(final short move, long validityFlags)
	{
		moves[currentIndex] = move;
		validityFlags = (validityFlags | -validityFlags) >>> 63;
		//validityFlags = (validityFlags != 0 ? 1 : 0);
		currentIndex += validityFlags;
	}
	
	private long isValidPinnedMove(int startSquare, int targetSquare)
	{
	    long isPinned = (pinRaysMask >>> startSquare) & 1L;
	    return ((isPinned ^ 1L) | (((pinTable[startSquare] >>> targetSquare) & 1L) & isPinned));
	}
	
	private void calculateAllAttacks()
	{
		final long orthoSliders = bitBoards.getOrthogonalSliders(enemyColor);
		final long diagSliders = bitBoards.getDiagonalSliders(enemyColor);
		final long notKingBoard = allPiecesBitboard ^ friendlyKingBoard;
		
		long enemySlidingAttackMap = createSlidingAttackMap(orthoSliders, notKingBoard, true);
		enemySlidingAttackMap |= createSlidingAttackMap(diagSliders, notKingBoard, false);
		
		long orthoRays = PrecomputedMagicNumbers.getRookMoves(friendlyKingIndex, enemyPiecesBitboard);
		long diagRays = PrecomputedMagicNumbers.getBishopMoves(friendlyKingIndex, enemyPiecesBitboard);
		
		createChecksPins(orthoRays & orthoSliders, orthoRays, true);
		createChecksPins(diagRays & diagSliders, diagRays, false);
				
		long enemyKnightAttacks = 0;
		long enemyKnightsBitboard = bitBoards.pieceBoards[PieceHelper.KNIGHT | enemyColor];
		
		// only possible for one knight to be checking the king
		long knightAttackingKing = PrecomputedData.KNIGHT_MOVES[friendlyKingIndex] & enemyKnightsBitboard;
		
		// set the checking square if this piece is giving a check
		checkRaysMask |= knightAttackingKing;
		
		while (enemyKnightsBitboard != 0)
		{
			final int knightIndex = Bitboards.getLSB(enemyKnightsBitboard);
			enemyKnightsBitboard = Bitboards.toggleBit(enemyKnightsBitboard, knightIndex);
			enemyKnightAttacks |= PrecomputedData.KNIGHT_MOVES[knightIndex];
		}
		
		// pawn attacks next
		final long enemyPawnsBitboard = bitBoards.pieceBoards[PieceHelper.PAWN | enemyColor];
		enemyPawnAttackMap = 0;
		
		// mask out edge files and shift pawns according to color along the board
		enemyPawnAttackMap = Long.rotateLeft(enemyPawnsBitboard & (~A_FILE), 7 - 16*enemyColor);
		enemyPawnAttackMap |= Long.rotateLeft(enemyPawnsBitboard & (~H_FILE), 9 - 16*enemyColor);
		
		// figure out where the attack is coming from
		long potentialPawnLocations = PrecomputedData.getPawnCaptures(friendlyKingIndex, friendlyColor);
		checkRaysMask |= potentialPawnLocations & enemyPawnsBitboard;
		
		final int enemyKingIndex = Bitboards.getLSB(bitBoards.pieceBoards[PieceHelper.KING | enemyColor]);
		final long enemyKingMoves = PrecomputedData.KING_MOVES[enemyKingIndex];
		
		enemyAttackMap = enemySlidingAttackMap | enemyKnightAttacks | enemyPawnAttackMap | enemyKingMoves;
		
		numChecks = Long.bitCount(checkRaysMask & enemyPiecesBitboard);
		
		// numChecks == 0 -> -1 (all 1s), numChecks != 0 -> 0
		checkRaysMask |= (((numChecks | -numChecks) >>> 63) - 1);
	}
	
	public long createSlidingAttackMap(long sliders, long notKingBoard, boolean orthogonal)
	{
		long enemySlidingAttackMap = 0;
		while (sliders != 0)
		{
			final int sliderSquare = Bitboards.getLSB(sliders);
			sliders = Bitboards.toggleBit(sliders, sliderSquare);
			long moves = PrecomputedMagicNumbers.getSliderMoves(sliderSquare, notKingBoard, orthogonal);
			
			enemySlidingAttackMap |= moves;
		}
		return enemySlidingAttackMap;
	}
	
	public void createChecksPins(long sliders, long kingRays, boolean orthogonal)
	{
		while (sliders != 0)
		{
			final long sliderBoard = Long.lowestOneBit(sliders);
			final int sliderSquare = Bitboards.getLSB(sliders);
			sliders = Bitboards.toggleBit(sliders, sliderSquare);
			
			long returnRay = PrecomputedMagicNumbers.getSliderMoves(sliderSquare, enemyPiecesBitboard | friendlyKingBoard, orthogonal) & kingRays;
			
			int friendlies = Long.bitCount(returnRay & friendlyPiecesBitboard);
			if (friendlies == 0)
			{
				checkRaysMask |= returnRay | sliderBoard;
			}
			else if (friendlies == 1)
			{
				int index = Bitboards.getLSB(returnRay & friendlyPiecesBitboard);
				pinTable[index] = returnRay | sliderBoard;
				pinRaysMask |= returnRay | sliderBoard;
			}
		}
	}
	
	public boolean inCheck()
	{
		return numChecks > 0;
	}
}
