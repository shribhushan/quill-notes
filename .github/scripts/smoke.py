#!/usr/bin/env python3
"""End-to-end smoke test of the *installed* APK on a running emulator (adb + uiautomator, no test framework).

Fresh install -> first-run onboarding -> create a note -> add a task -> browse the tabs -> search ->
settings -> switch the app language to Hindi and Marathi. Screenshots, UI dumps and logcat are written to
the output directory. Exits non-zero when the app crashes or a step fails, but always finishes all steps it
can so the artifacts show how far it got.

Usage: smoke.py OUT_DIR
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.quillnotes"
ACTIVITY = f"{PKG}/.MainActivity"
OUT = sys.argv[1] if len(sys.argv) > 1 else "smoke-out"
os.makedirs(OUT, exist_ok=True)

failures = []
steps_log = open(os.path.join(OUT, "steps.txt"), "w", encoding="utf-8")


def say(msg):
    print(msg, flush=True)
    steps_log.write(msg + "\n")
    steps_log.flush()


# ---------------------------------------------------------------- adb helpers

def adb(*args, timeout=90, binary=False):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")


def shot(name):
    png = adb("exec-out", "screencap", "-p", binary=True)
    with open(os.path.join(OUT, f"{name}.png"), "wb") as f:
        f.write(png)


def tap(x, y):
    adb("shell", "input", "tap", str(x), str(y))


def key(code):
    adb("shell", "input", "keyevent", str(code))


def type_text(text):
    adb("shell", "input", "text", text.replace(" ", "%s"))


# ---------------------------------------------------------------- UI inspection

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def parse_ui(xml_text):
    """-> list of dicts: text, desc, cls, clickable, cx, cy, w, h (only nodes with usable bounds)."""
    root = ET.fromstring(xml_text)
    nodes = []
    for n in root.iter("node"):
        m = BOUNDS.match(n.get("bounds", ""))
        if not m:
            continue
        x1, y1, x2, y2 = map(int, m.groups())
        nodes.append({
            "text": n.get("text", ""),
            "desc": n.get("content-desc", ""),
            "cls": n.get("class", ""),
            "clickable": n.get("clickable") == "true",
            "cx": (x1 + x2) // 2, "cy": (y1 + y2) // 2, "w": x2 - x1, "h": y2 - y1,
        })
    return nodes


def dump_ui(retries=5):
    for _ in range(retries):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        xml_text = adb("exec-out", "cat", "/sdcard/ui.xml")
        if xml_text.lstrip().startswith("<?xml"):
            try:
                return xml_text, parse_ui(xml_text)
            except ET.ParseError:
                pass
        time.sleep(1.0)
    return "", []


def match(nodes, pattern, field="any"):
    rx = re.compile(pattern, re.I)
    out = []
    for n in nodes:
        hay = [n["text"], n["desc"]] if field == "any" else [n[field]]
        if any(h and rx.search(h) for h in hay):
            out.append(n)
    return out


def wait_for(pattern, timeout=25, field="any", save_as=None):
    """Polls the UI until a node matching [pattern] exists; returns it (or None)."""
    end = time.time() + timeout
    last_xml = ""
    while time.time() < end:
        xml_text, nodes = dump_ui(retries=2)
        last_xml = xml_text or last_xml
        found = match(nodes, pattern, field)
        if found:
            if save_as:
                open(os.path.join(OUT, f"{save_as}.xml"), "w", encoding="utf-8").write(xml_text)
            return found[0]
        time.sleep(1.0)
    if save_as and last_xml:
        open(os.path.join(OUT, f"{save_as}.xml"), "w", encoding="utf-8").write(last_xml)
    return None


def tap_node(pattern, timeout=20, field="any"):
    node = wait_for(pattern, timeout, field)
    if not node:
        raise AssertionError(f"could not find '{pattern}' on screen")
    tap(node["cx"], node["cy"])
    return node


def edit_fields():
    _, nodes = dump_ui()
    return [n for n in nodes if n["cls"].endswith("EditText")]


def step(name, fn):
    say(f"--> {name}")
    try:
        fn()
        say(f"    ok: {name}")
    except Exception as e:  # noqa: BLE001 - a failed step must not stop the others
        failures.append(f"{name}: {e}")
        say(f"    FAILED: {name}: {e}")
        try:
            shot("FAILED_" + re.sub(r"\W+", "_", name))
        except Exception:  # noqa: BLE001
            pass


def crash_report():
    crash = adb("logcat", "-d", "-b", "crash")
    full = adb("logcat", "-d")
    open(os.path.join(OUT, "logcat.txt"), "w", encoding="utf-8").write(full)
    open(os.path.join(OUT, "crash.txt"), "w", encoding="utf-8").write(crash)
    return find_problems(crash, full)


def find_problems(crash, full):
    """Only this app's crashes count: emulator system apps misbehave now and then."""
    problems = []
    own_crash_lines = [l for l in crash.splitlines() if PKG in l and not l.startswith("-----")]
    if own_crash_lines:
        problems.append("crash buffer has entries for the app: " + own_crash_lines[0][:160])
    if re.search(rf"FATAL EXCEPTION[^\n]*\n[^\n]*Process: {re.escape(PKG)}", full):
        problems.append("FATAL EXCEPTION in the app process")
    if re.search(rf"ANR in {re.escape(PKG)}", full):
        problems.append("ANR reported for the app")
    return problems


