import unittest

from app.services.document_tables import extract_result_rows, format_result_row


def _row(items, y, *, confidence=98):
    words = []
    for text, x0, x1 in items:
        words.append({"text": text, "x0": x0, "x1": x1, "y0": y, "y1": y + 10,
                      "confidence": confidence})
    return words


class MedicalTableExtractionTests(unittest.TestCase):
    def _layout(self, test_header="Investigation", value_header="Observed Value",
                range_header="Reference Range"):
        return _row([(test_header, 0, 100), (value_header, 150, 270),
                     ("Units", 320, 360), (range_header, 410, 540)], 10)

    def test_preserves_result_unit_range_and_page_in_distinct_columns(self):
        words = self._layout() + _row([
            ("Platelet", 0, 55), ("Count", 60, 105), ("72,000", 150, 200),
            ("/uL", 320, 345), ("150,000-450,000", 410, 530),
        ], 30)
        rows = extract_result_rows(words, page_number=2)
        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["test_name"], "Platelet Count")
        self.assertEqual(rows[0]["result_value"], "72,000")
        self.assertEqual(rows[0]["unit"], "/uL")
        self.assertEqual(rows[0]["reference_range"], "150,000-450,000")
        self.assertEqual(rows[0]["page_number"], 2)
        self.assertEqual(rows[0]["column_headers"]["result_value"], "Observed Value")
        self.assertEqual(rows[0]["cells"][1]["text"], "72,000")
        self.assertEqual(rows[0]["row_bounds"]["y0"], 30)
        self.assertEqual(rows[0]["table_bounds"]["y0"], 10)
        self.assertFalse(rows[0]["ambiguous"])
        self.assertIn("Test: Platelet Count | Result: 72,000", format_result_row(rows[0]))

    def test_preserves_decimal_precision_and_qualifiers_as_literal_values(self):
        words = self._layout() + _row([("Hemoglobin", 0, 105), ("10.80", 150, 195),
                                       ("g/dL", 320, 360), ("12.00-16.00", 410, 520)], 30)
        words += _row([("CRP", 0, 40), ("<5", 150, 170), ("mg/L", 320, 360),
                       ("0-10", 410, 450)], 50)
        rows = extract_result_rows(words)
        self.assertEqual([row["result_value"] for row in rows], ["10.80", "<5"])

    def test_missing_value_is_flagged_and_never_filled_from_reference_range(self):
        words = self._layout() + _row([("Test X", 0, 55), ("", 150, 270),
                                       ("unit", 320, 360), ("5-10", 410, 450)], 30)
        row = extract_result_rows(words)[0]
        self.assertIsNone(row["result_value"])
        self.assertEqual(row["reference_range"], "5-10")
        self.assertTrue(row["ambiguous"])

    def test_alternate_headers_and_missing_explicit_unit(self):
        words = _row([("Test", 0, 30), ("Value", 150, 185), ("Normal Range", 410, 520)], 10)
        words += _row([("Glucose", 0, 55), ("98", 150, 170), ("70-140", 410, 450)], 30)
        row = extract_result_rows(words)[0]
        self.assertEqual(row["test_name"], "Glucose")
        self.assertEqual(row["result_value"], "98")
        self.assertIsNone(row["unit"])

    def test_multiple_tables_and_continued_page_keep_order_and_location(self):
        first_layout = self._layout() + _row([("Glucose", 0, 55), ("98", 150, 170),
                              ("mg/dL", 320, 365), ("70-140", 410, 450)], 30)
        first_layout += _row([("Parameter", 0, 100), ("Value", 150, 270),
                              ("Units", 320, 360), ("Normal Range", 410, 540)], 50)
        first_layout += _row([("Glucose", 0, 55), (">200", 150, 190),
                              ("mg/dL", 320, 365), ("70-140", 410, 450)], 70)
        same_page = extract_result_rows(first_layout, page_number=1)
        self.assertEqual([(r["table_index"], r["row_index"], r["page_number"]) for r in same_page],
                         [(1, 1, 1), (2, 1, 1)])
        second_words = self._layout("Parameter", "Value", "Normal Range") + _row(
            [("Glucose", 0, 55), ("100", 150, 180), ("mg/dL", 320, 365), ("70-140", 410, 450)], 30)
        second = extract_result_rows(second_words, page_number=2, table_index_start=3)
        self.assertEqual((second[0]["table_index"], second[0]["page_number"]), (3, 2))


if __name__ == "__main__":
    unittest.main()
