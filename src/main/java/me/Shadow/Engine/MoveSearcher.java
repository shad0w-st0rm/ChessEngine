package me.Shadow.Engine;

public class MoveSearcher
{
	short bestMove;
	short bestMoveCurrentIteration;
	
	boolean searchCancelled;
	
	private Board board;
	private MoveGenerator moveGen;
	private MoveOrderer moveOrderer;
	private TranspositionTable transpositionTable;
	static final int MAX_DEPTH = 64;
	
	static final int positiveInfinity = 1_000_000;
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
		bestMove = bestMoveCurrentIteration = MoveHelper.NULL_MOVE;
		moveOrderer.clearHistoryHeuristic();
		transpositionTable.setObsoleteFlag(board.bitBoards.getNumPawns(PieceHelper.WHITE_PIECE), board.bitBoards.getNumPawns(PieceHelper.BLACK_PIECE), board.boardInfo.getCastlingRights());
		int depth = 0;
		
		while (!searchCancelled && depth < MAX_DEPTH)
		{
			depth++;
			final int evaluation = search(depth, 0, negativeInfinity, positiveInfinity);
			
			if (bestMoveCurrentIteration != MoveHelper.NULL_MOVE)
			{
				bestMove = bestMoveCurrentIteration;
				
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
			}
						
			bestMoveCurrentIteration = MoveHelper.NULL_MOVE;
		}
				
		return bestMove;
	}
	
	public void stopSearch()
	{
		searchCancelled = true;
	}
	
	public int search(final int depth, final int plyFromRoot, int alpha, final int beta)
	{
		if (searchCancelled) return 0;
		
		if ((board.isDuplicatePosition() && plyFromRoot > 0) || board.boardInfo.getHalfMoves() >= 100)
		{
			return 0;
		}
		
		int obsoleteFlag = transpositionTable.createObsoleteFlag(board.bitBoards.getNumPawns(PieceHelper.WHITE_PIECE), board.bitBoards.getNumPawns(PieceHelper.BLACK_PIECE), board.boardInfo.getCastlingRights());
		
		final int transposEval = transpositionTable.lookupEvaluation(board.boardInfo.getZobristHash(), depth, alpha, beta, obsoleteFlag);
		if (transposEval != TranspositionTable.LOOKUP_FAILED)
		{			
			if (plyFromRoot == 0)
			{
				bestMoveCurrentIteration = transpositionTable.lookupMove(board.boardInfo.getZobristHash());
			}
			
			return transposEval;
		}
		
		if (depth == 0)
		{
			return searchCaptures(alpha, beta);
		}
		
		final short [] moves = moveGen.generateMoves(false);
		
		if (moves.length == 0)
		{
			if (moveGen.inCheck)
				return negativeInfinity;
			else
				return 0;
		}

		final short firstMove = (plyFromRoot == 0 ? bestMove : transpositionTable.lookupMove(board.boardInfo.getZobristHash()));
		final int[] scores = moveOrderer.guessMoveEvals(board, moves, firstMove, moveGen.enemyAttackMap, moveGen.enemyPawnAttackMap, false, plyFromRoot);
		
		int bound = TranspositionTable.UPPER_BOUND;
		short bestMoveInPosition = MoveHelper.NULL_MOVE;
		final int length = moves.length;
		for (int i = 0; i < length; i++)
		{
			MoveOrderer.singleSelectionSort(moves, scores, i);
			
			final short move = moves[i];
			
			final BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			final int captured = board.movePiece(move);
			int evaluation = -(search(depth - 1, plyFromRoot + 1, -beta, -alpha));
			board.moveBack(move, captured, boardInfoOld);
			
			if (searchCancelled)
			{
				return 0;
			}
			
			if (evaluation > (positiveInfinity - depth) || evaluation < (negativeInfinity + depth))
			{
				evaluation += ((evaluation > 0) ? -1 : 1);
			}
			
			if (evaluation > alpha)
			{
				bound = TranspositionTable.EXACT_BOUND;
				alpha = evaluation;
				bestMoveInPosition = move;
				
				if (plyFromRoot == 0)
				{
					bestMoveCurrentIteration = move;
				}
			}
			
			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				bound = TranspositionTable.LOWER_BOUND;
				
				
				transpositionTable.storeEvaluation(board.boardInfo.getZobristHash(), beta, depth, bound, move, obsoleteFlag);
				
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
				
				return beta;
			}
		}
		
		transpositionTable.storeEvaluation(board.boardInfo.getZobristHash(), alpha, depth, bound, bestMoveInPosition, obsoleteFlag);
		
		return alpha;
	}
	
	public int searchCaptures(int alpha, final int beta)
	{
		if (searchCancelled) return 0;
		
		// captures arent forced so check eval before capturing something
		int evaluation = staticEvaluation();
		
		if (evaluation >= beta)
		{
			return evaluation;
		}
		alpha = Math.max(alpha, evaluation);
		
		final short [] moves = moveGen.generateMoves(true);
		
		final int [] scores = moveOrderer.guessMoveEvals(board, moves, MoveHelper.NULL_MOVE, moveGen.enemyAttackMap, moveGen.enemyPawnAttackMap, true, 0);
		
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
	
	
	public int staticEvaluation()
	{
		double evaluation = 0;
		evaluation = board.boardInfo.getWhiteMaterial() + board.boardInfo.getWhiteSquareBonus();
		evaluation -= (board.boardInfo.getBlackMaterial() + board.boardInfo.getBlackSquareBonus());
		
		final int whiteKingIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[PieceHelper.WHITE_KING]);
		final int blackKingIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[PieceHelper.BLACK_KING]);
		evaluation += forceKingToCorner(whiteKingIndex, blackKingIndex, 1 - (board.boardInfo.getBlackMaterial() / 2000));
		evaluation -= forceKingToCorner(blackKingIndex, whiteKingIndex, 1 - (board.boardInfo.getWhiteMaterial() / 2000));
		
		evaluation *= board.boardInfo.isWhiteToMove() ? 1 : -1;
		
		return (int)evaluation;
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
	
	public void newPosition()
	{
		moveOrderer.clearKillers();
		transpositionTable.clearTable();
	}
}
