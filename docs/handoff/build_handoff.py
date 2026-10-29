#!/usr/bin/env python3
"""Create the offline HTML conversation handoff using only Python's standard library.

No app source is modified, no device/build command is run, and no file is sent.
Only explicit conversation assets and selected project files are embedded.
"""
from __future__ import annotations

import base64
import hashlib
import html
import json
import mimetypes
from pathlib import Path
import re
import subprocess
from datetime import datetime
from zoneinfo import ZoneInfo

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = HERE / "AETHER_HMI_Conversation_Handoff.html"
ESC = html.escape


def inline(text: str) -> str:
    # Escape first; inline code is protected from emphasis substitutions.
    protected = []
    def code(match):
        protected.append("<code>" + ESC(match.group(1)) + "</code>")
        return f"\x00CODE{len(protected)-1}\x00"
    text = re.sub(r"`([^`]+)`", code, text)
    text = ESC(text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    # Only public HTTPS/HTTP links become live external links; local references stay text.
    text = re.sub(r"\[([^\]]+)\]\((https?://[^\s)]+)\)", r'<a rel="noreferrer" href="\2">\1</a>', text)
    for i, value in enumerate(protected):
        text = text.replace(f"\x00CODE{i}\x00", value)
    return text


def markdown(text: str) -> str:
    result, paragraph, listing, code_lines = [], [], None, None
    def flush():
        if paragraph:
            result.append("<p>" + inline(" ".join(paragraph)) + "</p>")
            paragraph.clear()
    def close_list():
        nonlocal listing
        if listing:
            result.append(f"</{listing}>")
            listing = None
    for line in text.splitlines():
        if line.startswith("```"):
            flush(); close_list()
            if code_lines is None:
                code_lines = []
            else:
                result.append("<pre><code>" + ESC("\n".join(code_lines)) + "</code></pre>")
                code_lines = None
            continue
        if code_lines is not None:
            code_lines.append(line)
            continue
        heading = re.match(r"^(#{1,6})\s+(.+)$", line)
        bullet = re.match(r"^\s*(-|\d+\.)\s+(.+)$", line)
        if heading:
            flush(); close_list()
            level = min(6, len(heading[1])+1)
            result.append(f"<h{level}>" + inline(heading[2]) + f"</h{level}>")
        elif bullet:
            flush()
            kind = "ul" if bullet[1] == "-" else "ol"
            if listing != kind:
                close_list(); result.append(f"<{kind}>"); listing = kind
            result.append("<li>" + inline(bullet[2]) + "</li>")
        elif not line.strip():
            flush(); close_list()
        elif line.startswith(">"):
            flush(); close_list()
            result.append("<blockquote>" + inline(line.lstrip("> ")) + "</blockquote>")
        elif re.fullmatch(r"\s*[-*_]{3,}\s*", line):
            flush(); close_list(); result.append("<hr>")
        else:
            close_list(); paragraph.append(line.strip())
    flush(); close_list()
    if code_lines is not None:
        result.append("<pre>" + ESC("\n".join(code_lines)) + "</pre>")
    return "\n".join(result)


def section(anchor, title, body, note=""):
    return (f'<section id="{anchor}" class="section"><div class="section-heading">'
            f'<h2>{ESC(title)}</h2><a class="anchor" href="#{anchor}">#</a></div>'
            + (f'<p class="muted">{ESC(note)}</p>' if note else "") + body + "</section>")


conversation = json.loads((HERE / "conversation-public.json").read_text())
supplements = json.loads((HERE / "additional-context-requests.json").read_text())
context = (HERE / "AETHER_CONTEXT_HANDOFF.md").read_text()
report = json.loads((ROOT / "docs/vehicle-build-verification.json").read_text())

known_assets = [
    ("Home — approved visual design", "/home/robin/Pictures/AETHER/Design/home.png"),
    ("Climate — approved visual design", "/home/robin/Pictures/AETHER/Design/climate_screen.png"),
    ("Climate — original cabin with painted airflow", "/home/robin/Pictures/AETHER/Assets/ChatGPT Image 16 Oct 2026, 00_33_.png"),
    ("Vehicle — approved visual design", "/home/robin/Pictures/AETHER/Design/vehicle.png"),
    ("Vehicle — model and scene specification screenshot", "/home/robin/Pictures/Screenshots/Screenshot from 2026-10-25 14-02-18.png"),
    ("Vehicle — supplied scenic source (original painted ring)", "/home/robin/Pictures/AETHER/Assets/futuristic_neon_vehicle_control_dashboard.png"),
    ("Vehicle — size/grounding example car.png", "/home/robin/Pictures/AETHER/Assets/car.png"),
    ("Earlier output — old Jeep, before GenericSUV", "/home/robin/Pictures/AETHER/Pasted image.png"),
    ("Overview — supplied transparent top-view pic5.png", "/home/robin/Pictures/AETHER/Assets/pic5.png"),
    ("Latest requirement — desired wet ground and luminous ring", "/home/robin/Pictures/Screenshots/Screenshot from 2026-10-26 23-26-35.png"),
    ("Latest reported output — unacceptable ground and button strip", "/home/robin/Pictures/Screenshots/Screenshot from 2026-10-26 23-27-12.png"),
    ("Last verified v0.5.1 — physical-phone Overview (historical baseline)", str(ROOT / "docs/verification/vehicle-v0.5.1/final-overview.png")),
]
image_ids = {path: f"asset-{i+1}" for i, (_, path) in enumerate(known_assets)}
missing = set()
for turn in conversation["turns"]:
    for message in turn["messages"]:
        for content in message.get("content", []):
            if content.get("type") == "localImage" and not Path(content["path"]).is_file():
                missing.add(content["path"])
for item in supplements:
    for path in item["attachments"]:
        if not Path(path).is_file():
            missing.add(path)


def attachments(paths):
    parts = []
    for path in paths:
        if path in image_ids:
            parts.append(f'<a class="asset-link" href="#{image_ids[path]}">{ESC(Path(path).name)}</a>')
        else:
            parts.append(f'<span class="missing-ref">Reference path: {ESC(path)} (not embedded)</span>')
    return '<div class="attachments">' + "".join(parts) + "</div>" if parts else ""


css = """
:root{color-scheme:dark;--bg:#07101c;--panel:#101e30;--line:#24425c;--text:#e9f1f9;--muted:#a8bdd0;--cyan:#5ce4ee;--amber:#ffd68a}
*{box-sizing:border-box}html{scroll-behavior:smooth;scroll-padding-top:24px}body{margin:0;background:var(--bg);color:var(--text);font:16px/1.67 system-ui,-apple-system,Segoe UI,sans-serif}a{color:var(--cyan);text-underline-offset:3px}a:hover{color:white}header{padding:54px max(25px,calc((100vw - 1400px)/2)) 35px;background:linear-gradient(125deg,#142d46,#081421);border-bottom:1px solid var(--line)}.eyebrow{letter-spacing:.15em;text-transform:uppercase;font-size:12px;font-weight:700;color:var(--cyan)}h1{font-size:clamp(30px,4vw,50px);line-height:1.13;letter-spacing:-.03em;max-width:1000px;margin:12px 0 17px}.lede{max-width:1000px;font-size:18px;color:#c4d5e6}.pills{display:flex;flex-wrap:wrap;gap:9px;margin:22px 0}.pill{font-size:12px;border:1px solid #3b657e;border-radius:30px;padding:5px 12px;background:#112539}.pill.warn{color:var(--amber);border-color:#806232}.layout{max-width:1450px;margin:auto;display:grid;grid-template-columns:225px minmax(0,1fr);gap:35px;padding:30px 25px 70px}nav{align-self:start;position:sticky;top:24px;font-size:14px}nav a{display:block;padding:7px 0;text-decoration:none;color:#bcd2e4}nav a:hover{color:var(--cyan)}nav h2{font-size:12px;text-transform:uppercase;letter-spacing:.14em;color:#7898b3}.section{margin:0 0 30px;background:var(--panel);border:1px solid var(--line);border-radius:16px;padding:28px 30px;overflow:hidden}.section-heading{display:flex;align-items:center;justify-content:space-between;gap:15px}.section-heading>h2{font-size:24px;margin:0 0 14px;line-height:1.3}.anchor{font-size:14px;opacity:.55;text-decoration:none}h3{font-size:21px;margin:32px 0 12px;line-height:1.35}h4{font-size:18px;margin:25px 0 10px}p{margin:12px 0}li{margin:8px 0}ul,ol{padding-left:23px}.muted,small{color:var(--muted)}.banner{padding:20px 23px;border-left:4px solid var(--amber);background:#30291b;border-radius:9px;margin:22px 0}.banner strong{color:#ffe2a9}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:15px}.card{background:#0a1726;border:1px solid var(--line);padding:18px;border-radius:10px}.card h3{margin:0 0 8px;font-size:17px}pre{white-space:pre-wrap;overflow-wrap:anywhere;background:#07111e;border:1px solid #22374e;border-radius:9px;padding:17px;font:12.5px/1.6 ui-monospace,SFMono-Regular,Consolas,monospace;tab-size:4}code{font: .88em ui-monospace,SFMono-Regular,Consolas,monospace;color:#b3f2e9;background:#081725;border-radius:4px;padding:.12em .3em;overflow-wrap:anywhere}pre code{padding:0;background:none;color:#c3d5e5}blockquote{border-left:3px solid #426c86;margin:16px 0;padding:1px 18px;color:#bbd0e2}details{border:1px solid var(--line);border-radius:10px;margin:13px 0;background:#0b1827}summary{padding:15px 18px;cursor:pointer;font-weight:650;line-height:1.5;overflow-wrap:anywhere}details[open]>summary{border-bottom:1px solid var(--line)}.detail-body{padding:5px 19px 18px}figure{margin:15px 0 28px}figure img{display:block;max-width:100%;height:auto;margin:15px auto;max-height:850px;object-fit:contain;background:repeating-conic-gradient(#101b2b 0 25%,#15273c 0 50%) 0/20px 20px;border-radius:7px}figcaption{font-size:12px;color:var(--muted);overflow-wrap:anywhere}.asset-path{display:block;color:#8bb2cf;margin-top:6px;font-family:monospace;font-size:12px}.attachments{display:flex;flex-wrap:wrap;gap:8px;margin:12px 0}.asset-link,.missing-ref{font-size:12px;border:1px solid #31516b;border-radius:7px;padding:5px 8px;overflow-wrap:anywhere}.missing-ref{color:#d3b28a}.message{padding:18px;border-left:3px solid #31536f;margin:17px 0;background:#0d2031;border-radius:0 9px 9px 0}.message.user{border-left-color:var(--cyan);background:#0b2934}.message .role{font-size:11px;text-transform:uppercase;letter-spacing:.13em;color:#8caec8;font-weight:700}.message.user .role{color:var(--cyan)}.message h3{font-size:18px;margin-top:18px}.message h2{font-size:19px}.turn-meta{font-size:12px;font-weight:400;color:var(--muted);display:block;margin-top:5px}.toolbar{display:flex;gap:9px;flex-wrap:wrap;margin:20px 0}button{font:600 13px system-ui;color:#d9f6fa;background:#17374a;border:1px solid #3c687f;border-radius:8px;padding:10px 13px;cursor:pointer}button:hover{background:#20526a}button:focus-visible,a:focus-visible,summary:focus-visible{outline:3px solid #71e3e7;outline-offset:3px}table{width:100%;border-collapse:collapse;font-size:13px;margin:17px 0;table-layout:fixed}td,th{border-bottom:1px solid var(--line);padding:10px;text-align:left;vertical-align:top;overflow-wrap:anywhere}th{color:#97bdd2}td:first-child{width:28%}.file-list{font:12px/1.9 ui-monospace,monospace;overflow-wrap:anywhere}.foot{font-size:13px;color:#a0b6ca;padding:0 10px}.scope{font-size:13px;border-top:1px solid #3b5268;padding-top:15px;margin-top:25px}.noscript{color:#ffd68a}.stat{font-size:26px;font-weight:750;line-height:1.2;color:var(--cyan);margin-bottom:9px}.compact h3{margin-top:18px}#copy-status{font-size:13px;color:var(--muted);align-self:center}.hide-print{display:block}
@media(max-width:950px){.layout{grid-template-columns:1fr;gap:15px;padding:20px 15px}nav{position:static;display:flex;flex-wrap:wrap;gap:7px 16px}nav h2{width:100%;margin-bottom:1px}nav a{font-size:12px}header{padding:32px 22px}.section{padding:23px 20px}.grid{grid-template-columns:1fr}}
@media print{:root{color-scheme:light}body{background:white;color:#182535;font:10.5pt/1.5 Georgia,serif}header{background:none;color:#142437;padding:15px 0}.eyebrow,.lede,.muted,small,.turn-meta{color:#45586b}.layout{display:block;padding:0;max-width:none}nav,.toolbar,.anchor,.hide-print{display:none!important}.section,details,.message,.card{background:white;border-color:#bbc8d3;break-inside:auto;padding:12px}.section{margin-bottom:20px}h1{font-size:28pt}h2,h3,h4{break-after:avoid}pre{background:#f4f6f8;color:#10263b;border-color:#bbc8d3;font-size:8pt}code,pre code{color:#103c55;background:#eef4f6}figure img{max-height:650px}a{color:#15556e}.banner{background:#fff8e5;color:#43331e}.banner strong{color:#543c16}.pill,.message .role{color:#234c65}summary{color:#172d42}.foot{color:#45586b}details:not([open])>*:not(summary){display:block!important}}
"""

sections = []
summary = """
<div class="banner"><strong>Outstanding work — do not confuse source with the last APK.</strong><br>The last verified binary is v0.5.1/code 8. The user's latest seven fixes are unfinished. Current source has partial edits and references a missing <code>vehicle_ground.png</code>; it has not been validated after those edits.</div>
<div class="grid"><div class="card"><div class="stat">5 tabs · 1 APK</div><p>Home, Vehicle, Climate, Media, Settings. Kotlin / Compose, shared mock repository, same-process Media3, Filament.</p></div><div class="card"><div class="stat">Physical phone only</div><p>Samsung SM_G990E, serial <code>RZCT902Y60B</code>. Do not use the laptop emulator. Recheck connection before any future testing.</p></div><div class="card"><div class="stat">51 tests passed</div><p>Historical v0.5.1 verification: build passed; zero lint errors, nine warnings. These results do not cover the latest incomplete fixes.</p></div><div class="card"><div class="stat">Resume the 7 fixes</div><p>Wet ground, visible shadows, correct lamps/blink, Park hazards vs Reverse, horizontal-only orbit, remove camera buttons, and live Home attention.</p></div></div>
<p>This standalone file contains a context guide, public conversation, reference images and selected current-source/document snapshots. It works offline. No model, APK or video binary is embedded; transfer the checkout and those files separately to continue implementation.</p>
<p class="scope">Reference images and attached document text are project inputs. The human requests and their chronological corrections define the task. Earlier assistant completion claims are historical reports, not proof that later defects are fixed.</p>
"""
sections.append(section("start", "Start here", summary))
sections.append(section("context", "Complete continuation context", markdown(context)))

supp_body = '<p>These user requests are visible in the conversation context but absent from the retrieved message lists. They are included explicitly rather than silently omitted or attributed to fabricated history timestamps.</p>'
for i, item in enumerate(supplements, 1):
    supp_body += f'<details open><summary>{ESC(item["title"])}<span class="turn-meta">{ESC(item["position"])}</span></summary><div class="detail-body">'
    supp_body += '<article class="message user"><div class="role">User — context-visible request</div>' + markdown(item["text"]) + '</article>'
    if item.get("reference_context"):
        supp_body += '<p class="muted">' + ESC(item["reference_context"]) + '</p>'
    supp_body += attachments(item["attachments"]) + '</div></details>'
sections.append(section("additional", "Additional user requests preserved from context", supp_body))

transcript = '<p>Chronological public messages returned by the app history API. User text and assistant messages are preserved; phase labels distinguish progress updates from final replies. Empty history turns are not expanded into invented conversations. The current export turn includes only its initial request and acknowledgment captured at retrieval.</p>'
public_count = 0
for n, turn in enumerate(conversation["turns"], 1):
    messages = turn["messages"]
    if not messages:
        continue
    timestamp = datetime.fromtimestamp(turn["startedAt"], ZoneInfo("Asia/Kolkata")).strftime("%d %b %Y · %H:%M IST") if turn.get("startedAt") else "Timestamp unavailable"
    first = next((c["text"] for m in messages if m["role"] == "user" for c in m.get("content", []) if c.get("type") == "text"), "Continuation of implementation")
    heading = re.sub(r"\s+", " ", first).strip()
    if heading.startswith("# Files mentioned"):
        heading = first.split("## My request:", 1)[-1].strip()
        heading = re.sub(r"\s+", " ", heading)
    heading = heading[:125] + ("…" if len(heading) > 125 else "")
    transcript += f'<details class="chat-turn"><summary>{ESC(heading)}<span class="turn-meta">{ESC(timestamp)} · turn {n} · {ESC(turn["status"])} · {len(messages)} public messages</span></summary><div class="detail-body">'
    for message in messages:
        public_count += 1
        role = message["role"]
        if role == "user":
            text = "\n\n".join(c["text"] for c in message.get("content", []) if c.get("type") == "text")
            paths = [c["path"] for c in message.get("content", []) if c.get("type") == "localImage"]
            label = "User"
        else:
            text = message["text"]
            paths = []
            label = "Assistant · " + ("final reply" if message.get("phase") in ("final", "final_answer") else "progress update")
        transcript += f'<article class="message {role}"><div class="role">{ESC(label)}</div>{markdown(text)}{attachments(paths)}</article>'
    transcript += '</div></details>'
sections.append(section("transcript", "Available public conversation", transcript, f"{public_count} public messages, plus {len(supplements)} supplemental context-visible user requests. No internal reasoning or tool-instruction transcript is included."))

gallery = '<p>Original PNG bytes are embedded below for offline viewing. Open an image to inspect it; designs, examples, current complaints and historical device evidence are labeled separately. Original local paths are provenance, not remote download links.</p>'
asset_manifest = []
for title, raw_path in known_assets:
    path = Path(raw_path)
    anchor = image_ids[raw_path]
    if path.is_file():
        raw = path.read_bytes()
        uri = 'data:' + (mimetypes.guess_type(path.name)[0] or 'image/png') + ';base64,' + base64.b64encode(raw).decode()
        gallery += f'<figure id="{anchor}"><details><summary>{ESC(title)}<span class="turn-meta">{len(raw)/1048576:.2f} MiB · embedded original</span></summary><div class="detail-body"><img loading="lazy" src="{uri}" alt="{ESC(title, quote=True)}"><figcaption>{ESC(title)}<span class="asset-path">{ESC(raw_path)}</span></figcaption></div></details></figure>'
        asset_manifest.append({'title': title, 'path': raw_path, 'embedded': True, 'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()})
    else:
        gallery += f'<p class="missing-ref">Unavailable: {ESC(raw_path)}</p>'
        asset_manifest.append({'title':title,'path':raw_path,'embedded':False})
gallery += '<h3>Clipboard attachments no longer available on disk</h3><p>Their filenames and relevant design requirements remain in the transcript/context. They are not replaced with fabricated images.</p><ul>'
gallery += ''.join('<li><code>' + ESC(path) + '</code></li>' for path in sorted(missing)) + '</ul>'
sections.append(section("references", "Embedded visual references", gallery))

sources = [
    "app/build.gradle.kts", "gradle.properties", "app/src/main/AndroidManifest.xml", "app/src/main/assets/demo_vehicle.json",
    "app/src/main/java/com/aether/hmi/domain/DashboardModels.kt",
    "app/src/main/java/com/aether/hmi/domain/VehicleControls.kt",
    "app/src/main/java/com/aether/hmi/domain/VehicleAttention.kt",
    "app/src/main/java/com/aether/hmi/ui/components/VehicleStatus.kt",
    "app/src/main/java/com/aether/hmi/data/MockVehicleRepository.kt",
    "app/src/main/java/com/aether/hmi/feature/home/DashboardViewModel.kt",
    "app/src/main/java/com/aether/hmi/feature/home/HomeScreen.kt",
    "app/src/main/java/com/aether/hmi/feature/vehicle/VehicleScreen.kt",
    "app/src/main/java/com/aether/hmi/feature/vehicle/VehicleComponents.kt",
    "app/src/main/java/com/aether/hmi/feature/vehicle/VehicleModelStage.kt",
    "app/src/main/java/com/aether/hmi/feature/vehicle/VehicleSceneViewModel.kt",
    "app/src/main/java/com/aether/hmi/feature/vehicle/VehicleSceneMath.kt",
    "app/src/main/java/com/aether/hmi/feature/vehicle/VehicleLampPolicy.kt",
    "app/src/main/java/com/aether/hmi/feature/vehicle/renderer/FilamentVehicleRenderer.kt",
    "app/src/main/java/com/aether/hmi/feature/vehicle/renderer/VehicleScenery.kt",
    "app/src/main/java/com/aether/hmi/feature/vehicle/renderer/VehicleEnvironment.kt",
    "app/src/main/java/com/aether/hmi/ui/shell/AetherApp.kt",
    "app/src/main/res/values/vehicle_strings.xml", "app/src/main/res/values/vehicle_status_strings.xml",
    "app/src/test/java/com/aether/hmi/domain/VehicleControlsBehaviorTest.kt",
    "app/src/test/java/com/aether/hmi/feature/vehicle/VehicleLampPolicyTest.kt",
    "app/src/test/java/com/aether/hmi/feature/vehicle/VehicleSceneMathTest.kt",
    "tools/prepare_generic_suv.py",
]
snapshots = '<div class="banner"><strong>Current, partially edited source snapshot.</strong> These files are included for context and diagnosis. This is not the source state that produced the last verified APK. The missing ground resource and unfinished signal/pitch/UI integration must be resolved before claiming a successful new build.</div>'
source_manifest = []
for relative in sources:
    path = ROOT / relative
    if not path.is_file():
        continue
    raw = path.read_bytes(); text = raw.decode('utf-8')
    source_manifest.append({'path':relative,'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()})
    snapshots += f'<details><summary>{ESC(relative)}<span class="turn-meta">{len(text.splitlines())} lines · current source, unverified after latest edits</span></summary><div class="detail-body"><pre><code>{ESC(text)}</code></pre></div></details>'
sections.append(section("source", "Selected current-source snapshots", snapshots))

docs = ["docs/AETHER_SYSTEM_PROMPT.md", "docs/HOME_VALIDATION.md", "docs/CLIMATE_IMPLEMENTATION.md", "docs/VEHICLE_IMPLEMENTATION.md", "docs/vehicle-build-verification.json", "docs/generic-suv-model-manifest.json", "docs/generic-suv-source-audit.json", "README.md"]
doc_body = '<p>Document snapshots are historical evidence and implementation context. Their dates/version labels matter. In particular, v0.5.1 verification predates the latest seven requested fixes.</p>'
for relative in docs:
    path = ROOT / relative
    if not path.is_file():
        continue
    text = path.read_text()
    body = markdown(text) if path.suffix == '.md' else '<pre><code>' + ESC(text) + '</code></pre>'
    doc_body += f'<details><summary>{ESC(relative)}<span class="turn-meta">Snapshot at export; preserve version/provenance distinctions</span></summary><div class="detail-body compact">{body}</div></details>'
sections.append(section("documents", "Project documents and verification evidence", doc_body))

git_status = subprocess.run(['git','status','--short'],cwd=ROOT,text=True,capture_output=True,check=True).stdout
audit = {
    'exportDate':'2026-10-27','timezone':'Asia/Kolkata','threadTitle':conversation['title'],'threadId':conversation['id'],
    'publicMessageCount':public_count,'supplementalUserRequests':len(supplements),'embeddedImageCount':sum(x['embedded'] for x in asset_manifest),
    'missingClipboardReferences':sorted(missing),'sources':source_manifest,'assets':asset_manifest,
    'knownCurrentBlocker':'VehicleScenery.kt references missing R.drawable.vehicle_ground; latest app fixes unbuilt/unverified',
    'lastVerifiedVersion':report['version'],'lastVerifiedTests':report['testTotal'],
    'scope':'public conversation and technical handoff; no hidden reasoning/system/developer instructions; no full repo/model/APK/video binaries',
}
(HERE / 'handoff-manifest.json').write_text(json.dumps(audit,indent=2)+'\n')
manifest_body = '<p>Export checksums identify the text/image snapshots. The original checkout contains substantial modified and untracked work; preserve it when transferring the project.</p>'
manifest_body += '<h3>Working-tree status at export</h3><pre>' + ESC(git_status) + '</pre>'
manifest_body += '<details><summary>Export inventory and checksums</summary><div class="detail-body"><pre>' + ESC(json.dumps(audit,indent=2)) + '</pre></div></details>'
manifest_body += '<p>Not embedded: original GenericSUV/Jeep GLBs, APK, MP4 recording, complete repository and SDK caches. Their locations and provenance are recorded above. Share them separately when implementation work is transferred.</p>'
sections.append(section("manifest", "Export inventory and transfer notes", manifest_body))

payload = json.dumps({'contextMarkdown':context,'conversation':conversation,'additionalRequests':supplements,'manifest':audit},ensure_ascii=False).replace('<','\\u003c').replace('>','\\u003e').replace('&','\\u0026')
js = """
const data=JSON.parse(document.getElementById('handoff-data').textContent);
document.getElementById('expand').onclick=()=>document.querySelectorAll('details').forEach(d=>d.open=true);
document.getElementById('collapse').onclick=()=>document.querySelectorAll('details').forEach(d=>d.open=false);
document.getElementById('print').onclick=()=>window.print();
document.getElementById('copy').onclick=async()=>{let s=document.getElementById('copy-status');try{await navigator.clipboard.writeText(data.contextMarkdown);s.textContent='Continuation context copied.';}catch(e){const a=document.createElement('textarea');a.value=data.contextMarkdown;document.body.append(a);a.select();const ok=document.execCommand('copy');a.remove();s.textContent=ok?'Continuation context copied.':'Use Download context instead.';}};
document.getElementById('download').onclick=()=>{const a=document.createElement('a');a.href=URL.createObjectURL(new Blob([data.contextMarkdown],{type:'text/markdown;charset=utf-8'}));a.download='AETHER_CONTEXT_HANDOFF.md';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000);};
window.addEventListener('beforeprint',()=>document.querySelectorAll('details').forEach(d=>d.open=true));
document.addEventListener('click',e=>{const a=e.target.closest('a[href^="#asset-"]');if(!a)return;const target=document.querySelector(a.getAttribute('href'));if(target){target.closest('details')?.setAttribute('open','');target.querySelector('details')?.setAttribute('open','');}});
"""
nav = ''.join(f'<a href="#{anchor}">{title}</a>' for anchor,title in [
    ('start','Start here'),('context','Continuation context'),('additional','Additional user requests'),('transcript','Public conversation'),('references','Design & output images'),('source','Current source snapshots'),('documents','Docs & verification'),('manifest','Transfer inventory')])
document = f'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="description" content="Standalone AETHER HMI conversation and engineering handoff, including current incomplete fixes, reference images and historical verification.">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'none'; base-uri 'none'; form-action 'none'">
<title>AETHER HMI — Complete Conversation &amp; Context Handoff</title><style>{css}</style></head>
<body><header><div class="eyebrow">AETHER / Android HMI / Context transfer</div>
<h1>The conversation, the build history,<br>and the work still to finish.</h1>
<p class="lede">A self-contained handoff for the next engineer or AI, with original design references, the available public chat, technical decisions and a precise resume point.</p>
<div class="pills"><span class="pill">Exported 27 October 2026 · IST</span><span class="pill">Last verified: v0.5.1 / code 8</span><span class="pill warn">Latest seven fixes unfinished</span><span class="pill">Offline · no external resources</span></div>
<div class="toolbar"><button id="copy">Copy continuation context</button><button id="download">Download text context</button><button id="expand">Expand all</button><button id="collapse">Collapse all</button><button id="print">Print / Save PDF</button><span id="copy-status" role="status"></span></div>
<noscript><p class="noscript">JavaScript is optional. All content and images remain available; open the disclosure sections manually.</p></noscript></header>
<div class="layout"><nav aria-label="Handoff contents"><h2>Contents</h2>{nav}<p class="muted">Use your browser's Find command to search the page. Expand all to include collapsed sections.</p></nav>
<main>{''.join(sections)}<p class="foot">Prepared from the public conversation and explicitly relevant local project files. No app build or device test was run for this export. Files are provided for sharing; nothing has been sent to another person or service.</p></main></div>
<script type="application/json" id="handoff-data">{payload}</script><script>{js}</script></body></html>'''
OUT.write_text(document,encoding='utf-8')
print(json.dumps({'path':str(OUT),'bytes':OUT.stat().st_size,'MiB':round(OUT.stat().st_size/1048576,2),'publicMessages':public_count,'supplementalRequests':len(supplements),'images':len(asset_manifest),'sourceSnapshots':len(source_manifest)},indent=2))
