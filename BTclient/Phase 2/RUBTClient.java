//package client;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.*;
import java.io.*;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.nio.ByteBuffer;
import java.lang.*;
public class RUBTClient{

    public static String file_destination;
    public static byte[] peerid = "alexchriskyung123456".getBytes();
    public static byte[] protocol_string = new byte[] { 'B', 'i', 't', 'T',
			'o', 'r', 'r', 'e', 'n', 't', ' ', 'p', 'r', 'o', 't', 'o', 'c',
			'o', 'l' };
	public static TorrentInfo tInfo;
	public static byte[] info_hash = null;
    public static MemCheck globalMemory = null;
    public static final int PEER_LIMIT = 5;
	public static void main(String[] args) throws FileNotFoundException, IOException{
		/*Get variables ready*/
        
        String torrentFN;
		String saveFN;
		TrackerResponse tResponseDecoded = null;
		boolean found = false;
		boolean ret = false;
		ArrayList<Peer> peers = null;
		byte[] tResponse = null;
		String encodedText = "";
		byte[] b = null;
        /*check command line arguments*/
		if(args.length == 2){
			torrentFN = args[0];
			saveFN = args[1];
			file_destination = saveFN;
		}
		else{
			System.out.println("Invalid number of command line arguments");
			return;
		}
        RandomAccessFile fSave = null;
        try{
            fSave = new RandomAccessFile(new File(saveFN), "rw");
        }catch(FileNotFoundException e){}
        /*read torrent file*/
		BufferedReader reader = null;
		try{
			b = Files.readAllBytes(Paths.get(torrentFN));
		} catch(FileNotFoundException e){
			System.out.println("Caught Exception File not found");
			//e.printStackTrace();
		} catch(IOException e){
			System.out.println("Caught Exception IOException");
			//e.printStackTrace();
		} finally {
			try{
				if(reader != null){
					reader.close();
				}
			} catch(IOException e){
				System.out.println("Caught Exception IOException");
				//e.printStackTrace();
			}

		}
        /*send bytes to helper class*/
        try{
            tInfo = new TorrentInfo(b);
            try{
                fSave.setLength((long)tInfo.file_length);
                byte[] empty = new byte[tInfo.file_length];
                fSave.write(empty, 0,empty.length);
                }catch(IOException e){}
            globalMemory = new MemCheck(tInfo,fSave);
            System.out.println("File mem length is " + fSave.length());
            System.out.println(tInfo.file_name);
        }catch(BencodingException e){
            System.out.println("Bencoding Exception");
        }
        try{
        	tResponse = getTrackerResponse(tInfo);
        }
        catch(Exception e){
        	System.out.println("Problem with GET Request, program exiting");
        	return;
        }
        /*decode the tracker response*/
        try{
        	tResponseDecoded = decodeTrackerResponse(tResponse);
        }catch(Exception e){
        	System.out.println("Problem decoding tracker response");
        	return;
        }

        peers = tResponseDecoded.peers;
        int peerIndex;
        info_hash = tInfo.info_hash.array();
        if(peers.size() < 15){
            //in actual production peerIndex < peers.size();
            for(peerIndex = 0; peerIndex < 3; peerIndex++){
              if(!peers.get(peerIndex).ipAdd.equals("128.6.171.132")){
		      Peer peer = new Peer(peers.get(peerIndex).ipAdd, peers.get(peerIndex).port, tInfo ,peerid);
              Thread thread = new Thread(peer);
              thread.start();
            }
	       }
        }else{
            for(peerIndex = 0; peerIndex < 3; peerIndex++){
              Peer peer = new Peer(peers.get(peerIndex).ipAdd, peers.get(peerIndex).port, tInfo ,peerid);
              Thread thread = new Thread(peer);
              thread.start();
           }
        }
        while(!globalMemory.isFinished){
            try {
                Thread.sleep(1000);

            }catch(InterruptedException ex){
                Thread.currentThread().interrupt();
            }
        }
        ArrayList<Piece> finalP = globalMemory.pieces;
        FileOutputStream fOutStream;
        try{
        fOutStream = new FileOutputStream(new File(file_destination));
        }catch(FileNotFoundException e){
            return;
        }
        for(int i = 0; i < globalMemory.numPieces; i++){
            Piece f = finalP.get(i);
            try{
                fOutStream.write(f.getBytes());
            }catch(IOException e){
                System.out.println("Problem");
                return;
            }
        }

    }
    
    public static TrackerResponse decodeTrackerResponse(byte[] tr) throws BencodingException{
    	Object o = Bencoder2.decode(tr);
    	HashMap<ByteBuffer, Object> response = (HashMap<ByteBuffer, Object>) o;
    	TrackerResponse tr2 = null;
        //call TrackerResponse.java decode the information
    	try{
    		tr2 = new TrackerResponse(response);
    	}catch(Exception e){
    		System.out.println("problem getting tracker response");
    		e.printStackTrace();
    	}
    	if(tr2 == null){
    		System.out.println("nothing being decoded");
    	}
    	return tr2;
    }

    public static byte[] getTrackerResponse(TorrentInfo ti) throws UnknownHostException, IOException{
    	String info_hash = toHexString(ti.info_hash.array());
    	String peer_id = toHexString("alexchriskyung123456".getBytes());
    	String port = "" + 6883;
    	String downloaded = "" + 0;
    	String uploaded = "" + 0;
    	String left = "" + ti.file_length;
    	String announce = ti.announce_url.toString();
    	String aURL= announce.toString();
    	aURL += "?" + "info_hash" + "=" + info_hash + "&" + "peer_id" + "=" + peer_id + "&" + "port" + "=" + port + "&" + "uploaded" + "="
    	+ uploaded + "&" + "downloaded" + "=" + downloaded + "&" + "left" + "=" + left;
    	System.out.println("URL is : " + aURL);
    	HttpURLConnection con = (HttpURLConnection)new URL(aURL).openConnection();
    	DataInputStream dInStream = new DataInputStream(con.getInputStream());
    	int dSize = con.getContentLength();
    	byte[] retBytes = new byte[dSize];
    	dInStream.readFully(retBytes);
    	dInStream.close();
    	return retBytes;
    }
    
    public static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        char[] hex = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A',
            'B', 'C', 'D', 'E', 'F' };
        
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            byte hi = (byte) ((b >> 4) & 0x0f);
            byte lo = (byte) (b & 0x0f);
            sb.append('%').append(hex[hi]).append(hex[lo]);
        }
        
        return sb.toString();
    }
    
}
