#!/usr/bin/env python3
"""监控 GitHub Actions workflow run，实时输出各 job 进度。"""
import json, os, sys, time, urllib.request, urllib.error

TOKEN = os.environ["TOKEN"]
OWNER = os.environ["OWNER"]
REPO  = os.environ["REPO"]
RUN_ID = os.environ["RUN_ID"]
POLL = 15  # seconds

API = "https://api.github.com"
HEADERS = {
    "Authorization": f"token {TOKEN}",
    "Accept": "application/vnd.github+json",
    "User-Agent": "trae-build-monitor",
    "X-GitHub-Api-Version": "2022-11-28",
}

def gh_get(path):
    req = urllib.request.Request(f"{API}{path}", headers=HEADERS)
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.loads(r.read().decode())

def state_icon(status, conclusion):
    if status != "completed":
        if status == "in_progress": return "🔵"
        if status == "queued":      return "⚪"
        if status == "waiting":     return "⏳"
        if status == "requested":   return "📥"
        if status == "pending":     return "⏸️"
        return "❔"
    c = conclusion or "null"
    return {
        "success": "✅", "failure": "❌", "cancelled": "🚫",
        "skipped": "⏭️", "timed_out": "⏰", "action_required": "🔧",
        "neutral": "➖",
    }.get(c, "❔")

start = time.time()
last_jobs = {}
print(f"📡 监控开始: Run #{RUN_ID}")
print(f"🔗 直链: https://github.com/{OWNER}/{REPO}/actions/runs/{RUN_ID}")
print("-" * 78)

while True:
    try:
        run = gh_get(f"/repos/{OWNER}/{REPO}/actions/runs/{RUN_ID}")
        jobs_resp = gh_get(f"/repos/{OWNER}/{REPO}/actions/runs/{RUN_ID}/jobs?per_page=100")
    except (urllib.error.HTTPError, urllib.error.URLError) as e:
        print(f"[warn] API 请求失败: {e}，{POLL}s 后重试")
        time.sleep(POLL)
        continue

    status     = run["status"]
    conclusion = run.get("conclusion")
    dur = int(time.time() - start)
    mm, ss = divmod(dur, 60); hh, mm = divmod(mm, 60)
    ts = time.strftime("%H:%M:%S", time.localtime())
    print(f"\n[{ts}] 已用时 {hh:02d}:{mm:02d}:{ss:02d}  |  Run status={status}  conclusion={conclusion}")

    jobs = jobs_resp.get("jobs", [])
    jobs.sort(key=lambda j: j.get("started_at") or j.get("created_at") or "")
    for j in jobs:
        name = j["name"]
        js, jc = j["status"], j.get("conclusion")
        jdur_s = ""
        if j.get("started_at"):
            from datetime import datetime, timezone
            try:
                st = datetime.fromisoformat(j["started_at"].replace("Z","+00:00"))
                end = datetime.fromisoformat(j["completed_at"].replace("Z","+00:00")) if j.get("completed_at") else datetime.now(timezone.utc)
                d = int((end-st).total_seconds())
                jmm, jss = divmod(d, 60)
                jdur_s = f"  ({jmm}m{jss:02d}s)"
            except Exception:
                pass
        icon = state_icon(js, jc)
        steps = j.get("steps", [])
        done = sum(1 for s in steps if s["status"]=="completed")
        total = len(steps)
        step_str = f"  steps={done}/{total}" if total else ""
        print(f"   {icon} {name:32s} status={js:12s} conclusion={str(jc):10s}{step_str}{jdur_s}")

    if status == "completed":
        print("-" * 78)
        if conclusion == "success":
            print("🎉 构建全部成功 (conclusion=success)")
        else:
            print(f"⚠️  Run 结束但状态 = {conclusion}")
        try:
            art = gh_get(f"/repos/{OWNER}/{REPO}/actions/runs/{RUN_ID}/artifacts?per_page=50")
            arts = art.get("artifacts", [])
            print(f"\n📦 生成 Artifacts ({len(arts)} 个):")
            for a in arts:
                size_mb = a.get("size_in_bytes",0)/1024/1024
                exp  = a.get("expires_at","?")
                print(f"   - {a['name']:30s}  id={a['id']:<10} size={size_mb:.1f}MB  expires={exp[:10]}  expired={a.get('expired')}")
            with open("/tmp/artifacts.json","w") as f:
                json.dump(arts, f)
        except Exception as e:
            print(f"[warn] 获取 artifacts 失败: {e}")
        sys.exit(0 if conclusion == "success" else 2)

    time.sleep(POLL)
