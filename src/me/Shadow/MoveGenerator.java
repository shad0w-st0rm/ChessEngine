package me.Shadow;

import java.util.ArrayList;
import java.util.HashMap;

public class MoveGenerator
{
	Board board;
	Bitboards bitBoards;
	
	int friendlyColor;
	int enemyColor;
	int friendlyColorIndex;
	int enemyColorIndex;
	int friendlyKingIndex;
	
	boolean inCheck;
	boolean doubleCheck;
	
	long friendlyPiecesBitboard;
	long enemyPiecesBitboard;
	long allPiecesBitboard;
	long emptySquaresBitboard;
	long enemyAttackMap;
	long enemySlidingAttackMap;
	
	long checkRaysMask;
	long pinRaysMask;
	long filteredMovesMask;
	boolean capturesOnly;
	
	public MoveGenerator(Board board)
	{
		this.board = board;
		bitBoards = board.bitBoards;
	}
	
	public Move[] generateMoves(boolean capturesOnly)
	{
		analyzePosition();
		this.capturesOnly = capturesOnly;
		if (capturesOnly) filteredMovesMask = enemyPiecesBitboard;
		else filteredMovesMask = ~0L; // all bits set to 1
		
		ArrayList<Move> moves = new ArrayList<Move>(50);	// 50 is an arbitrary number
		generateKingMoves(moves);
		if (!doubleCheck)
		{
			generateSliderMoves(moves);
			generateKnightMoves(moves);
			generatePawnMoves(moves);
		}
		
		// TODO: this sucks
		return moves.toArray(new Move[0]);
	}
	
	public void analyzePosition()
	{
		friendlyColor = board.boardInfo.isWhiteToMove() ? Piece.WHITE_PIECE : Piece.BLACK_PIECE;
		enemyColor = friendlyColor ^ Piece.BLACK_PIECE;
		friendlyColorIndex = friendlyColor >>> 3;
		enemyColorIndex = enemyColor >>> 3;
		
		friendlyKingIndex = Bitboards.getLSB(bitBoards.pieceBoards[Piece.KING | friendlyColor]);
		
		inCheck = doubleCheck = false;
		
		friendlyPiecesBitboard = bitBoards.colorBoards[friendlyColorIndex];
		enemyPiecesBitboard = bitBoards.colorBoards[enemyColorIndex];
		allPiecesBitboard = friendlyPiecesBitboard | enemyPiecesBitboard;
		emptySquaresBitboard = ~allPiecesBitboard;
		
		checkRaysMask = pinRaysMask = 0;
		
		calculateAllAttacks();
	}
	
