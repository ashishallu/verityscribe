import 'package:flutter/material.dart';

const navy = Color(0xff102a43);
const blue = Color(0xff1769e0);
const teal = Color(0xff0ba9a8);

void main() => runApp(const DoctorApp());

class DoctorApp extends StatefulWidget {
  const DoctorApp({super.key});
  @override
  State<DoctorApp> createState() => _DoctorAppState();
}

class _DoctorAppState extends State<DoctorApp> {
  ThemeMode mode = ThemeMode.light;
  @override
  Widget build(BuildContext context) => MaterialApp(
        debugShowCheckedModeBanner: false,
        title: 'VerityScribe Doctor',
        themeMode: mode,
        theme: ThemeData(
            useMaterial3: true,
            colorScheme: ColorScheme.fromSeed(seedColor: blue),
            scaffoldBackgroundColor: const Color(0xfff5f8fc)),
        darkTheme: ThemeData(
            useMaterial3: true,
            colorScheme: ColorScheme.fromSeed(
                seedColor: blue, brightness: Brightness.dark)),
        home: LoginPage(
            onTheme: () => setState(() => mode =
                mode == ThemeMode.light ? ThemeMode.dark : ThemeMode.light)),
      );
}

class LoginPage extends StatelessWidget {
  const LoginPage({super.key, required this.onTheme});
  final VoidCallback onTheme;
  @override
  Widget build(BuildContext context) => Scaffold(
      body: SafeArea(
          child: Center(
              child: SingleChildScrollView(
                  padding: const EdgeInsets.all(24),
                  child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 430),
                      child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(children: [
                              Image.asset(
                                  'assets/icon/verityscribe_doctor_icon.png',
                                  width: 58),
                              const SizedBox(width: 12),
                              const Expanded(
                                  child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                    Text('VerityScribe',
                                        style: TextStyle(
                                            fontSize: 22,
                                            fontWeight: FontWeight.w800,
                                            color: navy)),
                                    Text('DOCTOR WORKSPACE',
                                        style: TextStyle(
                                            fontSize: 10,
                                            color: teal,
                                            letterSpacing: 1.4,
                                            fontWeight: FontWeight.bold))
                                  ])),
                              IconButton(
                                  onPressed: onTheme,
                                  icon: const Icon(Icons.dark_mode_outlined))
                            ]),
                            const SizedBox(height: 45),
                            const Text('Welcome back, Doctor.',
                                style: TextStyle(
                                    fontSize: 30,
                                    fontWeight: FontWeight.w800,
                                    color: navy)),
                            const SizedBox(height: 8),
                            const Text(
                                'Your secure clinical workspace is ready.'),
                            const SizedBox(height: 30),
                            const TextField(
                                decoration: InputDecoration(
                                    labelText: 'Username or hospital ID',
                                    prefixIcon: Icon(Icons.person_outline),
                                    border: OutlineInputBorder())),
                            const SizedBox(height: 14),
                            const TextField(
                                obscureText: true,
                                decoration: InputDecoration(
                                    labelText: 'Password',
                                    prefixIcon: Icon(Icons.lock_outline),
                                    border: OutlineInputBorder())),
                            Row(children: [
                              const Checkbox(value: true, onChanged: null),
                              const Text('Remember me'),
                              const Spacer(),
                              TextButton(
                                  onPressed: () {},
                                  child: const Text('Forgot password?'))
                            ]),
                            const SizedBox(height: 12),
                            SizedBox(
                                width: double.infinity,
                                child: FilledButton(
                                    onPressed: () => Navigator.pushReplacement(
                                        context, route(const Workspace())),
                                    style: FilledButton.styleFrom(
                                        backgroundColor: navy,
                                        padding: const EdgeInsets.all(17)),
                                    child: const Text('Sign in to workspace'))),
                            const SizedBox(height: 12),
                            SizedBox(
                                width: double.infinity,
                                child: OutlinedButton.icon(
                                    onPressed: () => Navigator.pushReplacement(
                                        context, route(const Workspace())),
                                    icon: const Icon(Icons.fingerprint),
                                    label: const Text('Use biometric login'))),
                          ]))))));
}

class Workspace extends StatefulWidget {
  const Workspace({super.key});
  @override
  State<Workspace> createState() => _WorkspaceState();
}

