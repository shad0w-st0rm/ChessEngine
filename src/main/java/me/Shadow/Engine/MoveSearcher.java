package me.Shadow.Engine;

public class MoveSearcher
{
	static final int MAX_DEPTH = 64;
	static final int MAX_PV_LENGTH = 2 * MAX_DEPTH;

	// ~16.6 million (fits evenly in move ordering)
	static final int MAX_HIST_SCORE = (1 << 23);

	static final int positiveInfinity = 0x3FFF;
	static final int negativeInfinity = -positiveInfinity;

	static final short PV_NODE = 0;
	static final short CUT_NODE = 1;
	static final short ALL_NODE = 2;
	static final short UNK_NODE = 3;

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

			pvLine = new short[MAX_PV_LENGTH];
			SearchContext context = new SearchContext();
			context.setAlphaBetaParams(depth, 0, negativeInfinity, positiveInfinity);
			context.setBestEvalMoveParams(negativeInfinity, bestMove, pvLine, PV_NODE);
			context.setBoardInfoParams(board.zobristHash, board.pawnsHash, board.packBoardInfo());
			context.setMovesParams(0, 0, 0);

			eval = search(context);

			bestMove = context.bestMove;

			currentStats.depth = depth;
			currentStats.evaluation = eval;
			currentStats.move = bestMove;
			currentStats.pvLine = pvLine;
			currentStats.time = System.currentTimeMillis() - time;

			currentStats.printSearchStats();

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
		
		
		int nullEval = searchNullMove(context);
		if (nullEval >= context.beta) return nullEval;

		
		if (context.plyFromRoot > 0 && context.bestMove != MoveHelper.NULL_MOVE)
		{
			searchMove(context.bestMove, context, context.movesStartIndex);

			if (searchCancelled)
				return 0;

			if (context.bestEval >= context.beta)
			{
				currentStats.betaPreMoveGenCuts++;
				if (MoveHelper.isCapture(board, context.bestMove))
					currentStats.betaPreMoveGenCaptures++;
				else
					currentStats.betaPreMoveGenQuiets++;

				// fail soft
				return context.bestEval;
			}
		}
		

		int numMoves = moveGen.generateMoves(MoveGenerator.ALL_MOVES, context.movesStartIndex);

		// checkmate/stalemate
		if (numMoves == 0)
			return moveGen.inCheck() ? negativeInfinity : 0;

