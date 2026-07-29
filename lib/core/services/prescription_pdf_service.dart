import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import '../../models/entities.dart';

class PrescriptionPdfService {
  Future<File> create(Consultation consultation, List<Medicine> medicines) async {
    final document = pw.Document();
    document.addPage(pw.Page(pageFormat: PdfPageFormat.a4, build: (context) => pw.Padding(padding: const pw.EdgeInsets.all(32), child: pw.Column(crossAxisAlignment: pw.CrossAxisAlignment.start, children: [
      pw.Text('NOVACARE HOSPITAL', style: pw.TextStyle(fontSize: 22, fontWeight: pw.FontWeight.bold, color: PdfColor.fromInt(0xFF2459E0))),
      pw.Text('Cardiology and Preventive Care'), pw.Divider(), pw.SizedBox(height: 14),
      pw.Text('PRESCRIPTION', style: pw.TextStyle(fontSize: 16, fontWeight: pw.FontWeight.bold)), pw.SizedBox(height: 12),
      pw.Text('Patient: Ashish Allu   •   Verity ID: VS-2048-731'),
      pw.Text('Doctor: ${consultation.doctorName}   •   ${consultation.hospital}'),
      pw.SizedBox(height: 16), pw.Text('Diagnosis: ${consultation.diagnosis}', style: pw.TextStyle(fontWeight: pw.FontWeight.bold)), pw.SizedBox(height: 12),
      ...medicines.map((medicine) => pw.Padding(padding: const pw.EdgeInsets.only(bottom: 9), child: pw.Text('${medicine.name} ${medicine.dosage}\n${medicine.schedule}. ${medicine.howToTake}'))),
      pw.SizedBox(height: 10), pw.Text('Follow-up: 21 August 2026'), pw.SizedBox(height: 10), pw.Text('Doctor notes: ${consultation.summary}'),
      pw.Spacer(), pw.Divider(), pw.Text('Electronically prepared by VerityScribe.', style: const pw.TextStyle(fontSize: 8)),
    ]))));
    final directory = await getApplicationDocumentsDirectory();
    final file = File('${directory.path}/VerityScribe_Prescription_${consultation.id}.pdf');
    await file.writeAsBytes(await document.save());
    return file;
  }
}
