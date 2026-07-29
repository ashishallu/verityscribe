import '../../models/entities.dart';
final patient=Patient(id:'usr_ashish',name:'Ashish Allu',email:'ashish@verityscribe.app',medicalId:'VS-2048-731',dateOfBirth:DateTime(1998,5,14));
const doctor=Doctor(id:'doc_001',name:'Dr. Meera Kapoor',specialty:'Cardiology',hospital:'NovaCare Hospital');
final appointment=Appointment(id:'apt_001',doctor:doctor,scheduledAt:DateTime(2026,7,21,10,30),status:'confirmed');
const medicines=[Medicine(id:'med_1',name:'Metformin',dosage:'500 mg',purpose:'Diabetes management',schedule:'08:00 AM • After breakfast',remaining:12,initialQuantity:30,dailyUsage:2,taken:true),Medicine(id:'med_2',name:'Atorvastatin',dosage:'10 mg',purpose:'Heart health',schedule:'08:00 PM • After dinner',remaining:16,initialQuantity:30,dailyUsage:1),Medicine(id:'med_3',name:'Vitamin D3',dosage:'60,000 IU',purpose:'Bone health',schedule:'Sunday • 08:00 AM',remaining:4,initialQuantity:4,dailyUsage:1)];
final records=[Consultation(id:'con_1',doctorName:'Dr. Ananya Rao',summary:'Blood pressure is improving. Continue current medication and monitor readings.',date:DateTime(2026,7,18)),Consultation(id:'con_2',doctorName:'Dr. Meera Kapoor',summary:'Cardiology follow-up with vitals and lifestyle recommendations.',date:DateTime(2026,6,28))];
const metrics=HealthMetrics(heartRate:72,steps:6248,spo2:98,sleep:Duration(hours:7,minutes:42));
