package me.Shadow.Engine;

import java.util.ArrayList;

public class MoveSearcher
{
	short bestMove;
	
	boolean searchCancelled;
	
	private Board board;
	private MoveGenerator moveGen;
	private MoveOrderer moveOrderer;
	private TranspositionTable transpositionTable;
	static final int MAX_DEPTH = 64;
	
	static final int positiveInfinity = 0x3FFF;
	static final int negativeInfinity = -positiveInfinity;
	
	public MoveSearcher(Board board)
	{
		this.board = board;
		moveGen = new MoveGenerator(board);
		moveOrderer = new MoveOrderer();
		transpositionTable = new TranspositionTable();
	}
	
	public short startSearch()
	{
		searchCancelled = false;
		
		// clearSearchStats();
		bestMove = MoveHelper.NULL_MOVE;
		moveOrderer.clearHistoryHeuristic();
		int obsoleteFlag = transpositionTable.setObsoleteFlag(board.bitBoards.getNumPawns(PieceHelper.WHITE_PIECE), board.bitBoards.getNumPawns(PieceHelper.BLACK_PIECE), board.boardInfo.getCastlingRights());
		int depth = 0;
		
		do
		{
			depth++;
			final int evaluation = rootSearch(depth, negativeInfinity, positiveInfinity, obsoleteFlag);
			//final int evaluation = search(depth, 0, negativeInfinity, positiveInfinity);
						
			if (evaluation >= (positiveInfinity - depth))
			{
				//System.out.println("Forced mate for engine found");
				break;
			}
			else if (evaluation <= (negativeInfinity + depth))
			{
				//System.out.println("Forced mate against engine found");
				break;
			}
		} while (!searchCancelled && depth < MAX_DEPTH);
		
		//System.out.println(depth);
				
		return bestMove;
	}
	
	public int rootSearch(final int depth, int alpha, int beta, int obsoleteFlag)
	{
		final short [] moves = moveGen.generateMoves(false);
		
		// checkmate/stalemate
		if (moves.length == 0) return moveGen.inCheck ? negativeInfinity : 0;
		
		final int[] scores = moveOrderer.guessMoveEvals(board, moves, bestMove, false, 0);
		
		int bound = TranspositionTable.UPPER_BOUND;
		final int length = moves.length;
		for (int i = 0; i < length; i++)
		{
			MoveOrderer.singleSelectionSort(moves, scores, i);
			
			final short move = moves[i];
			
			int evaluation = searchMove(move, alpha, beta, depth, 0, i);
			
			if (searchCancelled)
			{
				if (bestMove == MoveHelper.NULL_MOVE) bestMove = move;
				return evaluation;
			}
			
			
			if (evaluation > alpha)
			{
				bound = TranspositionTable.EXACT_BOUND;
				alpha = evaluation;
				
				bestMove = move;
			}
			
			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				transpositionTable.storeEvaluation(board.boardInfo.getZobristHash(), beta, depth, TranspositionTable.LOWER_BOUND, move, obsoleteFlag);
				return beta;
			}
		}
		
		transpositionTable.storeEvaluation(board.boardInfo.getZobristHash(), alpha, depth, bound, bestMove, obsoleteFlag);
		