		// if searched a move already, push it to top
		moveOrderer.guessMoveEvals(context.pvLine[context.plyFromRoot], context.bestMove, false, context.plyFromRoot,
				context.movesStartIndex, numMoves);

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
				return 0;
			}

			if (context.bestEval >= context.beta)
			{
				// fail soft
				return context.bestEval;
			}
		}

		updateNodeType(context);

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

		
		int childNodeType = UNK_NODE;
		if (context.nodeType == ALL_NODE)
			childNodeType = CUT_NODE;
		else if (context.nodeType == CUT_NODE)
			childNodeType = ALL_NODE;
		else if (context.nodeType == PV_NODE)
		{
			if (!context.raisedAlpha)
				childNodeType = PV_NODE;
			else
				childNodeType = CUT_NODE;
		}
		else if (context.nodeType == UNK_NODE)
			childNodeType = UNK_NODE;
		
		short[] continuedPvLine = new short[MAX_PV_LENGTH];

		SearchContext newContext = new SearchContext();
		newContext.setAlphaBetaParams(context.depth - 1, context.plyFromRoot + 1, -context.beta, -(context.alpha));
		newContext.setBestEvalMoveParams(negativeInfinity, MoveHelper.NULL_MOVE, new short[MAX_PV_LENGTH], childNodeType);
		newContext.setBoardInfoParams(board.zobristHash, board.pawnsHash, board.packBoardInfo());
		newContext.setMovesParams(0, context.numExtensions, nextMoveGenIndex);

		if (extendSearch(move, context))
		{
			newContext.numExtensions++;
			newContext.depth++;
		}
		else if (canReduce(captured, context))
		{
			// TODO: more sophisticated reduction strategy
			newContext.depth = Math.max(newContext.depth - 2, 0);
			//newContext.depth--;
		}
		
		int evaluation = 0;
		
		if (context.numMovesSearched == 0)
		{
			evaluation = -(search(newContext));
		}
		else
		{
			newContext.resetContext(newContext.depth, -(context.alpha + 1), -context.alpha, childNodeType);
			newContext.isZeroWindow = true;
			
			evaluation = -(search(newContext));
			
			if (evaluation > context.alpha && (context.beta - context.alpha) > 1)
			{
				newContext.resetContext(newContext.depth, -context.beta, -context.alpha, childNodeType);
				evaluation = -(search(newContext));
			}
		}

		board.moveBack(move, captured, context.zobristHash, context.pawnsHash, context.boardInfo);
		
		if (searchCancelled) return;
		
		if (evaluation > (positiveInfinity - context.depth) || evaluation < (negativeInfinity + context.depth))
			evaluation += ((evaluation > 0) ? -1 : 1);

		context.bestEval = Math.max(evaluation, context.bestEval);

		if (evaluation >= context.beta)
		{
			handleBetaCutoff(move, captured, evaluation, context);
		}
		else if (evaluation > context.alpha)
		{
			handleAlphaRaise(move, evaluation, context, continuedPvLine);
		}
	}
	
	public int searchNullMove(SearchContext context)
	{
		if (isNullMoveSafe())
		{
			int reduction = 3;
			int newDepth = Math.max(context.depth - reduction, 0);
			board.makeNullMove();
			
			SearchContext newContext = new SearchContext();
			newContext.setAlphaBetaParams(newDepth, context.plyFromRoot + 1, -context.beta, -(context.beta - 1));
			short[] continuedPvLine = new short[MAX_PV_LENGTH];
			newContext.setBestEvalMoveParams(negativeInfinity, MoveHelper.NULL_MOVE, continuedPvLine, CUT_NODE);
			newContext.setBoardInfoParams(board.zobristHash, board.pawnsHash, board.packBoardInfo());
			newContext.setMovesParams(0, context.numExtensions, context.movesStartIndex);
			
			int eval = -search(newContext);
			
			board.undoNullMove(context.zobristHash, context.boardInfo);
			
			return eval;
		}
		return negativeInfinity;
	}
	
	public boolean isNullMoveSafe()
	{
		if (board.inCheck()) return false;
		
		// get all pieces of side to move, and then ignore king and pawns
		long colorBoard = board.bitBoards.colorBoards[board.colorToMove];
		colorBoard ^= board.bitBoards.pieceBoards[PieceHelper.KING | board.colorToMove];
		colorBoard ^= board.bitBoards.pieceBoards[PieceHelper.PAWN | board.colorToMove];
		
		// if no other pieces remain, fear of zugzwang
		return colorBoard != 0;
	}

	public boolean extendSearch(short move, SearchContext context)
	{
		if (context.numExtensions < 3)
		{
			if (board.inCheck())
			{
				return true;
			}
			else
			{
				// give bonus to a pawn moving to one square from promotion
				// maybe change this just to a promotion move itself
				int target = MoveHelper.getTargetIndex(move);
				if (((board.squares[target] & PieceHelper.TYPE_MASK) == PieceHelper.PAWN)
						&& (Utils.getSquareRank(target) == 2 || Utils.getSquareRank(target) == 7))
				{
					// do not extend and reduce at the same node
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean canReduce(byte captured, SearchContext parent)
	{
		if (parent.plyFromRoot > 0 && parent.numMovesSearched >= 3 && captured == PieceHelper.NONE && parent.depth >= 2)
		{
			return true;
		}
		return false;
	}

	public void handleAlphaRaise(short move, int evaluation, SearchContext context, short[] childPvLine)
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

		context.raisedAlpha = true;
		context.nodeType = PV_NODE;
		context.alpha = evaluation;
		context.bestMove = move;

		context.pvLine[context.plyFromRoot] = move;
		for (int i = context.plyFromRoot + 1; i < childPvLine.length; i++)
		{
			if (childPvLine[i] != MoveHelper.NULL_MOVE)
				context.pvLine[i] = childPvLine[i];
			else
			{
				context.pvLine[i] = MoveHelper.NULL_MOVE;
				break;
			}
		}
	}

	public void handleBetaCutoff(short move, byte captured, int evaluation, SearchContext context)
	{
		if (captured == PieceHelper.NONE) // ignore captures for killer moves
		{
			updateKillersAndHistoryHeuristic(move, context);
		}

		currentStats.cutNodes++;
		currentStats.betaCuts++;
		if (context.numMovesSearched == 1)
			currentStats.betaFirstMoveCuts++;

		if (MoveHelper.isCapture(board, move))
		{
			currentStats.betaCutCaptures++;
			if (context.numMovesSearched == 1)
				currentStats.betaFirstMoveCaptures++;
		}
		else
		{
			currentStats.betaCutQuiets++;
			if (context.numMovesSearched == 1)
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

	public void updateKillersAndHistoryHeuristic(short move, SearchContext context)
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
				// signed right shift
				moveOrderer.historyHeuristic[index] >>= 3;
			}
		}

		// subtract one move bc this move should not be reduced
		for (int i = context.movesStartIndex; i < (context.movesStartIndex + context.numMovesSearched - 1); i++)
		{
			short qMove = moves[i];
			if (MoveHelper.isCapture(board, qMove))
			{
				currentStats.betaCutPriorCaptures++;
				continue;
			}
			currentStats.betaCutPriorQuiets++;

			int reduceIndex = (qMove & 0xFFF) | (board.colorToMove << 12);
			moveOrderer.historyHeuristic[reduceIndex] -= context.depth * context.depth;
		}
	}
	
	public void updateNodeType(SearchContext context)
	{
		if (context.nodeType == PV_NODE && !context.raisedAlpha)
		{
			// handle expected PV node not raising alpha
			currentStats.wrongPVNodes++;
			context.nodeType = ALL_NODE;
		}
		else if (context.nodeType == CUT_NODE)
		{
			// handle a cut node not failing high
			if (context.raisedAlpha)
			{
				currentStats.wrongCutNodesPV++;
				context.nodeType = PV_NODE;
			}
			else
			{
				currentStats.wrongCutNodesAll++;
				context.nodeType = ALL_NODE;
			}
		}
		else if (context.nodeType == ALL_NODE && context.raisedAlpha)
		{
			// should not be possible
			context.nodeType = PV_NODE;
		}
		else if (context.nodeType == UNK_NODE)
		{
			// should not be possible, UNK node is currently unused
			context.nodeType = context.raisedAlpha ? PV_NODE : ALL_NODE;
		}

		currentStats.movesSearched += context.numMovesSearched;
		if (context.nodeType == ALL_NODE)
		{
			currentStats.allNodes++;
			currentStats.allNodeMovesSearched += context.numMovesSearched;
		}
		else
		{
			currentStats.pvNodes++;
			currentStats.pvNodeMovesSearched += context.numMovesSearched;
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
				return beta;
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
		boolean raisedAlpha;
		boolean isZeroWindow;

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
				short[] pvLine, long zobristHash, long pawnsHash, short[] boardInfo, int numMovesSearched,
				int numExtensions, int movesStartIndex, int nodeType)
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
		
		public void resetContext(int depth, int alpha, int beta, int nodeType)
		{
			setAlphaBetaParams(depth, plyFromRoot, alpha, beta);
			this.nodeType = nodeType;
			raisedAlpha = false;
			bestEval = negativeInfinity;
			bestMove = MoveHelper.NULL_MOVE;
			pvLine = new short[MAX_PV_LENGTH];
			numMovesSearched = 0;
		}
	}

	class SearcherStats
	{
		int depth, evaluation;
		short move;
		short[] pvLine;
		long time;

		int ttLookups, ttHits, ttMisses, ttDepthLow;

		// done
		int betaCuts, betaCutTT, betaCutCaptures, betaCutQuiets;

		// done
		int betaPreMoveGenCuts, betaPreMoveGenCaptures, betaPreMoveGenQuiets;

		// done
		int betaFirstMoveCuts, betaFirstMoveCaptures, betaFirstMoveQuiets;

		// done
		int betaCutPriorMoves, betaCutPriorCaptures, betaCutPriorQuiets;

		// done
		int raiseAlphaMoves, raiseAlphaCaptures, raiseAlphaQuiets;

		// done
		int nodes, pvNodes, cutNodes, allNodes;

		// done
		int movesSearched, pvNodeMovesSearched, cutNodeMovesSearched, allNodeMovesSearched;

		int wrongPVNodes, wrongCutNodesAll, wrongCutNodesPV;

		public void printSearchStats()
		{
			System.out.println(
					"+------------------------------------------------------------------------------------------------------------------------+");
			System.out.println("Search completed to depth " + depth + " with evaluation " + evaluation);
			System.out.println("Best Move " + MoveHelper.toString(move) + " found in " + time + " ms");
			System.out.println(getPvLine());
			System.out.println(nodes + " Nodes Searched -- " + pvNodes + " PV Nodes -- " + cutNodes + " Cut Nodes -- "
					+ allNodes + " All Nodes");
			System.out.println(wrongPVNodes + " PV Nodes Wrongly Guessed -- " + wrongCutNodesPV
					+ " Cut Nodes Wrong (Were PV) -- " + wrongCutNodesAll + " Cut Nodes Wrong (Were All) -- ");
			System.out.println(movesSearched + " Moves Searched -- " + pvNodeMovesSearched + " PV Node Moves -- "
					+ cutNodeMovesSearched + " Cut Node Moves -- " + allNodeMovesSearched + " All Node Moves");
			System.out.println(betaCuts + " # Beta Cuts -- " + betaCutTT + " TT Cuts -- " + betaCutCaptures
					+ " Captures -- " + betaCutQuiets + " Quiets");
			System.out.println(betaPreMoveGenCuts + " # Beta Cuts Pre MoveGen -- " + betaPreMoveGenCaptures
					+ " Captures -- " + betaPreMoveGenQuiets + " Quiets");
			System.out.println(betaFirstMoveCuts + " # Beta Cuts First Move -- " + betaFirstMoveCaptures
					+ " Captures -- " + betaFirstMoveQuiets + " Quiets");
			System.out.println(betaCutPriorMoves + " # Beta Cuts Prior Moves -- " + betaCutPriorCaptures
					+ " Captures -- " + betaCutPriorQuiets + " Quiets");
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
