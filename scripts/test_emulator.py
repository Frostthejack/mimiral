#!/usr/bin/env python3
"""
test_emulator.py — Automated emulator test runner for Mimiral Android app.

Automates the full pipeline:
  1. Build debug APK             (./gradlew --no-daemon assembleDebug)
  2. Start emulator if needed    (emulator -avd <AVD> -no-window -no-audio -no-snapshot)
  3. Wait for boot completion    (adb wait-for-device + sys.boot_completed)
  4. Install APK                 (adb install --no-streaming -r)
  5. Launch app                  (adb shell am start -n com.miranal.app/.MainActivity)
  6. Verify no crash             (adb logcat scan for FATAL / AndroidRuntime)
  7. Capture screenshot          (adb exec-out screencap -p)

Exit 0 on success, non-zero on failure.

Usage:
    python scripts/test_emulator.py [--avd NAME] [--apk PATH] [--screenshot PATH]

Environment variables (defaults defined below):
    ANDROID_HOME    Path to Android SDK
    AVD_NAME        AVD to launch
    APP_PACKAGE     App package name
    APP_ACTIVITY    Main activity
"""

import argparse
import os
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path

# ---------------------------------------------------------------------------
# Defaults — overridable via env vars / CLI flags
# ---------------------------------------------------------------------------
ANDROID_HOME = Path(os.environ.get(
    "ANDROID_HOME",
    r"C:\Users\luned\AppData\Local\Android\Sdk",
))

AVD_NAME = os.environ.get("AVD_NAME", "Medium_Phone_API_36.1")
APP_PACKAGE = os.environ.get("APP_PACKAGE", "com.mimiral.app")
APP_ACTIVITY = os.environ.get("APP_ACTIVITY", ".MainActivity")
DEFAULT_SCREENSHOT = r"C:\Users\luned\AppData\Local\Temp\mimiral_test.png"

# Emulator / tool binary paths
EMULATOR = ANDROID_HOME / "emulator" / "emulator.exe"
ADB = ANDROID_HOME / "platform-tools" / "adb.exe"

# Boot-wait timeout (seconds)
BOOT_TIMEOUT = 120
# Time (s) after app launch before we scan logcat for crashes
LAUNCH_SETTLE_SEC = 5
# logcat crash window (last N lines scanned)
CRASH_SCAN_LINES = 200

# ---------------------------------------------------------------------------
# Logging helpers
# ---------------------------------------------------------------------------
def log(msg: str) -> None:
    timestamp = time.strftime("%H:%M:%S")
    print(f"[{timestamp}] {msg}", flush=True)

def log_fail(msg: str) -> None:
    log(f"FAIL — {msg}")

def log_ok(msg: str) -> None:
    log(f"OK   — {msg}")

# ---------------------------------------------------------------------------
# Subprocess helpers
# ---------------------------------------------------------------------------

def run(
    cmd: list[str] | str,
    *,
    cwd: str | Path | None = None,
    env: dict | None = None,
    shell: bool = False,
    capture: bool = False,
    timeout: int = 300,
) -> subprocess.CompletedProcess:
    """Run a command, raising on non-zero exit."""
    if isinstance(cmd, str) and not shell:
        cmd = shlex.split(cmd)
    log(f"$ {' '.join(str(c) for c in cmd)}")
    try:
        p = subprocess.run(
            cmd,
            cwd=str(cwd) if cwd else None,
            env=env,
            shell=shell,
            capture_output=capture,
            text=True,
            timeout=timeout,
        )
    except FileNotFoundError as exc:
        raise RuntimeError(f"Command not found: {cmd[0]}") from exc
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError(f"Command timed out after {timeout}s: {cmd}") from exc
    if p.returncode != 0:
        detail = (p.stderr or p.stdout or "").strip()
        raise RuntimeError(
            f"Command failed (exit {p.returncode}): {cmd}\n{detail}"
        )
    return p


def adb(*args: str, timeout: int = 60) -> subprocess.CompletedProcess:
    """Run adb with the given arguments."""
    env = {**os.environ, "ANDROID_HOME": str(ANDROID_HOME)}
    return run([str(ADB), *args], env=env, capture=True, timeout=timeout)


def emulator(*args: str, timeout: int = 300) -> subprocess.CompletedProcess:
    """Run emulator with the given arguments."""
    env = {**os.environ, "ANDROID_HOME": str(ANDROID_HOME)}
    return run([str(EMULATOR), *args], env=env, capture=True, timeout=timeout)

