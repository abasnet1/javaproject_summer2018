/*
 * 		Filename: 	CoincideGUI.java
 * 		Author:		Stephen J. DeMonico
 * 		Version:	0.3 Alpha
 * 		
 * 		This provides a touchscreen operable GUI for Coincide, the digital coincidence counter. See user manual for operation.
 */

package coincide_front_end_master;

import java.awt.EventQueue;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.GridLayout;
import javax.swing.JLabel;
import java.awt.Font;
import javax.swing.JTextPane;
import java.awt.Color;
import javax.swing.UIManager;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JToggleButton;
import javax.swing.JTextField;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.awt.event.ActionEvent;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeListener;
import jssc.SerialPortException;
import jssc.SerialPortList;
import jssc.SerialPortTimeoutException;
import javax.swing.event.ChangeEvent;
import javax.swing.border.EtchedBorder;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import javax.swing.Timer;
import javax.swing.JOptionPane;
import javax.swing.JComboBox;


import java.awt.Insets;
import java.io.IOException;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;



public class CoincideGUI {
	
	// CONSTANTS: none of these constants should be changed unless corresponding changes to FPGA are also made!!
	private static final double FPGA_DRDY_PERIOD = .5; // in sec, how often the FPGA will present fresh data
	private static final int POLL_FLAG_ADDRESS = 98; // FPGA address of polling flag
	private static final int SHORT_WINDOW_ADDRESS = 99; // FPGA address of window one width
	private static final int LONG_WINDOW_ADDRESS = 100; // FPGA address of window two width
	private static final int POLL_DELAY = 50; // how often we check for new data, in ms
	private static final int MAX_WIDGETS = 20; // the number of widgets on the data panel
	private static final int[] translate = {0, 1, 4, 2, 5, 7, 10, 3, 6, 8, 11, 9, 12, 13, 14, 15, 0, 16, 19, 17, 20, 22, 25, 18, 21, 23, 26, 24, 27, 28, 29}; // translation matrix for data array
	private static final double clockPeriod = 6.67; // used to express window width in ns
	
