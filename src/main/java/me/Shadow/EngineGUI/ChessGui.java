package me.Shadow.EngineGUI;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import me.Shadow.Engine.*;

public class ChessGui
{
	final JPanel gui = new JPanel(new BorderLayout(3, 3));
	JButton[][] chessBoardSquares = new JButton[8][8];
	Image[][] chessPieceImages = new Image[2][6];
	JPanel chessBoard;
	final JTextField message = new JTextField("Make a move!");
	public static final int KING = 0, QUEEN = 1, BISHOP = 2, KNIGHT = 3, ROOK = 4, PAWN = 5;
	public static final int[] STARTING_ROW =
	{ ROOK, KNIGHT, BISHOP, KING, QUEEN, BISHOP, KNIGHT, ROOK };
	public static final int BLACK = 0, WHITE = 1;
	Color lightBoard = new Color(239, 217, 183);
	Color darkBoard = new Color(180, 136, 101);
	Color lightRed = new Color(241, 58, 67);
	Color darkRed = new Color(235, 49, 60);
	Color lightYellow = new Color(246, 235, 122);
	Color darkYellow = new Color(217, 194, 83);
	
	boolean guiReady = false;

	Engine engine;

	int indexOld = -1;

	public void createGui(Engine engineIn)
	{
		Runnable r = new Runnable()
		{
			@Override
			public void run()
			{
				engine = engineIn;
				initializeGui();
				updatePieces();

				JFrame f = new JFrame("Chess Engine");
				f.add(getGui());
				// Ensures JVM closes after frame(s) closed and
				// all non-daemon threads are finished
				f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				// See https://stackoverflow.com/a/7143398/418556 for demo.
				// f.setLocationByPlatform(true);
				f.setLocation(750, 10);

				// ensures the frame is the minimum size it needs to be
				// in order display the components within it
				f.pack();
				// ensures the minimum size is enforced.
				f.setMinimumSize(f.getSize());
				f.setVisible(true);
			}
		};
		// Swing GUIs should be created and updated on the EDT
		// http://docs.oracle.com/javase/tutorial/uiswing/concurrency
		SwingUtilities.invokeLater(r);
	}

