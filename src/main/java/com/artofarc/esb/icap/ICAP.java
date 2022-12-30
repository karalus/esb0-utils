/*
The MIT License (MIT)

Copyright (c) 2013 Baekalfen, 2022 Andre Karalus

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package com.artofarc.esb.icap;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.artofarc.util.KMPInputStream;
import com.artofarc.util.URLUtils;

/**
 * ICAP implementation for virus scanners.
 * @see https://github.com/Baekalfen/ICAP-avscan
 */
public final class ICAP implements Closeable {
    private static final int BUFFER_SIZE = 8 * 1024;
    private static final int STD_RECEIVE_LENGTH = 8192;
    private static final int STD_SEND_LENGTH = 8192;
    private static final String VERSION   = "1.0";
    private static final String USERAGENT = "ESB0 ICAP Client/1.1";
    private static final byte[] EOL = "\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final KMPInputStream.Pattern ICAPTERMINATOR = new KMPInputStream.Pattern("\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
    private static final KMPInputStream.Pattern HTTPTERMINATOR = new KMPInputStream.Pattern("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));

    private final String serverIP;
    private final int port;
    private final String icapService;

    private final Socket client;
    private final OutputStream out;
    private final KMPInputStream in;

    private final int stdPreviewSize;
    private final byte[] recvBuffer = new byte[STD_RECEIVE_LENGTH];

    private Map<String,String> responseMap;
    private String responseText;
    private long optionsExpiration = Long.MAX_VALUE;
    private long lastUse = Long.MAX_VALUE;
    private ScanEngine scanEngine;

    static class ScanEngine {
    	public boolean isVirus(int status, Map<String, String> responseMap) {
    		return status == 403 || responseMap.containsKey("X-Infection-Found");
    	}

    	public boolean isOk(int status) {
    		return status == 200 || status == 204;
    	}

    	public String parseResponse(Map<String, String> responseMap, String response) {
    		return responseMap.get("X-Virus-ID");
    	}
    }

    /**
     * Initializes the socket connection and IO streams. It asks the server for the available options and
     * changes settings to match it.
     * @param serverIP The IP address to connect to.
     * @param port The port in the host to use.
     * @param icapService The service to use (fx "avscan").
     */
    public ICAP(String serverIP, int port, String icapService) throws IOException, ICAPException{
        this.icapService = icapService;
        this.serverIP = serverIP;
        this.port = port;
        //Initialize connection
        client = new Socket(serverIP, port);
        client.setKeepAlive(true);
        client.setSoTimeout(60000);

        //Opening out stream
        out = new BufferedOutputStream(client.getOutputStream(), BUFFER_SIZE);

        //Opening in stream
        in = new KMPInputStream(new BufferedInputStream(client.getInputStream()));

        stdPreviewSize = options();
    }

    /**
     * Initializes the socket connection and IO streams. This overload doesn't
     * use getOptions(), instead a previewSize is specified.
     * @param s The IP address to connect to.
     * @param p The port in the host to use.
     * @param icapService The service to use (fx "avscan").
     * @param previewSize Amount of bytes to  send as preview.
     */
    public ICAP(String s,int p, String icapService, int previewSize) throws IOException{
        this.icapService = icapService;
        serverIP = s;
        port = p;
        //Initialize connection
        client = new Socket(serverIP, port);
        client.setKeepAlive(true);
        client.setSoTimeout(60000);

        //Opening out stream
        out = new BufferedOutputStream(client.getOutputStream(), BUFFER_SIZE);

        //Opening in stream
        in = new KMPInputStream(new BufferedInputStream(client.getInputStream()));

        stdPreviewSize = previewSize;
    }

    public ScanEngine getScanEngine() {
    	if (scanEngine == null) {
    		scanEngine = new ScanEngine();
    	}
		return scanEngine;
	}

	public void setScanEngine(ScanEngine scanEngine) {
		this.scanEngine = scanEngine;
	}

	/**
     * Given a filepath, it will send the file to the server and return true,
     * if the server accepts the file. Visa-versa, false if the server rejects it.
     * @param filename Relative or absolute filepath to a file.
     * @return Returns true when no infection is found.
     */
    public boolean scanFile(String filename, InputStream fileInStream) throws IOException,ICAPException{
        // First part of header
        String resHeader= "GET /" + URLUtils.encode(filename) + " HTTP/1.1\r\nHost: " + serverIP + ":" + port + "\r\n\r\n";
        String resBody = resHeader + "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n";

        byte[] buffer = new byte[STD_SEND_LENGTH];
        int len = fileInStream.read(buffer);
        int previewSize = len < stdPreviewSize ? len : stdPreviewSize;

        sendString("RESPMOD icap://"+serverIP+"/"+icapService+" ICAP/"+VERSION+"\r\n"
            +"Host: "+serverIP+"\r\n"
            +"User-Agent: "+USERAGENT+"\r\n"
            +"Allow: 204\r\n"
            +"Preview: "+previewSize+"\r\n"
            +"Encapsulated: req-hdr=0, res-hdr=" + resHeader.length() + ", res-body="+resBody.length()+"\r\n"
            +"\r\n"
            +resBody
            +Integer.toHexString(previewSize) +"\r\n", false);

        //Sending preview or, if smaller than previewSize, the whole file.
        out.write(buffer, 0, previewSize);
        out.write(EOL);
        if (len<=stdPreviewSize){
            sendString("0; ieof\r\n\r\n", true);
        }
        else if (previewSize != 0){
            out.write(HTTPTERMINATOR.bytes());
            out.flush();
        }

        // Parse the response! It might not be "100 continue"
        // if fileSize<previewSize, then this is actually the response
        // otherwise it is a "go" for the rest of the file.
        if (len>previewSize){
            responseMap = parseHeaders(parse(ICAPTERMINATOR));

            String tempString = responseMap.get("StatusCode");
            if (tempString != null){
                int status = Integer.parseInt(tempString);

                if (getScanEngine().isVirus(status, responseMap) ) {
                	return false;
                }
                if (getScanEngine().isOk(status)) {
                	return true;
                }
                switch (status){
                    case 100: break; //Continue transfer
                    case 404: throw new ICAPException("404: ICAP Service not found");
                    default: throw new ICAPException("Server returned unexpected status code:"+status);
                }
            }
            else {
                throw new ICAPException("Unexpected or no status code in response header.");
            }
            //Sending remaining part of file
            len -= previewSize;
            do {
                sendString(Integer.toHexString(len) +"\r\n", false);
                out.write(buffer, previewSize, len);
                out.write(EOL);
                previewSize = 0;
            } while ((len = fileInStream.read(buffer)) != -1);
            //Closing file transfer.
            out.write(HTTPTERMINATOR.bytes());
            out.flush();
        }

        responseMap = parseHeaders(parse(ICAPTERMINATOR));
        responseText = null;

        String tempString=responseMap.get("StatusCode");
        if (tempString != null){
            int status = Integer.parseInt(tempString);

            if (getScanEngine().isVirus(status, responseMap) ) {
            	responseText = getScanEngine().parseResponse(responseMap, parse(HTTPTERMINATOR));
            	return false;
            }
            if (getScanEngine().isOk(status)) {
            	return true;
            }
        }
        throw new ICAPException("Unexpected or no status code in response header.");
    }

    /**
     * Asks for the servers available options.
     * @return Preview size.
     */
    private int options() throws IOException, ICAPException{
        //Send OPTIONS header and receive response
    	sendString("OPTIONS icap://"+serverIP+"/"+icapService+" ICAP/"+VERSION+"\r\n"
                + "Host: "+serverIP+"\r\n"
                + "User-Agent: "+USERAGENT+"\r\n"
                + "Encapsulated: null-body=0\r\n"
                + "\r\n", true);

        responseMap = parseHeaders(parse(ICAPTERMINATOR));

        if (responseMap.get("StatusCode") != null){
            int status = Integer.parseInt(responseMap.get("StatusCode"));

            switch (status){
                case 200:
                    String tempString = responseMap.get("Options-TTL");
                    if (tempString != null) {
                        optionsExpiration = System.nanoTime() + TimeUnit.SECONDS.toNanos(Integer.parseInt(tempString));
                    }
                    tempString = responseMap.get("Preview");
                    if (tempString != null){
                        return Integer.parseInt(tempString);
                    }
                default: throw new ICAPException("Could not get preview size from server");
            }
        }
        else{
            throw new ICAPException("Could not get options from server");
        }
    }

    /**
     * Receive a chunk of ICAP data as a response to a request. The returned string can be parsed with {@link #parseHeaders} if it contains headers.
     * @return String of the raw response including terminator
     */
    private String parse(KMPInputStream.Pattern terminator) throws IOException, ICAPException{
        in.setPattern(terminator);
        int pos = in.read(recvBuffer);
        lastUse = System.nanoTime();
        if (in.indexOf() < 0) {
            throw new ICAPException("Terminator not found");
        }
        return new String(recvBuffer,0,pos, StandardCharsets.UTF_8);
    }

    /**
     * Given a raw response header as a String, it will parse through it and return a HashMap of the result
     * @param response A raw response header as a String.
     * @return HashMap of the key,value pairs of the response
     */
    private Map<String,String> parseHeaders(String response){
        Map<String,String> headers = new HashMap<>();

        /****SAMPLE:****
         * ICAP/1.0 204 Unmodified
         * Server: C-ICAP/0.1.6
         * Connection: keep-alive
         * ISTag: CI0001-000-0978-6918203
         */
        // The status code is located between the first 2 whitespaces.
        // Read status code
        int x = response.indexOf(" ",0);
        int y = response.indexOf(" ",x+1);
        String statusCode = response.substring(x+1,y);
        headers.put("StatusCode", statusCode);

        // Each line in the sample is ended with "\r\n".
        // When (i+2==response.length()) The end of the header have been reached.
        // The +=2 is added to skip the "\r\n".
        // Read headers
        int i = response.indexOf("\r\n",y), j;
        i+=2;
        while (i+2<response.length() && (j = response.indexOf(':',i)) > 0) {

            String key = response.substring(i, j);
            j += 2;
            i = response.indexOf("\r\n",j);
            String value = response.substring(j, i);
            i+=2;
            while (i+2<response.length() && Character.isWhitespace(response.charAt(i))) {
            	// folded header (e.g. X-Violations-Found)
            	j = response.indexOf("\r\n",i);
            	value += response.substring(i, j);
            	i=j+2;
            }
            headers.put(key, value);
        }

        return headers;
    }

    /**
     * Sends a String through the socket connection. Used for sending ICAP/HTTP headers.
     */
    private void sendString(String requestHeader, boolean withFlush) throws IOException{
        out.write(requestHeader.getBytes(StandardCharsets.UTF_8));
        if (withFlush) {
            out.flush();
        }
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

	/**
	 * @return The ISTag which refers to a unique version of scan engine and signature files. Length is 32 chars.
	 */
	public String getISTag() throws ICAPException, IOException {
		if (responseMap == null || System.nanoTime() > optionsExpiration) {
			options();
		}
		String ISTag = responseMap.get("ISTag");
		return ISTag.substring(1, ISTag.length() - 1);
	}

	/**
	 * @return The response text to a virus finding inside a html page.
	 */
	public String getResponseText() {
		return responseText;
	}

	/**
	 * @return The time in seconds the underlying socket has not been used.
	 */
	public long getIdleTime() {
		return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - lastUse);
	}

}
