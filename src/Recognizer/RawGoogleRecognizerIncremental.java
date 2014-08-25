/**
 * DOCKS is a framework for post-processing results of Cloud-based speech 
 * recognition systems.
 * Copyright (C) 2014 Johannes Twiefel
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Contact:
 * 7twiefel@informatik.uni-hamburg.de
 */
package Recognizer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.SecureRandom;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;

import javaFlacEncoder.FLACFileWriter;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import Data.Result;
import Utils.Printer;

/**
 * Recognizer used to connect to Google ASR
 * 
 * @author Johannes Twiefel
 * 
 */
public class RawGoogleRecognizerIncremental implements StandardRecognizer {
	private static String TAG = "BaseRecognizer";
	private String name = "Google";

	// get result from an open connection to Google
	private Result getResult(HttpURLConnection connection) {
		BufferedReader in = null;
		Printer.printWithTime(TAG, "receiving inputstream");
		try {
			// get result stream from connection
			in = new BufferedReader(new InputStreamReader(
					connection.getInputStream()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
//		Scanner s;
//		try {
//			if (connection.getResponseCode() != 200) {
//			    s = new Scanner(connection.getErrorStream());
//			} else {
//			    s = new Scanner(connection.getInputStream());
//			}
//
//		s.useDelimiter("\\Z");
//		String response = s.next();
//		System.out.println(response);
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
		String decodedString = "";
		String resultJSON = null;
		Printer.printWithTime(TAG, "decoding string");
		try {
			// get JSON result
			while ((decodedString = in.readLine()) != null) {
				Printer.printWithTime(TAG, decodedString);
				resultJSON = decodedString;
				if(resultJSON.equals("{\"result\":[]}"))
					continue;
				Result result = new Result();
				if(resultJSON.contains("final\":true"))
					result.setFinal();
				
				while (resultJSON.indexOf("transcript") != -1) {
					
					resultJSON = resultJSON
							.substring(resultJSON.indexOf("transcript") + 13);
					String utterance;
					// clean from special chars
					utterance = resultJSON.substring(0, resultJSON.indexOf("\""));
					utterance = utterance.replace("@", "");

					utterance = utterance.replaceAll("[^a-zA-Z 0-9]", "");
					utterance = utterance.replaceAll(" +", " ");

					if (!utterance.equals(""))
						if (utterance.charAt(0) == ' ')
							utterance = utterance.substring(1);

					// add to resultT
					if (!utterance.equals(""))
						result.addResult(utterance);

				}
				resultQueue.add(result);
				//result.printShort();
			}
			Result r = new Result();
			r.setEndSignal();
			resultQueue.add(r);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}



		// return result
		return null;

	}

	private static AudioFormat getAudioFormat() {
		float sampleRate = 16000;
		int sampleSizeInBits = 16;
		int channels = 1;
		boolean signed = true;
		boolean bigEndian = false;
		AudioFormat format = new AudioFormat(sampleRate, sampleSizeInBits,
				channels, signed, bigEndian);
		return format;
	}

	private String pair = "";

	private String getPair() {
		
		if (pair.equals("")) {
			SecureRandom random = new SecureRandom();
			pair = new BigInteger(96, random).toString(32);
			Printer.printWithTime(TAG, "creating pair "+pair);
		}
		Printer.printWithTime(TAG, "creating pair "+pair);
		return pair;

	}

	private Queue<Result> resultQueue = new LinkedList<Result>();
	public Result getNextPartialResult()
	{
		Printer.printWithTime(TAG, "trying to get result from queue");
		Result res = resultQueue.peek();
		while(res==null)
			res=resultQueue.peek();
		resultQueue.poll();
		Printer.printWithTime(TAG, "got result from queue");
		return res;
	}
	// get connection to Google
	private HttpURLConnection getUpConnection() {
		HttpURLConnection connection = null;
		Printer.printWithTime(TAG, "creating URL");

		String pair = getPair();
		// String request = "https://www.google.com/speech-api/v2/recognize?" +
		// "xjerr=1&client=chromium&lang=en-US&maxresults=10&pfilter=0&key="+key+"&output=json";
		String upstream = "https://www.google.com/speech-api/full-duplex/v1/up?key="
				+ key
				+ "&pair="
				+ pair
				+ "&lang=en-US&maxAlternatives=10&client=chromium&continuous&interim&output=json&xjerr=1";

		URL url = null;
		try {// create new request
			url = new URL(upstream);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Printer.printWithTime(TAG, "creating http connection");
		Printer.printWithTime(TAG, upstream);
		try {// open connection
			connection = (HttpURLConnection) url.openConnection();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// adjust the connection
		Printer.printWithTime(TAG, "adjusting connection");
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setInstanceFollowRedirects(false);
		try {
			connection.setRequestMethod("POST");
		} catch (ProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		connection.setRequestProperty("Transfer-Encoding",
				"chunked");
		connection.setChunkedStreamingMode(0);
		connection.setRequestProperty("Content-Type",
				"audio/x-flac; rate=16000");
		connection
				.setRequestProperty(
						"User-Agent",
						"Mozilla/5.0 (Windows NT 6.3; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2049.0 Safari/537.36");
		connection.setConnectTimeout(60000);
		connection.setUseCaches(false);

		return connection;

	}


	
	private HttpURLConnection getDownConnection() {
		HttpURLConnection connection = null;
		Printer.printWithTime(TAG, "creating URL");

		String pair = getPair();
		// String request = "https://www.google.com/speech-api/v2/recognize?" +
		// "xjerr=1&client=chromium&lang=en-US&maxresults=10&pfilter=0&key="+key+"&output=json";
		String downstream = "https://www.google.com/speech-api/full-duplex/v1/down?pair="+pair;
		
		URL url = null;
		try {// create new request
			url = new URL(downstream);
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Printer.printWithTime(TAG, "creating http connection");
		Printer.printWithTime(TAG, downstream);
		try {// open connection
			connection = (HttpURLConnection) url.openConnection();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		// adjust the connection
		Printer.printWithTime(TAG, "adjusting connection");
		connection.setDoOutput(true);
		//connection.setDoInput(true);
		//connection.setInstanceFollowRedirects(false);
		try {
			connection.setRequestMethod("GET");
		} catch (ProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		connection
				.setRequestProperty(
						"User-Agent",
						"Mozilla/5.0 (Windows NT 6.3; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2049.0 Safari/537.36");
		connection.setConnectTimeout(60000);
		//connection.setUseCaches(false);
//		Scanner s;
//		try {
//			if (connection.getResponseCode() != 200) {
//			    s = new Scanner(connection.getErrorStream());
//			} else {
//			    s = new Scanner(connection.getInputStream());
//			}
//
//		s.useDelimiter("\\Z");
//		String response = s.next();
//		System.out.println(response);
//		} catch (IOException e1) {
//			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}
		return connection;

	}
	
	
	// get the stream to write audio data to
	private DataOutputStream getStream(HttpURLConnection con) {
		DataOutputStream stream = null;
		try {
			stream = new DataOutputStream(con.getOutputStream());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return stream;
	}

	// write audio data to the stream
	private void writeToStream(DataOutputStream stream, AudioInputStream ai) {
		int buffer_size = 4000;
		byte tempBuffer[] = new byte[buffer_size];

		Printer.printWithTime(TAG, "buffer size: " + buffer_size);

		boolean run = true;
		int i = 0;
		InputStream byteInputStream;
		FLACFileWriter ffw = new FLACFileWriter();
		ByteArrayOutputStream boas = new ByteArrayOutputStream();
		ByteArrayOutputStream boas2 = new ByteArrayOutputStream();
		AudioInputStream ais;
		Printer.printWithTime(TAG, "recording started");
		while (run) {
			int cnt = -1;
			try {// read data from the audio input stream
				cnt = ai.read(tempBuffer, 0, buffer_size);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			Printer.printWithTimeF(TAG, "read :" + cnt);
			if (cnt > 0) {// if there is data
				Printer.reset();

				byteInputStream = new ByteArrayInputStream(tempBuffer);
				ais = new AudioInputStream(byteInputStream, getAudioFormat(),
						cnt); // open a new audiostream
				try {
					ffw.write(ais, FLACFileWriter.FLAC, boas);// convert audio
																// data to FLAC
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Printer.printWithTimeF(TAG, "boas size: " + boas.size()
						+ " i: " + i + " cnt: " + cnt + " boas content: "
						+ boas.toByteArray());
				Printer.printWithTimeF(TAG, "writing data");
				try {
					Printer.printWithTime(TAG, "writing "+boas.toByteArray().length+" bytes");
					stream.write(boas.toByteArray());// write FLAC audio data to
														// the output stream to
														// google
					boas2.write(tempBuffer);
					boas.reset();

				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			} else
				run = false;
		}
		Printer.printWithTime(TAG, "recording stopped");

	}



	/**
	 * recognize from an audio inpustream like the voice activity detector
	 * 
	 * @param ai
	 *            e.g. the voice activity detector
	 * @return a result containing 10-best list
	 */
	public Result recognize(AudioInputStream ai) {
		// get connection to google
		HttpURLConnection con = null;// getConnection();

		// get stream from connection
		DataOutputStream stream = getStream(con);

		Printer.printWithTime(TAG, "starting AudioInput");

		Printer.printWithTime(TAG, " AudioInput started");

		// write to stream
		writeToStream(stream, ai);

		// get result
		Result res = getResult(con);

		// flush and close the stream
		try {
			stream.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Printer.printWithTime(TAG, "closing");

		try {
			stream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// disconnect from google
		con.disconnect();

		Printer.printWithTime(TAG, "Done");

		return res;
	}

	private void startDownConnection()
	{
		Thread t = new Thread(){
			public void run() {
				HttpURLConnection downCon = getDownConnection();
				Result res = getResult(downCon);
				downCon.disconnect();
			}
			
		};
		t.start();

		
	}
	
	private final static byte[] FINAL_CHUNK = new byte[] { '0', '\r', '\n', '\r', '\n' };
	
	/**
	 * recognize from an audio file (16kHz, 1 channel, signed, little endian)
	 * 
	 * @param fileName
	 *            path to the file containing audio
	 */
	public Result recognizeFromFile(String fileName) {

		// open connection to google
		HttpURLConnection upCon = getUpConnection();


		// get stream from connection
		DataOutputStream stream = getStream(upCon);
		startDownConnection();
		
		File file = new File(fileName);

		FLACFileWriter ffw = new FLACFileWriter();

		try {
			// convert whole file to FLAC
			AudioInputStream inputStream = AudioSystem
					.getAudioInputStream(file);
			ByteArrayOutputStream boas = new ByteArrayOutputStream();
			ffw.write(inputStream, FLACFileWriter.FLAC, boas);
			Printer.printWithTime(TAG, "writing to stream");
			// write FLAC to stream
			stream.write(boas.toByteArray());
			stream.write(FINAL_CHUNK);
			Printer.printWithTime(TAG, "written to stream");

		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (UnsupportedAudioFileException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

		
		// get result
		
		//Result res = getResult(upCon);
		
		// flush and close stream
		try {
			stream.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Printer.printWithTime(TAG, "closing");

		try {
			stream.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// disconnect from google
		upCon.disconnect();
		

		Printer.printWithTime(TAG, "Done");
		// res.print();
		return null;
	}

	@Override
	public Result recognizeFromResult(Result r) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getReferenceRecognizer() {
		// TODO Auto-generated method stub
		return -1;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return name;
	}

	private String key;

	public RawGoogleRecognizerIncremental(String key) {
		this.key = key;
	}

}