	public void generateKingMoves(ArrayList<Move> moves)
	{
		long legalMoves = PrecomputedData.KING_MOVES[friendlyKingIndex] & (~friendlyPiecesBitboard) & (~enemyAttackMap);
		legalMoves &= filteredMovesMask;
		while (legalMoves != 0)
		{
			int targetSquare = Bitboards.getLSB(legalMoves);
			legalMoves = Bitboards.toggleBit(legalMoves, targetSquare);
			moves.add(new Move(friendlyKingIndex, targetSquare, Move.NO_FLAG));
		}
		
		if (!(capturesOnly || inCheck))
		{
			// castling moves
			if (board.boardInfo.getCastlingRights()[friendlyColorIndex * 2])
			{
				long castleLegalMask = enemyAttackMap | allPiecesBitboard;
				castleLegalMask &= friendlyColor == Piece.WHITE_PIECE ? PrecomputedData.WHITE_KINGSIDE_CASTLING_CLEAR_MASK : PrecomputedData.BLACK_KINGSIDE_CASTLING_CLEAR_MASK;
				if (castleLegalMask == 0)
				{
					moves.add(new Move(friendlyKingIndex, friendlyKingIndex + 2, Move.CASTLING_FLAG));
				}
			}
			
			if (board.boardInfo.getCastlingRights()[(friendlyColorIndex * 2) + 1])
			{
				long castleLegalMask = 0;
				castleLegalMask |= allPiecesBitboard & (friendlyColor == Piece.WHITE_PIECE ? PrecomputedData.WHITE_QUEENSIDE_CASTLING_CLEAR_MASK : PrecomputedData.BLACK_QUEENSIDE_CASTLING_CLEAR_MASK);
				castleLegalMask |= enemyAttackMap & (friendlyColor == Piece.WHITE_PIECE ? PrecomputedData.WHITE_QUEENSIDE_CASTLING_SAFE_MASK : PrecomputedData.BLACK_QUEENSIDE_CASTLING_SAFE_MASK);
				if (castleLegalMask == 0)
				{
					moves.add(new Move(friendlyKingIndex, friendlyKingIndex - 2, Move.CASTLING_FLAG));
				}
			}
		}
	}
	
	
	public void generateSliderMoves(ArrayList<Move> moves)
	{
		long orthoSlidersBitboard = bitBoards.getOrthogonalSliders(friendlyColor);
		long diagSlidersBitboard = bitBoards.getDiagonalSliders(friendlyColor);
		long allPossibleMoves = (~friendlyPiecesBitboard) & checkRaysMask & filteredMovesMask;
		
		if (inCheck)
		{
			orthoSlidersBitboard &= ~pinRaysMask;
			diagSlidersBitboard &= ~pinRaysMask;
		}
		
		while (orthoSlidersBitboard != 0)
		{
			int sliderSquare = Bitboards.getLSB(orthoSlidersBitboard);
			orthoSlidersBitboard = Bitboards.toggleBit(orthoSlidersBitboard, sliderSquare);
			
			long rookMoves = PrecomputedMagicNumbers.getRookMoves(sliderSquare, allPiecesBitboard) & allPossibleMoves;
			if (isPiecePinned(sliderSquare))
			{
				rookMoves &= PrecomputedData.rayAlignMask[sliderSquare][friendlyKingIndex];
			}
			
			while (rookMoves != 0)
			{
				int targetSquare = Bitboards.getLSB(rookMoves);
				rookMoves = Bitboards.toggleBit(rookMoves, targetSquare);
				moves.add(new Move(sliderSquare, targetSquare, Move.NO_FLAG));
			}
		}
		
		while (diagSlidersBitboard != 0)
		{
			int sliderSquare = Bitboards.getLSB(diagSlidersBitboard);
			diagSlidersBitboard = Bitboards.toggleBit(diagSlidersBitboard, sliderSquare);
			
			long bishopMoves = PrecomputedMagicNumbers.getBishopMoves(sliderSquare, allPiecesBitboard) & allPossibleMoves;
			if (isPiecePinned(sliderSquare))
			{
				bishopMoves &= PrecomputedData.rayAlignMask[sliderSquare][friendlyKingIndex];
			}
			
			while (bishopMoves != 0)
			{
				int targetSquare = Bitboards.getLSB(bishopMoves);
				bishopMoves = Bitboards.toggleBit(bishopMoves, targetSquare);
				moves.add(new Move(sliderSquare, targetSquare, Move.NO_FLAG));
			}
		}
	}
	
	public void generateKnightMoves(ArrayList<Move> moves)
	{
		// ignore pinned knights because they are always absolutely pinned
		long knightsBitboard = bitBoards.pieceBoards[Piece.KNIGHT | friendlyColor] & (~pinRaysMask);
		while (knightsBitboard != 0)
		{
			int startSquare = Bitboards.getLSB(knightsBitboard);
			knightsBitboard = Bitboards.toggleBit(knightsBitboard, startSquare);
			
			long legalMoves = PrecomputedData.KNIGHT_MOVES[startSquare] & (~friendlyPiecesBitboard);
			legalMoves &= filteredMovesMask & checkRaysMask;
			
			while (legalMoves != 0)
			{
				int targetSquare = Bitboards.getLSB(legalMoves);
				legalMoves = Bitboards.toggleBit(legalMoves, targetSquare);
				moves.add(new Move(startSquare, targetSquare, Move.NO_FLAG));
			}
		}
	}
	
