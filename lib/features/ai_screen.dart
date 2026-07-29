import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
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
  void _upload() => showModalBottomSheet<void>(context: context, builder: (sheetContext) => SafeArea(child: ListView(shrinkWrap:true, children: ['PDF','Prescription','Blood Report','X-Ray','MRI','Lab Report','ECG','Medicine Photo'].map((type) => ListTile(leading: const Icon(Icons.attach_file_rounded), title: Text('Upload $type'), onTap: () { Navigator.pop(sheetContext); _send(attachment: type); })).toList())));
  @override
  Widget build(BuildContext context) {
    final messages = ref.watch(clinicProvider).messages;
    return Column(children: [
      Padding(padding: const EdgeInsets.fromLTRB(20,18,20,12), child: Row(children: [const AppLogo(), const SizedBox(width:11), Text('Verity AI', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight:FontWeight.w800)), const Spacer(), IconButton(onPressed: () => ref.read(clinicProvider.notifier).clearChat(), icon: const Icon(Icons.delete_outline_rounded))])),
      Padding(padding: const EdgeInsets.symmetric(horizontal:20), child: SoftCard(color: Theme.of(context).colorScheme.primaryContainer, child: const Text('Clinical context: mild hypertension • no known allergies • 2 active medicines', style: TextStyle(fontSize:12,fontWeight:FontWeight.w700)))),
      Expanded(child: messages.isEmpty ? const Center(child: Text('Start a conversation with Verity.', style: TextStyle(color:AppTheme.muted))) : ListView.builder(controller: _scroll, padding: const EdgeInsets.all(20), itemCount: messages.length + (_typing ? 1 : 0), itemBuilder: (context,index) { if(index==messages.length) return const Padding(padding: EdgeInsets.all(8), child: Text('Verity is thinking…', style: TextStyle(color:AppTheme.muted))); final message=messages[index]; return _MessageBubble(message:message); })),
      Padding(padding: const EdgeInsets.fromLTRB(12,6,12,14), child: Row(children: [IconButton(onPressed: _upload, icon: const Icon(Icons.add_circle_outline_rounded)), Expanded(child: TextField(controller:_input, onSubmitted: (_) => _send(), decoration: const InputDecoration(hintText:'Ask Verity anything...'))), IconButton(onPressed: () => _send(attachment:'Voice note'), icon: const Icon(Icons.mic_rounded,color:AppTheme.blue)), IconButton(onPressed: _send, icon: const Icon(Icons.send_rounded,color:AppTheme.blue))])),
    ]);
  }
}

class _MessageBubble extends StatelessWidget {
  final ChatMessage message;
  const _MessageBubble({required this.message});
  @override
  Widget build(BuildContext context) => Align(alignment: message.isUser ? Alignment.centerRight : Alignment.centerLeft, child: Container(margin: const EdgeInsets.only(bottom:12), padding: const EdgeInsets.all(13), constraints: const BoxConstraints(maxWidth:310), decoration: BoxDecoration(color: message.isUser ? AppTheme.blue : Theme.of(context).colorScheme.surfaceContainerHighest, borderRadius: BorderRadius.circular(18)), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [if(message.attachment != null) Text('📎 ${message.attachment}', style: TextStyle(fontWeight:FontWeight.w800,color:message.isUser?Colors.white:AppTheme.blue)), Text(message.text, style: TextStyle(color:message.isUser?Colors.white:Theme.of(context).colorScheme.onSurface,height:1.4)), const SizedBox(height:4), Text('${message.sentAt.hour.toString().padLeft(2,'0')}:${message.sentAt.minute.toString().padLeft(2,'0')}', style: TextStyle(fontSize:9,color:message.isUser?Colors.white70:AppTheme.muted))])));
}
