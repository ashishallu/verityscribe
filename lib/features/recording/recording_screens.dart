import 'dart:async';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../theme/app_theme.dart';
import '../../widgets/ui.dart';

class RecordingScreen extends StatefulWidget { const RecordingScreen({super.key}); @override State<RecordingScreen> createState() => _RecordingScreenState(); }
class _RecordingScreenState extends State<RecordingScreen> with SingleTickerProviderStateMixin {
  bool recording=false; int seconds=0; Timer? timer; late final AnimationController pulse;
  @override void initState(){super.initState();pulse=AnimationController(vsync:this,duration:const Duration(milliseconds:900))..repeat(reverse:true);}
  @override void dispose(){timer?.cancel();pulse.dispose();super.dispose();}
  void toggle(){setState(()=>recording=!recording);if(recording){timer=Timer.periodic(const Duration(seconds:1),(_)=>setState(()=>seconds++));}else{timer?.cancel();}}
  @override Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Record securely')),
    body: Padding(
      padding: const EdgeInsets.all(20),
      child: Column(children: [
        ScaleTransition(scale: Tween<double>(begin:.92,end:1.08).animate(pulse), child: Container(width:150,height:150,decoration:BoxDecoration(shape:BoxShape.circle,gradient:const LinearGradient(colors:[AppTheme.blue,AppTheme.cyan]),boxShadow:[BoxShadow(color:AppTheme.blue.withValues(alpha:.25),blurRadius:30)]),child:const Icon(Icons.mic_rounded,size:64,color:Colors.white))),
        const SizedBox(height:20),
        Text('${(seconds~/60).toString().padLeft(2,'0')}:${(seconds%60).toString().padLeft(2,'0')}',style:Theme.of(context).textTheme.displaySmall?.copyWith(fontWeight:FontWeight.w800)),
        Text(recording?'Listening securely • Noise reduction active':'Ready to record',style:const TextStyle(color:AppTheme.muted)),
        const SizedBox(height:24),
        const Expanded(child: SoftCard(child: _Transcript())),
        const SizedBox(height:18),
        Row(children: [
          Expanded(child: OutlinedButton.icon(onPressed: toggle, icon: Icon(recording ? Icons.pause_rounded : Icons.mic_rounded), label: Text(recording ? 'Pause' : 'Start'))),
          const SizedBox(width: 12),
          Expanded(child: FilledButton.icon(onPressed: seconds == 0 ? null : () => context.go('/session-review'), icon: const Icon(Icons.stop_rounded), label: const Text('Stop & review'))),
        ]),
      ]),
    ),
  );
}
class _Transcript extends StatelessWidget { const _Transcript(); @override Widget build(BuildContext context)=>const Center(child:Text('Transcription is unavailable until a doctor consultation is active.',textAlign:TextAlign.center)); }
class SessionReviewScreen extends StatelessWidget { const SessionReviewScreen({super.key}); @override Widget build(BuildContext context)=>Scaffold(appBar:AppBar(title:const Text('AI session review')),body:const Center(child:Padding(padding:EdgeInsets.all(24),child:Text('AI review is available from the Doctor App after an authenticated consultation. No transcript or clinical draft was generated.',textAlign:TextAlign.center))); }