	private JFrame frame; // main frame
	private int intervalCounter; // used for counting outside pollTimer, MUST be global
	private FileWriter write; // used for text file output
	private PrintWriter printw; // used for text file output
	
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() { // creates event handler thread
			public void run() {
				try {
					CoincideGUI window = new CoincideGUI();
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public CoincideGUI() { // constructor
		initialize();
	}


	@SuppressWarnings("deprecation")
	private void initialize() { // initialize frame contents
		String workingDir = ""; // string used for text file output
		DateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss"); // date format used for text file output
		Date date = new Date(); // date used for text file output
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
		int[] requiredParams = {2, 2, 1, 0}; // 0: first window 1: second window 2: integration interval 3: data points
		UIManager.put("Label.disabledForeground",Color.DARK_GRAY); // force disabled label color to avoid cross-platform differences
		RegisterInterface r = new RegisterInterface(); // used for communication with FPGA
		ButtonGroup radioBtnGrp = new ButtonGroup();  // used to make radio buttons exclusive
		ArrayList<NumWidget> numwid = new ArrayList<NumWidget>(MAX_WIDGETS); // used to store references to widget instances
		Timer pollTimer = new Timer(POLL_DELAY,null); // timer that drives polling behavior
		
		// next several hundred lines are GUI layout, color, etc
		// consult Java/SWING documentation for details
		frame = new JFrame();
		frame.setBounds(100, 100, 800, 480); 
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(true);
		frame.setTitle("COINCIDE");
		frame.getContentPane().setLayout(new BorderLayout(0, 0));
		frame.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
		frame.setLocation(0,-20); //attempts to force coincide window to top of RPi display (unresolved)
		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		frame.getContentPane().add(tabbedPane);
		
		JPanel data = new JPanel();
		tabbedPane.addTab("Data", null, data, null);
		data.setLayout(new BorderLayout(0, 0));
		
		JPanel buttonPanel = new JPanel();
		data.add(buttonPanel, BorderLayout.SOUTH);
		GridBagLayout gbl_buttonPanel = new GridBagLayout();
		gbl_buttonPanel.columnWidths = new int[]{165, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0};
		gbl_buttonPanel.rowHeights = new int[]{48, 0};
		gbl_buttonPanel.columnWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
		gbl_buttonPanel.rowWeights = new double[]{0.0, Double.MIN_VALUE};
		buttonPanel.setLayout(gbl_buttonPanel);
		
		JToggleButton btnGO = new JToggleButton("GO");
		btnGO.setForeground(Color.GREEN);
		btnGO.setFont(new Font("Tahoma", Font.BOLD, 22));
		GridBagConstraints gbc_btnGO = new GridBagConstraints();
		gbc_btnGO.fill = GridBagConstraints.BOTH;
		gbc_btnGO.gridx = 0;
		gbc_btnGO.gridy = 0;
		buttonPanel.add(btnGO, gbc_btnGO);
		
		JTextPane txtpnWindowWidth = new JTextPane();
		txtpnWindowWidth.setFont(new Font("Tahoma", Font.PLAIN, 11));
		txtpnWindowWidth.setBackground(UIManager.getColor("Button.background"));
		txtpnWindowWidth.setText("Short: " + (int)(clockPeriod*(2 * requiredParams[0] - 1)) + "ns\r\nLong: "
				+ (int)(clockPeriod*(2 * requiredParams[1] - 1)) + "ns\r\nInterval: " + requiredParams[2]*FPGA_DRDY_PERIOD+"s");
		GridBagConstraints gbc_txtpnWindowWidth = new GridBagConstraints();
		gbc_txtpnWindowWidth.fill = GridBagConstraints.BOTH;
		gbc_txtpnWindowWidth.gridx = 1;
		gbc_txtpnWindowWidth.gridy = 0;
		buttonPanel.add(txtpnWindowWidth, gbc_txtpnWindowWidth);
		
		JButton btnA = new JButton("A");
		btnA.setForeground(Color.GREEN);
		btnA.setFont(new Font("Tahoma", Font.BOLD, 32));
		GridBagConstraints gbc_btnA = new GridBagConstraints();
		gbc_btnA.fill = GridBagConstraints.BOTH;
		gbc_btnA.gridx = 2;
		gbc_btnA.gridy = 0;
		buttonPanel.add(btnA, gbc_btnA);
		
		JButton btnB = new JButton("B");
		btnB.setForeground(Color.GREEN);
		btnB.setFont(new Font("Tahoma", Font.BOLD, 32));
		GridBagConstraints gbc_btnB = new GridBagConstraints();
		gbc_btnB.fill = GridBagConstraints.BOTH;
		gbc_btnB.gridx = 3;
		gbc_btnB.gridy = 0;
		buttonPanel.add(btnB, gbc_btnB);
		
		JButton btnC = new JButton("C");
		btnC.setForeground(Color.GREEN);
		btnC.setFont(new Font("Tahoma", Font.BOLD, 32));
		GridBagConstraints gbc_btnC = new GridBagConstraints();
		gbc_btnC.fill = GridBagConstraints.BOTH;
		gbc_btnC.gridx = 4;
		gbc_btnC.gridy = 0;
		buttonPanel.add(btnC, gbc_btnC);
		
		JButton btnD = new JButton("D");
		btnD.setForeground(Color.GREEN);
		btnD.setFont(new Font("Tahoma", Font.BOLD, 32));
		GridBagConstraints gbc_btnD = new GridBagConstraints();
		gbc_btnD.fill = GridBagConstraints.BOTH;
		gbc_btnD.gridx = 5;
		gbc_btnD.gridy = 0;
		buttonPanel.add(btnD, gbc_btnD);
		
		JButton btnP = new JButton("P");
		btnP.setEnabled(true);
		btnP.setForeground(Color.MAGENTA);
		btnP.setFont(new Font("Tahoma", Font.BOLD, 32));
		GridBagConstraints gbc_btnP = new GridBagConstraints();
		gbc_btnP.fill = GridBagConstraints.BOTH;
		gbc_btnP.gridx = 6;
		gbc_btnP.gridy = 0;
		buttonPanel.add(btnP, gbc_btnP);
		
		JButton btnF = new JButton("F");
		btnF.setEnabled(false);
		btnF.setForeground(Color.MAGENTA);
		btnF.setFont(new Font("Tahoma", Font.BOLD, 32));
		GridBagConstraints gbc_btnF = new GridBagConstraints();
		gbc_btnF.fill = GridBagConstraints.BOTH;
		gbc_btnF.gridx = 7;
		gbc_btnF.gridy = 0;
		buttonPanel.add(btnF, gbc_btnF);
		
		JButton btnLS = new JButton("L/S");
		btnLS.setForeground(Color.BLUE);
		btnLS.setFont(new Font("Tahoma", Font.BOLD, 32));
		GridBagConstraints gbc_btnLS = new GridBagConstraints();
		gbc_btnLS.fill = GridBagConstraints.BOTH;
		gbc_btnLS.gridx = 8;
		gbc_btnLS.gridy = 0;
		buttonPanel.add(btnLS, gbc_btnLS);
		
		JButton btnNG = new JButton("#/G");
		btnNG.setEnabled(false);
		btnNG.setFont(new Font("Tahoma", Font.BOLD, 32));
		GridBagConstraints gbc_btnNG = new GridBagConstraints();
		gbc_btnNG.fill = GridBagConstraints.BOTH;
		gbc_btnNG.gridx = 9;
		gbc_btnNG.gridy = 0;
		buttonPanel.add(btnNG, gbc_btnNG);
		
		
		
		JPanel dataPanel = new JPanel();
		dataPanel.setBorder(new EtchedBorder(EtchedBorder.LOWERED, null, null));
		data.add(dataPanel, BorderLayout.CENTER);
		dataPanel.setLayout(new GridLayout(5, 4, 1, 1));
		
		// this loop creates and places all 20 widgets, and adds them to the widget array
		for(int j = 0; j < 20; j++) {
				JPanel panel = new JPanel();
				panel.setBorder(new LineBorder(new Color(0, 0, 0)));
				dataPanel.add(panel);
				panel.setLayout(new GridLayout(0, 1, 0, 0));				
				NumWidget numWidget_0 = new NumWidget();
				panel.add(numWidget_0);
				numWidget_0.setBorder(null);
				radioBtnGrp.add(numWidget_0.getRadioButton());
				numWidget_0.setState(0);
				numWidget_0.addMouseListener(new MouseListener(){
					public void mouseClicked(MouseEvent e) {
						numWidget_0.getRadioButton().setSelected(true);
					}
					public void mousePressed(MouseEvent e) {
				    }
				    public void mouseReleased(MouseEvent e) {
				    }
				    public void mouseEntered(MouseEvent e) {
				    }
				    public void mouseExited(MouseEvent e) {
				    }
				});
				numwid.add(numWidget_0);
		}
		numwid.get(0).getRadioButton().setSelected(true);
		
		JPanel parameters = new JPanel();
		tabbedPane.addTab("Settings", null, parameters, null);
		GridBagLayout gbl_parameters = new GridBagLayout();
		gbl_parameters.columnWidths = new int[] {500, 140, 140, 0};
		gbl_parameters.rowHeights = new int[] {50, 50, 50, 50, 50, 50, 50, 0};
		gbl_parameters.columnWeights = new double[]{0.0, 0.0, 1.0, Double.MIN_VALUE};
		gbl_parameters.rowWeights = new double[]{1.0, 1.0, 1.0, 1.0, 0.0, 1.0, 0.0, Double.MIN_VALUE};
		parameters.setLayout(gbl_parameters);
		
		JLabel lblWindow1 = new JLabel("Short Window Width:");
		lblWindow1.setHorizontalAlignment(SwingConstants.CENTER);
		lblWindow1.setFont(new Font("Tahoma", Font.PLAIN, 40));
		GridBagConstraints gbc_lblWindow1 = new GridBagConstraints();
		gbc_lblWindow1.insets = new Insets(0, 0, 5, 5);
		gbc_lblWindow1.fill = GridBagConstraints.BOTH;
		gbc_lblWindow1.gridx = 0;
		gbc_lblWindow1.gridy = 0;
		parameters.add(lblWindow1, gbc_lblWindow1);
		
		JLabel lblWindow1ns = new JLabel((int)(clockPeriod*(2 * requiredParams[0] - 1)) + "ns");
		lblWindow1ns.setFont(new Font("Tahoma", Font.PLAIN, 50));
		GridBagConstraints gbc_lblWindow1ns = new GridBagConstraints();
		gbc_lblWindow1ns.insets = new Insets(0, 0, 5, 5);
		gbc_lblWindow1ns.gridx = 1;
		gbc_lblWindow1ns.gridy = 0;
		parameters.add(lblWindow1ns, gbc_lblWindow1ns);
		
		JSpinner spinnerWindow1 = new JSpinner();
		spinnerWindow1.setModel(new SpinnerNumberModel(2, 2, 15, 1));
		spinnerWindow1.setFont(new Font("Tahoma", Font.PLAIN, 50));
		spinnerWindow1.setEnabled(false);
		GridBagConstraints gbc_spinnerWindow1 = new GridBagConstraints();
		gbc_spinnerWindow1.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerWindow1.insets = new Insets(0, 0, 5, 0);
		gbc_spinnerWindow1.gridx = 2;
		gbc_spinnerWindow1.gridy = 0;
		parameters.add(spinnerWindow1, gbc_spinnerWindow1);
				
		JLabel lblWindow2 = new JLabel("Long Window Width:");
		lblWindow2.setFont(new Font("Tahoma", Font.PLAIN, 40));
		lblWindow2.setHorizontalAlignment(SwingConstants.CENTER);
		GridBagConstraints gbc_lblWindow2 = new GridBagConstraints();
		gbc_lblWindow2.insets = new Insets(0, 0, 5, 5);
		gbc_lblWindow2.fill = GridBagConstraints.BOTH;
		gbc_lblWindow2.gridx = 0;
		gbc_lblWindow2.gridy = 1;
		parameters.add(lblWindow2, gbc_lblWindow2);
		
		JLabel lblWindow2ns = new JLabel((int)(clockPeriod*(2 * requiredParams[1] - 1)) + "ns");
		lblWindow2ns.setFont(new Font("Tahoma", Font.PLAIN, 50));
		GridBagConstraints gbc_lblWindow2ns = new GridBagConstraints();
		gbc_lblWindow2ns.insets = new Insets(0, 0, 5, 5);
		gbc_lblWindow2ns.gridx = 1;
		gbc_lblWindow2ns.gridy = 1;
		parameters.add(lblWindow2ns, gbc_lblWindow2ns);
		
		JSpinner spinnerWindow2 = new JSpinner();
		spinnerWindow2.setModel(new SpinnerNumberModel(2, 2, 15, 1));
		spinnerWindow2.setFont(new Font("Tahoma", Font.PLAIN, 50));
		spinnerWindow2.setEnabled(false);
		GridBagConstraints gbc_spinnerWindow2 = new GridBagConstraints();
		gbc_spinnerWindow2.insets = new Insets(0, 0, 5, 0);
		gbc_spinnerWindow2.fill = GridBagConstraints.HORIZONTAL;
		gbc_spinnerWindow2.gridx = 2;
		gbc_spinnerWindow2.gridy = 1;
		parameters.add(spinnerWindow2, gbc_spinnerWindow2);
				
		JLabel lblIntegrationInterval = new JLabel("Integration Interval:");
		lblIntegrationInterval.setHorizontalAlignment(SwingConstants.CENTER);
		lblIntegrationInterval.setFont(new Font("Tahoma", Font.PLAIN, 40));
		GridBagConstraints gbc_lblIntegrationInterval = new GridBagConstraints();
		gbc_lblIntegrationInterval.insets = new Insets(0, 0, 5, 5);
		gbc_lblIntegrationInterval.fill = GridBagConstraints.BOTH;
		gbc_lblIntegrationInterval.gridx = 0;
		gbc_lblIntegrationInterval.gridy = 2;
		parameters.add(lblIntegrationInterval, gbc_lblIntegrationInterval);
		
		JSpinner spinnerIntegrationInterval = new JSpinner();
		spinnerIntegrationInterval.setFont(new Font("Tahoma", Font.PLAIN, 50));
		spinnerIntegrationInterval.setModel(new SpinnerNumberModel(new Integer(1), new Integer(1), null, new Integer(1)));
		GridBagConstraints gbc_spinnerIntegrationInterval = new GridBagConstraints();
		gbc_spinnerIntegrationInterval.insets = new Insets(0, 0, 5, 0);
		gbc_spinnerIntegrationInterval.fill = GridBagConstraints.BOTH;
		gbc_spinnerIntegrationInterval.gridx = 2;
		gbc_spinnerIntegrationInterval.gridy = 2;
		parameters.add(spinnerIntegrationInterval, gbc_spinnerIntegrationInterval);
		
		JLabel lblIIsec = new JLabel(requiredParams[2]*FPGA_DRDY_PERIOD +"s");
		lblIIsec.setFont(new Font("Tahoma", Font.PLAIN, 50));
		GridBagConstraints gbc_lblIIsec = new GridBagConstraints();
		gbc_lblIIsec.insets = new Insets(0, 0, 5, 5);
		gbc_lblIIsec.gridx = 1;
		gbc_lblIIsec.gridy = 2;
		parameters.add(lblIIsec, gbc_lblIIsec);
				
		JLabel lblDataPoints = new JLabel("Data Points: (0 = infinite)");
		lblDataPoints.setHorizontalAlignment(SwingConstants.CENTER);
		lblDataPoints.setFont(new Font("Tahoma", Font.PLAIN, 32));
		GridBagConstraints gbc_lblDataPoints = new GridBagConstraints();
		gbc_lblDataPoints.insets = new Insets(0, 0, 5, 5);
		gbc_lblDataPoints.fill = GridBagConstraints.BOTH;
		gbc_lblDataPoints.gridx = 0;
		gbc_lblDataPoints.gridy = 3;
		parameters.add(lblDataPoints, gbc_lblDataPoints);
		
		JSpinner spinnerDataPoints = new JSpinner();
		spinnerDataPoints.setModel(new SpinnerNumberModel(new Integer(0), new Integer(0), null, new Integer(1)));
		spinnerDataPoints.setFont(new Font("Tahoma", Font.PLAIN, 50));
		GridBagConstraints gbc_spinnerDataPoints = new GridBagConstraints();
		gbc_spinnerDataPoints.insets = new Insets(0, 0, 5, 0);
		gbc_spinnerDataPoints.fill = GridBagConstraints.BOTH;
		gbc_spinnerDataPoints.gridx = 2;
		gbc_spinnerDataPoints.gridy = 3;
		parameters.add(spinnerDataPoints, gbc_spinnerDataPoints);
		
		JLabel lblOutputFilename = new JLabel("Output Filename:");
		lblOutputFilename.setFont(new Font("Tahoma", Font.PLAIN, 40));
		lblOutputFilename.setHorizontalAlignment(SwingConstants.CENTER);
		GridBagConstraints gbc_lblOutputFilename = new GridBagConstraints();
		gbc_lblOutputFilename.insets = new Insets(0, 0, 5, 5);
		gbc_lblOutputFilename.fill = GridBagConstraints.BOTH;
		gbc_lblOutputFilename.gridx = 0;
		gbc_lblOutputFilename.gridy = 4;
		parameters.add(lblOutputFilename, gbc_lblOutputFilename);
		
		JTextField outputFilenameTextField = new JTextField();
		outputFilenameTextField.setHorizontalAlignment(SwingConstants.CENTER);
		outputFilenameTextField.setFont(new Font("Tahoma", Font.PLAIN, 24));
		GridBagConstraints gbc_outputFilenameTextField = new GridBagConstraints();
		gbc_outputFilenameTextField.gridwidth = 2;
		gbc_outputFilenameTextField.insets = new Insets(0, 0, 5, 0);
		gbc_outputFilenameTextField.fill = GridBagConstraints.BOTH;
		gbc_outputFilenameTextField.gridx = 1;
		gbc_outputFilenameTextField.gridy = 4;
		parameters.add(outputFilenameTextField, gbc_outputFilenameTextField);
		outputFilenameTextField.setColumns(10);
				
		JLabel serialLabel = new JLabel("Serial Port:");
		serialLabel.setHorizontalAlignment(SwingConstants.CENTER);
		serialLabel.setFont(new Font("Tahoma", Font.PLAIN, 40));
		GridBagConstraints gbc_serialLabel = new GridBagConstraints();
		gbc_serialLabel.insets = new Insets(0, 0, 5, 5);
		gbc_serialLabel.fill = GridBagConstraints.BOTH;
		gbc_serialLabel.gridx = 0;
		gbc_serialLabel.gridy = 5;
		parameters.add(serialLabel, gbc_serialLabel);
		
		JComboBox<String> serialList = new JComboBox<String>(SerialPortList.getPortNames());
		serialList.setFont(new Font("Tahoma", Font.PLAIN, 40));
		GridBagConstraints gbc_serialList = new GridBagConstraints();
		gbc_serialList.gridwidth = 2;
		gbc_serialList.insets = new Insets(0, 0, 5, 0);
		gbc_serialList.fill = GridBagConstraints.BOTH;
		gbc_serialList.gridx = 1;
		gbc_serialList.gridy = 5;
		parameters.add(serialList, gbc_serialList);
		
		JToggleButton tglbtnWrite = new JToggleButton("WRITE");
		tglbtnWrite.setForeground(Color.GREEN);
		tglbtnWrite.setFont(new Font("Tahoma", Font.BOLD, 40));
		GridBagConstraints gbc_tglbtnWrite = new GridBagConstraints();
		gbc_tglbtnWrite.gridwidth = 2;
		gbc_tglbtnWrite.fill = GridBagConstraints.BOTH;
		gbc_tglbtnWrite.gridx = 1;
		gbc_tglbtnWrite.gridy = 6;
		parameters.add(tglbtnWrite, gbc_tglbtnWrite);
			
		JToggleButton tglbtnConnect = new JToggleButton("CONNECT");
		tglbtnConnect.setForeground(Color.GREEN);
		tglbtnConnect.setFont(new Font("Tahoma", Font.BOLD, 40));
		GridBagConstraints gbc_tglbtnConnect = new GridBagConstraints();
		gbc_tglbtnConnect.insets = new Insets(0, 0, 0, 5);
		gbc_tglbtnConnect.fill = GridBagConstraints.BOTH;
		gbc_tglbtnConnect.gridx = 0;
		gbc_tglbtnConnect.gridy = 6;
		parameters.add(tglbtnConnect, gbc_tglbtnConnect);
		
				
		
		// Action/Event Listeners
				
		// all spinners (short/long windows, integration interval, # data points) detect if user changes value, store new value and update label text
		// window width spinners also update FPGA and are only changeable when FPGA is connected
		spinnerWindow1.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				try {
					r.write(SHORT_WINDOW_ADDRESS, requiredParams[0]);
				} catch (SerialPortException e1) {
					e1.printStackTrace();
				}
				requiredParams[0] = (int) spinnerWindow1.getValue();
				lblWindow1ns.setText((int)(clockPeriod*(2 * requiredParams[0] - 1)) + "ns");
				txtpnWindowWidth.setText("Window 1: " + (int)(clockPeriod*(2 * requiredParams[0] - 1)) + "ns\r\nWindow 2: "
						+ (int)(clockPeriod*(2 * requiredParams[1] - 1)) + "ns\r\nInterval: " + requiredParams[2]*FPGA_DRDY_PERIOD+"s");
			}
		});

		spinnerWindow2.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				try {
					r.write(LONG_WINDOW_ADDRESS, requiredParams[1]);
				} catch (SerialPortException e1) {
					e1.printStackTrace();
				}
				requiredParams[1] = (int) spinnerWindow2.getValue();
				lblWindow2ns.setText((int)(clockPeriod*(2 * requiredParams[1] - 1)) + "ns");
				txtpnWindowWidth.setText("Window 1: " + (int)(clockPeriod*(2 * requiredParams[0] - 1)) + "ns\r\nWindow 2: "
						+ (int)(clockPeriod*(2 * requiredParams[1] - 1)) + "ns\r\nInterval: " + requiredParams[2]*FPGA_DRDY_PERIOD+"s");
			}
		});
		
		spinnerIntegrationInterval.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				requiredParams[2] = (int) spinnerIntegrationInterval.getValue();
				lblIIsec.setText(requiredParams[2]*FPGA_DRDY_PERIOD +"s");
				txtpnWindowWidth.setText("Window 1: " + (int)(clockPeriod*(2 * requiredParams[0] - 1)) + "ns\r\nWindow 2: " 
						+ (int)(clockPeriod*(2 * requiredParams[1] - 1)) + "ns\r\nInterval: " + requiredParams[2]*FPGA_DRDY_PERIOD+"s");
			}
		});
		
		spinnerDataPoints.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				requiredParams[3] = (int) spinnerDataPoints.getValue();
				}
		});
		
	
			
		
		
		// WRITE toggle button enables/disables write to disk, opens/closes file w/ user specified name, or date/time stamp if no name specified
		tglbtnWrite.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(tglbtnWrite.isSelected()) {
					tglbtnWrite.setText("DON'T WRITE");
					tglbtnWrite.setForeground(Color.RED);
					outputFilenameTextField.setEnabled(false);
					if(outputFilenameTextField.getText().equals(""))
						try {
							write = new FileWriter(workingDir + df.format(date));
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					else
						try {
							write = new FileWriter(workingDir + outputFilenameTextField.getText());
						} catch (IOException e1) {
							e1.printStackTrace();
						}
					printw = new PrintWriter(write);
				}
				else {
					tglbtnWrite.setText("WRITE");
					tglbtnWrite.setForeground(Color.GREEN);
					outputFilenameTextField.setEnabled(true);
					try {
						write.close();
						printw.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			}
		});
		
		
		// CONNECT toggle button enables/disables serial connection, enables window width spinners, pushes spinner values to FPGA
		tglbtnConnect.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(tglbtnConnect.isSelected()) {
					if (!r.connect(serialList.getSelectedItem().toString())) {
						JOptionPane.showMessageDialog(frame, "Failed to connect!");
						tglbtnConnect.setSelected(false);
					}
					else {
						tglbtnConnect.setText("DISCONNECT");
						tglbtnConnect.setForeground(Color.RED);
						spinnerWindow1.setEnabled(true);
						spinnerWindow2.setEnabled(true);
						try {
							r.write(SHORT_WINDOW_ADDRESS, requiredParams[0]);
							r.write(LONG_WINDOW_ADDRESS, requiredParams[1]);
						} catch (SerialPortException e1) {
							e1.printStackTrace();
						}
					}
				}
				else {
					tglbtnConnect.setText("CONNECT");
					tglbtnConnect.setForeground(Color.GREEN);
					spinnerWindow1.setEnabled(false);
					spinnerWindow2.setEnabled(false);
			        try {r.disconnect();} // disconnect serial port
			        catch (SerialPortException e1) {e1.printStackTrace();}
				}
			}
		});
		
		// timer that drives polling
		
		pollTimer.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				try {
					if(r.read(POLL_FLAG_ADDRESS) != 0) 
					
					{ // if the data ready flag is up
						
						if(tglbtnWrite.isSelected() && (intervalCounter%requiredParams[2] == 0))
							printw.printf(dtf.format(LocalDateTime.now()) + ","); //For every new line of counts, shows current date and and time of the system.
						r.write(POLL_FLAG_ADDRESS,0); // put it back down
						intervalCounter++; // increment interval counter (gets reset by GO/STOP toggle)
						for(int i = 0; i < MAX_WIDGETS; i++) { // loop through all widgets
							NumWidget tmp = numwid.get(i);
							if((tmp.getState() != 0) && (tmp.getState() != 16)) { // check if widget is configured for a channel
								tmp.setAcc(tmp.getAcc() + r.read(translate[tmp.getState() - 1])); // if it is, poll the respective register
								if(intervalCounter%requiredParams[2] == 0) { // check if we have reached integration interval									
									tmp.setTextFieldText(String.format("%,d", tmp.getAcc())); // if so, update display
									if(tglbtnWrite.isSelected())
									 	printw.printf(tmp.getAcc()+ ","); // if write enabled, write to disk
										tmp.setAcc(0); // reset accumulator
										
								
								}
							
								
							}
							// if this widget holds a user parameter and write is enabled, write parameter to file
							if(tmp.getLabelPEnabled() && tglbtnWrite.isSelected()) printw.printf(tmp.getTextFieldText()+",");
						}
						if(tglbtnWrite.isSelected()) 
							printw.printf("\r\n"); // if write enabled, end line/carriage return
						if(intervalCounter == requiredParams[3]) {
							pollTimer.stop(); // if we are at max data points, stop (always fails if data points > 0)
						// reset GO button to stopped state
							btnGO.setText("GO");
							btnGO.setForeground(Color.GREEN);
							btnGO.setSelected(false);
						}
					}
				} catch (SerialPortException e) {
					e.printStackTrace();
				} catch (SerialPortTimeoutException e) {
					e.printStackTrace();
				}
			}
		});
		// enable/disable channel A on selected widget (bit 0 of widget state)
		btnA.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for(int i = 0; i < numwid.size(); i++) {
					NumWidget tmp = numwid.get(i);
					if(tmp.getRadioButton().isSelected()) {
						if(tmp.getLabelAEnabled()) {
							tmp.setLabelAEnabled(false);
							tmp.setState(tmp.getState() - 1);
							tmp.setTextFieldText("");
						}
						else {
							tmp.setLabelAEnabled(true);
							tmp.setState(tmp.getState() + 1);
							tmp.setLabelPEnabled(false);
							tmp.setTextFieldEditable(false);
						}
					}
				}
			}
		});
		// enable/disable channel B on selected widget (bit 1 of widget state)
		btnB.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for(int i = 0; i < numwid.size(); i++) {
					NumWidget tmp = numwid.get(i);
					if(tmp.getRadioButton().isSelected()) {
						if(tmp.getLabelBEnabled()) {
							tmp.setLabelBEnabled(false);
							tmp.setState(tmp.getState() - 2);
							tmp.setTextFieldText("");
						}
						else {
							tmp.setLabelBEnabled(true);
							tmp.setState(tmp.getState() + 2);
							tmp.setLabelPEnabled(false);
							tmp.setTextFieldEditable(false);
						}
					}
				}
			}
		});
		// enable/disable channel C on selected widget (bit 2 of widget state)
		btnC.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for(int i = 0; i < numwid.size(); i++) {
					NumWidget tmp = numwid.get(i);
					if(tmp.getRadioButton().isSelected()) {
						if(tmp.getLabelCEnabled()) {
							tmp.setLabelCEnabled(false);
							tmp.setState(tmp.getState() - 4);
							tmp.setTextFieldText("");
						}
						else {
							tmp.setLabelCEnabled(true);
							tmp.setState(tmp.getState() + 4);
							tmp.setLabelPEnabled(false);
							tmp.setTextFieldEditable(false);
						}
					}
				}
			}
		});
		// enable/disable channel D on selected widget (bit 3 of widget state)
		btnD.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for(int i = 0; i < numwid.size(); i++) {
					NumWidget tmp = numwid.get(i);
					if(tmp.getRadioButton().isSelected()) {
						if(tmp.getLabelDEnabled()) {
							tmp.setLabelDEnabled(false);
							tmp.setState(tmp.getState() - 8);
							tmp.setTextFieldText("");
						}
						else { 
							tmp.setLabelDEnabled(true);
							tmp.setState(tmp.getState() + 8);
							tmp.setLabelPEnabled(false);
							tmp.setTextFieldEditable(false);
						}
					}
				}
			}
		});
		// enable/disable widget for user parameter use
		btnP.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for(int i = 0; i < numwid.size(); i++) {
					NumWidget tmp = numwid.get(i);
					if(tmp.getRadioButton().isSelected()) {
						if(tmp.getLabelPEnabled()) {
							tmp.setLabelPEnabled(false);
						}
						else  {
							tmp.setLabelPEnabled(true);
							tmp.setLabelAEnabled(false);
							tmp.setLabelBEnabled(false);
							tmp.setLabelCEnabled(false);
							tmp.setLabelDEnabled(false);
							tmp.setTextFieldEditable(true);
						}
						tmp.setState(0);
					}
				}
			}
		});
		// toggle widget between short and long windows (state offset by 16)
		btnLS.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				for(int i = 0; i < numwid.size(); i++) {
					NumWidget tmp = numwid.get(i);
					if(tmp.getRadioButton().isSelected()) {
						if(tmp.getLabelLSText() == "L") {
							tmp.setLabelLSText("S");
							tmp.setState(tmp.getState() - 16);
							tmp.setTextFieldText("");
						}
						else {
							tmp.setLabelLSText("L");
							tmp.setState(tmp.getState() + 16);
						}
					}
				}
			}
		});
		
		
			
		// GO toggle button checks status of serial connection, starts/stops polling
		btnGO.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if(btnGO.isSelected()) {
					if(!tglbtnConnect.isSelected()) {
						JOptionPane.showMessageDialog(frame, "NOT CONNECTED!");
						btnGO.setSelected(false);
					}
					else {
						btnGO.setText("STOP");
						btnGO.setForeground(Color.RED);
						intervalCounter = 0;
						pollTimer.start();
					}
				}
				else{
					btnGO.setText("GO");
					btnGO.setForeground(Color.GREEN);
					pollTimer.stop();
				}
			}
		});
	}
}



