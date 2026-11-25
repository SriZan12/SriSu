import contextlib
from datetime import datetime, timezone
import io
import hashlib
import json
from pathlib import Path
import sys
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import run

SYNTHETIC_FID = "c" + "a" * 21


class NotificationKitTest(unittest.TestCase):
    def test_binding_lookup_matches_the_backend_namespaced_target_digest(self):
        # notifications.services.register persists SHA-256(kind + ':' + target).
        # A raw-FID digest previously rejected every real registered phone.
        stored_digest = hashlib.sha256(("fid:" + SYNTHETIC_FID).encode()).hexdigest()
        manager = Mock()
        def lookup(**filters):
            row = {"user_id": 1} if filters["target_digest"] == stored_digest else None
            query = Mock()
            query.values.return_value.first.return_value = row
            return query
        manager.filter.side_effect = lookup
        prefs = Mock()
        prefs.filter.return_value.first.return_value = SimpleNamespace(push=True, activity=True)
        cursor = contextlib.nullcontext(Mock())
        modules = {
            "django": SimpleNamespace(setup=lambda: None),
            "django.conf": SimpleNamespace(settings=SimpleNamespace(FIREBASE_PROJECT_ID=run.PROJECT, NOTIFICATIONS_ENABLED=True)),
            "django.db": SimpleNamespace(connection=SimpleNamespace(vendor="postgresql", cursor=lambda: cursor), transaction=SimpleNamespace(atomic=contextlib.nullcontext)),
            "django.utils": SimpleNamespace(timezone=SimpleNamespace(now=lambda: datetime.now(timezone.utc))),
            "notifications.models": SimpleNamespace(Installation=SimpleNamespace(objects=manager), Preferences=SimpleNamespace(objects=prefs)),
            "notifications.services": SimpleNamespace(digest=lambda value: hashlib.sha256(value.encode()).hexdigest(), installation_cutoff=lambda: datetime(2026, 1, 1, tzinfo=timezone.utc)),
        }
        with patch.dict(sys.modules, modules), patch.object(sys, "path", list(sys.path)), patch.dict(run.os.environ):
            run.require_binding(SYNTHETIC_FID)
            with self.assertRaises(run.TestKitError):
                run.require_binding("d" + "b" * 21)
        self.assertEqual(manager.filter.call_args_list[0].kwargs["target_kind"], "fid")
        self.assertEqual(manager.filter.call_args_list[0].kwargs["platform"], "android")

    def test_readiness_denial_never_calls_firebase(self):
        sender = Mock()
        with self.assertRaises(run.TestKitError):
            run.execute_request({"case": "audible", "fid": SYNTHETIC_FID, "send": True},
                Mock(side_effect=run.TestKitError("Device not registered.")), sender)
        sender.assert_not_called()

    def test_validation_cannot_accidentally_send(self):
        sender, binding = Mock(), Mock()
        for case in run.CASES:
            report = run.execute_request({"case": case, "config_only": True}, binding, sender, lambda: SYNTHETIC_FID)
            self.assertTrue(sender.call_args.kwargs["dry_run"])
            self.assertEqual(report["device_delivery"], "not_verified")
            self.assertNotIn(SYNTHETIC_FID, json.dumps(report))
        binding.assert_not_called()
        with self.assertRaises(run.TestKitError):
            run.execute_request({"case": "audible", "config_only": True, "send": True}, binding, sender)

    def test_send_is_explicit_and_only_one_registered_device(self):
        sender, binding = Mock(), Mock()
        run.execute_request({"case": "quiet", "fid": SYNTHETIC_FID}, binding, sender)
        self.assertTrue(sender.call_args.kwargs["dry_run"])
        report = run.execute_request({"case": "quiet", "fid": SYNTHETIC_FID, "send": True}, binding, sender)
        self.assertFalse(sender.call_args.kwargs["dry_run"])
        self.assertEqual(sender.call_args.args[0].fid, SYNTHETIC_FID)
        self.assertIsNone(sender.call_args.args[0].topic)
        self.assertEqual(binding.call_count, 2)
        self.assertEqual(report["mode"], "send")

    def test_channels_expiry_and_data_only_match_expected_behavior(self):
        now = datetime(2026, 10, 29, tzinfo=timezone.utc)
        quiet = run.make_message(run.specification("quiet", now), SYNTHETIC_FID)
        self.assertEqual(quiet.android.notification.channel_id, "srisu_updates_quiet")
        self.assertIsNone(quiet.android.notification.sound)
        loud = run.make_message(run.specification("audible", now), SYNTHETIC_FID)
        self.assertEqual(loud.android.notification.channel_id, "srisu_updates")
        self.assertEqual(loud.android.notification.sound, "default")
        self.assertEqual(loud.android.notification.visibility, "private")
        self.assertEqual(run.specification("short_ttl", now)["ttl"], 60)
        data = run.make_message(run.specification("data_only", now), SYNTHETIC_FID)
        self.assertIsNone(data.notification)
        self.assertIsNone(data.android.notification)
        expired = run.specification("expired_payload", now)
        self.assertLess(int(expired["data"]["expires_at"]), int(now.timestamp()))
        unknown = run.specification("unknown_delivery", now)
        self.assertGreater(int(unknown["data"]["expires_at"]), int(now.timestamp()))
        self.assertEqual(run.specification("invalid_schema", now)["data"]["schema"], "unsupported-qa")

    def test_invalid_target_and_request_fail_before_sending(self):
        sender = Mock()
        for request in ({"case": "audible", "fid": "not-an-id"},
                        {"case": "audible", "topic": "everyone"},
                        {"case": "audible", "config_only": "yes"},
                        {"case": "missing", "fid": SYNTHETIC_FID}):
            with self.assertRaises(run.TestKitError):
                run.execute_request(request, Mock(), sender)
        sender.assert_not_called()

    def test_stdin_target_never_enters_process_arguments(self):
        completed = Mock(returncode=0, stdout=json.dumps({"result": "accepted"}))
        with patch.object(run.subprocess, "run", return_value=completed) as process, contextlib.redirect_stdout(io.StringIO()) as out:
            run.run_case("audible", fid=SYNTHETIC_FID, live=True)
        self.assertNotIn(SYNTHETIC_FID, repr(process.call_args.args))
        self.assertIn(SYNTHETIC_FID, process.call_args.kwargs["input"])
        self.assertNotIn(SYNTHETIC_FID, out.getvalue())



if __name__ == "__main__":
    unittest.main()
