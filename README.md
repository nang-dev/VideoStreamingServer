# Web Video Streaming Server (youtube sort-of clone)

### View this project at: [http://3.13.89.142/](http://3.13.89.142/)

This is a web server that streams video, or any, file. Essentially, you can watch any video in my file system. Just like how you can watch videos from Youtube's database.

The way it works is that it listens to GET requests from your browser and uses HTTP Responses to send over buffered video byte streams.

### How to do it yourself:

First, we're going to be using nginx and Amazon AWS for our web server: https://medium.com/@nathan_149/install-nginx-on-amazon-ec2-in-5-minutes-get-a-web-server-up-and-running-3516fd06b76

Next, on your AWS machine, you can compile with:

    javac ContentServer.java
and start with:

    java ContentServer [port[
I use port 10007, so its `java ContentServer 10007`

Fill the `content`folder with any files to serve, and now anyone can access them via the Internet! :)
