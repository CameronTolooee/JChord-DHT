/*+----------------------------------------------------------------------
 ||
 ||  	IO Protocol: Specification of the parameters and rules to be used
 ||					 in all communication between servers and clients.
 ||
 |+-----------------------------------------------------------------------
 ||
 ||      Files: 1. Filename (includes full path!!!)
 ||				3. File 4096 bytes at a time
 ||
 ||		 Hearbeats: 1. Type 
 ||					2. Data
 ||
 ++-----------------------------------------------------------------------*/
package dht.net;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;

import dht.event.DHTEvent;
import dht.event.DHTEvent.EventType;
import dht.event.QueryEvent;
import dht.event.StorageEvent;

public class IO {
	
	private Socket socket;
	private ObjectInputStream input;
	private ObjectOutputStream output;
	private static final int BUFFER_SIZE = 4096; /* Max buffer size is (2^32)- 1; 4096 bytes is the block size of the new advanced format sector drives*/
	private String ip;
	
	public IO(Socket socket){
		this.setIp(socket.getInetAddress().getHostAddress().toString());
		this.socket = socket;
		try {
			this.output = new ObjectOutputStream(this.socket.getOutputStream());
			this.input = new ObjectInputStream(this.socket.getInputStream());
		} catch (IOException io){
			io.printStackTrace();
		}
	}
	
	
	/* Should send update information to all the nodes that have finger table entry for this node
	 * SERVER SPECIFIC!!!!!!!
	 * */
	public void heartBeat() {
		//TODO 
	}
	
	public void sendMessage(String message){
		try{
			output.writeObject(message);
			output.flush();
		} catch(IOException e){
			e.printStackTrace();

		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public String receiveMessage(){
		Object o;
		try {
			String message = "";
			o = input.readObject();

			if(o instanceof String){
				message = (String) o;
			}else
				System.err.println("Class: IO.java, Function: receiveFile(), Error: expected file name as String, received "+(o.toString()));
			return message;	
		} catch(IOException | ClassNotFoundException e){ 
			e.printStackTrace();
		}
		return null;
	}
		
	/* Sends a file over the socket
	 * If this file is to be written in a specific directory as specified by the dir_store command the file
	 * must already point to the correct location before being sent to this method
	 * */
	public void sendFile(File file, String path){
		
		System.out.println("Sending file...");
		System.out.println(path+file.getName());
		FileInputStream fileInput = null;
		try {
			/* SEND THE FILE NAME AS STRING*/
			if(!path.endsWith("/"))
				path+="/";
			output.writeObject(path+file.getName());
			output.flush();
			
			/* SEND THE FILE 4096 BYTES AT A TIME */
			fileInput = new FileInputStream(file);
			byte[] buffer = new byte[BUFFER_SIZE]; 
			Integer bytesRead = 0;
	
			while ( (bytesRead = fileInput.read(buffer)) > 0) {
				output.writeObject(bytesRead);
				output.flush();
				output.writeObject(buffer);
				output.flush();
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				fileInput.close();
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Sending file complete...");
	}
	
		
	/* Reads a file from the socket; returns the file it read */
	public void receiveFile(String path){

		System.out.println("Receiving file...");
		FileOutputStream fileOutput = null;
		Object o;
		try {
			/* READ FILE NAME and create parent directories if they don't exist */
			String filename = "";
			o = input.readObject();
			if(o instanceof String){
				filename = createParents(path);
			} else
				System.err.println("Class: IO.java, Function: receiveFile(), Error: expected file name as String, received "+(o.toString()));

			/* READ FILE 4096 BYTES AT A TIME
			 * Note: FileOutputStream creates empty file!!!
			 */
			fileOutput = new FileOutputStream(filename);
			byte[] buffer = new byte[BUFFER_SIZE];
			Integer bytesRead = 0;
			do {
				o = input.readObject();
				if(o instanceof Integer){
					bytesRead = (Integer) o;
				}else
					System.err.println("Class: IO.java, Function: receiveFile(), Error: expected file content as Integer, received "+(o.toString()));

				o = input.readObject();
				if(o instanceof byte[]){
					buffer = (byte[]) o;
				} else
					System.err.println("Class: IO.java, Function: receiveFile(), Error: expected file content as byte[], received "+(o.toString()));

				fileOutput.write(buffer, 0, bytesRead);
			} while(bytesRead==BUFFER_SIZE);

			/* Catching the EOF exception separate from a IOException allows detection of empty files (peeking at the stream did not work because it was not consistent)*/
		}catch(EOFException e){	
			/* Client sent an empty file */
			return;
		}catch(IOException | ClassNotFoundException e){ 
			e.printStackTrace();
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		System.out.println("Receiving file complete...");
	}
		
	/* Create the parent directories for the given file
	 */
	private String createParents(String filepath){
		System.out.println(filepath);
		String filename = filepath;
		if(filepath.contains("/")){
			String path = filepath.substring(0, filepath.lastIndexOf("/"));
			String[] dirs = path.split("/");
			try {
				System.out.println(Arrays.toString(dirs));
				String temp = "/";
				for(String dir : dirs){
					temp += dir+"/";
					if(!new File(temp).exists()) {
						System.out.println(new File(temp).mkdir());
					} 
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return filename;
	}

	public DHTEvent getEvent() {
		Object o  = null;
		DHTEvent event = null;
		try {
			o = input.readObject();
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
		}
		if (o instanceof DHTEvent) {
			event = (DHTEvent) o;
		} else {
			return null;
		}
		return event;
	}
	
	public void sendEvent(DHTEvent event) {	
		try{
			if(event.getEventType() == EventType.STORAGE) {
				output.writeObject(event);
				output.flush();
				StorageEvent se = (StorageEvent) event;
				sendFile(se.getFile(), se.getPath());
			} else if(event.getEventType() == EventType.QUERY) {
				output.writeObject(event);
				output.flush();
				QueryEvent qe = (QueryEvent) event;
				receiveFile(qe.getDest());
			} else {
				output.writeObject(event);
				output.flush();
			}
		}catch(IOException e){
			e.printStackTrace();
		}
		finally{
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	protected void finalize(){
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String getIp() {
		return ip;
	}


	public void setIp(String ip) {
		this.ip = ip;
	}
}

