/*
The MIT License (MIT)

Copyright (c) 2013 Baekalfen

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
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

/**
 * @see https://github.com/Baekalfen/ICAP-avscan
 */
public final class ICAP implements Closeable {
    private static final Charset StandardCharsetsUTF8 = Charset.forName("UTF-8");
    private static final int BUFFER_SIZE = 32 * 1024;
    private static final int STD_RECEIVE_LENGTH = 8192;
    private static final int STD_SEND_LENGTH = 8192;
    private static final String VERSION   = "1.0";
    private static final String USERAGENT = "IT-Kartellet ICAP Client/1.1";
    private static final String ICAPTERMINATOR = "\r\n\r\n";
    private static final String HTTPTERMINATOR = "0\r\n\r\n";

    private final String serverIP;
    private final int port;

    private final Socket client;
    private final DataOutputStream out;
    private final DataInputStream in;

    private final String icapService;

    private final int stdPreviewSize;

    private Map<String,String> responseMap;

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

        //Openening out stream
        OutputStream outToServer = client.getOutputStream();
        out = new DataOutputStream(new BufferedOutputStream(outToServer, BUFFER_SIZE));

        //Openening in stream
        InputStream inFromServer = client.getInputStream();
        in = new DataInputStream(inFromServer);

        String parseMe = getOptions();
        responseMap = parseHeader(parseMe);

        if (responseMap.get("StatusCode") != null){
            int status = Integer.parseInt(responseMap.get("StatusCode"));

            switch (status){
                case 200:
                	String tempString = responseMap.get("Preview");
                    if (tempString != null){
                        stdPreviewSize=Integer.parseInt(tempString);
                        break;
                    }
                default: throw new ICAPException("Could not get preview size from server");
            }
        }
        else{
            throw new ICAPException("Could not get options from server");
        }
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
    public ICAP(String s,int p, String icapService, int previewSize) throws IOException, ICAPException{
        this.icapService = icapService;
        serverIP = s;
        port = p;
        //Initialize connection
        client = new Socket(serverIP, port);

        //Opening out stream
        OutputStream outToServer = client.getOutputStream();
        out = new DataOutputStream(outToServer);

        //Opening in stream
        InputStream inFromServer = client.getInputStream();
        in = new DataInputStream(inFromServer);

        stdPreviewSize = previewSize;
    }

    /**
     * Given a filepath, it will send the file to the server and return true,
     * if the server accepts the file. Visa-versa, false if the server rejects it.
     * @param filename Relative or absolute filepath to a file.
     * @return Returns true when no infection is found.
     */
    public boolean scanFile(String filename) throws IOException,ICAPException{
        File file = new File(filename);
        try(InputStream inputStream = new FileInputStream(file)) {
            return scanFile(file.getName(), inputStream, file.length());
        }
    }

