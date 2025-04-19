package me.Shadow.Engine;

public class MoveSearcher
{
	static final int MAX_DEPTH = 64;
	static final int MAX_PV_LENGTH = 2 * MAX_DEPTH;

	// ~16.6 million (fits evenly in move ordering)
	static final int MAX_HIST_SCORE = (1 << 24);

	static final int positiveInfinity = 0x3FFF;
	static final int negativeInfinity = -positiveInfinity;

	static final short PV_NODE = 0;
	static final short CUT_NODE = 1;
	static final short ALL_NODE = 2;
	static final short UNK_NODE = ALL_NODE;

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
		moveOrderer.clearHistoryHeuristic();
		moveOrderer.clearKillers();
		int depth = 0;
		int eval = 0;
		short bestMove = MoveHelper.NULL_MOVE;

		short[] pvLine = new short[MAX_PV_LENGTH];

		do
		{
			depth++;

			long time = System.currentTimeMillis();
			currentStats = new SearcherStats();

			SearchContext context = new SearchContext();
			context.setAlphaBetaParams(depth, 0, negativeInfinity, positiveInfinity);
			context.setBestEvalMoveParams(negativeInfinity, bestMove, pvLine, UNK_NODE);
			context.setBoardInfoParams(board.zobristHash, board.pawnsHash, board.packBoardInfo());
			context.setMovesParams(0, 0, 0);

			eval = search(context);
			
			bestMove = context.bestMove;

			currentStats.depth = depth;
			currentStats.evaluation = eval;
			currentStats.move = bestMove;
			currentStats.pvLine = pvLine;
			currentStats.time = System.currentTimeMillis() - time;

			//currentStats.printSearchStats();

			if (eval >= (positiveInfinity - depth))
			{
				// System.out.println("Forced mate for engine found");
				break;
			}
			else if (eval <= (negativeInfinity + depth))
			{
				// System.out.println("Forced mate against engine found");
				break;
			}
		}
		while (!searchCancelled && depth < searchDepth);

