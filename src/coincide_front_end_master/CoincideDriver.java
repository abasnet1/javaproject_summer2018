// File: 		CoincideDriver.java
// Author:		Stephen J. DeMonico
// Version:		0.2 Alpha
// Last Mod:	1/16/2017
//
// Purpose: 	Provides UI and data collection/aggregation/output for Coincide back end
//

package coincide_front_end_master;

import java.util.Scanner; // required to accept user input
import jssc.*;			  // required to provide serial connection

public class CoincideDriver {
	private static final int ARR_LENGTH = 30;    // default data array length; FPGA provides two observation windows with 15 counts per window
	private static final double BIN_LENGTH = .5; // length, in seconds, of FPGA data bin
	private static final int POLL_FLAG_ADDRESS = 98; // FPGA address of polling flag
	private static final int WINDOW_ONE_ADDRESS = 99; // FPGA address of window one width
	private static final int WINDOW_TWO_ADDRESS = 100; // FPGA address of window two width
	public static void main(String[] args) {
		String portName = args[0]; // get serial port name (passed as command line argument)
		String greet = "Greetings, Professor Falken. Welcome to Coincide 0.2 Alpha. Please enter initial parameters.";
		String s0 = "Please enter the number of data points you would like to collect: ";
		String s1 = "Thank you for using Coincide. Goodbye.";
		String s2 = "Invalid selection.";
		String[] labels = {"A:    ","B:    ","C:    ","D:    ","AB:   ","AC:   ","AD:   ","BC:   ","BD:   ","CD:   ","ABC:  ","ABD:  ","ACD:  ","BCD:  ","ABCD: "};
		RegisterInterface mojo = new RegisterInterface(); // instance of RegisterInterface provided by Embedded Micro
		Scanner scan = new Scanner(System.in); // instance of scanner
		boolean menuLoop = true; // exit flag
		int menuSelection = 0; // stores user selected menu option
		int dataPoints = 0; // stores user selected # of data points
		int[] requiredParams = new int[3]; // 0: first window 1: second window 2: integration interval
		int[] last = new int[ARR_LENGTH]; // stores most recently aggregated data
		if (!mojo.connect(portName)) { // connect to serial port
			System.err.print("Failed to connect to " + portName);
			System.exit(1);
		}
		System.out.println(greet);
		setWindowWidths(scan, mojo, requiredParams); // at start up, force user to initialize observation window widths
		setInterval(scan, requiredParams); // at start up, force user to initialize integration interval 
		while(menuLoop) { // enter main program loop
			menuPrint(); // display menu
			menuSelection = Integer.parseInt(scan.nextLine()); // accept menu selection from user
			switch(menuSelection) { // execute menu selection
				case 1: System.out.println(s0); // query user for number of desired data points
				   		dataPoints = Integer.parseInt(scan.nextLine()); // accept number of data points (needs loop-until-break functionality)
				   		for(int i = 0; i < dataPoints; i++) { // begin data collection
				   			last = collectData(mojo, requiredParams[2]); // collect and aggregate one interval of data
				   			outputData(last, labels, requiredParams[2]); // output collected interval
				   		}
				   		break;
				case 2: setWindowWidths(scan, mojo, requiredParams); // update observation window widths
						break;
				case 3: setInterval(scan, requiredParams); // update integration interval
						break;
				case 4: getCurrentParams(requiredParams); // report current parameters to user
						break;
				case 5: //tau(last);
						System.out.println("Not Yet Implemented/n"); // report calculated observation windows of most recent data point
						break;
				case 6: menuLoop = false; // user requested program termination
				        try {mojo.disconnect();} // disconnect serial port
				        catch (SerialPortException e) {e.printStackTrace();}
						System.out.println(s1); // notify user of program termination
						break;
				default: 
						System.out.print(s2); // invalid selection fall through
						break;
			}
			
		}
		
	}
	
	// A method for setting observation window widths
	
	private static void setWindowWidths(Scanner sc, RegisterInterface r, int[] up) {
		String s0 = "Coincidence window width is p(2n - 1), where p is the period of the FPGA clock,";
		String s1 = "and n is the replicated pulse length in clock cycles.";
		String s2 = "Please enter the desired n for observation window 1 (min 2, max 15): ";
		String s3 = "Please enter the desired n for observation window 2 (min 2, max 15): ";
		System.out.println(s0); // define window width calculation for user
		System.out.println(s1);
		System.out.print(s2); // query user for first window width
		up[0] = Integer.parseInt(sc.nextLine()); // accept first window width
		System.out.print(s3); // query user for second window width
		up[1] = Integer.parseInt(sc.nextLine()); // accept second window width
		try { // write window widths to FPGA
			r.write(WINDOW_ONE_ADDRESS, up[0]);
			r.write(WINDOW_TWO_ADDRESS, up[1]);
		} catch (SerialPortException e) {
			e.printStackTrace();
		}
	}
	
	// A method for setting integration interval
	