    public boolean scanFile(String filename, InputStream fileInStream, long fileSize) throws IOException,ICAPException{

        // First part of header
        String resHeader= "GET /" + URLEncoder.encode(filename, StandardCharsets.UTF_8.name()) + " HTTP/1.1\r\nHost: " + serverIP + ":" + port + "\r\n\r\n";
        String resBody = resHeader + "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Length: "+fileSize+"\r\n\r\n";

        int previewSize = stdPreviewSize;
        if (fileSize < stdPreviewSize){
            previewSize = (int) fileSize;
        }

        String requestBuffer =
            "RESPMOD icap://"+serverIP+"/"+icapService+" ICAP/"+VERSION+"\r\n"
            +"Host: "+serverIP+"\r\n"
            +"Connection:  close\r\n"
            +"User-Agent: "+USERAGENT+"\r\n"
            +"Allow: 204\r\n"
            +"Preview: "+previewSize+"\r\n"
            +"Encapsulated: req-hdr=0, res-hdr=" + resHeader.length() + ", res-body="+resBody.length()+"\r\n"
            +"\r\n"
            +resBody
            +Integer.toHexString(previewSize) +"\r\n";

        sendString(requestBuffer, false);

        //Sending preview or, if smaller than previewSize, the whole file.
        byte[] chunk = new byte[previewSize];

        fileInStream.read(chunk);
        out.write(chunk);
        sendString("\r\n", false);
        if (fileSize<=previewSize){
            sendString("0; ieof\r\n\r\n", true);
        }
        else if (previewSize != 0){
            sendString("0\r\n\r\n", true);
        }

        // Parse the response! It might not be "100 continue"
        // if fileSize<previewSize, then this is acutally the respond
        // otherwise it is a "go" for the rest of the file.
        int status;

        if (fileSize>previewSize){
            String parseMe = getHeader(ICAPTERMINATOR);
            responseMap = parseHeader(parseMe);

            String tempString = responseMap.get("StatusCode");
            if (tempString != null){
                status = Integer.parseInt(tempString);

                switch (status){
                    case 100: break; //Continue transfer
                    case 200: return false;
                    case 204: return true;
                    case 404: throw new ICAPException("404: ICAP Service not found");
                    default: throw new ICAPException("Server returned unknown status code:"+status);
                }
            }
        }

        //Sending remaining part of file
        if (fileSize > previewSize){
            byte[] buffer = new byte[STD_SEND_LENGTH];
            while ((fileInStream.read(buffer)) != -1) {
                sendString(Integer.toHexString(buffer.length) +"\r\n", false);
                out.write(buffer);
                sendString("\r\n", false);
            }
            //Closing file transfer.
            requestBuffer = "0\r\n\r\n";
            sendString(requestBuffer, true);
        }

        String response = getHeader(ICAPTERMINATOR);
        responseMap = parseHeader(response);

        String tempString=responseMap.get("StatusCode");
        if (tempString != null){
            status = Integer.parseInt(tempString);

            if (status == 204){return true;} //Unmodified

            if (status == 200){ //OK - The ICAP status is ok, but the encapsulated HTTP status will likely be different
                response = getHeader(HTTPTERMINATOR);
                int x = response.indexOf("<title>",0);
                int y = response.indexOf("</title>",x);
                String statusCode = response.substring(x+7,y);

                if (statusCode.equals("ProxyAV: Access Denied")){
                    return false;
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
    private String getOptions() throws IOException, ICAPException{
        //Send OPTIONS header and receive response
        //Sending and recieving
        String requestHeader =
                  "OPTIONS icap://"+serverIP+"/"+icapService+" ICAP/"+VERSION+"\r\n"
                + "Host: "+serverIP+"\r\n"
                + "User-Agent: "+USERAGENT+"\r\n"
                + "Encapsulated: null-body=0\r\n"
                + "\r\n";

        sendString(requestHeader, true);

        return getHeader(ICAPTERMINATOR);
    }

    /**
     * Receive an expected ICAP header as response of a request. The returned String should be parsed with parseHeader()
     * @param terminator
     * @return String of the raw response
     * @throws IOException
     * @throws ICAPException
     */
    private String getHeader(String terminator) throws IOException, ICAPException{
        byte[] endofheader = terminator.getBytes(StandardCharsetsUTF8);
        byte[] buffer = new byte[STD_RECEIVE_LENGTH];

        int n;
        int offset=0;
        //STD_RECEIVE_LENGTH-offset is replaced by '1' to not receive the next (HTTP) header.
        while((offset < STD_RECEIVE_LENGTH) && ((n = in.read(buffer, offset, 1)) != -1)) { // first part is to secure against DOS
            offset += n;
            if (offset>endofheader.length+13){ // 13 is the smallest possible message "ICAP/1.0 xxx "
                byte[] lastBytes = Arrays.copyOfRange(buffer, offset-endofheader.length, offset);
                if (Arrays.equals(endofheader,lastBytes)){
                    return new String(buffer,0,offset, StandardCharsetsUTF8);
                }
            }
        }
        throw new ICAPException("Error in getHeader() method");
    }

    /**
     * Given a raw response header as a String, it will parse through it and return a HashMap of the result
     * @param response A raw response header as a String.
     * @return HashMap of the key,value pairs of the response
     */
    private Map<String,String> parseHeader(String response){
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
        out.write(requestHeader.getBytes(StandardCharsetsUTF8));
        if (withFlush) {
            out.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (this.client != null) {
            this.client.close();
        }
    }

	public Map<String, String> getResponseMap() {
		return responseMap;
	}

}
