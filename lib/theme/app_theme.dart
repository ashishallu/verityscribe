import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class AppTheme {
  static const blue = Color(0xFF2459E0);
  static const cyan = Color(0xFF32C5E8);
  static const emerald = Color(0xFF14A87B);
  static const ink = Color(0xFF17233B);
  static const muted = Color(0xFF70809A);
  static const canvas = Color(0xFFF7F9FD);
  static ThemeData get light => ThemeData(
        useMaterial3: true,
        scaffoldBackgroundColor: canvas,
        colorScheme: ColorScheme.fromSeed(seedColor: blue, primary: blue, secondary: cyan, surface: Colors.white),
        textTheme: GoogleFonts.manropeTextTheme().apply(bodyColor: ink, displayColor: ink),
        appBarTheme: const AppBarTheme(backgroundColor: canvas, foregroundColor: ink, elevation: 0),
        cardTheme: CardThemeData(color: Colors.white, elevation: 0, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22))),
        snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
        inputDecorationTheme: InputDecorationTheme(filled: true, fillColor: Colors.white, border: OutlineInputBorder(borderRadius: BorderRadius.circular(18), borderSide: BorderSide.none)),
      );
  static ThemeData get dark => ThemeData(
    useMaterial3: true,
    scaffoldBackgroundColor: const Color(0xFF101827),
    colorScheme: ColorScheme.fromSeed(seedColor: cyan, brightness: Brightness.dark, surface: const Color(0xFF192337)),
    textTheme: GoogleFonts.manropeTextTheme(ThemeData.dark().textTheme).apply(bodyColor: const Color(0xFFF3F6FC), displayColor: const Color(0xFFF3F6FC)),
    cardTheme: CardThemeData(color: const Color(0xFF192337), elevation: 0, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22))),
    inputDecorationTheme: InputDecorationTheme(filled: true, fillColor: const Color(0xFF192337), border: OutlineInputBorder(borderRadius: BorderRadius.circular(18), borderSide: BorderSide.none)),
    navigationBarTheme: const NavigationBarThemeData(backgroundColor: Color(0xFF192337)),
    snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
  );
}
