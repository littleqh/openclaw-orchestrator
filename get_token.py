#!/usr/bin/env python3
"""
从 CC Switch SQLite 数据库读取 MiniMax API 凭据。
用法: python get_token.py

读取 Windows 上 CC Switch 的数据库 (~/.cc-switch/cc-switch.db)，
输出可被 source 执行的 shell 变量赋值。
"""
import sqlite3, json, os, sys

CC_SWITCH_DB = "/mnt/c/Users/littl/.cc-switch/cc-switch.db"

def get_token():
    if not os.path.exists(CC_SWITCH_DB):
        print(f"# Error: CC Switch DB not found at {CC_SWITCH_DB}", file=sys.stderr)
        sys.exit(1)

    conn = sqlite3.connect(CC_SWITCH_DB)
    cursor = conn.cursor()
    cursor.execute("SELECT settings_config FROM providers WHERE name='MiniMax'")
    row = cursor.fetchone()
    conn.close()

    if not row:
        print("# Error: MiniMax provider not found in CC Switch DB", file=sys.stderr)
        sys.exit(1)

    config = json.loads(row[0])
    env = config.get("env", {})
    token = env.get("ANTHROPIC_AUTH_TOKEN", "")
    base_url = env.get("ANTHROPIC_BASE_URL", "https://api.minimaxi.com/anthropic")
    model = env.get("ANTHROPIC_MODEL", "MiniMax-M2.7")

    if not token:
        print("# Error: ANTHROPIC_AUTH_TOKEN not found", file=sys.stderr)
        sys.exit(1)

    print(f'export ANTHROPIC_AUTH_TOKEN="{token}"')
    print(f'export ANTHROPIC_BASE_URL="{base_url}"')
    print(f'export ANTHROPIC_MODEL="{model}"')
    print(f'export ANTHROPIC_DEFAULT_OPUS_MODEL="{model}"')
    print(f'export ANTHROPIC_DEFAULT_HAIKU_MODEL="{model}"')
    print(f'export ANTHROPIC_DEFAULT_SONNET_MODEL="{model}"')

if __name__ == "__main__":
    get_token()
