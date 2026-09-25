import unittest
import re

class PhoneValidatorPy:
    E164_REGEX = re.compile(r"^\+[1-9]\d{6,14}$")
    COUNTRY_CODES = {
        "33": "FR", "32": "BE", "41": "CH", "1": "US/CA",
        "44": "GB", "49": "DE", "34": "ES", "39": "IT",
        "225": "CI", "221": "SN", "237": "CM", "212": "MA"
    }

    @classmethod
    def validate(cls, number_str, default_region="FR"):
        trimmed = number_str.strip().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        if not trimmed:
            return {"is_valid": False, "error": "Le numéro ne peut pas être vide."}
        if not trimmed.startswith("+"):
            if trimmed.startswith("00"):
                formatted = "+" + trimmed[2:]
            elif trimmed.startswith("0") and default_region == "FR":
                formatted = "+33" + trimmed[1:]
            else:
                formatted = "+" + trimmed
        else:
            formatted = trimmed

        if not cls.E164_REGEX.match(formatted):
            return {"is_valid": False, "error": "Format international invalide (E.164 requis)."}

        digits = formatted[1:]
        detected_country = "INTERNATIONAL"
        for l in (3, 2, 1):
            if len(digits) >= l and digits[:l] in cls.COUNTRY_CODES:
                detected_country = cls.COUNTRY_CODES[digits[:l]]
                break

        return {"is_valid": True, "e164": formatted, "country": detected_country}

    @classmethod
    def mask_phone(cls, phone_e164):
        if len(phone_e164) < 7:
            return "***"
        prefix = phone_e164[:3]
        suffix = phone_e164[-3:]
        bullets = "•" * max(3, len(phone_e164) - 6)
        return f"{prefix} {bullets} {suffix}"


class ReportGeneratorPy:
    @staticmethod
    def generate(phone, category, date_str, desc, notes, evidence_count, dossier_id):
        evidence_summary = (
            f"{evidence_count} pièce(s) justificative(s) documentée(s) (captures d'écran horodatées)."
            if evidence_count > 0
            else "Aucun fichier annexe joint pour le moment."
        )
        context_sec = f"\n\nContexte additionnel vérifié :\n{notes}" if notes else ""
        return f"""Objet : Signalement circonstancié d'un compte WhatsApp suspect [Dossier ref : {dossier_id}]

Numéro concerné :
{phone}

Motif du signalement :
{category}

Date(s) et chronologie des événements :
{date_str}

Résumé factuel des faits constatés :
{desc}{context_sec}

Éléments matériels et preuves disponibles :
{evidence_summary}

Demande formelle :
« En tant qu'utilisateur ayant directement constaté ces agissements, je transmets respectueusement ces éléments objectifs aux équipes de modération et de confiance & sécurité de WhatsApp afin qu'un examen soit conduit conformément aux Conditions d'utilisation et aux Politiques de sécurité de la plateforme. »
"""


class TestWhAlertCore(unittest.TestCase):
    def test_phone_validation_e164(self):
        res1 = PhoneValidatorPy.validate("+33612345678")
        self.assertTrue(res1["is_valid"])
        self.assertEqual(res1["country"], "FR")

        res2 = PhoneValidatorPy.validate("06 12 34 56 78")
        self.assertTrue(res2["is_valid"])
        self.assertEqual(res2["e164"], "+33612345678")

        res3 = PhoneValidatorPy.validate("+14155552671")
        self.assertTrue(res3["is_valid"])
        self.assertEqual(res3["country"], "US/CA")

        res_invalid = PhoneValidatorPy.validate("+123")
        self.assertFalse(res_invalid["is_valid"])

    def test_masking(self):
        masked = PhoneValidatorPy.mask_phone("+33612345678")
        self.assertEqual(masked, "+33 •••••• 678")

    def test_report_generation(self):
        text = ReportGeneratorPy.generate(
            "+33612345678", "Arnaque", "2026-09-25 14:00",
            "Message suspect reçu demandant un virement.", "", 1, "DOS-001"
        )
        self.assertIn("+33612345678", text)
        self.assertIn("DOS-001", text)
        self.assertIn("Conditions d'utilisation", text)
        self.assertNotIn("bannir immédiatement", text)

if __name__ == "__main__":
    unittest.main()