	public void generatePawnMoves(ArrayList<Move> moves)
	{
		int direction = friendlyColor == Piece.WHITE_PIECE ? 1 : -1;
		long promotionRank = 0x00000000000000FFL;	// masks the first rank		
		long doublePushTargetRank = promotionRank << 24;	// fourth rank for white pieces
		if (friendlyColor == Piece.WHITE_PIECE) promotionRank <<= 56;	// promotion rank is 8th for white pieces
		if (friendlyColor == Piece.BLACK_PIECE) doublePushTargetRank <<= 8;	// target rank is 5th for black pieces
		
		long pawnsBitboard = bitBoards.pieceBoards[Piece.PAWN | friendlyColor];
		long singlePushSquares = Bitboards.shift(pawnsBitboard, (direction * 8)) & emptySquaresBitboard;
		
		// single push square needs to be a move as well to ensure that pawn does not hop over a piece
		long doublePushSquares = Bitboards.shift(singlePushSquares, (direction * 8)) & doublePushTargetRank & emptySquaresBitboard & checkRaysMask;
		
		long singlePushAndPromoting = singlePushSquares & promotionRank & checkRaysMask;
		singlePushSquares &= ~promotionRank & checkRaysMask;
		
		
		
		long aFileMask = 0x0101010101010101L;
		long hFileMask = aFileMask << 7;
		long captureLeftMask = (friendlyColor == Piece.WHITE_PIECE) ? ~aFileMask : ~hFileMask;
		long captureRightMask = (friendlyColor == Piece.WHITE_PIECE) ? ~hFileMask : ~aFileMask;
		
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
				int targetSquare = Bitboards.getLSB(singlePushSquares);
				singlePushSquares = Bitboards.toggleBit(singlePushSquares, targetSquare);
				int startSquare = targetSquare - (direction * 8);
				
				if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[startSquare][friendlyKingIndex] == PrecomputedData.rayAlignMask[targetSquare][friendlyKingIndex])
				{
					moves.add(new Move(startSquare, targetSquare, Move.NO_FLAG));
				}
			}
			
			while (doublePushSquares != 0)
			{
				int targetSquare = Bitboards.getLSB(doublePushSquares);
				doublePushSquares = Bitboards.toggleBit(doublePushSquares, targetSquare);
				int startSquare = targetSquare - (direction * 16);
				
				if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[startSquare][friendlyKingIndex] == PrecomputedData.rayAlignMask[targetSquare][friendlyKingIndex])
				{
					moves.add(new Move(startSquare, targetSquare, Move.PAWN_DOUBLE_PUSH_FLAG));
				}
			}
		}
		
		while (captureLeftSquares != 0)
		{
			int targetSquare = Bitboards.getLSB(captureLeftSquares);
			captureLeftSquares = Bitboards.toggleBit(captureLeftSquares, targetSquare);
			int startSquare = targetSquare - (direction * 7);
			
			if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[startSquare][friendlyKingIndex] == PrecomputedData.rayAlignMask[targetSquare][friendlyKingIndex])
			{
				moves.add(new Move(startSquare, targetSquare, Move.NO_FLAG));
			}
		}
		
		while (captureRightSquares != 0)
		{
			int targetSquare = Bitboards.getLSB(captureRightSquares);
			captureRightSquares = Bitboards.toggleBit(captureRightSquares, targetSquare);
			int startSquare = targetSquare - (direction * 9);
			
			if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[startSquare][friendlyKingIndex] == PrecomputedData.rayAlignMask[targetSquare][friendlyKingIndex])
			{
				moves.add(new Move(startSquare, targetSquare, Move.NO_FLAG));
			}
		}
		
		while (singlePushAndPromoting != 0)
		{
			int targetSquare = Bitboards.getLSB(singlePushAndPromoting);
			singlePushAndPromoting = Bitboards.toggleBit(singlePushAndPromoting, targetSquare);
			int startSquare = targetSquare - (direction * 8);
			
			if (!isPiecePinned(startSquare)) // if promoting straight forward, can only be absolutely pinned
			{
				addPromotionMoves(moves, startSquare, targetSquare);
			}
		}
		
		while (captureLeftPromoting != 0)
		{
			int targetSquare = Bitboards.getLSB(captureLeftPromoting);
			captureLeftPromoting = Bitboards.toggleBit(captureLeftPromoting, targetSquare);
			int startSquare = targetSquare - (direction * 7);
			
			if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[startSquare][friendlyKingIndex] == PrecomputedData.rayAlignMask[targetSquare][friendlyKingIndex])
			{
				addPromotionMoves(moves, startSquare, targetSquare);
			}
		}
		
		while (captureRightPromoting != 0)
		{
			int targetSquare = Bitboards.getLSB(captureRightPromoting);
			captureRightPromoting = Bitboards.toggleBit(captureRightPromoting, targetSquare);
			int startSquare = targetSquare - (direction * 9);
			
			if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[startSquare][friendlyKingIndex] == PrecomputedData.rayAlignMask[targetSquare][friendlyKingIndex])
			{
				addPromotionMoves(moves, startSquare, targetSquare);
			}
		}
		
		if (board.boardInfo.getEnPassantIndex() != -1)
		{
			int targetSquare = board.boardInfo.getEnPassantIndex();
			long enPassantSquareBitboard = 1L << targetSquare;
			int captureSquare = board.boardInfo.getEnPassantIndex() - (direction * 8);
			
			if (((1L << captureSquare) & checkRaysMask) != 0)
			{
				long enPassantPawns = Bitboards.shift(enPassantSquareBitboard & captureRightMask, (-direction) * 7) & pawnsBitboard;
				enPassantPawns = enPassantPawns | (Bitboards.shift(enPassantSquareBitboard & captureLeftMask, (-direction) * 9) & pawnsBitboard);
				
				while (enPassantPawns != 0)
				{
					int startSquare = Bitboards.getLSB(enPassantPawns);
					enPassantPawns = Bitboards.toggleBit(enPassantPawns, startSquare);
					if (!isPiecePinned(startSquare) || PrecomputedData.rayAlignMask[startSquare][friendlyKingIndex] == PrecomputedData.rayAlignMask[targetSquare][friendlyKingIndex])
					{
						if (isEnPassantLegal(startSquare, captureSquare))
						{
							moves.add(new Move(startSquare, targetSquare, Move.EN_PASSANT_CAPTURE_FLAG));
						}
					}
				}
			}
		}
	}
	
	public void addPromotionMoves(ArrayList<Move> moves, int startSquare, int targetSquare)
	{
		moves.add(new Move(startSquare, targetSquare, Move.PROMOTION_QUEEN_FLAG));
		if (!capturesOnly)
		{
			moves.add(new Move(startSquare, targetSquare, Move.PROMOTION_ROOK_FLAG));
			moves.add(new Move(startSquare, targetSquare, Move.PROMOTION_BISHOP_FLAG));
			moves.add(new Move(startSquare, targetSquare, Move.PROMOTION_KNIGHT_FLAG));
		}
	}
	
	public boolean isEnPassantLegal(int startSquare, int captureSquare)
	{
		if (friendlyKingIndex / 8 != startSquare / 8) return true; // king is not on same rank
		
		long startSquareBitboard = 1l << startSquare;
		long captureSquareBitboard = 1l << captureSquare;
				
		int rank = friendlyKingIndex / 8;
		long rankMask = (0x00000000000000FFL << (rank * 8));	// mask of all squares on this rank
		
		long orthogonalSliders = bitBoards.getOrthogonalSliders(enemyColor) & rankMask;
		
		while (orthogonalSliders != 0)
		{
			int sliderSquare = Bitboards.getLSB(orthogonalSliders);
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
	
	public boolean isPiecePinned(int square)
	{
		return (pinRaysMask & (1L << square)) != 0;
	}
	
	public void calculateAllAttacks()
	{
		calculateSliderAttacks();
		
		for (int direction = 0; direction < 8; direction++)
		{
			boolean diagonal = direction >= 4;
			long sliders = diagonal ? bitBoards.getDiagonalSliders(enemyColor) : bitBoards.getOrthogonalSliders(enemyColor);
						
			if ((PrecomputedData.rayDirectionMask[friendlyKingIndex][direction] & sliders) == 0)
			{
				// no enemy sliders of relevant type along this ray on this specific direction from the friendly king
				continue;
			}
						
			// at least one relevant slider exists
			boolean friendlyPieceFound = false;
			long potentialPinRayMask = 0;
			int numSquares = PrecomputedData.numSquaresToEdge[friendlyKingIndex][direction];
			int directionOffset = PrecomputedData.directionOffsets[direction];
			
			for (int i = 1; i <= numSquares; i++)
			{
				int newIndex = friendlyKingIndex + (directionOffset * i);
				potentialPinRayMask |= (1L << newIndex);
				
				int pieceInfo = board.squares[newIndex];
				if (pieceInfo != Piece.NONE)
				{
					if (Piece.isColor(pieceInfo, friendlyColor))
					{
						// possible pinned piece found or second piece found so no pin
						if (!friendlyPieceFound) friendlyPieceFound = true;
						else break;
					}
					else
					{
						// enemy piece found so if it can attack king along this ray pin or check found
						if ((diagonal && Piece.isDiagonalSlider(pieceInfo)) || (!diagonal && Piece.isOrthogonalSlider(pieceInfo)))
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
		long kingBitboard = 1L << friendlyKingIndex;
		long enemyKnightsBitboard = bitBoards.pieceBoards[Piece.KNIGHT | enemyColor];
		
		while (enemyKnightsBitboard != 0)
		{
			int knightIndex = Bitboards.getLSB(enemyKnightsBitboard);
			enemyKnightsBitboard = Bitboards.toggleBit(enemyKnightsBitboard, knightIndex);
			
			// get pregenerated knight moves
			long newKnightAttacks = PrecomputedData.KNIGHT_MOVES[knightIndex];
			
			if ((newKnightAttacks & kingBitboard) != 0)
			{
				// set the checking square if this piece is pinning
				checkRaysMask |= 1L << knightIndex;
				doubleCheck = inCheck;
				inCheck = true;
			}
			
			enemyKnightAttacks |= newKnightAttacks;
		}
		
		// pawn attacks next
		long enemyPawnsBitboard = bitBoards.pieceBoards[Piece.PAWN | enemyColor];
		long enemyPawnAttacks = 0;
		long aFileMask = 0x0101010101010101L;
		long hFileMask = aFileMask << 7;
		
		// mask out edge files and shift pawns according to color along the board
		if (enemyColor == Piece.WHITE_PIECE)
		{
			enemyPawnAttacks = ((enemyPawnsBitboard & (~aFileMask)) << 7) | ((enemyPawnsBitboard & (~hFileMask)) << 9);
		}
		else
		{
			enemyPawnAttacks = ((enemyPawnsBitboard & (~aFileMask)) >>> 9) | ((enemyPawnsBitboard & (~hFileMask)) >>> 7);
		}
				
		if ((enemyPawnAttacks & kingBitboard) != 0)
		{
			// figure out where the attack is coming from
			doubleCheck = inCheck;
			inCheck = true;
			
			long pawnLocations = 0;
			// mask out edge files and pretend the king is a pawn to see which squares it could attack
			if (friendlyColor == Piece.WHITE_PIECE)
			{
				pawnLocations = ((kingBitboard & (~aFileMask)) << 7) | ((kingBitboard & (~hFileMask)) << 9);
			}
			else
			{
				pawnLocations = ((kingBitboard & (~aFileMask)) >>> 9) | ((kingBitboard & (~hFileMask)) >>> 7);
			}
			checkRaysMask |= pawnLocations & enemyPawnsBitboard;
		}
		
		int enemyKingIndex = Bitboards.getLSB(bitBoards.pieceBoards[Piece.KING | enemyColor]);
		long enemyKingMoves = PrecomputedData.KING_MOVES[enemyKingIndex];
		
		enemyAttackMap = enemySlidingAttackMap | enemyKnightAttacks | enemyPawnAttacks | enemyKingMoves;
				
		if (!inCheck)
		{
			// set all bits to 1
			checkRaysMask = ~0L;
		}
	}
	
	public void calculateSliderAttacks()
	{
		long orthoSliders = bitBoards.getOrthogonalSliders(enemyColor);
		long diagSliders = bitBoards.getDiagonalSliders(enemyColor);
		enemySlidingAttackMap = 0;
		enemySlidingAttackMap |= sliderAttacks(orthoSliders, enemyColor, true);
		enemySlidingAttackMap |= sliderAttacks(diagSliders, enemyColor, false);
	}
	
	private long sliderAttacks(long pieceBoard, int sliderColor, boolean orthogonal)
	{
		long slidingAttacks = 0;
		long notKingBoard = allPiecesBitboard & ~(1L << friendlyKingIndex);
		
		while (pieceBoard != 0)
		{
			// get the square of the slider
			int sliderSquare = Bitboards.getLSB(pieceBoard);
			pieceBoard = Bitboards.toggleBit(pieceBoard, sliderSquare);
			
			slidingAttacks |= PrecomputedMagicNumbers.getSliderMoves(sliderSquare, notKingBoard, orthogonal);
		}
		
		return slidingAttacks;
	}
}
