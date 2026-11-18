"""Ticket 102: os vídeos prontos do Brabo ganham cópia numa pasta só deles."""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import brabo  # noqa: E402


class TestEntregar(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.pasta = Path(self.tmp.name)
        self.video = self.pasta / "c-01-brabo.mp4"
        self.video.write_bytes(b"video")
        self.video.with_suffix(".post.txt").write_text("TÍTULO: x", encoding="utf-8")

    def tearDown(self):
        self.tmp.cleanup()

    def test_copia_video_e_post_sem_mexer_no_original(self):
        destino = brabo.entregar(self.video)
        self.assertEqual(destino.parent.name, brabo.PASTA_PRONTOS)
        self.assertEqual(destino.read_bytes(), b"video")
        self.assertTrue(destino.with_suffix(".post.txt").is_file())
        self.assertTrue(self.video.is_file())  # o original fica

    def test_nunca_sobrescreve(self):
        primeiro = brabo.entregar(self.video)
        self.video.write_bytes(b"novo")
        segundo = brabo.entregar(self.video)
        self.assertEqual(primeiro.read_bytes(), b"video")
        self.assertEqual(segundo.name, "c-01-brabo-2.mp4")
        self.assertEqual(segundo.read_bytes(), b"novo")


if __name__ == "__main__":
    unittest.main()