class _WorkspaceState extends State<Workspace> {
  int tab = 0;
  @override
  Widget build(BuildContext context) {
    const screens = [
      Dashboard(),
      PatientSearch(),
      AiAssistant(),
      ProfilePage()
    ];
    return Scaffold(
        body: SafeArea(child: screens[tab]),
        bottomNavigationBar: NavigationBar(
            selectedIndex: tab,
            onDestinationSelected: (v) => setState(() => tab = v),
            destinations: const [
              NavigationDestination(
                  icon: Icon(Icons.grid_view_rounded), label: 'Home'),
              NavigationDestination(
                  icon: Icon(Icons.people_outline), label: 'Patients'),
              NavigationDestination(
                  icon: Icon(Icons.auto_awesome_outlined), label: 'Assist'),
              NavigationDestination(
                  icon: Icon(Icons.person_outline), label: 'Profile')
            ]));
  }
}

class Dashboard extends StatelessWidget {
  const Dashboard({super.key});
  @override
  Widget build(BuildContext context) =>
      ListView(padding: const EdgeInsets.all(20), children: [
        Row(children: [
          const CircleAvatar(
              radius: 24,
              backgroundImage:
                  AssetImage('assets/icon/verityscribe_doctor_icon.png')),
          const SizedBox(width: 12),
          const Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                Text('Good morning, Dr. Sharma',
                    style:
                        TextStyle(fontSize: 19, fontWeight: FontWeight.w800)),
                Text('Cardiology • Verity General Hospital',
                    style: TextStyle(color: Colors.blueGrey))
              ])),
          IconButton(
              onPressed: () {},
              icon: const Badge(child: Icon(Icons.notifications_none_rounded)))
        ]),
        const SizedBox(height: 22),
        Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
                gradient: const LinearGradient(colors: [navy, blue]),
                borderRadius: BorderRadius.circular(24)),
            child: const Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Wednesday, 23 July',
                      style: TextStyle(color: Color(0xffb7d6ff))),
                  SizedBox(height: 8),
                  Text('A focused day ahead.',
                      style: TextStyle(
                          color: Colors.white,
                          fontSize: 25,
                          fontWeight: FontWeight.bold)),
                  SizedBox(height: 8),
                  Text('8 consultations and 3 pending reports.',
                      style: TextStyle(color: Color(0xffe1eeff)))
                ])),
        const SizedBox(height: 22),
        const Text('Today at a glance',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        const SizedBox(height: 10),
        Wrap(spacing: 9, runSpacing: 9, children: const [
          Metric('08', 'Appointments', Icons.calendar_today_outlined),
          Metric('03', 'Waiting now', Icons.hourglass_bottom),
          Metric('11', 'Reports pending', Icons.description_outlined),
          Metric('06', 'Completed', Icons.check_circle_outline)
        ]),
        const SizedBox(height: 25),
        const Text("Today's appointments",
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        ...patients.map((p) => PatientTile(patient: p))
      ]);
}

class Metric extends StatelessWidget {
  const Metric(this.value, this.label, this.icon, {super.key});
  final String value, label;
  final IconData icon;
  @override
  Widget build(BuildContext context) => SizedBox(
      width: 162,
      child: Card(
          child: Padding(
              padding: const EdgeInsets.all(14),
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(icon, color: teal),
                    const SizedBox(height: 12),
                    Text(value,
                        style: const TextStyle(
                            fontSize: 23, fontWeight: FontWeight.w800)),
                    Text(label, style: const TextStyle(fontSize: 12))
                  ]))));
}

class PatientSearch extends StatefulWidget {
  const PatientSearch({super.key});
  @override
  State<PatientSearch> createState() => _PatientSearchState();
}

class _PatientSearchState extends State<PatientSearch> {
  String query = '';
  @override
  Widget build(BuildContext context) {
    final found = patients.where((p) => ('${p.name} ${p.id} ${p.reason}')
        .toLowerCase()
        .contains(query.toLowerCase()));
    return ListView(padding: const EdgeInsets.all(20), children: [
      const Text('Patient directory',
          style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800)),
      const SizedBox(height: 10),
      TextField(
          onChanged: (text) => setState(() => query = text),
          decoration: const InputDecoration(
              hintText: 'Search name, ID, disease, medicine...',
              prefixIcon: Icon(Icons.search),
              border: OutlineInputBorder())),
      const SizedBox(height: 12),
      ...found.map((p) => PatientTile(patient: p))
    ]);
  }
}

