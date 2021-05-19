import java.net.*; 
import java.io.*; 
import java.time.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.lang.Math;
import java.nio.charset.StandardCharsets;

// MyRunnable is to enable spinning up thread that calls serveClient
class MyRunnable implements Runnable {

    private Socket clientSocket;

    public MyRunnable(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void run() {
        try {
            ContentServer.serveClient(this.clientSocket);
        }   
        catch (Exception e){
            System.err.println(e);
        }
    }
}


// ContentServer is a functioning web file server that serves files in ./content/
public class ContentServer { 

    static int CHUNK_SIZE = 1000000; // Chunk size of 1MB

    public static void main(String[] args) throws IOException { 
        ServerSocket serverSocket = null; 
        int portNumber = Integer.parseInt(args[0]);
        String timeStr = getHTTPTime();

        try { 
            serverSocket = new ServerSocket(portNumber); 
        } 
        catch (IOException e) { 
            System.err.println("Could not listen on port: " + portNumber); 
            System.err.println(e);
            System.exit(1); 
        } 

        while(true) {
            Socket clientSocket = null; 
            try { 
                // Wait for connection
                clientSocket = serverSocket.accept(); 
            } 
            catch (IOException e) { 
                System.err.println("Accept failed."); 
                System.exit(1); 
            } 

            try {
                System.out.println("Connected");
                MyRunnable myRunnable = new MyRunnable(clientSocket);
                Thread t = new Thread(myRunnable);
                t.start();
            }
            catch(Exception e) {
                System.out.println("Exception!" + e);
            }

        }
        //serverSocket.close(); 
    } 

    public static void serveClient(Socket clientSocket) throws IOException {
        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream()); 
        BufferedReader in = new BufferedReader( 
                new InputStreamReader( clientSocket.getInputStream())); 

        String inputLine;

        while ((inputLine = in.readLine()) != null) {
            String [] inputSplit = inputLine.split(" ");
            //System.out.print(inputLine);
            
            if(inputSplit[0].equals("GET")) {
                // Read rest of request:
                int rangeStart = -1;
                int rangeEnd = -1;
                while((inputLine = in.readLine()).length() > 0) {
                    //System.out.print(inputLine);
                    String [] headerSplit = inputLine.split(":");

                    // Parse Range, if necessary
                    if(headerSplit[0].equals("Range")) {
                        String [] rangeSplit = headerSplit[1].split("(=)|(-)");
                        if(rangeSplit.length >= 2) {
                            rangeStart = Integer.parseInt(rangeSplit[1]);
                        }
                        if(rangeSplit.length >= 3) {
                            rangeEnd = Integer.parseInt(rangeSplit[2]);
                        }
                    }
                }

                String fileName = inputSplit[1];
                try {
                    File requestedFile = new File("./content" + fileName);
                    if(!requestedFile.exists()) {
                        replyFileNotFound(out);
                        continue;
                    }
                    int fileLength = (int)requestedFile.length();
                    String fileSuffix = fileName.split("\\.")[1];
                    String contentType = getContentType(fileSuffix);
                    // If no range specified
                    if(rangeStart == -1 && rangeEnd == -1) {
                        if(contentType.contains("video")) {
                            if(fileLength < CHUNK_SIZE) {
                                sendWholeData(requestedFile, fileSuffix, out);
                            }
                            else {
                                sendPartialData(requestedFile, fileSuffix, out, rangeStart, rangeEnd);
                            }
                        }
                        else {
                            sendWholeData(requestedFile, fileSuffix, out);
                        }
                    }
                    // If range was specified
                    else {
                        sendPartialData(requestedFile, fileSuffix, out, rangeStart, rangeEnd);
                    }
                }
                catch(Exception e) {
                    System.err.println(e);
                }
            }
        } 
        out.close(); 
        in.close(); 
        clientSocket.close(); 
    }

