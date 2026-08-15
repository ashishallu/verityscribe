import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:pdf/widgets.dart' as pw;
import '../../models/entities.dart';

class PrescriptionPdfService {
  Future<File> create(Consultation consultation, List<Medicine> medicines) async {
    if (medicines.isEmpty) throw StateError('No prescription medicines are available.');
    final document = pw.Document();
    document.addPage(pw.Page(build: (_) => pw.Padding(padding: const pw.EdgeInsets.all(32), child: pw.Column(crossAxisAlignment: pw.CrossAxisAlignment.start, children: [
      pw.Text('VERITYSCRIBE', style: pw.TextStyle(fontSize: 22, fontWeight: pw.FontWeight.bold)),
      pw.Divider(), pw.Text('PRESCRIPTION', style: pw.TextStyle(fontSize: 16, fontWeight: pw.FontWeight.bold)),
      pw.SizedBox(height: 12), pw.Text('Doctor: ${consultation.doctorName}'), pw.Text('Date: ${consultation.date.toIso8601String().split('T').first}'),
      pw.SizedBox(height: 12), pw.Text('Diagnosis: ${consultation.diagnosis}'),
      ...medicines.map((medicine) => pw.Padding(padding: const pw.EdgeInsets.only(top: 9), child: pw.Text('${medicine.name} ${medicine.dosage}\n${medicine.schedule}\n${medicine.howToTake}'))),
      pw.SizedBox(height: 12), pw.Text('Notes: ${consultation.summary}'),
    ]))));
    final directory = await getApplicationDocumentsDirectory();
    final file = File('${directory.path}/VerityScribe_Prescription_${consultation.id}.pdf');
    await file.writeAsBytes(await document.save());
    return file;
  }
}