# ---------------------------------------------------------------- the scenario

def launch():
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)


def s_onboarding():
    skip = wait_for(r"^(Skip|छोड़ें|वगळा)$", timeout=40, save_as="ui_01_onboarding")
    shot("01_onboarding")
    if not skip:
        raise AssertionError("onboarding did not appear (is the app starting?)")
    tap(skip["cx"], skip["cy"])


def s_home_empty():
    if not wait_for(r"New note", timeout=25, field="desc", save_as="ui_02_home"):
        raise AssertionError("home screen with the create button did not appear")
    shot("02_home_empty")
    _, nodes = dump_ui()
    if not match(nodes, r"A blank page"):
        raise AssertionError("empty-state text 'A blank page' missing on a fresh install")


def s_create_note():
    tap_node(r"New note", field="desc")
    time.sleep(1.5)
    fields = edit_fields()
    if len(fields) < 2:
        raise AssertionError(f"expected title and body fields, found {len(fields)} text fields")
    shot("03_editor_new")
    tap(fields[0]["cx"], fields[0]["cy"])
    type_text("Smoke test")
    fields = edit_fields()
    tap(fields[-1]["cx"], fields[-1]["cy"])
    type_text("- Buy milk")
    key(66)                       # Enter: the list should continue with "- "
    type_text("Eggs")
    time.sleep(1.0)
    shot("04_editor_typed")
    xml_text, nodes = dump_ui()
    open(os.path.join(OUT, "ui_04_editor_typed.xml"), "w", encoding="utf-8").write(xml_text)
    body = "\n".join(n["text"] for n in nodes if n["cls"].endswith("EditText"))
    if "Eggs" not in body or "Buy milk" not in body:
        raise AssertionError(f"typed text not found in the editor: {body!r}")
    markers = len(re.findall(r"(?m)^\s*[-\u2022]\s", body))
    say(f"    list continuation after Enter: {'yes' if markers >= 2 else 'not detected'} ({markers} bullet markers in {body!r})")


def go_to_tabs(max_backs=4):
    """Presses back until a main tab screen (the create button, no text field) is showing."""
    for _ in range(max_backs):
        _, nodes = dump_ui()
        editing = [n for n in nodes if n["cls"].endswith("EditText")]
        if match(nodes, r"^New (note|journal entry|task)$", "desc") and not editing:
            return True
        key(4)
        time.sleep(1.2)
    return False


def s_save_and_return():
    key(4)                        # back: the first press may only close the keyboard
    time.sleep(1.5)
    go_to_tabs()
    if not wait_for(r"Smoke test", timeout=15, save_as="ui_05_home_with_note"):
        raise AssertionError("the new note did not show up on the home screen")
    shot("05_home_with_note")