	public final void initializeGui()
	{
		// create the images for the chess pieces
		createImages();

		// set up the main GUI
		gui.setBorder(new EmptyBorder(5, 5, 5, 5));
		JToolBar tools = new JToolBar();
		tools.setFloatable(false);
		gui.add(tools, BorderLayout.PAGE_START);

		JButton restore = new JButton("Restore");

		Action newGameAction = new AbstractAction("New")
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				engine.originalFEN = Board.defaultFEN;
				restore.doClick();
			}
		};
		tools.add(newGameAction);
		JButton createFEN = new JButton("Create FEN");
		createFEN.setActionCommand("createFEN");
		createFEN.addActionListener(new ToolbarButtons());
		tools.add(createFEN);

		restore.setActionCommand("restore");
		restore.addActionListener(new ToolbarButtons());
		tools.add(restore);
		tools.addSeparator();

		tools.add(new JButton("Resign")); // TODO - add functionality!
		tools.addSeparator();
		tools.add(message);
		message.setEditable(false);

		gui.add(new JLabel("?"), BorderLayout.LINE_START);

		chessBoard = new JPanel(new GridLayout(0, 8))
		{

			/**
			 * Override the preferred size to return the largest it can, in a square shape. Must (must, must) be added to a GridBagLayout as the only component (it uses the parent as a guide to size) with no GridBagConstaint (so it is centered).
			 */
			@Override
			public final Dimension getPreferredSize()
			{
				Dimension d = super.getPreferredSize();
				Dimension prefSize = null;
				Component c = getParent();
				if (c == null)
					prefSize = new Dimension((int) d.getWidth(), (int) d.getHeight());
				else if (c != null && c.getWidth() > d.getWidth() && c.getHeight() > d.getHeight())
					prefSize = c.getSize();
				else
					prefSize = d;
				int w = (int) prefSize.getWidth();
				int h = (int) prefSize.getHeight();
				// the smaller of the two sizes
				int s = (w > h ? h : w);
				return new Dimension(s, s);
			}
		};
		chessBoard.setBorder(new CompoundBorder(new EmptyBorder(8, 8, 8, 8), new LineBorder(darkBoard)));
		// Set the BG to be ochre
		Color ochre = new Color(204, 119, 34);
		chessBoard.setBackground(ochre);
		JPanel boardConstrain = new JPanel(new GridBagLayout());
		boardConstrain.setBackground(ochre);
		boardConstrain.add(chessBoard);
		gui.add(boardConstrain);

		// create the chess board squares
		Insets buttonMargin = new Insets(0, 0, 0, 0);
		for (int i = 0; i < chessBoardSquares.length; i++)
		{
			for (int j = 0; j < chessBoardSquares[i].length; j++)
			{
				JButton b = new JButton();
				b.setActionCommand("" + ((i * 8) + j));
				ButtonSquare listener = new ButtonSquare();
				b.addActionListener(listener);
				b.setMargin(buttonMargin);
				b.setBorder(null);
				// our chess pieces are 64x64 px in size, so we'll
				// 'fill this in' using a transparent icon..
				ImageIcon icon = new ImageIcon(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
				b.setIcon(icon);
				if ((j % 2 == 1 && i % 2 == 1) || (j % 2 == 0 && i % 2 == 0))
					b.setBackground(darkBoard);
				else
					b.setBackground(lightBoard);
				chessBoardSquares[i][j] = b;
			}
		}

		/*
		 * fill the chess board
		 */
		if (engine.engineColor != PieceHelper.WHITE_PIECE)
		{
			for (int i = 7; i >= 0; i--)
			{
				for (int j = 0; j < 8; j++)
				{
					chessBoard.add(chessBoardSquares[i][j]);
				}
			}
		}
		else
		{
			for (int i = 0; i < 8; i++)
			{
				for (int j = 7; j >= 0; j--)
				{
					chessBoard.add(chessBoardSquares[i][j]);
				}
			}
		}
		
		guiReady = true;
	}

	public final JComponent getGui()
	{
		return gui;
	}

	private final void createImages()
	{
		try
		{
			BufferedImage bi = ImageIO.read(Thread.currentThread().getContextClassLoader().getResource("chesspieces.png"));
			for (int i = 0; i < 2; i++)
			{
				for (int j = 0; j < 4; j++)
				{
					chessPieceImages[1 - i][j] = bi.getSubimage(j * 106, i * 106, 106, 106);
				}
				for (int j = 0; j < 3; j++)
				{
					chessPieceImages[1 - i][j + 3] = bi.getSubimage(j * 106 + 3 * 107, i * 106, 106, 106);
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
	}

	/** colorSet = 0 for default board colors, 1 for light/dark yellow, and 2 for light/dark red */
	public void setColor(int index, int colorSet)
	{
		Color light = lightBoard;
		Color dark = darkBoard;

		if (colorSet == 1)
		{
			light = lightYellow;
			dark = darkYellow;
		}
		else if (colorSet == 2)
		{
			light = lightRed;
			dark = darkRed;
		}
		int rowOld = index / 8;
		int colOld = index % 8;
		if ((rowOld + colOld) % 2 == 0)
			chessBoardSquares[rowOld][colOld].setBackground(dark);
		else
			chessBoardSquares[rowOld][colOld].setBackground(light);

	}

	public void updatePieces()
	{
		for (int i = 0; i < 64; i++)
		{
			int squareRow = i / 8;
			int squareCol = i % 8;
			ImageIcon icon = new ImageIcon(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB));
			if (engine.board.squares[i] != PieceHelper.NONE)
			{
				int pieceType = PieceHelper.getPieceType(engine.board.squares[i]);
				int pieceColor = PieceHelper.getColor(engine.board.squares[i]);
				if (pieceType == PieceHelper.PAWN)
					icon = new ImageIcon(chessPieceImages[(pieceColor) ^ 1][PAWN]);
				else if (pieceType == PieceHelper.KNIGHT)
					icon = new ImageIcon(chessPieceImages[(pieceColor) ^ 1][KNIGHT]);
				else if (pieceType == PieceHelper.ROOK)
					icon = new ImageIcon(chessPieceImages[(pieceColor) ^ 1][ROOK]);
				else if (pieceType == PieceHelper.BISHOP)
					icon = new ImageIcon(chessPieceImages[(pieceColor) ^ 1][BISHOP]);
				else if (pieceType == PieceHelper.QUEEN)
					icon = new ImageIcon(chessPieceImages[(pieceColor) ^ 1][QUEEN]);
				else if (pieceType == PieceHelper.KING)
					icon = new ImageIcon(chessPieceImages[(pieceColor) ^ 1][KING]);
			}
			chessBoardSquares[squareRow][squareCol].setIcon(icon);
		}
	}

	class ButtonSquare implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			int index = Integer.parseInt(e.getActionCommand());
			int row = index / 8;
			int col = index % 8;

			boolean moveMade = false;
			short pieceMove = MoveHelper.NULL_MOVE;

			if (indexOld != -1)
			{
				setColor(indexOld, 0);
			}

			if (indexOld == index)
			{
				indexOld = -1;
				for (short move : engine.movesOld)
				{
					setColor(MoveHelper.getTargetIndex(move), 0);
				}
				return;
			}

			if ((engine.board.colorToMove != engine.engineColor) && !engine.engineSearching && (chessBoardSquares[row][col].getBackground() == darkRed || chessBoardSquares[row][col].getBackground() == lightRed))
			{
				pieceMove = MoveHelper.NULL_MOVE;
				for (short move2 : engine.movesOld)
				{
					if (MoveHelper.getTargetIndex(move2) == index)
					{
						pieceMove = move2;
						break;
					}
				}
				
				engine.makeMove(pieceMove);
				moveMade = true;
			}

			for (short move : engine.movesOld)
			{
				setColor(MoveHelper.getTargetIndex(move), 0);
			}

			if (!moveMade && !engine.engineSearching)
			{
				int piece = engine.board.squares[index];
				if (piece != PieceHelper.NONE && PieceHelper.getColor(piece) != engine.engineColor)
				{
					short [] movesArray = new short[MoveGenerator.MAXIMUM_LEGAL_MOVES];
					int numMoves = (new MoveGenerator(engine.board, movesArray)).generateMoves(false, 0);
					
					ArrayList<Short> moves = new ArrayList<Short>();
					for (int i = 0; i < numMoves; i++)
					{
						short move = movesArray[i];
						if (MoveHelper.getStartIndex(move) == index) moves.add(move);
					}
					
					for (short move : moves)
					{
						setColor(MoveHelper.getTargetIndex(move), 2);
					}
					engine.movesOld = moves;
				}
			}

			setColor(index, 1);
			indexOld = index;

			if (moveMade)
			{
				setColor(index, 0);
				indexOld = -1;
				updatePieces();
				engine.playerMoveMade = true;
			}
		}
	}

	class ToolbarButtons implements ActionListener
	{
		public void actionPerformed(ActionEvent e)
		{
			String command = e.getActionCommand();
			if (command.equals("createFEN"))
			{
				String fen = engine.board.createFEN(true);
				message.setText(fen);
			}

			else if (command.equals("restore"))
			{
				if (engine.engineSearching)
					message.setText("Please wait until the engine plays a move before restoring original position!");
				else
				{
					message.setText("Make a move!");
					for (short move : engine.movesOld)
					{
						setColor(MoveHelper.getTargetIndex(move), 0);
					}

					if (indexOld != -1)
					{
						setColor(indexOld, 0);
					}

					if (engine.engineMoveOld != MoveHelper.NULL_MOVE)
					{
						setColor(MoveHelper.getTargetIndex(engine.engineMoveOld), 0);
						setColor(MoveHelper.getTargetIndex(engine.engineMoveOld), 0);
					}

					engine.board.loadFEN(engine.originalFEN);
					engine.engineSearching = false;
					//engine.transpositions.clear();
					engine.playerMoveMade = (engine.engineColor == engine.board.colorToMove);
					updatePieces();
				}
			}
		}
	}
}
