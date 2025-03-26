package me.Shadow.Engine;

import java.util.ArrayList;

public class MoveSearcher
{
	boolean searchCancelled;

	private Board board;
	private MoveGenerator moveGen;
	private MoveOrderer moveOrderer;
	private TranspositionTable transpositionTable;
	static final int MAX_DEPTH = 64;

	static final int CONTEMPT_FACTOR = 0;

	static final int positiveInfinity = 0x3FFF;
	static final int negativeInfinity = -positiveInfinity;

	final static class PositionEvaluation
	{
		long zobristHash;
		int eval;
		int depth;
		int bound;
		short move;
		int obsoleteFlag;

		public PositionEvaluation(long zobristHash, int eval, int depth, int bound, short move, int obsoleteFlag)
		{
			this.zobristHash = zobristHash;
			this.eval = eval;
			this.depth = depth;
			this.bound = bound;
			this.move = move;
			this.obsoleteFlag = obsoleteFlag;
		}

		public PositionEvaluation(long zobristHash)
		{
			this(zobristHash, negativeInfinity, 0, TranspositionTable.NULL_BOUND, MoveHelper.NULL_MOVE, 0);
		}
	}

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
		PositionEvaluation posEval = new PositionEvaluation(board.boardInfo.getZobristHash());
		moveOrderer.clearHistoryHeuristic();
		posEval.obsoleteFlag = transpositionTable.setObsoleteFlag(board.bitBoards.getNumPawns(PieceHelper.WHITE_PIECE),
				board.bitBoards.getNumPawns(PieceHelper.BLACK_PIECE), board.boardInfo.getCastlingRights());

		do
		{
			posEval.depth += 1;
			posEval = rootSearch(posEval, negativeInfinity, positiveInfinity);
			
			System.out.println(posEval.depth + " " + posEval.eval + " " + MoveHelper.toString(posEval.move));
			
			if (posEval.eval >= (positiveInfinity - posEval.depth))
			{
				// System.out.println("Forced mate for engine found");
				break;
			}
			else if (posEval.eval <= (negativeInfinity + posEval.depth))
			{
				// System.out.println("Forced mate against engine found");
				break;
			}
		}
		while (!searchCancelled && posEval.depth < MAX_DEPTH);

		// System.out.println(depth);