def s_planner():
    tap_node(r"^Planner$", field="desc")
    if not wait_for(r"Planner", field="text", timeout=15):
        raise AssertionError("planner screen did not open")
    time.sleep(1.0)
    shot("06_planner_empty")
    field = wait_for(r"Add a task", timeout=10)
    if not field:
        raise AssertionError("quick-add field not found")
    tap(field["cx"], field["cy"])
    type_text("Smoke task")
    tap_node(r"^Add task$", field="desc", timeout=10)
    if not wait_for(r"Smoke task", timeout=15, save_as="ui_07_planner_task"):
        raise AssertionError("the new task did not appear in the planner")
    shot("07_planner_task")


def s_journal():
    tap_node(r"^Journal$", field="desc")
    if not wait_for(r"Journal", field="text", timeout=15):
        raise AssertionError("journal screen did not open")
    time.sleep(1.0)
    shot("08_journal")


def s_search():
    tap_node(r"^Notes$", field="desc")
    wait_for(r"Smoke test", timeout=15)
    tap_node(r"Search notes, journal and tasks", timeout=15)
    time.sleep(1.0)
    fields = edit_fields()
    if not fields:
        raise AssertionError("search field not found")
    tap(fields[0]["cx"], fields[0]["cy"])
    type_text("milk")
    if not wait_for(r"Smoke test", timeout=15, save_as="ui_09_search"):
        raise AssertionError("searching 'milk' did not find the note")
    shot("09_search")
    if not go_to_tabs():
        raise AssertionError("could not get back from search to the main screens")


def s_settings():
    tap_node(r"settings", field="desc")
    if not wait_for(r"Theme", field="text", timeout=15, save_as="ui_10_settings"):
        raise AssertionError("settings screen did not open")
    time.sleep(1.0)
    shot("10_settings")
    adb("shell", "input", "swipe", "540", "1800", "540", "500", "300")
    time.sleep(1.0)
    shot("11_settings_data")
    go_to_tabs()


def set_language(tag, shot_name):
    out = adb("shell", "cmd", "locale", "set-app-locales", PKG, "--locales", tag)
    say(f"    set-app-locales {tag}: {out.strip()[:120]}")
    time.sleep(3.0)
    launch()
    time.sleep(3.0)
    wait_for(r".", timeout=10)
    shot(shot_name)


def s_hindi():
    set_language("hi", "12_home_hi")
    _, nodes = dump_ui()
    if not any(re.search(r"[ऀ-ॿ]", n["text"] + n["desc"]) for n in nodes):
        raise AssertionError("no Devanagari text on screen after switching to Hindi")
    tap_node(r"(सेटिंग|settings)", field="desc", timeout=10)
    time.sleep(1.5)
    shot("13_settings_hi")
    go_to_tabs()


def s_marathi():
    set_language("mr", "14_home_mr")
    _, nodes = dump_ui()
    if not any(re.search(r"[ऀ-ॿ]", n["text"] + n["desc"]) for n in nodes):
        raise AssertionError("no Devanagari text on screen after switching to Marathi")
    tap_node(r"(प्लॅनर|Planner)", field="desc", timeout=10)
    time.sleep(1.5)
    shot("15_planner_mr")
    set_language("en", "16_back_to_english")


def main():
    say(f"device: {adb('shell', 'getprop', 'ro.product.model').strip()} / API {adb('shell', 'getprop', 'ro.build.version.sdk').strip()}")
    version = re.search(r"versionName=(\S+)", adb("shell", "dumpsys", "package", PKG))
    say(f"app version: {version.group(1) if version else '?'}")
    adb("logcat", "-c")
    launch()
    step("first-run onboarding", s_onboarding)
    step("empty home screen", s_home_empty)
    step("create a note with a list", s_create_note)
    step("save and return home", s_save_and_return)
    step("planner: add a task", s_planner)
    step("journal tab", s_journal)
    step("global search", s_search)
    step("settings", s_settings)
    step("switch to Hindi", s_hindi)
    step("switch to Marathi", s_marathi)

    problems = crash_report()
    failures.extend(problems)
    result = "PASS" if not failures else "FAIL"
    with open(os.path.join(OUT, "result.txt"), "w", encoding="utf-8") as f:
        f.write(result + "\n" + "\n".join(failures) + "\n")
    say(f"RESULT: {result}")
    for p in failures:
        say(f"  - {p}")
    return 0 if result == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
