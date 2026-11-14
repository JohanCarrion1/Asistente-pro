import unittest
from unittest.mock import patch

from backend import api


class BackendTests(unittest.TestCase):
    def setUp(self):
        self.client = api.app.test_client()

    def test_estado_sin_config_local(self):
        self.assertEqual(self.client.get("/").json["status"], "online")

    def test_texto_obligatorio(self):
        self.assertEqual(self.client.post("/comando", json={}).status_code, 400)

    def test_consulta_groq(self):
        with patch.object(api, "API_KEY", "clave-de-prueba"), patch.object(api.requests, "post") as post:
            post.return_value.status_code = 200
            post.return_value.json.return_value = {"choices": [{"message": {"content": "Hola"}}]}
            result = self.client.post("/comando", json={"texto": "Hola"})
            self.assertEqual(result.json["respuesta"], "Hola")
            self.assertEqual(post.call_args.kwargs["headers"]["Authorization"], "Bearer clave-de-prueba")

    def test_sin_clave_no_llama_proveedor(self):
        with patch.object(api, "API_KEY", ""), patch.object(api.requests, "post") as post:
            self.client.post("/comando", json={"texto": "Hola"})
            post.assert_not_called()
