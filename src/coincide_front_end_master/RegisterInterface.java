// Provided by Embedded Micro as-is (www.embeddedmicro.com)
// requires java simple serial connector (jssc)

package coincide_front_end_master;

import java.util.Arrays;

import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;
import jssc.SerialPortTimeoutException;

public class RegisterInterface {
	private SerialPort serialPort;

	public RegisterInterface() {

	}
	
	// A method that returns true if the serial port is open, false if not
	
	public boolean isConnected() throws SerialPortException {
		return serialPort.isOpened();
	}
	
    // A method for connecting to the Mojo on serial port "port"
	
	public boolean connect(String port) {
		if (port == null)
			return false;

		if (port.equals(""))
			return false;
		if (!Arrays.asList(SerialPortList.getPortNames()).contains(port))
			return false;

		serialPort = new SerialPort(port);
		try {
			serialPort.openPort();
		} catch (SerialPortException e) {
			return false;
		}
		try {
			serialPort.setParams(500000, 8, 1, 0);
		} catch (SerialPortException e) {
			try {
				serialPort.closePort();
			} catch (SerialPortException e1) {
				e1.printStackTrace();
			}
			return false;
		}

		return true;
	}

	// A method for disconnecting from the serial port
	
	public boolean disconnect() throws SerialPortException {
		return serialPort.closePort();
	}

	public boolean write(int address, int data) throws SerialPortException {
		byte[] buff = new byte[9];
		buff[0] = (byte) (1 << 7);
		buff[1] = (byte) (address & 0xff);
		buff[2] = (byte) ((address >> 8) & 0xff);
		buff[3] = (byte) ((address >> 16) & 0xff);
		buff[4] = (byte) ((address >> 24) & 0xff);
		buff[5] = (byte) (data & 0xff);
		buff[6] = (byte) ((data >> 8) & 0xff);
		buff[7] = (byte) ((data >> 16) & 0xff);
		buff[8] = (byte) ((data >> 24) & 0xff);
		return serialPort.writeBytes(buff);
	}

	// A public method for writing data to an address or addresses on the Mojo
	// returns true on success, false on failure
	// addresses can be written individually or sequentially
	
	public boolean write(int address, boolean increment, int[] data) throws SerialPortException {
		for (int i = 0; i < data.length; i += 64) {
			int length = Math.min(data.length - i, 64);
			if (!write64(address, increment, data, i, length))
				return false;
			if (increment)
				address += length;
		}
		return true;
	}
	
	// A private method for writing data, called by write()

	private boolean write64(int address, boolean increment, int[] data, int start, int length) throws SerialPortException {
		byte[] buff = new byte[5 + length * 4];
		buff[0] = (byte) ((1 << 7) | (length - 1));
		if (increment)
			buff[0] |= (1 << 6);
		buff[1] = (byte) (address & 0xff);
		buff[2] = (byte) ((address >> 8) & 0xff);
		buff[3] = (byte) ((address >> 16) & 0xff);
		buff[4] = (byte) ((address >> 24) & 0xff);
		for (int i = 0; i < length; i++) {
			buff[i * 4 + 5] = (byte) (data[i + start] & 0xff);
			buff[i * 4 + 6] = (byte) ((data[i + start] >> 8) & 0xff);
			buff[i * 4 + 7] = (byte) ((data[i + start] >> 16) & 0xff);
			buff[i * 4 + 8] = (byte) ((data[i + start] >> 24) & 0xff);
		}

		return serialPort.writeBytes(buff);
	}
	
	// A public method for reading a single address, once.

	public int read(int address) throws SerialPortException, SerialPortTimeoutException {
		byte[] buff = new byte[5];
		buff[0] = (byte) (0 << 7);
		buff[1] = (byte) (address & 0xff);
		buff[2] = (byte) ((address >> 8) & 0xff);
		buff[3] = (byte) ((address >> 16) & 0xff);
		buff[4] = (byte) ((address >> 24) & 0xff);
		if (!serialPort.writeBytes(buff))
			throw new SerialPortException(serialPort.getPortName(), "readReg", "Failed to write address");
		buff = serialPort.readBytes(4, 1000);
		return (buff[0] & 0xff) | (buff[1] & 0xff) << 8 | (buff[2] & 0xff) << 16 | (buff[3] & 0xff) << 24;
	}

	// A public method for batch reading - addresses can be read sequentially
	
	public void read(int address, boolean increment, int[] data) throws SerialPortException, SerialPortTimeoutException {
		for (int i = 0; i < data.length; i += 64) {
			int length = Math.min(data.length - i, 64);
			read64(address, increment, data, i, length);
			if (increment)
				address += length;
		}
	}
	
	// A private method for batch reading, called by read()

	private void read64(int address, boolean increment, int[] data, int start, int length) throws SerialPortException, SerialPortTimeoutException {
		byte[] buff = new byte[5];
		buff[0] = (byte) ((0 << 7) | (length - 1));
		if (increment)
			buff[0] |= (1 << 6);
		buff[1] = (byte) (address & 0xff);
		buff[2] = (byte) ((address >> 8) & 0xff);
		buff[3] = (byte) ((address >> 16) & 0xff);
		buff[4] = (byte) ((address >> 24) & 0xff);

		if (!serialPort.writeBytes(buff))
			throw new SerialPortException(serialPort.getPortName(), "readReg", "Failed to write address");

		buff = serialPort.readBytes(length * 4, 3000);

		for (int i = 0; i < buff.length; i += 4) {
			data[i / 4 + start] = (buff[i] & 0xff) | (buff[i + 1] & 0xff) << 8 | (buff[i + 2] & 0xff) << 16 | (buff[i + 3] & 0xff) << 24;
		}
	}
}