class Patient {
  const Patient(
      this.name, this.id, this.age, this.time, this.reason, this.status);
  final String name, id, age, time, reason, status;
}

const patients = [
  Patient('Ananya Mehta', 'VS-10482', '54', '09:30 AM',
      'Hypertension follow-up', 'Waiting'),
  Patient('Rohan Kapoor', 'VS-10437', '62', '10:00 AM', 'Chest discomfort',
      'In Progress'),
  Patient(
      'Priya Nair', 'VS-10394', '41', '10:30 AM', 'Diabetes review', 'Waiting'),
  Patient('Arjun Malhotra', 'VS-10276', '68', '11:15 AM',
      'Post-operative review', 'Completed')
];

class PatientTile extends StatelessWidget {
  const PatientTile({super.key, required this.patient});
  final Patient patient;
  @override
  Widget build(BuildContext context) => Card(
      margin: const EdgeInsets.symmetric(vertical: 7),
      child: ListTile(
          onTap: () =>
              Navigator.push(context, route(PatientDetails(patient: patient))),
          leading: Hero(
              tag: patient.id,
              child: CircleAvatar(
                  backgroundColor: teal.withOpacity(.12),
                  child: Text(patient.name[0],
                      style: const TextStyle(
                          color: teal, fontWeight: FontWeight.bold)))),
          title: Text(patient.name,
              style: const TextStyle(fontWeight: FontWeight.w800)),
          subtitle: Text(
              '${patient.age} yrs • ${patient.id}\n${patient.time} · ${patient.reason}'),
          isThreeLine: true,
          trailing: Text(patient.status,
              style: const TextStyle(fontSize: 11, color: teal))));
}

class PatientDetails extends StatelessWidget {
  const PatientDetails({super.key, required this.patient});
  final Patient patient;
  @override
  Widget build(BuildContext context) => Scaffold(
      appBar: AppBar(title: const Text('Patient profile')),
      body: ListView(padding: const EdgeInsets.all(20), children: [
        Row(children: [
          Hero(
              tag: patient.id,
              child: CircleAvatar(
                  radius: 38,
                  backgroundColor: blue.withOpacity(.12),
                  child: Text(patient.name[0],
                      style: const TextStyle(fontSize: 30, color: blue)))),
          const SizedBox(width: 16),
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                Text(patient.name,
                    style: const TextStyle(
                        fontSize: 23, fontWeight: FontWeight.w800)),
                Text('${patient.age} years • Female • B+',
                    style: const TextStyle(color: Colors.blueGrey)),
                const Text('Verified insurance • VS-10482',
                    style: TextStyle(fontSize: 12, color: teal))
              ]))
        ]),
        const SizedBox(height: 18),
        FilledButton.icon(
            onPressed: () =>
                Navigator.push(context, route(const ConsultationPage())),
            icon: const Icon(Icons.play_arrow),
            label: const Text('Start consultation')),
        const SizedBox(height: 20),
        Section(
            'AI health summary',
            const Text(
                'Type 2 Diabetes is stable. Blood pressure has improved over the last three visits. Continue current medication and review in 14 days. Allergy alert: Penicillin.')),
        Section(
            'Latest vitals',
            const Wrap(spacing: 8, runSpacing: 8, children: [
              Chip(label: Text('BP 126/82')),
              Chip(label: Text('Heart rate 74')),
              Chip(label: Text('SpO₂ 98%')),
              Chip(label: Text('Glucose 142'))
            ])),
        Section(
            'Medical summary',
            const Info([
              'Known allergy • Penicillin',
              'Chronic disease • Type 2 Diabetes',
              'Current medicine • Metformin 500 mg',
              'Lifestyle • Non-smoker · Walks 4× weekly'
            ])),
        Section(
            'Previous consultations',
            const Info([
              '12 Jun 2026 • Diabetes review',
              '29 Apr 2026 • Hypertension management',
              '17 Jan 2026 • Annual cardiac screening'
            ])),
        Section(
            'Reports & imaging',
            const Info([
              'Blood panel • View / Download',
              'ECG • Normal sinus rhythm',
              'Chest X-ray • View image'
            ]))
      ]),
      floatingActionButton: FloatingActionButton.extended(
        backgroundColor: navy,
        onPressed: () => _openPatientAssistant(context, patient),
        icon: const Icon(Icons.auto_awesome),
        label: const Text('Patient AI'),
      ));
}

