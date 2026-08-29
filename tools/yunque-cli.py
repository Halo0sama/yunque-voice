#!/usr/bin/env python3
"""云雀语音助手 CLI 调试工具。

用法（需先 adb forward tcp:8766 tcp:8766 或设备本机访问）:
    python3 yunque-cli.py health
    python3 yunque-cli.py memories
    python3 yunque-cli.py conversations "小明"
    python3 yunque-cli.py speakers
    python3 yunque-cli.py relationships
    python3 yunque-cli.py interruptions
    python3 yunque-cli.py mcp list_memories
    python3 yunque-cli.py mcp add_memory --content "新的记忆"
"""
import sys
import urllib.request
import urllib.parse
import json

BASE = "http://127.0.0.1:8766"

def get(path):
    req = urllib.request.Request(BASE + path)
    with urllib.request.urlopen(req, timeout=5) as r:
        return r.read().decode()

def post(path, body):
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(),
                                  headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=5) as r:
        return r.read().decode()

def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return
    cmd = args[0]
    if cmd == "health":
        print(get("/api/health"))
    elif cmd == "memories":
        print(get("/api/memories"))
    elif cmd == "conversations":
        q = urllib.parse.quote(args[1]) if len(args) > 1 else ""
        print(get(f"/api/conversations?q={q}"))
    elif cmd == "speakers":
        print(get("/api/speakers"))
    elif cmd == "relationships":
        print(get("/api/relationships"))
    elif cmd == "interruptions":
        print(get("/api/interruptions"))
    elif cmd == "mcp" and len(args) >= 2:
        tool = args[1]
        content = ""
        if "--content" in args:
            i = args.index("--content")
            if i + 1 < len(args):
                content = args[i + 1]
        body = {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "tools/call",
            "params": {"name": tool, "arguments": {"content": content}}
        }
        print(post("/mcp", body))
    else:
        print("unknown command")
        print(__doc__)

if __name__ == "__main__":
    main()