# ---------------------------------------------------------------------------
# Pipeline steps
# ---------------------------------------------------------------------------

def step_build(project_dir: Path, apk_path: Path) -> None:
    """Step 1 — Build debug APK."""
    log("=== Step 1: Build debug APK ===")
    gradle = project_dir / "gradlew"
    if not gradle.exists():
        # Windows fallback
        gradle = project_dir / "gradlew.bat"
    if not gradle.exists():
        raise RuntimeError(f"Gradle wrapper not found in {project_dir}")

    env = {**os.environ, "ANDROID_HOME": str(ANDROID_HOME)}
    run(
        [str(gradle), "--no-daemon", "assembleDebug"],
        cwd=project_dir,
        env=env,
        timeout=600,
    )

    # Locate the built APK
    if not apk_path:
        candidates = list((project_dir / "app" / "build" / "outputs" / "apk" / "debug").glob("*.apk"))
        if not candidates:
            raise RuntimeError("assembleDebug succeeded but no APK found under app/build/outputs/apk/debug/")
        apk_path = candidates[0]

    if not apk_path.exists():
        raise RuntimeError(f"APK not found: {apk_path}")

    log_ok(f"APK built: {apk_path}")


def step_ensure_emulator() -> None:
    """Step 2 — Start emulator if not already running."""
    log("=== Step 2: Ensure emulator is running ===")
    try:
        result = adb("get-state", timeout=10)
        state = result.stdout.strip()
        if state == "device":
            log_ok("Emulator already running (device state)")
            return
        elif state == "offline":
            log("Emulator is offline — will restart")
        else:
            log(f"Unexpected adb state: {state}")
    except (RuntimeError, FileNotFoundError):
        log("adb not responding — emulator likely not running")

    # Start emulator headless
    log(f"Starting emulator: {AVD_NAME}")
    env = {**os.environ, "ANDROID_HOME": str(ANDROID_HOME)}
    subprocess.Popen(
        [
            str(EMULATOR),
            "-avd", AVD_NAME,
            "-no-window",
            "-no-audio",
            "-no-snapshot",
            "-gpu", "swiftshader_indirect",
        ],
        env=env,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    log("Emulator process launched (headless)")


def step_wait_boot() -> None:
    """Step 3 — Wait for emulator to fully boot."""
    log(f"=== Step 3: Waiting for boot (timeout {BOOT_TIMEOUT}s) ===")
    env = {**os.environ, "ANDROID_HOME": str(ANDROID_HOME)}

    # Wait for device to appear
    log("Waiting for device...")
    try:
        subprocess.run(
            [str(ADB), "wait-for-device"],
            env=env,
            timeout=BOOT_TIMEOUT,
        )
    except subprocess.TimeoutExpired:
        raise RuntimeError(f"Device did not appear within {BOOT_TIMEOUT}s")

    # Wait for boot_completed == 1
    deadline = time.time() + BOOT_TIMEOUT
    while time.time() < deadline:
        try:
            r = adb("shell", "getprop", "sys.boot_completed", timeout=10)
            if r.stdout.strip() == "1":
                log_ok("Device fully booted")
                return
        except RuntimeError:
            pass
        time.sleep(3)

    raise RuntimeError(f"Device did not finish booting within {BOOT_TIMEOUT}s")


def step_install_apk(apk_path: Path) -> None:
    """Step 4 — Install APK on emulator."""
    log("=== Step 4: Install APK ===")
    adb("install", "--no-streaming", "-r", str(apk_path), timeout=120)
    log_ok(f"APK installed: {apk_path.name}")


def step_launch_app() -> None:
    """Step 5 — Launch the app."""
    log("=== Step 5: Launch app ===")
    component = f"{APP_PACKAGE}/{APP_ACTIVITY}"
    adb("shell", "am", "start", "-n", component, timeout=30)
    log_ok(f"App launched: {component}")

    # Brief settle
    log(f"Waiting {LAUNCH_SETTLE_SEC}s for app to settle...")
    time.sleep(LAUNCH_SETTLE_SEC)


def step_verify_no_crash() -> None:
    """Step 6 — Scan logcat for crash indicators."""
    log("=== Step 6: Verify no crash ===")
    result = adc = adb(
        "logcat", "-d", "-t", str(CRASH_SCAN_LINES),
        timeout=30,
    )

    crash_patterns = [
        re.compile(r"FATAL\s+EXCEPTION", re.IGNORECASE),
        re.compile(r"AndroidRuntime.*FATAL", re.IGNORECASE),
        re.compile(r"Process:\s+" + re.escape(APP_PACKAGE) + r".*has\s+crashed", re.IGNORECASE),
        re.compile(r"java\.lang\..*Exception.*" + re.escape(APP_PACKAGE), re.IGNORECASE),
    ]

    lines = result.stdout.splitlines()
    for line in lines:
        for pat in crash_patterns:
            if pat.search(line):
                log_fail(f"Crash detected in logcat:\n  {line}")
                # Print surrounding context
                idx = lines.index(line)
                ctx = lines[max(0, idx - 2):idx + 3]
                for l in ctx:
                    print(f"    {l}")
                raise RuntimeError("App crashed after launch — logcat shows FATAL EXCEPTION")

    log_ok("No crash detected in logcat")


def step_screenshot(output: Path) -> None:
    """Step 7 — Capture screenshot."""
    log("=== Step 7: Capture screenshot ===")
    result = adb("exec-out", "screencap", "-p", timeout=30)

    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result.stdout if isinstance(result.stdout, bytes)
                       else result.stdout.encode())
    log_ok(f"Screenshot saved to {output}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Automated emulator test runner for Mimiral Android app",
    )
    parser.add_argument(
        "--avd", default=AVD_NAME,
        help=f"AVD name (default: {AVD_NAME})",
    )
    parser.add_argument(
        "--apk", default=None,
        help="Path to APK (auto-detected if omitted)",
    )
    parser.add_argument(
        "--screenshot", default=DEFAULT_SCREENSHOT,
        help=f"Screenshot output path (default: {DEFAULT_SCREENSHOT})",
    )
    parser.add_argument(
        "--project-dir", default=None,
        help="Project root directory (default: parent of scripts/)",
    )
    parser.add_argument(
        "--skip-build", action="store_true",
        help="Skip gradle build (use --apk to specify existing APK)",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    # Resolve project directory
    script_dir = Path(__file__).resolve().parent
    project_dir = Path(args.project_dir) if args.project_dir else script_dir.parent
    project_dir = project_dir.resolve()

    apk_path = Path(args.apk) if args.apk else None
    screenshot_path = Path(args.screenshot)

    log(f"Project dir : {project_dir}")
    log(f"ANDROID_HOME: {ANDROID_HOME}")
    log(f"AVD         : {args.avd}")

    # Validate binaries
    if not ADB.exists():
        log_fail(f"adb not found at {ADB}")
        return 1
    if not EMULATOR.exists():
        log_fail(f"emulator not found at {EMULATOR}")
        return 1

    try:
        # Step 1 — Build
        if not args.skip_build and not apk_path:
            step_build(project_dir, None)
            # APK path is auto-detected inside step_build; find it for later steps
            apk_dir = project_dir / "app" / "build" / "outputs" / "apk" / "debug"
            candidates = list(apk_dir.glob("*.apk"))
            if not candidates:
                raise RuntimeError("No APK found after build")
            apk_path = candidates[0]

        if not apk_path or not apk_path.exists():
            raise RuntimeError(f"APK not found: {apk_path}")
        log(f"Using APK: {apk_path}")

        # Step 2 — Start emulator
        step_ensure_emulator()

        # Step 3 — Wait for boot
        step_wait_boot()

        # Step 4 — Install APK
        step_install_apk(apk_path)

        # Step 5 — Launch app
        step_launch_app()

        # Step 6 — Verify no crash
        step_verify_no_crash()

        # Step 7 — Screenshot
        step_screenshot(screenshot_path)

        log("\n" + "=" * 60)
        log_ok("ALL STEPS PASSED — emulator test run succeeded")
        log("=" * 60)
        return 0

    except RuntimeError as exc:
        log_fail(str(exc))
        log("\n" + "=" * 60)
        log_fail("TEST RUN FAILED")
        log("=" * 60)
        return 1
    except FileNotFoundError as exc:
        log_fail(str(exc))
        return 1
    except KeyboardInterrupt:
        log("Interrupted by user")
        return 130


if __name__ == "__main__":
    sys.exit(main())
