import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:file_picker/file_picker.dart';
import '../providers/clinic_provider.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

class AiScreen extends ConsumerStatefulWidget {
  const AiScreen({super.key});
  @override
  ConsumerState<AiScreen> createState() => _AiScreenState();
}

class _AiScreenState extends ConsumerState<AiScreen> {
  final _input = TextEditingController();
  final _scroll = ScrollController();
  bool _typing = false;
  @override
  void dispose() { _input.dispose(); _scroll.dispose(); super.dispose(); }
  void _send({String? attachment}) {
    final text = attachment == null ? _input.text.trim() : 'Uploaded $attachment';
    if (text.isEmpty) return;
    _input.clear();
    ref.read(clinicProvider.notifier).sendMessage(text, attachment: attachment);
    setState(() => _typing = true);
    Future.delayed(const Duration(milliseconds:800), () { if (mounted) setState(() => _typing = false); });
    WidgetsBinding.instance.addPostFrameCallback((_) { if (_scroll.hasClients) _scroll.animateTo(_scroll.position.maxScrollExtent, duration: const Duration(milliseconds:300), curve: Curves.easeOut); });
  }
  Future<void> _pickReport(String label, String reportType) async {
    final selected = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['pdf', 'png', 'jpg', 'jpeg', 'webp'],
      withData: true,
    );
    if (!mounted || selected == null || selected.files.single.bytes == null) return;
    Navigator.of(context).pop();
    await ref.read(clinicProvider.notifier).uploadReport(
      reportType: reportType,
      filename: selected.files.single.name,
      bytes: selected.files.single.bytes!,
    );
    if (mounted && ref.read(clinicProvider).chatError != null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('The report could not be uploaded. Please try a PDF or image under 10 MB.')));
    }
  }

  void _upload() {
    const options = <(String, String, IconData)>[
      ('PDF report', 'pdf', Icons.picture_as_pdf_rounded),
      ('Prescription', 'prescription', Icons.receipt_long_rounded),
      ('Blood report', 'blood_report', Icons.bloodtype_outlined),
      ('X-ray', 'x_ray', Icons.monitor_heart_outlined),
      ('MRI', 'mri', Icons.biotech_outlined),
      ('Lab report', 'lab_report', Icons.science_outlined),
      ('ECG', 'ecg', Icons.monitor_heart_rounded),
      ('Medicine photo', 'medicine_photo', Icons.medication_outlined),
    ];
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 0, 12, 18),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const ListTile(title: Text('Add a health document', style: TextStyle(fontWeight: FontWeight.w800)), subtitle: Text('Private to your VerityScribe account')),
          ...options.map((option) => ListTile(
            leading: Icon(option.$3, color: AppTheme.blue), title: Text('Upload ${option.$1}'),
            onTap: () => _pickReport(option.$1, option.$2),
          )),
        ]),
      )),
    );
  }
  @override
  Widget build(BuildContext context) {
    final clinic = ref.watch(clinicProvider);
    final messages = clinic.messages;
    return Column(children: [
      Container(width: double.infinity, padding: const EdgeInsets.fromLTRB(20,18,20,16), decoration: const BoxDecoration(gradient: LinearGradient(colors:[AppTheme.blue, Color(0xFF38BDF8)])), child: Row(children: [const AppLogo(), const SizedBox(width:11), Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children:[Text('Verity AI', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight:FontWeight.w800,color:Colors.white)), const Text('Private answers from your care record', style: TextStyle(color:Colors.white70))])), IconButton(color: Colors.white,onPressed: () => ref.read(clinicProvider.notifier).clearChat(), icon: const Icon(Icons.delete_outline_rounded))])),
      Expanded(child: Column(children: [if (clinic.chatError != null) Padding(padding: const EdgeInsets.fromLTRB(20, 12, 20, 0), child: SoftCard(child: Row(children: [const Icon(Icons.info_outline_rounded, color: Colors.orange), const SizedBox(width: 10), const Expanded(child: Text('Verity could not complete that request. Please try again.')), TextButton(onPressed: () => ref.read(clinicProvider.notifier).clearChat(), child: const Text('Dismiss'))]))), Expanded(child: messages.isEmpty ? const _EmptyChat() : ListView.builder(controller: _scroll, padding: const EdgeInsets.all(20), itemCount: messages.length + (_typing ? 1 : 0), itemBuilder: (context,index) { if(index==messages.length) return const Padding(padding: EdgeInsets.all(8), child: Text('Verity is thinking…', style: TextStyle(color:AppTheme.muted))); final message=messages[index]; return _MessageBubble(message:message); }))])),
      Padding(padding: const EdgeInsets.fromLTRB(12,6,12,14), child: Row(children: [IconButton(onPressed: _upload, icon: const Icon(Icons.add_circle_outline_rounded)), Expanded(child: TextField(controller:_input, onSubmitted: (_) => _send(), decoration: const InputDecoration(hintText:'Ask Verity anything...'))), IconButton(onPressed: () => _send(attachment:'Voice note'), icon: const Icon(Icons.mic_rounded,color:AppTheme.blue)), IconButton(onPressed: _send, icon: const Icon(Icons.send_rounded,color:AppTheme.blue))])),
    ]);
  }
}

class _EmptyChat extends StatelessWidget {
  const _EmptyChat();
  @override
  Widget build(BuildContext context) => Center(child: Padding(padding: const EdgeInsets.all(28), child: SoftCard(child: Column(mainAxisSize: MainAxisSize.min, children: [
    const Icon(Icons.auto_awesome_rounded, color: AppTheme.blue, size: 38), const SizedBox(height: 12),
    Text('Your private care companion', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w800)), const SizedBox(height: 8),
    const Text('Ask a general health question, or upload a readable report. Your personal record is used only when your question needs it.', textAlign: TextAlign.center, style: TextStyle(color: AppTheme.muted, height: 1.45)),
  ]))));
}

class _MessageBubble extends StatelessWidget {
  final ChatMessage message;
  const _MessageBubble({required this.message});
  @override
  Widget build(BuildContext context) => Align(alignment: message.isUser ? Alignment.centerRight : Alignment.centerLeft, child: Container(margin: const EdgeInsets.only(bottom:12), padding: const EdgeInsets.all(13), constraints: const BoxConstraints(maxWidth:310), decoration: BoxDecoration(color: message.isUser ? AppTheme.blue : Theme.of(context).colorScheme.surfaceContainerHighest, borderRadius: BorderRadius.circular(18)), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [if(message.attachment != null) Text('📎 ${message.attachment}', style: TextStyle(fontWeight:FontWeight.w800,color:message.isUser?Colors.white:AppTheme.blue)), Text(message.text, style: TextStyle(color:message.isUser?Colors.white:Theme.of(context).colorScheme.onSurface,height:1.4)), const SizedBox(height:4), Text('${message.sentAt.hour.toString().padLeft(2,'0')}:${message.sentAt.minute.toString().padLeft(2,'0')}', style: TextStyle(fontSize:9,color:message.isUser?Colors.white70:AppTheme.muted))])));
}
