#!/usr/bin/env python3
import http.server
import socket
import socketserver
import os
import sys

PORT = 8080

class ObtainiumCompatibleHandler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {
        **http.server.SimpleHTTPRequestHandler.extensions_map,
        ".apk": "application/vnd.android.package-archive",
        ".html": "text/html; charset=utf-8",
        ".json": "application/json",
    }

    def end_headers(self):
        # Enable CORS and disable caching so Obtainium always checks the latest build
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
        self.send_header("Cache-Control", "no-cache, no-store, must-revalidate")
        super().end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self.end_headers()

def get_local_ip_addresses():
    addresses = []
    try:
        host_name = socket.gethostname()
        for ip in socket.gethostbyname_ex(host_name)[2]:
            if not ip.startswith("127.") and not ip.startswith("169.254."):
                addresses.append(ip)
    except Exception:
        pass

    if not addresses:
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
                s.connect(("8.8.8.8", 80))
                addresses.append(s.getsockname()[0])
        except Exception:
            addresses.append("127.0.0.1")
    return list(dict.fromkeys(addresses))

def main():
    os.chdir(os.path.dirname(os.path.abspath(__file__)))

    socketserver.TCPServer.allow_reuse_address = True
    try:
        server = socketserver.TCPServer(("0.0.0.0", PORT), ObtainiumCompatibleHandler)
    except OSError:
        # Fallback to alternate port if 8080 is busy
        port = 8000
        server = socketserver.TCPServer(("0.0.0.0", port), ObtainiumCompatibleHandler)
    else:
        port = PORT

    local_ips = get_local_ip_addresses()

    print("\n" + "=" * 60)
    print("  WoW Auto Fishing - Local Obtainium / Web Server")
    print("=" * 60)
    print("\nOpen on your phone (same Wi-Fi network):\n")
    for ip in local_ips:
        print(f"  * Web Browser / HTML Source: http://{ip}:{port}/")
        print(f"  * Direct APK URL in Obtainium: http://{ip}:{port}/wow-fishing-bot.apk\n")
    print("=" * 60)
    print("Serving files. Press Ctrl+C in terminal to stop server.\n")
    sys.stdout.flush()

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServer stopped.")

if __name__ == "__main__":
    main()
