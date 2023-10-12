package me.Shadow.Engine;

import java.util.ArrayList;

public class MoveSearcher
{
	short bestMove;
	short bestMoveCurrentIteration;
	boolean oneMoveSearched;
	
	boolean searchCancelled;
	
	Board board;
	MoveGenerator moveGen;
	MoveOrderer moveOrderer;
	TranspositionTable transpositionTable;
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
		oneMoveSearched = false;
		moveOrderer.clearHistoryHeuristic();
		int depth = 0;
		
		while (!searchCancelled && depth < MAX_DEPTH)
		{
			depth++;
			final int evaluation = search(depth, 0, negativeInfinity, positiveInfinity);
			
			if (oneMoveSearched)
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
			oneMoveSearched = false;
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
		
		if ((isDuplicatePosition() && plyFromRoot > 0) || board.boardInfo.getHalfMoves() >= 100)
		{
			return 0;
		}
		
		final int transposEval = transpositionTable.lookupEvaluation(board.boardInfo.getZobristHash(), depth, alpha, beta);
		if (transposEval != TranspositionTable.LOOKUP_FAILED)
		{			
			if (plyFromRoot == 0)
			{
				bestMoveCurrentIteration = transpositionTable.lookupMove(board.boardInfo.getZobristHash());
				oneMoveSearched = true;
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
					oneMoveSearched = true;
				}
			}
			
			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				bound = TranspositionTable.LOWER_BOUND;
				transpositionTable.storeEvaluation(board.boardInfo.getZobristHash(), beta, depth, bound, move);
				
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
		
		transpositionTable.storeEvaluation(board.boardInfo.getZobristHash(), alpha, depth, bound, bestMoveInPosition);
		
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
		final long zobristHash = board.boardInfo.getZobristHash();
		final ArrayList<Long> positions = board.boardInfo.getPositionList();
		positions.remove(positions.size() - 1); // remove the most recent hash
		
		int index = positions.size() - 1;
		final int minIndex = Math.max(index - board.boardInfo.getHalfMoves() + 1, 0);
		while (index >= minIndex)
		{
			if (positions.get(index) == zobristHash)
			{
				positions.add(zobristHash);
				return true;
			}
			index--;
		}
		positions.add(zobristHash);
		return false;
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
	
	/*
	public void clearSearchStats()
	{
		numPositions = numTranspositions = 0;
		moveGenTime = moveEvalTime = movePieceTime = moveBackTime = positionEvaluationTime = transpositionTime = boardInfoTime = checkRepetitionTime = 0;
	}
	
	public void printSearchStats()
	{
		System.out.println("Evaluation time: " + (System.currentTimeMillis() - startTime) + "\t\t\tEvaluation for Engine: " + "\t\tEvaluation Depth: ");
		System.out.println("Number of positions: " + numPositions + "\t\tNumber of Transpositions: " + numTranspositions + "\t\tStored Transpositions: " + transpositionTable.numStored);
		System.out.println("Move Generation Time: " + moveGenTime + "\t\tMove Eval Time: " + moveEvalTime);
		System.out.println("Move Piece Time: " + movePieceTime + "\t\t\tMove Back Time: " + moveBackTime + "\t\t\tEvaluation Time: " + positionEvaluationTime);
		System.out.println("Transposition Lookup Time: " + transpositionTime + "\t\t\tBoard Info Time: " + boardInfoTime + "\t\tCheck Repetition Time: " + checkRepetitionTime + "\n");
		long sum = moveGenTime + moveEvalTime + movePieceTime + moveBackTime + positionEvaluationTime + transpositionTime + boardInfoTime + checkRepetitionTime;
		System.out.println("Tracked Time: " + sum);
	}
	*/
}
