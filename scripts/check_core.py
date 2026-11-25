#!/usr/bin/env python3
"""Run real JVM tests without an Android SDK or third-party Python packages."""
from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
java = shutil.which("java")
if not java:
    raise SystemExit("Install JDK 17 or newer, then run this command again.")
sources = [root / "app/src/main/java/app/blessyou/sneeze" / name for name in
           ("RecognitionGate.java", "AudioWindow.java")]
sources.append(root / "app/src/test/java/app/blessyou/sneeze/CoreChecks.java")
with tempfile.TemporaryDirectory(prefix="blessyou-checks-") as target:
    subprocess.run([java, "com.sun.tools.javac.Main", "-source", "17", "-target", "17", "-d", target,
                    *map(str, sources)], check=True)
    subprocess.run([java, "-cp", target, "app.blessyou.sneeze.CoreChecks"], check=True)
