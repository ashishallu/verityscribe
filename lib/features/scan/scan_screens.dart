import 'package:flutter/material.dart';

class ScanScreen extends StatelessWidget {
  const ScanScreen({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: const Text('Document scan')), body: const Center(child: Padding(padding: EdgeInsets.all(24), child: Text('Document scanning is unavailable until a secure backend upload is configured.', textAlign: TextAlign.center))));
}

class MedicineResultScreen extends StatelessWidget {
  const MedicineResultScreen({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: const Text('Medicine scan')), body: const Center(child: Padding(padding: EdgeInsets.all(24), child: Text('Medicine recognition is unavailable. No clinical result was generated.', textAlign: TextAlign.center))));
}
