import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/app_providers.dart';
import '../models/entities.dart';

class AppointmentBookingScreen extends ConsumerStatefulWidget { const AppointmentBookingScreen({super.key}); @override ConsumerState<AppointmentBookingScreen> createState()=>_AppointmentBookingState(); }
class _AppointmentBookingState extends ConsumerState<AppointmentBookingScreen> {
  Hospital? hospital; Department? department; DoctorDirectoryItem? doctor; DateTime? date; TimeOfDay? time; String type='in_person'; final reason=TextEditingController(),notes=TextEditingController(); bool submitting=false; String? error;
  @override void dispose(){reason.dispose();notes.dispose();super.dispose();}
  Future<void> submit() async { if(hospital==null||doctor==null||date==null||time==null){setState(()=>error='Select a hospital, doctor, date and time.');return;} setState(()=>submitting=true); try {final d=date!,t=time!; await ref.read(healthRepositoryProvider).bookAppointment(doctorId:doctor!.id,hospitalId:hospital!.id,date:'${d.year.toString().padLeft(4,'0')}-${d.month.toString().padLeft(2,'0')}-${d.day.toString().padLeft(2,'0')}',time:'${t.hour.toString().padLeft(2,'0')}:${t.minute.toString().padLeft(2,'0')}',consultationType:type,reason:reason.text,notes:notes.text);ref.invalidate(appointmentsLiveProvider);if(mounted){ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content:Text('Appointment booked successfully.')));Navigator.pop(context);}}catch(e){setState(()=>error='Unable to book the appointment. Please try another time.');}finally{if(mounted)setState(()=>submitting=false);}}
  @override Widget build(BuildContext context){final hospitals=ref.watch(hospitalsProvider),doctors=ref.watch(doctorsDirectoryProvider);final departments=hospital==null?const AsyncValue<List<Department>>.data(<Department>[]):ref.watch(departmentsProvider(hospital!.id));final filtered=doctors.valueOrNull?.where((d)=>d.available&&(hospital==null||d.hospitalId==hospital!.id)&&(department==null||d.departmentId==department!.id)).toList()??<DoctorDirectoryItem>[];return Scaffold(appBar:AppBar(title:const Text('Book an appointment')),body:ListView(padding:const EdgeInsets.all(20),children:[const Text('Hospital',style:TextStyle(fontWeight:FontWeight.w700)),hospitals.when(loading:()=>const LinearProgressIndicator(),error:(e,_)=>const Text('Unable to load hospitals'),data:(items)=>DropdownButtonFormField<Hospital>(initialValue:hospital,items:items.map((x)=>DropdownMenuItem(value:x,child:Text(x.name))).toList(),onChanged:(x)=>setState((){hospital=x;department=null;doctor=null;}),decoration:const InputDecoration(hintText:'Select hospital'))),const SizedBox(height:14),const Text('Department',style:TextStyle(fontWeight:FontWeight.w700)),departments.when(loading:()=>const LinearProgressIndicator(),error:(e,_)=>const Text('Unable to load departments'),data:(items)=>DropdownButtonFormField<Department>(initialValue:department,items:items.map((x)=>DropdownMenuItem(value:x,child:Text(x.name))).toList(),onChanged:hospital==null?null:(x)=>setState((){department=x;doctor=null;}),decoration:const InputDecoration(hintText:'Select department'))),const SizedBox(height:14),const Text('Doctor',style:TextStyle(fontWeight:FontWeight.w700)),DropdownButtonFormField<DoctorDirectoryItem>(initialValue:doctor,items:filtered.map((x)=>DropdownMenuItem(value:x,child:Text('${x.name} • ${x.specialization}'))).toList(),onChanged:(x)=>setState(()=>doctor=x),decoration:const InputDecoration(hintText:'Select available doctor')),if(doctor!=null)Text('${doctor!.hospital} • ${doctor!.department} • ₹${doctor!.fee}'),OutlinedButton.icon(onPressed:()=>showDatePicker(context:context,firstDate:DateTime.now(),lastDate:DateTime.now().add(const Duration(days:365)),initialDate:date??DateTime.now()).then((x){if(x!=null)setState(()=>date=x);}),icon:const Icon(Icons.calendar_today),label:Text(date==null?'Select date':'Date: ${date!.toLocal().toString().split(' ').first}')),OutlinedButton.icon(onPressed:()=>showTimePicker(context:context,initialTime:time??TimeOfDay.now()).then((x){if(x!=null)setState(()=>time=x);}),icon:const Icon(Icons.schedule),label:Text(time==null?'Select time':'Time: ${time!.format(context)}')),DropdownButtonFormField<String>(initialValue:type,items:const [DropdownMenuItem(value:'in_person',child:Text('In person')),DropdownMenuItem(value:'video',child:Text('Video')),DropdownMenuItem(value:'phone',child:Text('Phone')),DropdownMenuItem(value:'chat',child:Text('Chat'))],onChanged:(x)=>setState(()=>type=x??'in_person'),decoration:const InputDecoration(labelText:'Consultation type')),TextField(controller:reason,maxLength:300,decoration:const InputDecoration(labelText:'Reason for visit')),TextField(controller:notes,maxLength:500,decoration:const InputDecoration(labelText:'Notes')),if(error!=null)Text(error!,style:const TextStyle(color:Colors.red)),FilledButton(onPressed:submitting?null:submit,child:Text(submitting?'Booking…':'Review and confirm'))]));}
}

