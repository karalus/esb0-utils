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
import java.util.concurrent.TimeUnit;

import com.artofarc.util.KMPInputStream;
import com.artofarc.util.URLUtils;

/**
 * @see https://github.com/Baekalfen/ICAP-avscan
 */
public final class ICAP implements Closeable {
    private static final int BUFFER_SIZE = 8 * 1024;
    private static final int STD_RECEIVE_LENGTH = 8192;
    private static final int STD_SEND_LENGTH = 8192;
    private static final String VERSION   = "1.0";
    private static final String USERAGENT = "ESB0 ICAP Client/1.1";
    private static final KMPInputStream.Pattern ICAPTERMINATOR = new KMPInputStream.Pattern("\r\n\r\n".getBytes(StandardCharsets.UTF_8));
    private static final KMPInputStream.Pattern HTTPTERMINATOR = new KMPInputStream.Pattern("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));

    private final String serverIP;
    private final int port;
    private final String icapService;

    private final Socket client;
    private final OutputStream out;
    private final KMPInputStream in;

    private final int stdPreviewSize;
    private final byte[] recvBuffer = new byte[STD_RECEIVE_LENGTH];

    private HashMap<String,String> responseMap;
    private String responseText;
    private long optionsExpiration = Long.MAX_VALUE;
    private long lastUse = Long.MAX_VALUE;

    /**
     * Initializes the socket connection and IO streams. It asks the server for the available options and
     * changes settings to match it.
     * @param serverIP The IP address to connect to.
     * @param port The port in the host to use.
     * @param icapService The service to use (fx "avscan").
     * @throws IOException
     * @throws ICAPException
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

        stdPreviewSize = getOptions();
    }

    /**
     * Initializes the socket connection and IO streams. This overload doesn't
     * use getOptions(), instead a previewSize is specified.
     * @param s The IP address to connect to.
     * @param p The port in the host to use.
     * @param icapService The service to use (fx "avscan").
     * @param previewSize Amount of bytes to  send as preview.
     * @throws IOException
     * @throws ICAPException
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

    public boolean scanFile(String filename, InputStream fileInStream) throws IOException,ICAPException{
        // First part of header
        String resHeader= "GET /" + URLUtils.encode(filename) + " HTTP/1.1\r\nHost: " + serverIP + ":" + port + "\r\n\r\n";
        String resBody = resHeader + "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n";

        byte[] buffer = new byte[STD_SEND_LENGTH];
        int len = fileInStream.read(buffer);
        int previewSize = stdPreviewSize;
        if (len < stdPreviewSize){
            previewSize = len;
        }

        String requestBuffer =
            "RESPMOD icap://"+serverIP+"/"+icapService+" ICAP/"+VERSION+"\r\n"
            +"Host: "+serverIP+"\r\n"
            +"User-Agent: "+USERAGENT+"\r\n"
            +"Allow: 204\r\n"
            +"Preview: "+previewSize+"\r\n"
            +"Encapsulated: req-hdr=0, res-hdr=" + resHeader.length() + ", res-body="+resBody.length()+"\r\n"
            +"\r\n"
            +resBody
            +Integer.toHexString(previewSize) +"\r\n";

        sendString(requestBuffer, false);

        //Sending preview or, if smaller than previewSize, the whole file.
        out.write(buffer, 0, previewSize);
        sendString("\r\n", false);
        if (len<=stdPreviewSize){
            sendString("0; ieof\r\n\r\n", true);
        }
        else if (previewSize != 0){
            out.write(HTTPTERMINATOR.bytes());
            out.flush();
        }

        // Parse the response! It might not be "100 continue"
        // if fileSize<previewSize, then this is acutally the respond
        // otherwise it is a "go" for the rest of the file.
        int status;

        if (len>previewSize){
            String parseMe = getHeader(ICAPTERMINATOR);
            responseMap = parseHeader(parseMe);

            String tempString = responseMap.get("StatusCode");
            if (tempString != null){
                status = Integer.parseInt(tempString);

                switch (status){
                    case 100: break; //Continue transfer
                    case 200: case 201: return false;
                    case 204: return true;
                    case 404: throw new ICAPException("404: ICAP Service not found");
                    default: throw new ICAPException("Server returned unknown status code:"+status);
                }
            }
            //Sending remaining part of file
            len -= previewSize;
            do {
                sendString(Integer.toHexString(len) +"\r\n", false);
                out.write(buffer, previewSize, len);
                sendString("\r\n", false);
                previewSize = 0;
            } while ((len = fileInStream.read(buffer)) != -1);
            //Closing file transfer.
            out.write(HTTPTERMINATOR.bytes());
            out.flush();
        }

        String response = getHeader(ICAPTERMINATOR);
        responseMap = parseHeader(response);
        responseText = null;

        String tempString=responseMap.get("StatusCode");
        if (tempString != null){
            status = Integer.parseInt(tempString);

            if (status == 204){return true;} //Unmodified

            if (status == 200 || status == 201){ //OK - The ICAP status is ok, but the encapsulated HTTP status will likely be different
                response = getHeader(HTTPTERMINATOR);
                int x = response.indexOf("</title>",0);
                if (x >= 0) {
                    int y = response.indexOf("</html>",x);
                    responseText = response.substring(x+8,y);

                    if (responseText.length() > 0){
                        return false;
                    }
                }
            }
        }
        throw new ICAPException("Unrecognized or no status code in response header.");
    }

    /**
     * Automatically asks for the servers available options and returns the raw response as a String.
     * @return String of the servers response.
     * @throws IOException
     * @throws ICAPException
     */
    private int getOptions() throws IOException, ICAPException{
        //Send OPTIONS header and receive response
        String requestHeader =
                  "OPTIONS icap://"+serverIP+"/"+icapService+" ICAP/"+VERSION+"\r\n"
                + "Host: "+serverIP+"\r\n"
                + "User-Agent: "+USERAGENT+"\r\n"
                + "Encapsulated: null-body=0\r\n"
                + "\r\n";

        sendString(requestHeader, true);

        String parseMe = getHeader(ICAPTERMINATOR);
        responseMap = parseHeader(parseMe);

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
     * Receive an expected ICAP header as response of a request. The returned String should be parsed with parseHeader()
     * @param terminator
     * @return String of the raw response
     * @throws IOException
     * @throws ICAPException
     */
    private String getHeader(KMPInputStream.Pattern terminator) throws IOException, ICAPException{
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
    private HashMap<String,String> parseHeader(String response){
        HashMap<String,String> headers = new HashMap<>();

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
        int i = response.indexOf("\r\n",y);
        i+=2;
        while (i+2!=response.length() && response.substring(i).contains(":")) {

            int n = response.indexOf(":",i);
            String key = response.substring(i, n);

            n += 2;
            i = response.indexOf("\r\n",n);
            String value = response.substring(n, i);

            headers.put(key, value);
            i+=2;
        }

        return headers;
    }

    /**
     * Sends a String through the socket connection. Used for sending ICAP/HTTP headers.
     * @param requestHeader
     * @param withFlush
     * @throws IOException
     */
    private void sendString(String requestHeader, boolean withFlush) throws IOException{
        out.write(requestHeader.getBytes(StandardCharsets.UTF_8));
        if (withFlush) {
            out.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
           client.close();
        }
    }

	public String getISTag() throws ICAPException, IOException {
		if (responseMap == null || System.nanoTime() > optionsExpiration) {
			getOptions();
		}
		String ISTag = responseMap.get("ISTag");
		return ISTag.substring(1, ISTag.length() - 1);
	}

	public String getResponseText() {
		return responseText;
	}

	public long getIdleTime() {
		return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - lastUse);
	}

}
