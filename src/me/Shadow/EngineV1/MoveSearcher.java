package me.Shadow.EngineV1;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MoveSearcher
{
	short bestMove;
	short bestMoveCurrentIteration;
	int bestEval;
	int bestEvalCurrentIteration;
	boolean oneMoveSearched;
	int maxDepthReached;
	
	boolean searchCancelled;
	int timeLimit;
	
	Board board;
	MoveGenerator moveGen;
	MoveOrderer moveOrderer;
	TranspositionTable transpositionTable;
	short[][] generatedMoves;
	static final int MAX_DEPTH = 64;
	
	
	static final int positiveInfinity = 1_000_000;
	static final int negativeInfinity = -positiveInfinity;
	
	long startTime;
	long moveGenTime, moveEvalTime;
	long movePieceTime, moveBackTime;
	long transpositionTime, positionEvaluationTime, checkRepetitionTime;
	long boardInfoTime;
	
	int numPositions;
	int numTranspositions;
	
	
	public MoveSearcher(Board board, int timeLimitMS)
	{
		this.board = board;
		moveGen = new MoveGenerator(board);
		moveOrderer = new MoveOrderer();
		generatedMoves = new short[MAX_DEPTH][MoveGenerator.MAXIMUM_LEGAL_MOVES];
		timeLimit = timeLimitMS;
		transpositionTable = new TranspositionTable();
	}
	
	public short startSearch()
	{
		searchCancelled = false;
		Timer timer = new Timer();
		timer.schedule(new TimerTask()
		{
			@Override
			public void run()
			{
				searchCancelled = true;
			}
		}, timeLimit);
		
		clearSearchStats();
		bestMove = bestMoveCurrentIteration = MoveHelper.NULL_MOVE;
		bestEval = bestEvalCurrentIteration = negativeInfinity;
		oneMoveSearched = false;
		int depth = maxDepthReached = 0;
		moveOrderer.clearHistoryHeuristic();
		
		startTime = System.currentTimeMillis();
		while (!searchCancelled && depth < MAX_DEPTH)
		{
			depth++;
			search(depth, 0, negativeInfinity, positiveInfinity);
			
			if (oneMoveSearched)
			{
				bestMove = bestMoveCurrentIteration;
				bestEval = bestEvalCurrentIteration;
				
				if (bestEvalCurrentIteration >= (positiveInfinity - depth))
				{
					//System.out.println("Forced mate for engine found");
					break;
				}
				else if (bestEvalCurrentIteration <= (negativeInfinity + depth))
				{
					//System.out.println("Forced mate against engine found");
					break;
				}
			}
						
			bestMoveCurrentIteration = MoveHelper.NULL_MOVE;
			bestEvalCurrentIteration = negativeInfinity;
			oneMoveSearched = false;
		}
		maxDepthReached = depth;
				
		return bestMove;
	}
	
	public int search(int depth, int plyFromRoot, int alpha, int beta)
	{
		if (searchCancelled) return 0;
		
		if ((isDuplicatePosition() && plyFromRoot > 0) || board.boardInfo.getHalfMoves() >= 100)
		{
			return 0;
		}
		
		long tempTime = System.currentTimeMillis();
		int transposEval = transpositionTable.lookupEvaluation(board.boardInfo.getZobristHash(), depth, alpha, beta);
		transpositionTime += System.currentTimeMillis() - tempTime;
		if (transposEval != TranspositionTable.LOOKUP_FAILED)
		{
			numTranspositions++;
			
			if (plyFromRoot == 0)
			{
				bestMoveCurrentIteration = transpositionTable.lookupMove(board.boardInfo.getZobristHash());;
				bestEvalCurrentIteration = transposEval;
				oneMoveSearched = true;
			}
			
			return transposEval;
		}
		
		if (depth == 0)
		{
			return searchCaptures(alpha, beta);
		}
		
		tempTime = System.currentTimeMillis();
		// Move [] moves = new Move[MoveGenerator.MAXIMUM_LEGAL_MOVES];
		short [] moves = generatedMoves[plyFromRoot];
		int moveCount = moveGen.generateMoves(moves, false);
		moveGenTime += System.currentTimeMillis() - tempTime;
		
		if (moveCount == 0)
		{
			if (moveGen.inCheck)
				return negativeInfinity;
			else
				return 0;
		}
		
		tempTime = System.currentTimeMillis();
		short firstMove = (plyFromRoot == 0 ? bestMove : transpositionTable.lookupMove(board.boardInfo.getZobristHash()));
		moveOrderer.guessMoveEvals(board, moves, moveCount, firstMove, moveGen.enemyAttackMap, moveGen.enemyPawnAttackMap, false, plyFromRoot);
		moveEvalTime += System.currentTimeMillis() - tempTime;
		
		int bound = TranspositionTable.UPPER_BOUND;
		short bestMoveInPosition = MoveHelper.NULL_MOVE;
		for (int i = 0; i < moveCount; i++)
		{
			short move = moves[i];
			
			tempTime = System.currentTimeMillis();
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			boardInfoTime += System.currentTimeMillis() - tempTime;
			
			tempTime = System.currentTimeMillis();
			int captured = board.movePiece(move);
			movePieceTime += System.currentTimeMillis() - tempTime;
			
			int evaluation = -(search(depth - 1, plyFromRoot + 1, -beta, -alpha));
			
			tempTime = System.currentTimeMillis();
			board.moveBack(move, captured, boardInfoOld);
			moveBackTime += System.currentTimeMillis() - tempTime;
			
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
					bestEvalCurrentIteration = alpha;
					oneMoveSearched = true;
				}
			}
			
			if (evaluation >= beta)
			{
				// move was too good so opponent will avoid this branch
				bound = TranspositionTable.LOWER_BOUND;
				
				tempTime = System.currentTimeMillis();
				transpositionTable.storeEvaluation(board.boardInfo.getZobristHash(), beta, depth, bound, move);
				transpositionTime += System.currentTimeMillis() - tempTime;
				
				if (captured == PieceHelper.NONE) // ignore captures for killer moves
				{
					if (plyFromRoot < MoveOrderer.maxKillerDepth)
					{
						moveOrderer.killers[plyFromRoot].addKiller(move);
					}
					
					int colorIndex = board.boardInfo.isWhiteToMove() ? 0 : 1;
					// multiply the color index by 64*64 = 2^12
					// then add start square multiplied by 64 = 2^6
					// then add target square
					int index = (colorIndex << 12) | (MoveHelper.getStartIndex(move) << 6) | MoveHelper.getTargetIndex(move);
					moveOrderer.historyHeuristic[index] += depth*depth;
				}
				
				return beta;
			}
		}
		
		tempTime = System.currentTimeMillis();
		transpositionTable.storeEvaluation(board.boardInfo.getZobristHash(), alpha, depth, bound, bestMoveInPosition);
		transpositionTime += System.currentTimeMillis() - tempTime;
		
		return alpha;
	}
	
	public int searchCaptures(int alpha, int beta)
	{
		if (searchCancelled) return 0;
		
		// captures arent forced so check eval before capturing something
		int evaluation = staticEvaluation();
		
		if (evaluation >= beta)
		{
			return evaluation;
		}
		alpha = Math.max(alpha, evaluation);
		
		long tempTime = System.currentTimeMillis();
		short [] moves = new short[128];	// theoretical maximum captures + queen promotions is 72 (though likely far less)
		int moveCount = moveGen.generateMoves(moves, true);
		moveGenTime += System.currentTimeMillis() - tempTime;
		
		tempTime = System.currentTimeMillis();
		moveOrderer.guessMoveEvals(board, moves, moveCount, MoveHelper.NULL_MOVE, moveGen.enemyAttackMap, moveGen.enemyPawnAttackMap, true, 0);
		moveEvalTime += System.currentTimeMillis() - tempTime;
				
		for (int i = 0; i < moveCount; i++)
		{
			short move = moves[i];
			
			tempTime = System.currentTimeMillis();
			BoardInfo boardInfoOld = new BoardInfo(board.boardInfo);
			boardInfoTime += System.currentTimeMillis() - tempTime;
			
			tempTime = System.currentTimeMillis();
			int captured = board.movePiece(move);
			movePieceTime += System.currentTimeMillis() - tempTime;
			
			evaluation = -(searchCaptures(-beta, -alpha));
			
			tempTime = System.currentTimeMillis();
			board.moveBack(move, captured, boardInfoOld);
			moveBackTime += System.currentTimeMillis() - tempTime;

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
		long temp = System.currentTimeMillis();
		long zobristHash = board.boardInfo.getZobristHash();
		ArrayList<Long> positions = board.boardInfo.getPositionList();
		boolean duplicate = (positions.indexOf(zobristHash) != (positions.size()-1));
		checkRepetitionTime += System.currentTimeMillis() - temp;
		return duplicate;
	}
	
	public int staticEvaluation()
	{
		long tempTime = System.currentTimeMillis();
		numPositions++;
		double evaluation = 0;
		evaluation = board.boardInfo.getWhiteMaterial() + board.boardInfo.getWhiteSquareBonus();
		evaluation -= (board.boardInfo.getBlackMaterial() + board.boardInfo.getBlackSquareBonus());
		
		int whiteKingIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[PieceHelper.WHITE_KING]);
		int blackKingIndex = Bitboards.getLSB(board.bitBoards.pieceBoards[PieceHelper.BLACK_KING]);
		evaluation += forceKingToCorner(whiteKingIndex, blackKingIndex, 1 - (board.boardInfo.getBlackMaterial() / 2000));
		evaluation -= forceKingToCorner(blackKingIndex, whiteKingIndex, 1 - (board.boardInfo.getWhiteMaterial() / 2000));
		
		evaluation *= board.boardInfo.isWhiteToMove() ? 1 : -1;
		
		positionEvaluationTime += System.currentTimeMillis() - tempTime;
		return (int)evaluation;
	}

	public int forceKingToCorner(int friendlyKingIndex, int enemyKingIndex, float endgameWeight)
	{
		if (endgameWeight < 0)
			return 0;

		double evaluation = 0;

		int enemyRank = 8 - (enemyKingIndex / 8);
		int enemyFile = (enemyKingIndex % 8) + 1;
		int distToCenter = Math.max(4 - enemyRank, enemyRank - 5) + Math.max(4 - enemyFile, enemyFile - 5);
		evaluation += distToCenter;

		int friendlyRank = 8 - (friendlyKingIndex / 8);
		int friendlyFile = (friendlyKingIndex % 8) + 1;
		int distBetweenKings = Math.abs(friendlyRank - enemyRank) + Math.abs(friendlyFile - enemyFile);
		evaluation += (distBetweenKings);

		return (int) (evaluation * endgameWeight);
	}
	
	public void clearSearchStats()
	{
		numPositions = numTranspositions = 0;
		moveGenTime = moveEvalTime = movePieceTime = moveBackTime = positionEvaluationTime = transpositionTime = boardInfoTime = checkRepetitionTime = 0;
	}
	
	public void printSearchStats()
	{
		System.out.println("Evaluation time: " + (System.currentTimeMillis() - startTime) + "\t\t\tEvaluation for Engine: " + bestEval + "\t\tEvaluation Depth: " + maxDepthReached);
		System.out.println("Number of positions: " + numPositions + "\t\tNumber of Transpositions: " + numTranspositions + "\t\tStored Transpositions: " + transpositionTable.numStored);
		System.out.println("Move Generation Time: " + moveGenTime + "\t\tMove Eval Time: " + moveEvalTime);
		System.out.println("Move Piece Time: " + movePieceTime + "\t\t\tMove Back Time: " + moveBackTime + "\t\t\tEvaluation Time: " + positionEvaluationTime);
		System.out.println("Transposition Lookup Time: " + transpositionTime + "\t\t\tBoard Info Time: " + boardInfoTime + "\t\tCheck Repetition Time: " + checkRepetitionTime + "\n");
		long sum = moveGenTime + moveEvalTime + movePieceTime + moveBackTime + positionEvaluationTime + transpositionTime + boardInfoTime + checkRepetitionTime;
		System.out.println("Tracked Time: " + sum);
	}
}
