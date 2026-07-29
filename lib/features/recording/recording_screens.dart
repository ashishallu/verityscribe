import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../providers/clinic_provider.dart';
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
class _Transcript extends StatelessWidget { const _Transcript(); @override Widget build(BuildContext context)=>ListView(children:const [Text('Live transcription',style:TextStyle(fontWeight:FontWeight.w800)),SizedBox(height:16),Text('Doctor',style:TextStyle(color:AppTheme.blue,fontWeight:FontWeight.w800)),Text('Continue Metformin twice daily after meals.'),SizedBox(height:14),Text('Patient',style:TextStyle(color:AppTheme.emerald,fontWeight:FontWeight.w800)),Text('Okay doctor, I will continue it.'),SizedBox(height:14),Text('Doctor',style:TextStyle(color:AppTheme.blue,fontWeight:FontWeight.w800)),Text('Come back after two weeks for a review.')]); }
class SessionReviewScreen extends ConsumerWidget { const SessionReviewScreen({super.key}); @override Widget build(BuildContext context,WidgetRef ref)=>Scaffold(appBar:AppBar(title:const Text('AI session review')),body:ListView(padding:const EdgeInsets.all(20),children:[SoftCard(color:Theme.of(context).colorScheme.primaryContainer,child:const Text('Clinical summary generated from your secure recording.',style:TextStyle(fontWeight:FontWeight.w700))),const SectionTitle('Medicines'),const SoftCard(child:ListTile(title:Text('Metformin 500 mg',style:TextStyle(fontWeight:FontWeight.w800)),subtitle:Text('Morning and evening • After food • 14 days'),trailing:Icon(Icons.edit_rounded))),const SectionTitle('Session summary'),const SoftCard(child:Text('Diagnosis: Type 2 diabetes - stable\n\nRecommendations: Continue medication, balanced meals and review in two weeks.')),const SizedBox(height:24),FilledButton(onPressed:(){ref.read(clinicProvider.notifier).addConsultation();context.go('/records');},style:FilledButton.styleFrom(minimumSize:const Size.fromHeight(54)),child:const Text('Confirm and save session'))])); }
