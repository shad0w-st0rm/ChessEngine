package me.Shadow.Engine;

public class MoveSearcher
{
	static final int MAX_DEPTH = 64;
	static final int MAX_PV_LENGTH = 2*MAX_DEPTH;
	
	// ~16.6 million (fits evenly in move ordering)
	static final int MAX_HIST_SCORE = (1 << 24);

	static final int positiveInfinity = 0x3FFF;
	static final int negativeInfinity = -positiveInfinity;
	
	static final short PV_NODE = 0;
	static final short CUT_NODE = 1;
	static final short ALL_NODE = 2;
	static final short UNKNOWN_NODE = 3;

	short bestMove;

	boolean searchCancelled;

	private Board board;
	private Evaluation evaluator;
	private MoveGenerator moveGen;
	private MoveOrderer moveOrderer;
	private TranspositionTable transpositionTable;
	private short[] moves;
	
	private SearcherStats currentStats;

	public MoveSearcher(Board board)
	{
		this.board = board;
		moves = new short[1024];
		moveGen = new MoveGenerator(board, moves);
		moveOrderer = new MoveOrderer(board, moves);
		transpositionTable = new TranspositionTable();
		evaluator = new Evaluation(board, transpositionTable);
		bestMove = MoveHelper.NULL_MOVE;
	}

	public short startSearch()
	{
		return startSearchDepth(MAX_DEPTH);
	}
	
	public short startSearchDepth(int searchDepth)
	{
		// bookkeping to ensure search doesn't go infinite by accident
		searchDepth = Math.min(searchDepth, MAX_DEPTH);
		searchDepth = Math.max(searchDepth, 0);
		
		searchCancelled = false;

		// clearSearchStats();
		bestMove = MoveHelper.NULL_MOVE;
		moveOrderer.clearHistoryHeuristic();
		moveOrderer.clearKillers();
		int depth = 0;
		int eval = 0;
		
		short [] pvLine = new short[MAX_PV_LENGTH];
		
		do
		{
			depth++;
			
			long time = System.currentTimeMillis();
			currentStats = new SearcherStats();
			
			eval = rootSearch(depth, negativeInfinity, positiveInfinity, pvLine);
			
			currentStats.depth = depth;
			currentStats.evaluation = eval;
			currentStats.move = bestMove;
			currentStats.pvLine = pvLine;
			currentStats.time = System.currentTimeMillis() - time;
			
			currentStats.printSearchStats();

			if (eval >= (positiveInfinity - depth))
			{
				//System.out.println("Forced mate for engine found");
				break;
			}
			else if (eval <= (negativeInfinity + depth))
			{
				//System.out.println("Forced mate against engine found");
				break;
			}
		}
		while (!searchCancelled && depth < searchDepth);
		
		return bestMove;
	}

