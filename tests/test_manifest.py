import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
from types import SimpleNamespace
import unittest
import xml.etree.ElementTree as ET


class ManifestTests(unittest.TestCase):
    def test_backend_local_habilitado_en_xml_valido(self):
        spec = importlib.util.spec_from_file_location("build_hook", "android/build_hook.py")
        hook = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(hook)
        with TemporaryDirectory() as directory:
            path = Path(directory) / "src/main/AndroidManifest.xml"
            path.parent.mkdir(parents=True)
            path.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:label="Asistente Pro" /></manifest>')
            hook.before_apk_assemble(SimpleNamespace(_dist=SimpleNamespace(dist_dir=directory)))
            application = ET.parse(path).getroot().find("application")
            self.assertEqual(application.get("{http://schemas.android.com/apk/res/android}usesCleartextTraffic"), "true")
            self.assertEqual(application.get("{http://schemas.android.com/apk/res/android}label"), "Asistente Pro")
