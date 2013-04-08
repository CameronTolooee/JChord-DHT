package dht.chord;

import java.net.Socket;

import java.util.concurrent.CountDownLatch;

import dht.event.DHTEvent;
import dht.event.SUpdateTableEvent;
import dht.event.StabilizeSEvent;
import dht.net.IO;

public class ChordStabilizeThread implements Runnable {

	private ChordNode node;
	private CountDownLatch cdl;
	
	public ChordStabilizeThread(ChordNode node, CountDownLatch cdl){
		this.cdl = cdl;
		this.node = node;
	}
	
	@Override
	public void run() {
		try {
			//Thread.sleep(1000);
			cdl.await();
			System.out.println("Done waiting");
			System.out.println("nodeID: "+ node.getId());
			System.out.println("succ ID: "+node.getSuccessor().getId());
			if(!node.getId().equals(node.getSuccessor().getId())){
				DHTEvent stabilizeEventS = new StabilizeSEvent(node);
				IO comm3 = new IO(new Socket(node.getSuccessor().getId(), ChordNode.PORT));
				comm3.sendEvent(stabilizeEventS);
				System.out.println("Sent stablization event");
			}
			//Thread.sleep(1000);
			node.updateTable(node);
			DHTEvent updateTable = new SUpdateTableEvent(node.getId());
			IO comm4 = new IO(new Socket(node.getSuccessor().getId(), ChordNode.PORT));
			comm4.sendEvent(updateTable);
			System.out.println("Initiated update table for other nodes");
		} catch (Exception e){
			e.printStackTrace();
		}
	}

}