	public int rootSearch(final int depth, int alpha, final int beta, short [] pvLine)
	{
		currentStats.nodes++;
		
		int numMoves = moveGen.generateMoves(MoveGenerator.ALL_MOVES, 0);

		// checkmate/stalemate
		if (numMoves == 0)
			return moveGen.inCheck() ? negativeInfinity : 0;

		moveOrderer.guessMoveEvals(pvLine[0], bestMove, false, 0, 0, numMoves);
		
		int bound = ALL_NODE;
		long zobristHash = board.zobristHash;
		long pawnsHash = board.pawnsHash;
		short[] boardInfoOld = board.packBoardInfo();
		int bestEval = negativeInfinity;
		for (int i = 0; i < numMoves; i++)
		{
			moveOrderer.singleSelectionSort(i, numMoves);

			final short move = moves[i];

			int evaluation = searchMove(move, alpha, beta, depth, 0, i, zobristHash, pawnsHash, boardInfoOld, numMoves, pvLine);

			if (searchCancelled)
			{
				if (bestMove == MoveHelper.NULL_MOVE)
					bestMove = move;
				return evaluation;
			}

			if (evaluation >= beta)
			{
				currentStats.cutNodes++;
				currentStats.betaCuts++;
				if (i == 0) currentStats.betaFirstMoveCuts++;
				if (MoveHelper.isCapture(board, move))
				{
					currentStats.betaCutCaptures++;
					if (i == 0) currentStats.betaFirstMoveCaptures++;
				}
				else
				{
					currentStats.betaCutQuiets++;
					if (i == 0) currentStats.betaFirstMoveQuiets++;
				}
				
				currentStats.movesSearched += i + 1;
				currentStats.cutNodeMovesSearched += i + 1;
				currentStats.betaCutPriorMoves += i;
				
				// move was too good so opponent will avoid this branch
				transpositionTable.storeEvaluation(board.zobristHash, evaluation, depth, CUT_NODE,
						move);
				
				// fail soft
				return evaluation;
			}
			
			bestEval = Math.max(evaluation, bestEval);
			
			if (evaluation > alpha)
			{
				currentStats.raiseAlphaMoves++;
				if (MoveHelper.isCapture(board, move))
				{
					currentStats.raiseAlphaCaptures++;
				}
				else
				{
					currentStats.raiseAlphaQuiets++;
				}
				
				bound = PV_NODE;
				alpha = evaluation;

				bestMove = move;
			}
		}
		
		currentStats.movesSearched += numMoves;
		if (bound == ALL_NODE)
		{
			currentStats.allNodes++;
			currentStats.allNodeMovesSearched += numMoves;
		}
		else
		{
			currentStats.pvNodes++;
			currentStats.pvNodeMovesSearched += numMoves;
		}
		
		// store fail soft
		transpositionTable.storeEvaluation(board.zobristHash, bestEval, depth, bound, bestMove);

		// fail soft
		return bestEval;
	}