void _openPatientAssistant(BuildContext context, Patient patient) {
  showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    backgroundColor: Colors.transparent,
    builder: (context) => DraggableScrollableSheet(
      initialChildSize: .58,
      minChildSize: .32,
      maxChildSize: .92,
      builder: (context, controller) => Container(
        padding: const EdgeInsets.fromLTRB(20, 10, 20, 20),
        decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surface.withOpacity(.96),
            borderRadius:
                const BorderRadius.vertical(top: Radius.circular(28))),
        child: ListView(controller: controller, children: [
          Center(
              child: Container(
                  width: 42,
                  height: 4,
                  decoration: BoxDecoration(
                      color: Colors.blueGrey.shade300,
                      borderRadius: BorderRadius.circular(8)))),
          const SizedBox(height: 14),
          Row(children: [
            const CircleAvatar(
                backgroundColor: teal,
                child: Icon(Icons.auto_awesome, color: Colors.white)),
            const SizedBox(width: 10),
            Expanded(
                child: Text('${patient.name} record assistant',
                    style: const TextStyle(
                        fontWeight: FontWeight.w800, fontSize: 17))),
            IconButton(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.close))
          ]),
          const SizedBox(height: 12),
          Wrap(
              spacing: 7,
              runSpacing: 7,
              children: [
                'Patient Summary',
                'Allergies',
                'Previous Surgeries',
                'Recent Reports',
                'Current Prescription',
                'Insurance',
                'Emergency Contact',
                'Latest Consultation'
              ]
                  .map((q) => ActionChip(label: Text(q), onPressed: () {}))
                  .toList()),
          const SizedBox(height: 18),
          const _AssistantBubble(
              'I only use the patient record currently open in this profile. Ask me to navigate allergies, reports, medicines, or emergency information.',
              false),
          _AssistantBubble(
              '${patient.name} has a documented Penicillin allergy, Type 2 Diabetes, and current Metformin 500 mg medication. The latest ECG is normal sinus rhythm.',
              true),
          const SizedBox(height: 12),
          const TextField(
              decoration: InputDecoration(
                  hintText: 'Ask about this patient record…',
                  prefixIcon: Icon(Icons.attach_file),
                  suffixIcon: Icon(Icons.send),
                  border: OutlineInputBorder())),
        ]),
      ),
    ),
  );
}

class _AssistantBubble extends StatelessWidget {
  const _AssistantBubble(this.text, this.isAssistant);
  final String text;
  final bool isAssistant;
  @override
  Widget build(BuildContext context) => Align(
      alignment: isAssistant ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
          margin: const EdgeInsets.only(bottom: 8),
          padding: const EdgeInsets.all(13),
          constraints: const BoxConstraints(maxWidth: 340),
          decoration: BoxDecoration(
              color: isAssistant
                  ? blue
                  : Theme.of(context).colorScheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(16)),
          child: Text(text,
              style: TextStyle(color: isAssistant ? Colors.white : null))));
}

class Section extends StatelessWidget {
  const Section(this.title, this.child, {super.key});
  final String title;
  final Widget child;
  @override
  Widget build(BuildContext context) => Padding(
      padding: const EdgeInsets.only(bottom: 20),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title,
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        const SizedBox(height: 9),
        Card(child: Padding(padding: const EdgeInsets.all(14), child: child))
      ]));
}

class Info extends StatelessWidget {
  const Info(this.items, {super.key});
  final List<String> items;
  @override
  Widget build(BuildContext context) => Column(
      children: items
          .map((item) => ListTile(
              dense: true,
              leading: const Icon(Icons.check_circle_outline, color: teal),
              title: Text(item)))
          .toList());
}

class ConsultationPage extends StatefulWidget {
  const ConsultationPage({super.key});
  @override
  State<ConsultationPage> createState() => _ConsultationPageState();
}