    public static String getHTTPTime() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(cal.getTime());
    }

    public static void replyFileNotFound(DataOutputStream out) throws IOException {
        String msg = "HTTP/1.1 404 Not Found\r\n";
        out.writeBytes(msg); 
    }

    public static void replyFileInternalError(DataOutputStream out) throws IOException {
        String msg = "HTTP/1.1 500 Internal Server Error\r\n";
        out.writeBytes(msg); 
    }


    public static String getContentType(String fileSuffix) {
        /*
            text/plain (.txt)
            text/css (.css)
            text/html (.htm, .html)
            image/gif (.gif)
            image/jpeg (.jpg, .jpeg)
            image/png (.png)
            video/webm (.webm)
            video/mp4 (.mp4)
            application/javascript (.js)
            application/ogg (.ogg)
            application/octet-stream (anything else)
        */
        switch(fileSuffix) {
            case "txt":
                return "text/plain";
            case "css":
                return "text/css";
            case "htm":
                return "text/html";
            case "html":
                return "text/html";
            case "gif":
                return "image/gif";
            case "jpg":
                return "image/jpeg";
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "webm":
                return "video/webm";
            case "mp4":
                return "video/mp4";
            case "js":
                return "application/javascript";
            case "ogg":
                return "application/ogg";
            default:
                return "application/octet-stream";
        }
    }

    public static void sendPartialData(File file, String fileSuffix, DataOutputStream out, int rangeStart, int rangeEnd) throws IOException {
        int fileLength = (int)file.length();
        int bytesDone = 0;

        if(rangeStart == -1) {
            rangeStart = 0;
        }
        if(rangeEnd == -1) {
            rangeEnd = Integer.min(rangeStart + CHUNK_SIZE, fileLength) - 1;
        }   

        RandomAccessFile raf = new RandomAccessFile(file, "r");
        raf.seek(rangeStart);
        
        int size = rangeEnd-rangeStart+1;
        String msg = "HTTP/1.1 206 Partial Content\r\nConnection: keep-alive\r\nKeep-Alive: timeout=50, max=1000\r\n";
        msg += "Date: " + getHTTPTime() + "\r\n";
        int contentLength = size;
        msg += "Content-Length: " + contentLength + "\r\n";
        String contentType = getContentType(fileSuffix);
        msg += "Content-Type: " + contentType + "\r\n";
        msg += "Accept-Ranges: bytes\r\n";
        msg += "Content-Range: bytes " + rangeStart + "-" + rangeEnd + "/" + fileLength + "\r\n";
        out.writeBytes(msg + "\r\n");

        byte fileContent[] = new byte[size];
        raf.read(fileContent);
        out.write(fileContent, 0, size);
        bytesDone += size;
    }


    public static void sendWholeData(File file, String fileSuffix, DataOutputStream out) throws IOException, FileNotFoundException {
        FileInputStream fin = new FileInputStream(file);
        //byte fileContent[] = new byte[CHUNK_SIZE];
        String msg = "HTTP/1.1 200 OK\r\nConnection: keep-alive\r\nKeep-Alive: timeout=50, max=1000\r\n";
        msg += "Date: " + getHTTPTime() + "\r\n";
        int contentLength = (int)file.length();
        int size = Integer.min(CHUNK_SIZE, contentLength);
        msg += "Content-Length: " + contentLength + "\r\n";
        String contentType = getContentType(fileSuffix);
        msg += "Content-Type: " + contentType + "\r\n";
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
	    msg += "Last-Modified: " + dateFormat.format(file.lastModified()) + "\r\n" ;

        //System.out.println(msg);
        out.writeBytes(msg + "\r\n");

        int bytesDone = 0;
        byte fileContent[] = new byte[CHUNK_SIZE];
        while(contentLength - bytesDone > 0 && fin.read(fileContent) != -1) {
            out.write(fileContent, 0, size);
            bytesDone += size;
            size = Integer.min(CHUNK_SIZE, contentLength - bytesDone);
            fileContent = new byte[CHUNK_SIZE];
        }
        out.writeBytes("\r\n");
    }
}