		return posEval.move;
	}

	public PositionEvaluation rootSearch(PositionEvaluation posEval, int alpha, int beta)
	{
		final short[] moves = moveGen.generateMoves(false);

		// checkmate/stalemate (in the root position ... )
		if (moves.length == 0)
		{
			posEval.eval = moveGen.inCheck ? negativeInfinity : CONTEMPT_FACTOR;
			posEval.move = MoveHelper.NULL_MOVE;
			posEval.bound = TranspositionTable.EXACT_BOUND;
			return posEval;
		}

		final int[] scores = moveOrderer.guessMoveEvals(board, moves, posEval.move, moveGen.enemyAttackMap,
				moveGen.enemyPawnAttackMap, false, 0);

		for (int i = 0; i < moves.length; i++)
		{
			MoveOrderer.singleSelectionSort(moves, scores, i);
			final short move = moves[i];

			boolean failedHigh = evaluateMove(posEval, move, alpha, beta, 0);

			if (searchCancelled)
			{
				if (posEval.move == MoveHelper.NULL_MOVE)
					posEval.move = move;

				return posEval;
			}

			if (failedHigh)
				return posEval;
			
			if (posEval.eval > alpha)
			{
				posEval.move = move;
				alpha = posEval.eval;
			}
		}

		posEval.bound = (posEval.eval < alpha) ? TranspositionTable.UPPER_BOUND : TranspositionTable.EXACT_BOUND;
		transpositionTable.storeEvaluation(posEval);

		return posEval;
	}

	public int search(final int depth, final int plyFromRoot, int alpha, int beta)
	{
		if (searchCancelled)
			return 0;

		if (isDuplicatePosition() || board.boardInfo.getHalfMoves() >= 100)
		{
			return CONTEMPT_FACTOR;
		}

		if (depth == 0)
		{
			return searchCaptures(alpha, beta);
		}

		int obsoleteFlag = transpositionTable.createObsoleteFlag(board.bitBoards.getNumPawns(PieceHelper.WHITE_PIECE),
				board.bitBoards.getNumPawns(PieceHelper.BLACK_PIECE), board.boardInfo.getCastlingRights());

		long zobristHash = board.boardInfo.getZobristHash();
		
		int alphaOriginal = alpha;
		
		PositionEvaluation transposEval = transpositionTable.getEntry(zobristHash);
		if (transposEval.bound != TranspositionTable.NULL_BOUND)
		{
			//System.out.println("transposition hit");
			if (transposEval.depth >= depth)
			{
				// If this position was searched exactly (probably a PV node and/or left most in
				// tree
				if (transposEval.bound == TranspositionTable.EXACT_BOUND)
				{
					return transposEval.eval;
				}
					
				// If lower bound, try and fail high or at least update alpha
				if (transposEval.bound == TranspositionTable.LOWER_BOUND)
				{
					if (transposEval.eval >= beta) return transposEval.eval;
					else alpha = Math.max(alpha, transposEval.eval);
				}
				else if (transposEval.bound == TranspositionTable.UPPER_BOUND)
				{
					if (transposEval.eval <= alpha) return transposEval.eval;
					else beta = Math.min(beta, transposEval.eval);
				}
			}
		}
		
		PositionEvaluation posEval = new PositionEvaluation(zobristHash);
		
		posEval.eval = negativeInfinity;
		posEval.obsoleteFlag = obsoleteFlag;
		posEval.depth = depth;
		posEval.move = transposEval.move;
		
		boolean playedFirst = false;
		if (posEval.move != MoveHelper.NULL_MOVE)
		{
			playedFirst = true;
			boolean failedHigh = evaluateMove(posEval, posEval.move, alpha, beta, plyFromRoot);

			if (failedHigh || searchCancelled)
				return posEval.eval;
			
			if (posEval.eval > alpha) alpha = posEval.eval;
		}

		final short[] moves = moveGen.generateMoves(false);

		// checkmate/stalemate
		if (moves.length == 0)
			return moveGen.inCheck ? negativeInfinity : CONTEMPT_FACTOR;

		// we need to keep firstMove first to ignore it, otherwise a different move may
		// get ignored
		final int[] scores = moveOrderer.guessMoveEvals(board, moves, posEval.move, moveGen.enemyAttackMap,
				moveGen.enemyPawnAttackMap, false, plyFromRoot);
		
		// skip the first move because we already looked at it
		if (playedFirst) MoveOrderer.singleSelectionSort(moves, scores, 0);
		
		// make sure to start on second move if already played first
		for (int i = (playedFirst ? 1 : 0); i < moves.length; i++)
		{
			MoveOrderer.singleSelectionSort(moves, scores, i);

			final short move = moves[i];

			boolean failedHigh = evaluateMove(posEval, move, alpha, beta, plyFromRoot);

			if (failedHigh || searchCancelled)
				return posEval.eval;
				
			
			if (posEval.eval > alpha)
			{
				posEval.move = move;
				alpha = posEval.eval;
			}
		}

		posEval.bound = (posEval.eval <= alphaOriginal) ? TranspositionTable.UPPER_BOUND : TranspositionTable.EXACT_BOUND;
		transpositionTable.storeEvaluation(posEval);

		return posEval.eval;
	}

	public boolean evaluateMove(PositionEvaluation posEval, short move, int alpha, int beta, int plyFromRoot)
	{
		BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
		int captured = board.movePiece(move);
		int evaluation = -(search(posEval.depth - 1, plyFromRoot + 1, -beta, -alpha));
		board.moveBack(move, captured, boardInfoOld);

		if (evaluation > (positiveInfinity - posEval.depth) || evaluation < (negativeInfinity + posEval.depth))
		{
			evaluation += ((evaluation > 0) ? -1 : 1);
		}

		if (searchCancelled)
		{
			//posEval.eval = 0;
			return false;
		}
		
		if (posEval.eval < evaluation)
		{
			posEval.move = move;
			posEval.eval = evaluation;
		}

		// move was too good so opponent will avoid this branch
		if (posEval.eval >= beta)
		{
			posEval.move = move;
			posEval.bound = TranspositionTable.LOWER_BOUND;
			transpositionTable.storeEvaluation(posEval);

			if (captured == PieceHelper.NONE) // Ignore captures for killer moves
			{
				if (plyFromRoot < MoveOrderer.maxKillerDepth)
				{
					moveOrderer.killers[plyFromRoot].addKiller(posEval.move);
				}

				final int colorIndex = board.boardInfo.isWhiteToMove() ? 0 : 1;
				// multiply the color index by 64*64 = 2^12
				// then add start square multiplied by 64 = 2^6
				// then add target square
				final int index = (colorIndex << 12) | (MoveHelper.getStartIndex(posEval.move) << 6)
						| MoveHelper.getTargetIndex(posEval.move);
				moveOrderer.historyHeuristic[index] += posEval.depth * posEval.depth;
			}
			return true;
		}

		return false;
	}

	public int searchCaptures(int alpha, int beta)
	{
		if (searchCancelled)
			return 0;

		// captures arent forced so check eval before capturing something
		int evaluation = staticEvaluation();
		
		if (evaluation >= beta)
		{
			return evaluation;
		}
				
		PositionEvaluation transposEval = transpositionTable.getEntry(board.boardInfo.getZobristHash());
		if (transposEval.bound != TranspositionTable.NULL_BOUND)
		{
			// If this position was searched exactly (probably a PV node and/or left most in
			// tree
			if (transposEval.bound == TranspositionTable.EXACT_BOUND)
			{
				return transposEval.eval;
			}
			// If lower bound, try and fail high or at least update alpha
			if (transposEval.bound == TranspositionTable.LOWER_BOUND)
			{
				if (transposEval.eval >= beta) return transposEval.eval;
				else alpha = Math.max(alpha, transposEval.eval);
			}
			else if (transposEval.bound == TranspositionTable.UPPER_BOUND)
			{
				if (transposEval.eval <= alpha) return transposEval.eval;
				else beta = Math.min(beta, transposEval.eval);
				beta = Math.min(beta, transposEval.eval);
			}
		}
		

		alpha = Math.max(alpha, evaluation);

		final short[] moves = moveGen.generateMoves(true);

		final int[] scores = moveOrderer.guessMoveEvals(board, moves, MoveHelper.NULL_MOVE, moveGen.enemyAttackMap,
				moveGen.enemyPawnAttackMap, true, 0);

		final int length = moves.length;
		
		int bestEval = evaluation;
		for (int i = 0; i < length; i++)
		{
			MoveOrderer.singleSelectionSort(moves, scores, i);

			final short move = moves[i];

			final BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			final int captured = board.movePiece(move);
			evaluation = -(searchCaptures(-beta, -alpha));
			board.moveBack(move, captured, boardInfoOld);
			
			if (searchCancelled)
				return 0;

			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				return evaluation;
			}
			
			bestEval = Math.max(bestEval, evaluation);
			alpha = Math.max(alpha, evaluation);
		}

		return bestEval;
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
		evaluation = board.boardInfo.getWhiteMaterial() + board.boardInfo.getWhiteSquareBonus();
		evaluation -= (board.boardInfo.getBlackMaterial() + board.boardInfo.getBlackSquareBonus());

		final int whiteKingIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[PieceHelper.WHITE_KING]);
		final int blackKingIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[PieceHelper.BLACK_KING]);
		evaluation += forceKingToCorner(whiteKingIndex, blackKingIndex,
				1 - (board.boardInfo.getBlackMaterial() / 2000));
		evaluation -= forceKingToCorner(blackKingIndex, whiteKingIndex,
				1 - (board.boardInfo.getWhiteMaterial() / 2000));

		evaluation *= board.boardInfo.isWhiteToMove() ? 1 : -1;

		return (int) evaluation;
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