class _ConsultationPageState extends State<ConsultationPage> {
  bool paused = false, stopped = false;
  @override
  Widget build(BuildContext context) => Scaffold(
      appBar: AppBar(title: const Text('Live consultation')),
      body: Padding(
          padding: const EdgeInsets.all(20),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('00:24',
                style: TextStyle(fontSize: 34, fontWeight: FontWeight.w800)),
            const Text('Live clinical transcription'),
            const SizedBox(height: 14),
            Container(
                height: 68,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                    color: teal.withOpacity(.1),
                    borderRadius: BorderRadius.circular(18)),
                child: const Text('⌁ ︿ ⌁ ︿ 〰 ︿ ⌁ ︿ 〰',
                    style: TextStyle(fontSize: 27, color: teal))),
            const SizedBox(height: 15),
            const Expanded(
                child: Column(children: [
              ListTile(
                  title: Text('Dr. Sharma'),
                  subtitle: Text(
                      'How have your readings been since our last visit?')),
              ListTile(
                  title: Text('Ananya Mehta'),
                  subtitle:
                      Text('The morning readings have been lower, around 130.'))
            ])),
            if (stopped)
              const Card(
                  child: Padding(
                      padding: EdgeInsets.all(12),
                      child: Text(
                          'Extracted summary\nDiagnosis: Type 2 Diabetes, improving\nMedicine: Metformin 500 mg twice daily\nFollow-up: 14 days'))),
            Row(children: [
              Expanded(
                  child: OutlinedButton(
                      onPressed: () => setState(() => paused = !paused),
                      child: Text(paused ? 'Resume' : 'Pause'))),
              const SizedBox(width: 10),
              Expanded(
                  child: FilledButton(
                      onPressed: () => setState(() => stopped = true),
                      child: const Text('Stop & generate')))
            ])
          ])));
}

class AiAssistant extends StatelessWidget {
  const AiAssistant({super.key});
  @override
  Widget build(BuildContext context) =>
      ListView(padding: const EdgeInsets.all(20), children: [
        const Text('Clinical AI Assistant',
            style: TextStyle(fontSize: 25, fontWeight: FontWeight.w800)),
        const SizedBox(height: 6),
        const Text('Grounded in the local demonstration record.'),
        const SizedBox(height: 14),
        Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              'Summarise patient history',
              'Any allergies?',
              'Show blood sugar trend',
              'Prepare discharge summary'
            ]
                .map((text) => ActionChip(label: Text(text), onPressed: () {}))
                .toList()),
        const SizedBox(height: 18),
        const Card(
            child: Padding(
                padding: EdgeInsets.all(16),
                child: Text(
                    'I can help you explore synchronized clinical records and prepare consultation summaries.'))),
        const SizedBox(height: 10),
        const Card(
            color: Color(0xffe9f2ff),
            child: Padding(
                padding: EdgeInsets.all(16),
                child: Text(
                    'Ananya Mehta has been taking Metformin 500 mg twice daily since March. Her last HbA1c was 7.1%, showing a positive trend.'))),
        const SizedBox(height: 18),
        const TextField(
            decoration: InputDecoration(
                hintText: 'Ask about a patient...',
                prefixIcon: Icon(Icons.attach_file),
                suffixIcon: Icon(Icons.send),
                border: OutlineInputBorder()))
      ]);
}

class ProfilePage extends StatelessWidget {
  const ProfilePage({super.key});
  @override
  Widget build(BuildContext context) =>
      ListView(padding: const EdgeInsets.all(20), children: [
        const Center(
            child: CircleAvatar(
                radius: 42,
                backgroundColor: blue,
                child: Icon(Icons.person, color: Colors.white, size: 42))),
        const SizedBox(height: 12),
        const Center(
            child: Text('Dr. Aarav Sharma',
                style: TextStyle(fontSize: 23, fontWeight: FontWeight.w800))),
        const Center(
            child: Text('Senior Cardiologist • Verity General Hospital')),
        const SizedBox(height: 24),
        const Info([
          'Medical license • MED-IND-88427',
          'Experience • 14 years',
          'Languages • English, Hindi, Marathi',
          'Working hours • Mon–Sat, 08:00–17:00'
        ]),
        const SizedBox(height: 16),
        const Card(
            child: Column(children: [
          ListTile(
              leading: Icon(Icons.notifications_outlined),
              title: Text('Notification preferences')),
          ListTile(
              leading: Icon(Icons.security_outlined),
              title: Text('Privacy & security')),
          ListTile(
              leading: Icon(Icons.info_outline),
              title: Text('About VerityScribe Doctor'))
        ]))
      ]);
}

Route route(Widget page) => MaterialPageRoute(builder: (_) => page);
