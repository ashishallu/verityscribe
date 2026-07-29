import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../models/entities.dart';
import '../../providers/clinic_provider.dart';
import '../../theme/app_theme.dart';
import '../../widgets/ui.dart';

class AlarmsScreen extends ConsumerWidget {
  const AlarmsScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final medicines = ref.watch(clinicProvider).medicines;
    final taken = medicines.where((m) => m.taken).length;
    return ListView(padding: const EdgeInsets.only(bottom:28), children: [
      Padding(padding: const EdgeInsets.all(20), child: Row(children:[Text('Medication planner',style:Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight:FontWeight.w800)),const Spacer(),IconButton(onPressed:()=>context.push('/scan'),icon:const Icon(Icons.document_scanner_rounded))])),
      Padding(padding: const EdgeInsets.symmetric(horizontal:20), child: _ProgressCard(taken:taken,total:medicines.length)),
      _Section(title:'Morning',subtitle:'08:00 AM',color:AppTheme.blue,medicines:medicines.where((m)=>m.schedule.contains('08:00')||m.name=='Metformin').toList()),
      _Section(title:'Afternoon',subtitle:'12:30 PM',color:const Color(0xFF7C63D9),medicines:const []),
      _Section(title:'Evening',subtitle:'06:30 PM',color:const Color(0xFFE79A22),medicines:const []),
      _Section(title:'Night',subtitle:'08:00 PM',color:AppTheme.emerald,medicines:medicines.where((m)=>m.schedule.contains('PM')).toList()),
    ]);
  }
}
class _ProgressCard extends StatelessWidget { final int taken,total; const _ProgressCard({required this.taken,required this.total}); @override Widget build(BuildContext context)=>SoftCard(color:Theme.of(context).colorScheme.primaryContainer,child:Row(children:[SizedBox(width:86,height:86,child:Stack(alignment:Alignment.center,children:[CircularProgressIndicator(value:total==0?0:taken/total,strokeWidth:9,backgroundColor:Colors.white54,color:AppTheme.emerald),Text('$taken/$total',style:const TextStyle(fontWeight:FontWeight.w800))])),const SizedBox(width:17),Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[const Text("Today's progress",style:TextStyle(fontWeight:FontWeight.w800,fontSize:16)),Text('$taken medicines taken • ${total-taken} remaining',style:TextStyle(color:Theme.of(context).colorScheme.onSurfaceVariant,fontSize:12)),const SizedBox(height:9),const StatusPill('NEXT • 08:00 PM',AppTheme.blue)]))])); }
class _Section extends ConsumerWidget {
  final String title,subtitle; final Color color; final List<Medicine> medicines;
  const _Section({required this.title,required this.subtitle,required this.color,required this.medicines});
  @override Widget build(BuildContext context,WidgetRef ref) => Padding(
    padding:const EdgeInsets.fromLTRB(20,24,20,0),
    child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[
      Row(children:[Container(width:10,height:10,decoration:BoxDecoration(color:color,shape:BoxShape.circle)),const SizedBox(width:8),Text(title,style:Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight:FontWeight.w800)),const Spacer(),Text(subtitle,style:TextStyle(color:color,fontWeight:FontWeight.w800,fontSize:12))]),
      const SizedBox(height:10),
      if(medicines.isEmpty) SoftCard(child:Text('No reminders scheduled',style:TextStyle(color:Theme.of(context).colorScheme.onSurfaceVariant)))
      else ...medicines.map((medicine) => Padding(padding:const EdgeInsets.only(bottom:10),child:InkWell(onTap:()=>context.push('/alarm'),borderRadius:BorderRadius.circular(22),child:SoftCard(child:Row(children:[Container(width:48,height:48,decoration:BoxDecoration(color:color.withValues(alpha:.12),borderRadius:BorderRadius.circular(15)),child:Icon(Icons.medication_rounded,color:color)),const SizedBox(width:12),Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(medicine.name,style:const TextStyle(fontWeight:FontWeight.w800)),Text('${medicine.dosage} • ${medicine.schedule}',style:TextStyle(fontSize:11,color:Theme.of(context).colorScheme.onSurfaceVariant)),Text('Dr. ${medicine.doctor.replaceFirst('Dr. ','')} • After food',style:TextStyle(fontSize:11,color:color,fontWeight:FontWeight.w700))])),StatusPill(medicine.taken?'TAKEN':'PENDING',medicine.taken?AppTheme.emerald:color)]))))),
    ]),
  );
}
class AlarmReminderScreen extends ConsumerWidget { const AlarmReminderScreen({super.key}); @override Widget build(BuildContext context,WidgetRef ref){final medicine=ref.watch(clinicProvider).medicines.first;return Scaffold(body:SafeArea(child:Padding(padding:const EdgeInsets.all(24),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[const SizedBox(height:30),Text('08:00 AM',style:Theme.of(context).textTheme.displaySmall?.copyWith(fontWeight:FontWeight.w800,color:AppTheme.blue)),const Text('Your morning care is ready.',style:TextStyle(color:AppTheme.muted)),const SizedBox(height:32),SoftCard(child:ListTile(leading:const Icon(Icons.medication_rounded,color:AppTheme.blue,size:34),title:Text('${medicine.name} ${medicine.dosage}',style:const TextStyle(fontWeight:FontWeight.w800)),subtitle:Text('${medicine.purpose}\n${medicine.schedule}'))),const Spacer(),FilledButton(onPressed:(){ref.read(clinicProvider.notifier).markTaken(medicine.id);context.pop();},style:FilledButton.styleFrom(minimumSize:const Size.fromHeight(54)),child:const Text('Mark as taken')),const SizedBox(height:10),Row(children:[Expanded(child:OutlinedButton(onPressed:()=>context.pop(),child:const Text('Snooze'))),const SizedBox(width:10),Expanded(child:TextButton(onPressed:()=>context.pop(),child:const Text('Skip')))])]))));}}