	private static void setInterval(Scanner sc, int[] up) {
		String s0 = "Please enter the desired integration interval in halves of a second: ";
		System.out.print(s0); // query user for integration interval
		up[2] = Integer.parseInt(sc.nextLine()); // accept integration interval
	}
	
	// A method for displaying current parameters to user
	
	private static void getCurrentParams(int[] up) {
		String s0 = "Pulse length for observation window 1: ";
		String s1 = "Pulse length for observation window 2: ";
		String s2 = "Integration interval (half-seconds): ";
		System.out.println(s0 + up[0]);
		System.out.println(s1 + up[1]);
		System.out.println(s2 + up[2]);
	}
	
	// A method for checking the FPGA for new data
	
	private static boolean checkData(RegisterInterface r) {
		boolean drdy = false; // store state of flag for return
		int pollFlag; // store the poll flag
		try {
			pollFlag = r.read(POLL_FLAG_ADDRESS); // read the poll flag from the FPGA
			if(pollFlag != 0) drdy = true; // 1 means data is ready for collection
			else drdy = false; // 0 means no data is ready
		}
		catch (SerialPortException e) {e.printStackTrace();}
		catch (SerialPortTimeoutException e) {e.printStackTrace();}
		return drdy; // return flag
	}
	
	// A method for collecting and aggregating data
	
	private static int[] collectData(RegisterInterface r, int interval) {
		int i = 0; // a counter for the number of data bins collected from FPGA
		int j; // a counter for accumulating totals in acc
		int[] acc = new int[30]; // accumulator array
		int[] tmp = new int[30]; // temp array
		while(i < interval) { // stay in this loop as long as we have not collected a full interval's worth of data
			try {
				if(checkData(r)) { // check if there is data ready
					try { // if data is ready
						r.read(0,true,tmp); // fill tmp array with data from serial
						r.write(POLL_FLAG_ADDRESS, 0); // lower polling flag on FPGA
					}
					catch (SerialPortException e) {e.printStackTrace();}
					catch (SerialPortTimeoutException e) {e.printStackTrace();}
					
					i++; // increment
					for(j = 0; j < ARR_LENGTH; j++) acc[j] += tmp[j]; // add tmp to acc
				}
				Thread.sleep(30); // 30ms seems to be lower limit before FPGA gets flooded w/ requests and crashes
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return acc; // return aggregated data point
	}
	
	// A method for outputting aggregated data points
	
	private static void outputData(int[] last, String[] l, int interval) {
		double m = interval * BIN_LENGTH; // rate conversion factor
		for(int i = 0; i < ARR_LENGTH; i++) last[i] = (int)(last[i]/m); // conversion to rates 1/sec
		System.out.println("\t\tWindow 1\tWindow 2");
		System.out.println("\t\t--------\t--------");
		for(int j = 0; j < ARR_LENGTH/2; j++)
			System.out.printf(l[j] + "%,15d" + "%,15d%n",(long)last[j],(long)last[j + (ARR_LENGTH/2)]);
		tau(last);
	}
	
	// A method for printing the user menu	
	
	private static void menuPrint() {
		String s0 = "Coincide Main Menu:";
		String s1 = "1: Collect Data";
		String s2 = "2: Change Observation Window Widths";
		String s3 = "3: Change Integration Interval";
		String s4 = "4: View Current Parameters";
		String s5 = "5: Calculate Experimental Window Width (Tau)(NYI)";
		String s6 = "6: Exit";
		String s7 = "Enter Selection: ";
		System.out.println(s0);
		System.out.println();
		System.out.println(s1);
		System.out.println(s2);
		System.out.println(s3);
		System.out.println(s4);
		System.out.println(s5);
		System.out.println(s6);
		System.out.println();
		System.out.print(s7);
	}
	
	// A method for calculating observation windows of a data point
	
	private static void tau(int[] d) { //needs refinement
		System.out.println("Tau values for last data set: ");
		System.out.printf("AB: " + "%,15f" + "%,15f%n",(float)(d[4]/(d[0]*d[1]))*1000000000,(float)(d[19]/(d[15]*d[16]))*1000000000);
		System.out.printf("AC: " + "%,15f" + "%,15f%n",(float)(d[5]/(d[0]*d[2]))*1000000000,(float)(d[20]/(d[15]*d[17]))*1000000000);
		System.out.printf("AD: " + "%,15f" + "%,15f%n",(float)(d[6]/(d[0]*d[3]))*1000000000,(float)(d[21]/(d[15]*d[18]))*1000000000);
		System.out.printf("BC: " + "%,15f" + "%,15f%n",(float)(d[7]/(d[1]*d[2]))*1000000000,(float)(d[22]/(d[16]*d[17]))*1000000000);
		System.out.printf("BD: " + "%,15f" + "%,15f%n",(float)(d[8]/(d[1]*d[3]))*1000000000,(float)(d[23]/(d[16]*d[18]))*1000000000);
		System.out.printf("CD: " + "%,15f" + "%,15f%n",(float)(d[9]/(d[2]*d[3]))*1000000000,(float)(d[24]/(d[17]*d[18]))*1000000000);
		
	}

}