		return alpha;
	}
	
	public int search(final int depth, final int plyFromRoot, int alpha, int beta)
	{
		if (searchCancelled) return 0;
		
		if (isDuplicatePosition() || board.boardInfo.getHalfMoves() >= 100)
		{
			return 0;
		}
		
		int obsoleteFlag = transpositionTable.createObsoleteFlag(board.bitBoards.getNumPawns(PieceHelper.WHITE_PIECE), board.bitBoards.getNumPawns(PieceHelper.BLACK_PIECE), board.boardInfo.getCastlingRights());
		
		long zobristHash = board.boardInfo.getZobristHash();
		short bestMoveInPosition = MoveHelper.NULL_MOVE;
		short [] transposition = transpositionTable.getEntry(zobristHash);
		
		if (transposition != null)
		{
			bestMoveInPosition = transposition[TranspositionTable.MOVE_INDEX];
			if (transposition[TranspositionTable.DEPTH_INDEX] >= depth)
			{
				short bound = transposition[TranspositionTable.BOUND_INDEX];
				short eval = transposition[TranspositionTable.EVAL_INDEX];
				if (bound == TranspositionTable.EXACT_BOUND)
				{
					return transposition[TranspositionTable.EVAL_INDEX];
				}
				else if (bound == TranspositionTable.LOWER_BOUND)
				{
					if (eval >= beta) return beta;
					else alpha = Math.max(alpha, eval);
				}
				else if (bound == TranspositionTable.UPPER_BOUND)
				{
					if (eval <= alpha) return alpha;
					else beta = Math.max(beta, eval);
				}
			}
		}
		
		if (depth == 0) return searchCaptures(alpha, beta);
		
		int bound = TranspositionTable.UPPER_BOUND;
		boolean searchedFirst = false;
		if (bestMoveInPosition != MoveHelper.NULL_MOVE)
		{
			searchedFirst = true;
			
			int evaluation = searchMove(bestMoveInPosition, alpha, beta, depth, plyFromRoot, 0);
			
			if (searchCancelled) return evaluation;
			
			if (evaluation > alpha)
			{
				bound = TranspositionTable.EXACT_BOUND;
				alpha = evaluation;
			}
			
			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				transpositionTable.storeEvaluation(zobristHash, beta, depth, TranspositionTable.LOWER_BOUND, bestMoveInPosition, obsoleteFlag);
				return beta;
			}
		}
		
		final short [] moves = moveGen.generateMoves(false);
		
		// checkmate/stalemate
		if (moves.length == 0) return moveGen.inCheck ? negativeInfinity : 0;
		
		// ensure the potentially first searched move gets boosted to top with highest score
		final int[] scores = moveOrderer.guessMoveEvals(board, moves, bestMoveInPosition, false, plyFromRoot);
		// push the first searched move to the top (guaranteed to be highest score)
		if (searchedFirst) MoveOrderer.singleSelectionSort(moves, scores, 0);
		
		// ignore first move if we already searched it
		for (int i = (searchedFirst ? 1 : 0); i < moves.length; i++)
		{
			MoveOrderer.singleSelectionSort(moves, scores, i);
			
			final short move = moves[i];
			
			int evaluation = searchMove(move, alpha, beta, depth, plyFromRoot, i);
			
			if (searchCancelled) return evaluation;
			
			if (evaluation > alpha)
			{
				bound = TranspositionTable.EXACT_BOUND;
				alpha = evaluation;
				bestMoveInPosition = move;
			}
			
			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				transpositionTable.storeEvaluation(zobristHash, beta, depth, TranspositionTable.LOWER_BOUND, move, obsoleteFlag);
				return beta;
			}
		}
		
		transpositionTable.storeEvaluation(zobristHash, alpha, depth, bound, bestMoveInPosition, obsoleteFlag);
		
		return alpha;
	}
	
	public int searchMove(short move, int alpha, int beta, int depth, int plyFromRoot, int moveNum)
	{
		final BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
		final int captured = board.movePiece(move);
		
		int searchDepth = calculateSearchDepth(move, captured, depth, moveNum);
		
		int evaluation = -(search(searchDepth, plyFromRoot + 1, -beta, -alpha));
		board.moveBack(move, captured, boardInfoOld);
		
		if (evaluation > (positiveInfinity - depth) || evaluation < (negativeInfinity + depth))
			evaluation += ((evaluation > 0) ? -1 : 1);
		
		if (searchCancelled) return 0;
		
		if (evaluation >= beta)
		{
			if (captured == PieceHelper.NONE) // ignore captures for killer moves
			{
				if (plyFromRoot < MoveOrderer.maxKillerDepth)
				{
					moveOrderer.killers[plyFromRoot].addKiller(move);
				}
				
				final int colorIndex = board.boardInfo.isWhiteToMove() ? 0 : 1;
				// multiply the color index by 64*64 = 2^12
				// then add start square multiplied by 64 = 2^6
				// then add target square
				final int index = (colorIndex << 12) | (MoveHelper.getStartIndex(move) << 6) | MoveHelper.getTargetIndex(move);
				moveOrderer.historyHeuristic[index] += depth*depth;
			}
		}
		
		return evaluation;
	}
	
	public int calculateSearchDepth(short move, int captured, int depth, int moveNum)
	{
		depth--;
		if (board.inCheck()) return depth + 1;
		
		// give bonus to a pawn moving to one square from promotion
		// maybe change this just to a promotion move itself
		int target = MoveHelper.getTargetIndex(move);
		if (((board.squares[target] & PieceHelper.TYPE_MASK) == PieceHelper.PAWN) && (Utils.getSquareRank(target) == 2 || Utils.getSquareRank(target) == 7))
		{
			return depth + 1;
		}
		
		// search reduction, ensure depth doesn't go negative
		return Math.max(((moveNum >= 3 && captured == PieceHelper.NONE && depth >= 3) ? (depth - 1) : depth), 0);
	}
	
	public int searchCaptures(int alpha, int beta)
	{
		if (searchCancelled) return 0;
		
		long zobristHash = board.boardInfo.getZobristHash();
		short [] transposition = transpositionTable.getEntry(zobristHash);
		
		if (transposition != null)
		{			
			// transposition depth is always greater than 0
			short bound = transposition[TranspositionTable.BOUND_INDEX];
			short eval = transposition[TranspositionTable.EVAL_INDEX];
			if (bound == TranspositionTable.EXACT_BOUND)
			{
				return transposition[TranspositionTable.EVAL_INDEX];
			}
			else if (bound == TranspositionTable.LOWER_BOUND)
			{
				if (eval >= beta) return beta;
				else alpha = Math.max(alpha, eval);
			}
			else if (bound == TranspositionTable.UPPER_BOUND)
			{
				if (eval <= alpha) return alpha;
				else beta = Math.max(beta, eval);
			}
		}
		
		// captures arent forced so check eval before capturing something
		int evaluation = staticEvaluation();
		
		if (evaluation >= beta)
		{
			return evaluation;
		}
		alpha = Math.max(alpha, evaluation);
		
		final short [] moves = moveGen.generateMoves(true);
		
		final int [] scores = moveOrderer.guessMoveEvals(board, moves, MoveHelper.NULL_MOVE, true, 0);
		
		final int length = moves.length;
		for (int i = 0; i < length; i++)
		{
			MoveOrderer.singleSelectionSort(moves, scores, i);
			
			final short move = moves[i];
			
			final BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			final int captured = board.movePiece(move);
			evaluation = -(searchCaptures(-beta, -alpha));
			board.moveBack(move, captured, boardInfoOld);

			if (searchCancelled) return 0;

			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				return evaluation;
			}
			alpha = Math.max(alpha, evaluation);
		}
		
		return alpha;
	}
	
	public boolean isDuplicatePosition()
	{
		if (board.boardInfo.getHalfMoves() < 4)
			return false;

		final long zobristHash = board.boardInfo.getZobristHash();
		final ArrayList<Long> positions = board.boardInfo.getPositionList();

		int index = positions.size() - 5;
		final int minIndex = Math.max(index - board.boardInfo.getHalfMoves() + 1, 0);
		while (index >= minIndex)
		{
			if (positions.get(index) == zobristHash)
			{
				return true;
			}
			index -= 2;
		}
		return false;
	}
	
	public int staticEvaluation()
	{
		double evaluation = 0;
		
		float gamePhase = getGamePhase();
		evaluation = evaluateMaterial(gamePhase);
		
		final int whiteKingIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[PieceHelper.WHITE_KING]);
		final int blackKingIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[PieceHelper.BLACK_KING]);
		
		evaluation += forceKingToCorner(whiteKingIndex, blackKingIndex, 1 - (gamePhase * (24 / 5.0f)));
		evaluation -= forceKingToCorner(blackKingIndex, whiteKingIndex, 1 - (gamePhase * (24 / 5.0f)));
		
		evaluation += evaluatePawns(PieceHelper.WHITE_PIECE, PieceHelper.BLACK_PIECE);
		evaluation -= evaluatePawns(PieceHelper.BLACK_PIECE, PieceHelper.WHITE_PIECE);
		
		evaluation *= board.boardInfo.isWhiteToMove() ? 1 : -1;
		
		return (int)evaluation;
	}
	
	public double evaluateMaterial(float gamePhase)
	{
		int mgScore = board.boardInfo.getWhiteMGBonus() - board.boardInfo.getBlackMGBonus();
		int egScore = board.boardInfo.getWhiteEGBonus() - board.boardInfo.getBlackEGBonus();
		return mgScore * gamePhase + egScore * (1- gamePhase);
	}
	
	public float getGamePhase()
	{
		int mgPhase = 0;
		mgPhase += Long.bitCount(board.bitBoards.pieceBoards[PieceHelper.WHITE_QUEEN]
				| board.bitBoards.pieceBoards[PieceHelper.BLACK_QUEEN])
		* PieceHelper.getGamePhaseValue(PieceHelper.QUEEN);
		
		mgPhase += Long.bitCount(board.bitBoards.pieceBoards[PieceHelper.WHITE_ROOK]
				| board.bitBoards.pieceBoards[PieceHelper.BLACK_ROOK])
		* PieceHelper.getGamePhaseValue(PieceHelper.QUEEN);
		
		mgPhase += Long.bitCount(board.bitBoards.pieceBoards[PieceHelper.WHITE_BISHOP]
				| board.bitBoards.pieceBoards[PieceHelper.BLACK_BISHOP])
		* PieceHelper.getGamePhaseValue(PieceHelper.QUEEN);
		
		if (mgPhase > 24) mgPhase = 24;
		return mgPhase / 24.0f;
	}
	
	public int evaluatePawns(int friendlyColor, int enemyColor)
	{
		final int [] passedPawnsBonus = {0, 10, 15, 25, 40, 60, 100, 0};
		boolean whitePieces = friendlyColor == PieceHelper.WHITE_PIECE;
		int evaluation = 0;
		
		long pawnsBitboard = board.bitBoards.pieceBoards[friendlyColor | PieceHelper.PAWN];
		long friendlyPawns = pawnsBitboard;
		long enemyPawns = board.bitBoards.pieceBoards[enemyColor | PieceHelper.PAWN];
		
		while (pawnsBitboard != 0)
		{
			int pawnIndex = Bitboards.getLSB(pawnsBitboard);
			pawnsBitboard = Bitboards.toggleBit(pawnsBitboard, pawnIndex);
			
			long ranksAheadMask = -1;	// All 1 bits
			ranksAheadMask = whitePieces ? (ranksAheadMask << (((pawnIndex / 8) + 1) * 8)) : (ranksAheadMask >>> ((8 - (pawnIndex / 8)) * 8));
			//long ranksAheadMask = MoveGenerator.ALL_ONE_BITS << ((pawnIndex & ~8) + 8);	// shift all 1s mask up by this many ranks
			int file = pawnIndex & 0x7;
			long fileMask = MoveGenerator.A_FILE << (file);	// mask last 3 bits, aka mod 8
			long tripleFileMask = fileMask;
			if (file > 0) tripleFileMask |= (tripleFileMask >>> 1);	// left file
			if (file < 7) tripleFileMask |= (tripleFileMask << 1);	// right file
			
			long passedPawnMask = ranksAheadMask & tripleFileMask & enemyPawns;
			if (passedPawnMask == 0) evaluation += passedPawnsBonus[whitePieces ? (pawnIndex / 8) :  (7 - (pawnIndex / 8))];	// getting the rank
			
			long friendlyPawnsAhead = (ranksAheadMask & fileMask & friendlyPawns);
			evaluation -= 10 * (Long.bitCount(friendlyPawnsAhead) * Long.bitCount(friendlyPawnsAhead));
			
			/*
			// next/prev rank are swapped for white/black pawns
			long nextRankMask = MoveGenerator.FIRST_RANK << ((pawnIndex & ~7) + (whitePieces ? 8 : -8));
			long prevRankMask = MoveGenerator.FIRST_RANK << ((pawnIndex & ~7) - (whitePieces ? 8 : -8));
			long friendlyPawnsSupported = tripleFileMask & ~fileMask & nextRankMask & friendlyPawns;
			long friendlyPawnsSupporting = tripleFileMask & ~fileMask & prevRankMask & friendlyPawns;
						
			evaluation += 5 * Long.bitCount(friendlyPawnsSupported) * Long.bitCount(friendlyPawnsSupported) + 5;
			evaluation += 5 * Long.bitCount(friendlyPawnsSupporting) * Long.bitCount(friendlyPawnsSupporting) + 5;
			*/
		}
		
		return evaluation;
	}

	public int forceKingToCorner(final int friendlyKingIndex, final int enemyKingIndex, final float endgameWeight)
	{
		if (endgameWeight < 0)
			return 0;

		double evaluation = 0;

		final int enemyRank = 8 - (enemyKingIndex / 8);
		final int enemyFile = (enemyKingIndex % 8) + 1;
		final int distToCenter = Math.max(4 - enemyRank, enemyRank - 5) + Math.max(4 - enemyFile, enemyFile - 5);
		evaluation += distToCenter;

		final int friendlyRank = 8 - (friendlyKingIndex / 8);
		final int friendlyFile = (friendlyKingIndex % 8) + 1;
		final int distBetweenKings = Math.abs(friendlyRank - enemyRank) + Math.abs(friendlyFile - enemyFile);
		evaluation += (distBetweenKings);

		return (int) (evaluation * endgameWeight);
	}
	
	public void stopSearch()
	{
		searchCancelled = true;
	}
	
	public void newPosition()
	{
		moveOrderer.clearKillers();
		transpositionTable.clearTable();
	}
}
