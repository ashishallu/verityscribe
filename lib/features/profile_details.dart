import 'package:flutter/material.dart';

class DetailsPage extends StatelessWidget {
  final String title;
  final List<MapEntry<String, String>> items;
  const DetailsPage({required this.title, required this.items, super.key});
  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: Text(title)),
        body: Center(child: Text(items.isEmpty ? 'No live $title records available.' : 'Live $title data is unavailable in this view.')),
      );
}

const insuranceDetails = <MapEntry<String, String>>[];
const emergencyDetails = <MapEntry<String, String>>[];
const hospitalDetails = <MapEntry<String, String>>[];
const deviceDetails = <MapEntry<String, String>>[];