	public int search(final int depth, final int plyFromRoot, int alpha, int beta, int moveIndex, short [] pvLine)
	{
		if (searchCancelled)
			return 0;

		if (isDuplicatePosition() || board.halfMoves >= 100)
		{
			return 0;
		}
		
		if (depth == 0)
			return searchCaptures(alpha, beta, moveIndex);
		
		currentStats.nodes++;

		long zobristHash = board.zobristHash;
		short bestMoveInPosition = MoveHelper.NULL_MOVE;
		short[] transposition = transpositionTable.getEntry(zobristHash);

		if (transposition != null)
		{
			bestMoveInPosition = transposition[TranspositionTable.MOVE_INDEX];
			if (transposition[TranspositionTable.DEPTH_INDEX] >= depth)
			{
				
				short bound = transposition[TranspositionTable.BOUND_INDEX];
				short eval = transposition[TranspositionTable.EVAL_INDEX];
				if (bound == PV_NODE)
				{
					currentStats.pvNodes++;
					pvLine[plyFromRoot] = bestMoveInPosition;
					return transposition[TranspositionTable.EVAL_INDEX];
				}
				else if (bound == CUT_NODE)
				{
					if (eval >= beta)
					{
						currentStats.cutNodes++;
						currentStats.betaCuts++;
						currentStats.betaCutTT++;
						
						// fail soft
						return eval;
					}
					else
						alpha = Math.max(alpha, eval);
				}
				else if (bound == ALL_NODE)
				{
					if (eval <= alpha)
					{
						currentStats.allNodes++;
						
						// fail soft
						return eval;
					}
					else
						beta = Math.max(beta, eval);
				}
			}
		}
		
		int bound = ALL_NODE;
		boolean searchedFirst = false;
		long pawnsHash = board.pawnsHash;
		short[] boardInfoOld = board.packBoardInfo();
		int bestEval = negativeInfinity;

		if (bestMoveInPosition != MoveHelper.NULL_MOVE)
		{
			searchedFirst = true;

			int evaluation = searchMove(bestMoveInPosition, alpha, beta, depth, plyFromRoot, 0, zobristHash, pawnsHash,
					boardInfoOld, moveIndex, pvLine);

			if (searchCancelled)
				return evaluation;
			
			bestEval = evaluation;

			if (evaluation >= beta)
			{
				currentStats.cutNodes++;
				currentStats.betaCuts++;
				currentStats.betaPreMoveGenCuts++;
				if (MoveHelper.isCapture(board, bestMoveInPosition))
				{
					currentStats.betaCutCaptures++;
					currentStats.betaPreMoveGenCaptures++;
				}
				else
				{
					currentStats.betaCutQuiets++;
					currentStats.betaPreMoveGenQuiets++;
				}
				
				currentStats.movesSearched += 1;
				currentStats.cutNodeMovesSearched += 1;
				currentStats.betaCutPriorMoves += 0;
				
				// move was too good so opponent will avoid this branch
				// store fail soft
				transpositionTable.storeEvaluation(zobristHash, evaluation, depth, CUT_NODE,
						bestMoveInPosition);

				// fail soft
				return evaluation;
			}
			
			if (evaluation > alpha)
			{
				currentStats.raiseAlphaMoves++;
				if (MoveHelper.isCapture(board, bestMoveInPosition))
				{
					currentStats.raiseAlphaCaptures++;
				}
				else
				{
					currentStats.raiseAlphaQuiets++;
				}
								
				bound = PV_NODE;
				alpha = evaluation;
			}
		}

		int numMoves = moveGen.generateMoves(MoveGenerator.ALL_MOVES, moveIndex);

		// checkmate/stalemate
		if (numMoves == 0)
			return moveGen.inCheck() ? negativeInfinity : 0;

		// ensure the potentially first searched move gets boosted to top with highest
		// score
		moveOrderer.guessMoveEvals(pvLine[plyFromRoot], bestMoveInPosition, false, plyFromRoot, moveIndex, numMoves);
		// push the first searched move to the top (guaranteed to be highest score)
		if (searchedFirst)
			moveOrderer.singleSelectionSort(moveIndex, moveIndex + numMoves);

		// ignore first move if we already searched it
		for (int i = moveIndex + (searchedFirst ? 1 : 0); i < (moveIndex + numMoves); i++)
		{
			moveOrderer.singleSelectionSort(i, moveIndex + numMoves);

			final short move = moves[i];

			int evaluation = searchMove(move, alpha, beta, depth, plyFromRoot, i - moveIndex, zobristHash, pawnsHash,
					boardInfoOld, moveIndex + numMoves, pvLine);

			if (searchCancelled)
				return evaluation;

			if (evaluation >= beta)
			{
				currentStats.cutNodes++;
				currentStats.betaCuts++;
				if (i == moveIndex) currentStats.betaFirstMoveCuts++;
				if (MoveHelper.isCapture(board, move))
				{
					currentStats.betaCutCaptures++;
					if (i == moveIndex) currentStats.betaFirstMoveCaptures++;
				}
				else
				{
					currentStats.betaCutQuiets++;
					if (i == moveIndex) currentStats.betaFirstMoveQuiets++;
				}
				
				currentStats.movesSearched += i - moveIndex + 1;
				currentStats.cutNodeMovesSearched += i - moveIndex + 1;
				currentStats.betaCutPriorMoves += i - moveIndex;
				
				// move was too good so opponent will avoid this branch
				// store fail soft
				transpositionTable.storeEvaluation(zobristHash, evaluation, depth, CUT_NODE, move);
				// fail soft
				return evaluation;
			}
			
			bestEval = Math.max(evaluation, bestEval);
			
			if (evaluation > alpha)
			{
				currentStats.raiseAlphaMoves++;
				if (MoveHelper.isCapture(board, move))
				{
					currentStats.raiseAlphaCaptures++;
				}
				else
				{
					currentStats.raiseAlphaQuiets++;
				}
				
				bound = PV_NODE;
				alpha = evaluation;
				bestMoveInPosition = move;
			}
		}
		
		currentStats.movesSearched += numMoves;
		if (bound == ALL_NODE)
		{
			currentStats.allNodes++;
			currentStats.allNodeMovesSearched += numMoves;
		}
		else
		{
			currentStats.pvNodes++;
			currentStats.pvNodeMovesSearched += numMoves;
		}

		// store fail soft
		transpositionTable.storeEvaluation(zobristHash, bestEval, depth, bound, bestMoveInPosition);

		// fail soft
		return bestEval;
	}

