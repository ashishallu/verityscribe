import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
final themeProvider = StateNotifierProvider<ThemeNotifier, ThemeMode>((_) => ThemeNotifier());
class ThemeNotifier extends StateNotifier<ThemeMode> { ThemeNotifier() : super(ThemeMode.light) { _restore(); } Future<void> _restore() async { final prefs=await SharedPreferences.getInstance(); state=prefs.getBool('dark_mode') == true ? ThemeMode.dark : ThemeMode.light; } Future<void> toggle(bool dark) async { state=dark ? ThemeMode.dark : ThemeMode.light; final prefs=await SharedPreferences.getInstance(); await prefs.setBool('dark_mode', dark); } }