		return bestMove;
	}

	public int search(SearchContext context)
	{
		if (context.plyFromRoot > 0 && searchCancelled)
			return 0;

		if (context.plyFromRoot > 0 && (isDuplicatePosition() || board.halfMoves >= 100))
		{
			return 0;
		}

		if (context.depth == 0)
			return searchCaptures(context.alpha, context.beta, context.movesStartIndex);

		currentStats.nodes++;

		short[] transposition = transpositionTable.getEntry(context.zobristHash);
		if (context.plyFromRoot > 0 && transposition != null)
		{
			context.bestMove = transposition[TranspositionTable.MOVE_INDEX];
			if (transposition[TranspositionTable.DEPTH_INDEX] >= context.depth)
			{

				short bound = transposition[TranspositionTable.BOUND_INDEX];
				short eval = transposition[TranspositionTable.EVAL_INDEX];
				if (bound == PV_NODE)
				{
					currentStats.pvNodes++;
					context.pvLine[context.plyFromRoot] = context.bestMove;
					return transposition[TranspositionTable.EVAL_INDEX];
				}
				else if (bound == CUT_NODE)
				{
					if (eval >= context.beta)
					{
						currentStats.cutNodes++;
						currentStats.betaCuts++;
						currentStats.betaCutTT++;

						// fail soft
						return eval;
					}
					else
					{
						context.alpha = Math.max(context.alpha, eval);
					}
				}
				else if (bound == ALL_NODE)
				{
					if (eval <= context.alpha)
					{
						currentStats.allNodes++;

						// fail soft
						return eval;
					}
					else
						context.beta = Math.min(context.beta, eval);
				}
			}
		}
		
		if (context.plyFromRoot > 0 && context.bestMove != MoveHelper.NULL_MOVE)
		{
			searchMove(context.bestMove, context, context.movesStartIndex);

			if (searchCancelled)
				return 0;

			if (context.bestEval >= context.beta)
			{
				// fail soft
				return context.bestEval;
			}
		}
		
		int numMoves = moveGen.generateMoves(MoveGenerator.ALL_MOVES, context.movesStartIndex);

		// checkmate/stalemate
		if (numMoves == 0)
			return moveGen.inCheck() ? negativeInfinity : 0;
		
		//if searched a move already, push it to top
		moveOrderer.guessMoveEvals(context.pvLine[context.plyFromRoot], context.bestMove, false, context.plyFromRoot, context.movesStartIndex, numMoves);
		
		// push the first searched move to the top (guaranteed to be highest score)
		if (context.numMovesSearched > 0)
		{
			moveOrderer.singleSelectionSort(context.movesStartIndex, context.movesStartIndex + numMoves);
		}

		// ignore first move if we already searched it
		for (int i = context.movesStartIndex + context.numMovesSearched; i < (context.movesStartIndex + numMoves); i++)
		{
			moveOrderer.singleSelectionSort(i, context.movesStartIndex + numMoves);

			final short move = moves[i];
			
			searchMove(move, context, context.movesStartIndex + numMoves);

			if (searchCancelled)
			{
				if (context.plyFromRoot == 0 && context.bestMove == MoveHelper.NULL_MOVE)
					context.bestMove = move;
				return 0;
			}
			
			if (context.bestEval >= context.beta)
			{
				// fail soft
				return context.bestEval;
			}
		}

		currentStats.movesSearched += numMoves;
		if (context.nodeType == ALL_NODE || context.nodeType == UNK_NODE)
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
		transpositionTable.storeEvaluation(context.zobristHash, context.bestEval, context.depth, context.nodeType,
				context.bestMove);

		// fail soft
		return context.bestEval;
	}

	public void searchMove(short move, SearchContext context, int nextMoveGenIndex)
	{
		context.numMovesSearched++;

		final byte captured = board.movePiece(move);

		short[] continuedPvLine = new short[MAX_PV_LENGTH];

		SearchContext newContext = new SearchContext();
		newContext.setAlphaBetaParams(context.depth - 1, context.plyFromRoot + 1, -context.beta, -context.alpha);
		// TODO: set expected child node type based on parent type
		newContext.setBestEvalMoveParams(negativeInfinity, MoveHelper.NULL_MOVE, continuedPvLine, UNK_NODE);
		newContext.setBoardInfoParams(board.zobristHash, board.pawnsHash, board.packBoardInfo());
		newContext.setMovesParams(0, context.numExtensions, nextMoveGenIndex);
		
		searchExtensionsReductions(move, captured, context, newContext);

		int evaluation = -(search(newContext));

		board.moveBack(move, captured, context.zobristHash, context.pawnsHash, context.boardInfo);

		if (evaluation > (positiveInfinity - context.depth) || evaluation < (negativeInfinity + context.depth))
			evaluation += ((evaluation > 0) ? -1 : 1);
		
		context.bestEval = Math.max(evaluation, context.bestEval);

		if (evaluation >= context.beta)
		{
			if (captured == PieceHelper.NONE) // ignore captures for killer moves
			{
				if (context.plyFromRoot < MoveOrderer.maxKillerDepth)
				{
					moveOrderer.addKiller(move, context.plyFromRoot);
				}

				// keep start/target square and add color to move for index
				int index = (move & 0xFFF) | (board.colorToMove << 12);
				// int index = (board.squares[MoveHelper.getStartIndex(move)] << 6) |
				// MoveHelper.getTargetIndex(move);
				moveOrderer.historyHeuristic[index] += context.depth * context.depth;

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

			currentStats.cutNodes++;
			currentStats.betaCuts++;
			if (context.numMovesSearched == 0)
				currentStats.betaFirstMoveCuts++;

			if (MoveHelper.isCapture(board, move))
			{
				currentStats.betaCutCaptures++;
				if (context.numMovesSearched == 0)
					currentStats.betaFirstMoveCaptures++;
			}
			else
			{
				currentStats.betaCutQuiets++;
				if (context.numMovesSearched == 0)
					currentStats.betaFirstMoveQuiets++;
			}

			currentStats.movesSearched += context.numMovesSearched;
			currentStats.cutNodeMovesSearched += context.numMovesSearched;
			currentStats.betaCutPriorMoves += context.numMovesSearched - 1;

			context.nodeType = CUT_NODE;

			// move was too good so opponent will avoid this branch
			// store fail soft
			transpositionTable.storeEvaluation(board.zobristHash, evaluation, context.depth, context.nodeType, move);
		}
		else if (evaluation > context.alpha)
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

			context.nodeType = PV_NODE;
			context.alpha = evaluation;
			context.bestMove = move;
			
			context.pvLine[context.plyFromRoot] = move;
			for (int i = context.plyFromRoot + 1; i < continuedPvLine.length; i++)
			{
				if (continuedPvLine[i] != MoveHelper.NULL_MOVE)
					context.pvLine[i] = continuedPvLine[i];
				else
				{
					context.pvLine[i] = MoveHelper.NULL_MOVE;
					break;
				}
			}
		}
	}

	public void searchExtensionsReductions(short move, int captured, SearchContext parentContext, SearchContext childContext)
	{
		if (childContext.numExtensions < 3)
		{
			if (board.inCheck())
			{
				childContext.numExtensions++;
				childContext.depth++;
				// do not extend and reduce at the same node
				return;
			}
			else
			{
				// give bonus to a pawn moving to one square from promotion
				// maybe change this just to a promotion move itself
				int target = MoveHelper.getTargetIndex(move);
				if (((board.squares[target] & PieceHelper.TYPE_MASK) == PieceHelper.PAWN)
						&& (Utils.getSquareRank(target) == 2 || Utils.getSquareRank(target) == 7))
				{
					childContext.numExtensions++;
					childContext.depth++;
					// do not extend and reduce at the same node
					return;
				}
			}
		}
		
		if (parentContext.numMovesSearched >= 3 && captured == PieceHelper.NONE && childContext.depth >= 2)
		{
			// avoid negative search depths
			// TODO: Not necessary due to already checked depth condition
			childContext.depth = Math.max(0, childContext.depth - 1);
		}
	}

	public int searchCaptures(int alpha, int beta, int moveIndex)
	{
		if (searchCancelled)
			return 0;

		long zobristHash = board.zobristHash;
		short bestMoveInPosition = MoveHelper.NULL_MOVE;
		short[] transposition = transpositionTable.getEntry(zobristHash);
		int bestEval = negativeInfinity;

		if (transposition != null)
		{
			// transposition depth is always greater than equal 0
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
				{
					alpha = Math.max(alpha, eval);
				}
			}
			else if (bound == ALL_NODE)
			{
				// fail soft
				if (eval <= alpha)
					return eval;
				else
					beta = Math.min(beta, eval);
			}
		}

		// captures arent forced so check eval before capturing something
		int evaluation = evaluator.staticEvaluation();

		if (evaluation >= beta)
		{
			// fail soft
			return evaluation;
		}

		bestEval = Math.max(bestEval, evaluation);
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
			bestEval = Math.max(evaluation, bestEval);
			alpha = Math.max(alpha, evaluation);
		}

		// fail soft
		return bestEval;
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

	class SearchContext
	{
		int depth, plyFromRoot;
		int alpha, beta;
		
		int bestEval;
		short bestMove;
		short[] pvLine;
		int nodeType;
		
		long zobristHash;
		long pawnsHash;
		short[] boardInfo;

		int numMovesSearched;
		int numExtensions;
		int movesStartIndex;

		public SearchContext()
		{
			setAlphaBetaParams(0, 0, negativeInfinity, positiveInfinity);
			setBestEvalMoveParams(negativeInfinity, MoveHelper.NULL_MOVE, null, UNK_NODE);
			setBoardInfoParams(0, 0, null);
			setMovesParams(0, 0, 0);
		}

		public SearchContext(int depth, int plyFromRoot, int bestEval, short bestMove, int alpha, int beta,
				short[] pvLine, long zobristHash, long pawnsHash, short[] boardInfo, int numMovesSearched, int numExtensions, 
				int movesStartIndex, int nodeType)
		{
			setAlphaBetaParams(depth, plyFromRoot, alpha, beta);
			setBestEvalMoveParams(bestEval, bestMove, pvLine, nodeType);
			setBoardInfoParams(zobristHash, pawnsHash, boardInfo);
			setMovesParams(numMovesSearched, numExtensions, movesStartIndex);
		}

		public void setAlphaBetaParams(int depth, int plyFromRoot, int alpha, int beta)
		{
			this.depth = depth;
			this.plyFromRoot = plyFromRoot;
			this.alpha = alpha;
			this.beta = beta;
		}

		public void setBestEvalMoveParams(int bestEval, short bestMove, short[] pvLine, int nodeType)
		{
			this.bestEval = bestEval;
			this.bestMove = bestMove;
			this.pvLine = pvLine;
			this.nodeType = nodeType;
		}

		public void setBoardInfoParams(long zobristHash, long pawnsHash, short[] boardInfo)
		{
			this.zobristHash = zobristHash;
			this.pawnsHash = pawnsHash;
			this.boardInfo = boardInfo;
		}
		
		public void setMovesParams(int numMovesSearched, int numExtensions, int movesStartIndex)
		{
			this.numMovesSearched = numMovesSearched;
			this.numExtensions = 0;
			this.movesStartIndex = movesStartIndex;
		}
	}

	class SearcherStats
	{
		int depth, evaluation;
		short move;
		short[] pvLine;
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
			System.out.println(
					"+------------------------------------------------------------------------------------------------------------------------+");
			System.out.println("Search completed to depth " + depth + " with evaluation " + evaluation);
			System.out.println("Best Move " + MoveHelper.toString(move) + " found in " + time + " ms");
			System.out.println(getPvLine());
			System.out.println(nodes + " Nodes Searched -- " + pvNodes + " PV Nodes -- " + cutNodes + " Cut Nodes -- "
					+ allNodes + " All Nodes");
			System.out.println(movesSearched + " Moves Searched -- " + pvNodeMovesSearched + " PV Node Moves -- "
					+ cutNodeMovesSearched + " Cut Node Moves -- " + allNodeMovesSearched + " All Node Moves");
			System.out.println(betaCuts + " # Beta Cuts -- " + betaCutTT + " TT Cuts -- " + betaCutCaptures
					+ " Captures -- " + betaCutQuiets + " Quiets");
			System.out.println(betaPreMoveGenCuts + " # Beta Cuts Pre MoveGen -- " + betaPreMoveGenCaptures
					+ " Captures -- " + betaPreMoveGenQuiets + " Quiets");
			System.out.println(betaFirstMoveCuts + " # Beta Cuts First Move -- " + betaFirstMoveCaptures
					+ " Captures -- " + betaFirstMoveQuiets + " Quiets");
			System.out.println(raiseAlphaMoves + " # Moves Raising Alpha -- " + raiseAlphaCaptures + " Captures -- "
					+ raiseAlphaQuiets + " Quiets");
			System.out.println(
					"+------------------------------------------------------------------------------------------------------------------------+\n");
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
				else
					break;
			}
			return pvLine;
		}
	}
}
