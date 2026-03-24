"""
OpenClaw 监控面板 v4 - Python 版
pip install requests 后运行: python openclaw_monitor.py
"""

import requests
import json
import datetime


class OpenClawMonitor:
    def __init__(self, gateway_url: str, token: str):
        self.gateway_url = gateway_url.rstrip('/')
        self.token = token
        self.headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}

    def _invoke(self, tool: str, args: dict = None) -> dict:
        resp = requests.post(
            f"{self.gateway_url}/tools/invoke",
            headers=self.headers,
            json={"tool": tool, "action": "json", "args": args or {}}
        )
        return resp.json()

    def get_status(self) -> dict:
        return self._invoke("session_status", {"sessionKey": "agent:main:main"})

    def list_sessions(self) -> list:
        result = self._invoke("sessions_list", {})
        if result.get("ok"):
            return result.get("result", {}).get("details", {}).get("sessions", [])
        return []

    def get_subagents(self) -> dict:
        result = self._invoke("subagents", {"action": "list"})
        if result.get("ok"):
            return result.get("result", {}).get("details", {})
        return {}

    def get_memory(self, query: str, max_results: int = 3) -> dict:
        result = self._invoke("memory_search", {"query": query, "maxResults": max_results})
        if result.get("ok"):
            return result.get("result", {}).get("details", {})
        return {}

    def get_history(self, limit: int = 5) -> list:
        result = self._invoke("sessions_history", {"sessionKey": "agent:main:main", "limit": limit})
        if result.get("ok"):
            try:
                content = result.get("result", {}).get("content", [])
                if content:
                    parsed = json.loads(content[0]["text"])
                    return parsed.get("messages", [])
            except (json.JSONDecodeError, KeyError, IndexError):
                pass
        return []


def fmt_ts(ts_ms: int) -> str:
    return datetime.datetime.fromtimestamp(ts_ms / 1000).strftime("%m-%d %H:%M")

def extract_text(content) -> str:
    if isinstance(content, list):
        texts = []
        for item in content:
            if isinstance(item, dict):
                if item.get("type") == "text":
                    texts.append(item.get("text", ""))
                elif item.get("type") == "thinking":
                    texts.append(f"[思考] {item.get('thinking', '')[:80]}...")
        return " ".join(texts)[:100]
    return str(content)[:100]


def main():
    M = OpenClawMonitor(
        gateway_url="http://127.0.0.1:18789",
        token="13991a7955c0f59999eb7c689ee1234c0581586e8faf09b4"
    )

    print()
    print("=" * 60)
    print("  🦞 OpenClaw 监控面板 v4")
    print("=" * 60)
    print()

    # ── 1. Agent 状态 ────────────────────────────────
    print("📊 [Agent 状态]")
    s = M.get_status()
    if s.get("ok"):
        text = s["result"]["details"].get("statusText", "")
        for line in text.split("\n"):
            line = line.strip()
            if any(k in line for k in ["OpenClaw", "Time", "Model", "Tokens", "Context", "Usage", "Session", "Runtime", "Queue", "Think"]):
                print(f"  {line}")
    else:
        print(f"  ❌ {s.get('error', {}).get('message')}")
    print()

    # ── 2. Session 列表 ──────────────────────────────
    print("📋 [Session 列表]")
    sessions = M.list_sessions()
    print(f"  共 {len(sessions)} 个 session\n")
    for s in sessions:
        updated = fmt_ts(s.get("updatedAt", 0))
        tokens = s.get("totalTokens", 0)
        model = s.get("model", "?")
        channel = s.get("lastChannel", "?")
        key = s.get("key", "?")
        print(f"  🔹 {key}")
        print(f"     模型: {model} | Channel: {channel} | Tokens: {tokens:,}")
        print(f"     更新: {updated}\n")
    print()

    # ── 3. 子 Agent ─────────────────────────────────
    print("🤖 [子 Agent]")
    sa = M.get_subagents()
    active = sa.get("active", [])
    recent = sa.get("recent", [])
    if active:
        print(f"  活跃: {len(active)} 个")
        for a in active:
            print(f"    - {a}")
    else:
        print("  活跃: 无")
    if recent:
        print(f"  最近 (30min): {len(recent)} 个")
        for r in recent[:3]:
            print(f"    - {r}")
    print()

    # ── 4. 记忆搜索 ─────────────────────────────────
    print("🧠 [记忆搜索 - bilibili/b站]")
    mem = M.get_memory("bilibili b站 上传")
    results = mem.get("results", [])
    if results:
        for r in results[:3]:
            print(f"  • {str(r)[:100]}...")
    else:
        print("  无相关记忆")
    print()

    # ── 5. 技能列表 ─────────────────────────────────
    print("🛠  [已安装技能]")
    skills = [
        ("bilibili-upload",  "B站视频上传 - CDP上传到草稿箱"),
        ("bilibili-login",   "B站登录 - CDP获取登录Cookie"),
        ("feishu-doc",      "飞书文档 - 读写飞书云文档"),
        ("feishu-wiki",     "飞书百科 - 飞书知识库"),
        ("github",          "GitHub - gh CLI交互"),
        ("summarize",       "内容摘要 - summarize CLI工具"),
        ("weather",         "天气查询 - 无需API key"),
        ("agent-browser",   "浏览器自动化 - Agent Browser"),
        ("find-skills",     "技能发现 - ClawhHub搜索"),
        ("self-improving",  "自我改进 - 记录错误和修正"),
    ]
    for name, desc in skills:
        print(f"  • {name:<18} {desc}")
    print()

    # ── 6. 人格属性 ─────────────────────────────────
    print("🐺 [人格 / Identity]")
    print("  名字: Jack | 定位: AI助手 | Emoji: 🐺")
    print("  主人: 钱皓 (主人)")
    print("  性格: 实用、有点个性、乐意帮忙")
    print("  原则: 先做再说，不要废话；有问题先自己解决再问")
    print()

    # ── 7. 最近对话 ─────────────────────────────────
    print("💬 [最近对话 - 最近 3 条]")
    msgs = M.get_history(3)
    if msgs:
        for msg in reversed(msgs):
            role = msg.get("role", "?")
            content = extract_text(msg.get("content", ""))
            ts = fmt_ts(msg.get("timestamp", 0))
            prefix = "  👤" if role == "user" else "  🤖"
            print(f"{prefix} [{ts}] {content[:70]}")
    else:
        print("  无历史记录（API参数可能需要调整）")
    print()

    print("=" * 60)
    print(f"  查询时间: {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("  按 Ctrl+C 退出")
    print("=" * 60)


if __name__ == "__main__":
    main()