	public int searchMove(short move, int alpha, int beta, int depth, int plyFromRoot, int moveNum, long zobristHash, long pawnsHash, short[] boardInfoOld, int moveIndex, short [] pvLine)
	{
		final byte captured = board.movePiece(move);
		
		int searchDepth = calculateSearchDepth(move, captured, depth, moveNum);
		short [] continuedPvLine = new short[MAX_PV_LENGTH];

		int evaluation = -(search(searchDepth, plyFromRoot + 1, -beta, -alpha, moveIndex, continuedPvLine));

		board.moveBack(move, captured, zobristHash, pawnsHash, boardInfoOld);

		if (evaluation > (positiveInfinity - depth) || evaluation < (negativeInfinity + depth))
			evaluation += ((evaluation > 0) ? -1 : 1);

		if (searchCancelled)
			return 0;

		if (evaluation >= beta)
		{
			if (captured == PieceHelper.NONE) // ignore captures for killer moves
			{
				if (plyFromRoot < MoveOrderer.maxKillerDepth)
				{
					moveOrderer.addKiller(move, plyFromRoot);
				}

				// keep start/target square and add color to move for index
				//int index = (move & 0xFFF) | (board.colorToMove << 12);
				int index = (board.squares[MoveHelper.getStartIndex(move)] << 6) | MoveHelper.getTargetIndex(move);
				moveOrderer.historyHeuristic[index] += depth * depth;
				
				if (moveOrderer.historyHeuristic[index] >= MAX_HIST_SCORE)
				{
					for (int i = 0; i < moveOrderer.historyHeuristic.length; i++)
					{
						// scale values with respect to middle of max hist score
						int newValue = moveOrderer.historyHeuristic[index] - (MAX_HIST_SCORE >>> 1);
						newValue >>>= 3;
						moveOrderer.historyHeuristic[index] = newValue + (MAX_HIST_SCORE >>> 1);
					}
				}
			}
		}
		else if (evaluation > alpha)
		{
			pvLine[plyFromRoot] = move;
			for (int i = plyFromRoot + 1; i < continuedPvLine.length; i++)
			{
				if (continuedPvLine[i] != MoveHelper.NULL_MOVE) pvLine[i] = continuedPvLine[i];
				else
				{
					pvLine[i] = MoveHelper.NULL_MOVE;
					break;
				}
			}
		}

		return evaluation;
	}

	public int calculateSearchDepth(short move, int captured, int depth, int moveNum)
	{
		// base case, no extensions or reductions
		//return depth - 1;
		
		depth--;
		//if (board.inCheck())
			//depth++;
		
		// give bonus to a pawn moving to one square from promotion
		// maybe change this just to a promotion move itself
		
		/*
		int target = MoveHelper.getTargetIndex(move);
		if (((board.squares[target] & PieceHelper.TYPE_MASK) == PieceHelper.PAWN)
				&& (Utils.getSquareRank(target) == 2 || Utils.getSquareRank(target) == 7))
		{
			return depth + 1;
		}
		*/
		
		if (moveNum >= 3 && captured == PieceHelper.NONE && depth >= 2) depth--;
		
		return Math.max(depth, 0);
	}

	public int searchCaptures(int alpha, int beta, int moveIndex)
	{
		if (searchCancelled)
			return 0;

		long zobristHash = board.zobristHash;
		short bestMoveInPosition = MoveHelper.NULL_MOVE;
		short[] transposition = transpositionTable.getEntry(zobristHash);

		if (transposition != null)
		{
			// transposition depth is always greater than 0
			bestMoveInPosition = transposition[TranspositionTable.MOVE_INDEX];
			short bound = transposition[TranspositionTable.BOUND_INDEX];
			short eval = transposition[TranspositionTable.EVAL_INDEX];
			if (bound == PV_NODE)
			{
				return transposition[TranspositionTable.EVAL_INDEX];
			}
			else if (bound == CUT_NODE)
			{
				// fail soft
				if (eval >= beta)
					return eval;
				else
					alpha = Math.max(alpha, eval);
			}
			else if (bound == ALL_NODE)
			{
				// fail soft
				if (eval <= alpha)
					return eval;
				else
					beta = Math.max(beta, eval);
			}
		}

		// captures arent forced so check eval before capturing something
		int evaluation = evaluator.staticEvaluation();

		if (evaluation >= beta)
		{
			// fail soft
			return evaluation;
		}
		
		alpha = Math.max(alpha, evaluation);

		int numMoves = moveGen.generateMoves(MoveGenerator.CAPTURES_ONLY, moveIndex);

		moveOrderer.guessMoveEvals(MoveHelper.NULL_MOVE, bestMoveInPosition, true, 0, moveIndex, numMoves);

		long pawnsHash = board.pawnsHash;
		final short[] boardInfoOld = board.packBoardInfo();

		for (int i = moveIndex; i < (moveIndex + numMoves); i++)
		{
			moveOrderer.singleSelectionSort(i, (moveIndex + numMoves));

			final short move = moves[i];

			final byte captured = board.movePiece(move);
			evaluation = -(searchCaptures(-beta, -alpha, moveIndex + numMoves));
			board.moveBack(move, captured, zobristHash, pawnsHash, boardInfoOld);

			if (searchCancelled)
				return 0;

			if (evaluation >= beta)
			{
				// fail soft
				// move was too good so opponent will avoid this branch
				return evaluation;
			}
			alpha = Math.max(alpha, evaluation);
		}

		// fail soft
		return evaluation;
	}

