# Simple-GUI-HTTP (Java)

A small Java desktop port of [SkyDev06/Simple-Gui-HTTP](https://github.com/SkyDev06/Simple-Gui-HTTP).

## Features
- Start and stop HTTP or HTTPS.
- Configure host and port (defaults: localhost:80).
- Enable HTTPS and browse for a PEM certificate and private key.
- Show client address, request method/path, and response status in the log.
- Respond with HTTP 200 OK and `Hello World`.

## Requirements
- JDK 21 or newer.

## Build
Windows: run `build.bat`.
Linux/macOS: run `./build.sh`.

Then start the app with:

```sh
java -jar target/simple-gui-http.jar
```

## HTTPS
Select a matching X.509 certificate PEM and unencrypted RSA private key PEM. The app supports `BEGIN RSA PRIVATE KEY` (PKCS#1) and `BEGIN PRIVATE KEY` (PKCS#8). It does not support encrypted keys or EC keys.

For local testing, create a self-signed certificate:

```sh
openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem -sha256 -days 365 -nodes -subj "/CN=localhost"
```

A self-signed certificate causes normal browser trust warnings. Never commit production private keys or certificates.

Ports below 1024 may require elevated permissions. Use 8080 for local HTTP testing. Bind to `0.0.0.0` only if you want to accept connections on all network interfaces. This is a minimal test server, not a production web server.