class DoctorsDirectoryScreen extends ConsumerWidget {
  const DoctorsDirectoryScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(doctorsDirectoryProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Find a doctor'), actions: [IconButton(onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const AppointmentBookingScreen())), icon: const Icon(Icons.calendar_month))]),
      body: state.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _Error(message: error.toString(), retry: () => ref.invalidate(doctorsDirectoryProvider)),
        data: (doctors) => doctors.isEmpty
            ? const Center(child: Text('No doctors are available right now.'))
            : RefreshIndicator(
                onRefresh: () => ref.refresh(doctorsDirectoryProvider.future),
                child: ListView.builder(
                  itemCount: doctors.length,
                  itemBuilder: (context, index) {
                    final doctor = doctors[index];
                    return Card(
                      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                      child: ListTile(
                        leading: const CircleAvatar(child: Icon(Icons.person)),
                        title: Text(doctor.name.isEmpty ? 'Doctor' : doctor.name),
                        subtitle: Text('${doctor.specialization} • ${doctor.department}\n${doctor.hospital} • ${doctor.experience} years'),
                        isThreeLine: true,
                        trailing: Text(doctor.available ? 'Available' : 'Unavailable', style: TextStyle(color: doctor.available ? Colors.green : Colors.grey)),
                      ),
                    );
                  },
                ),
              ),
      ),
    );
  }
}

class AppointmentsLiveScreen extends ConsumerWidget {
  const AppointmentsLiveScreen({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(appointmentsLiveProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('My appointments')),
      body: state.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _Error(message: error.toString(), retry: () => ref.invalidate(appointmentsLiveProvider)),
        data: (items) => items.isEmpty
            ? const Center(child: Text('No appointments yet.'))
            : RefreshIndicator(
                onRefresh: () => ref.refresh(appointmentsLiveProvider.future),
                child: ListView(
                  children: items.map((item) => Card(
                    margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
                    child: ListTile(
                      title: Text(item.doctorName.isEmpty ? 'Doctor' : item.doctorName),
                      subtitle: Text('${item.date} ${item.time}\n${item.hospital} • ${item.department}'),
                      isThreeLine: true,
                      trailing: Text(item.status),
                    ),
                  )).toList(),
                ),
              ),
      ),
    );
  }
}

class _Error extends StatelessWidget {
  const _Error({required this.message, required this.retry});
  final String message;
  final VoidCallback retry;
  @override
  Widget build(BuildContext context) => Center(child: Padding(padding: const EdgeInsets.all(24), child: Column(mainAxisSize: MainAxisSize.min, children: [Text('Unable to load live data', style: Theme.of(context).textTheme.titleMedium), const SizedBox(height: 8), Text(message, textAlign: TextAlign.center), TextButton(onPressed: retry, child: const Text('Retry'))])));
}