	public boolean isDuplicatePosition()
	{
		long zobristHash = board.repetitionHistory[board.repetitionIndex];
		for (int i = board.repetitionIndex - 2; i >= 0; i -= 2)
		{
			if (board.repetitionHistory[i] == zobristHash)
			{
				return true;
			}
		}
		return false;
	}

	public void stopSearch()
	{
		searchCancelled = true;
	}
	
	class SearcherStats
	{
		int depth, evaluation;
		short move;
		short [] pvLine;
		long time;
		
		int ttLookups;
		int ttHits;
		int ttMisses, ttDepthLow;
		
		// done
		int betaCuts, betaCutTT, betaCutCaptures, betaCutQuiets;
		
		// done
		int betaPreMoveGenCuts, betaPreMoveGenCaptures, betaPreMoveGenQuiets;
		
		// done
		int betaFirstMoveCuts, betaFirstMoveCaptures, betaFirstMoveQuiets;
		
		
		int betaCutPriorMoves, betaCutPriorCaptures, betaCutPriorQuiets;
		
		// done
		int raiseAlphaMoves, raiseAlphaCaptures, raiseAlphaQuiets;
		
		// done
		int nodes, pvNodes, cutNodes, allNodes;
		
		// done
		int movesSearched, pvNodeMovesSearched, cutNodeMovesSearched, allNodeMovesSearched;
		
		public void printSearchStats()
		{
			System.out.println("+------------------------------------------------------------------------------------------------------------------------+");
			System.out.println("Search completed to depth " + depth + " with evaluation " + evaluation);
			System.out.println("Best Move " + MoveHelper.toString(move) + " found in " + time + " ms");
			System.out.println(getPvLine());
			System.out.println(nodes + " Nodes Searched -- " + pvNodes + " PV Nodes -- " + cutNodes + " Cut Nodes -- " + allNodes + " All Nodes");
			System.out.println(movesSearched + " Moves Searched -- " + pvNodeMovesSearched + " PV Node Moves -- " + cutNodeMovesSearched + " Cut Node Moves -- " + allNodeMovesSearched + " All Node Moves");
			System.out.println(betaCuts + " # Beta Cuts -- " + betaCutTT + " TT Cuts -- " + betaCutCaptures + " Captures -- " + betaCutQuiets + " Quiets");
			System.out.println(betaPreMoveGenCuts + " # Beta Cuts Pre MoveGen -- " + betaPreMoveGenCaptures + " Captures -- " + betaPreMoveGenQuiets + " Quiets");
			System.out.println(betaFirstMoveCuts + " # Beta Cuts First Move -- " + betaFirstMoveCaptures + " Captures -- " + betaFirstMoveQuiets + " Quiets");
			System.out.println(raiseAlphaMoves + " # Moves Raising Alpha -- " + raiseAlphaCaptures + " Captures -- " + raiseAlphaQuiets + " Quiets");
			System.out.println("+------------------------------------------------------------------------------------------------------------------------+\n");
		}
		
		public String getPvLine()
		{
			String pvLine = "PV Line: ";
			for (short move : this.pvLine)
			{
				if (move != MoveHelper.NULL_MOVE)
				{
					pvLine += MoveHelper.toString(move) + " ";
				}
				else break;
			}
			return pvLine;
		}
	}
}
