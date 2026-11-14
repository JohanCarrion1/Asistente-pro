"""Configura el HTTP local sin el escape de comillas de Buildozer 1.5."""
from pathlib import Path
import xml.etree.ElementTree as ET


def before_apk_assemble(toolchain):
    manifest = Path(toolchain._dist.dist_dir) / "src/main/AndroidManifest.xml"
    namespace = "http://schemas.android.com/apk/res/android"
    ET.register_namespace("android", namespace)
    tree = ET.parse(manifest)
    application = tree.getroot().find("application")
    if application is None:
        raise ValueError("El manifiesto no contiene application")
    application.set(f"{{{namespace}}}usesCleartextTraffic", "true")
    tree.write(manifest, encoding="utf-8", xml_declaration=True)
