import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/services/api_service.dart';
import '../providers/clinic_provider.dart';
import '../theme/app_theme.dart';

class AiScreen extends ConsumerStatefulWidget {
  const AiScreen({super.key});
  @override
  ConsumerState<AiScreen> createState() => _AiScreenState();
}

class _AiScreenState extends ConsumerState<AiScreen> {
  final _input = TextEditingController();
  final _scroll = ScrollController();

  @override
  void dispose() {
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _scrollToLatest() => WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scroll.hasClients) {
          _scroll.animateTo(_scroll.position.maxScrollExtent,
              duration: const Duration(milliseconds: 350), curve: Curves.easeOutCubic);
        }
      });

  void _send() {
    final text = _input.text.trim();
    if (text.isEmpty || ref.read(clinicProvider).chatLoading) return;
    _input.clear();
    ref.read(clinicProvider.notifier).sendMessage(text);
    _scrollToLatest();
  }

  Future<void> _pickReport(String reportType) async {
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
    _scrollToLatest();
    if (!mounted) return;
    final error = ref.read(clinicProvider).chatError;
    if (error != null) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(error is ApiException ? error.message : 'The report could not be uploaded. Please try again.'),
      ));
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
      backgroundColor: Colors.transparent,
      builder: (sheetContext) {
        final scheme = Theme.of(sheetContext).colorScheme;
        return Container(
        margin: const EdgeInsets.all(12),
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 20),
        decoration: BoxDecoration(color: scheme.surface, borderRadius: BorderRadius.circular(28), boxShadow: const [BoxShadow(color: Color(0x330B1E49), blurRadius: 30)]),
        child: SafeArea(top: false, child: Column(mainAxisSize: MainAxisSize.min, children: [
          Container(width: 40, height: 4, margin: const EdgeInsets.only(bottom: 12), decoration: BoxDecoration(color: const Color(0xFFD8E1F3), borderRadius: BorderRadius.circular(4))),
          const ListTile(
            leading: _RoundIcon(icon: Icons.lock_outline_rounded, color: AppTheme.blue),
            title: Text('Add a private health document', style: TextStyle(fontWeight: FontWeight.w800)),
            subtitle: Text('PDF or image • up to 10 MB'),
          ),
          const SizedBox(height: 6),
          Wrap(spacing: 8, runSpacing: 8, children: options.map((option) => ActionChip(
            avatar: Icon(option.$3, color: AppTheme.blue, size: 18),
            label: Text(option.$1),
            side: const BorderSide(color: Color(0xFFDCE6FA)),
            backgroundColor: scheme.surfaceContainerHighest,
            onPressed: () => _pickReport(option.$2),
          )).toList()),
        ])),
      );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final clinic = ref.watch(clinicProvider);
    final dark = Theme.of(context).brightness == Brightness.dark;
    return DecoratedBox(
      decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: dark ? const [Color(0xFF101827), Color(0xFF131F33), Color(0xFF101827)] : const [Color(0xFFF0F5FF), AppTheme.canvas, Color(0xFFF9FBFF)])),
      child: SafeArea(top: false, child: Center(child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 1100),
        child: Column(children: [
          const _ChatHeader(),
          if (clinic.chatError != null) _ErrorBanner(onDismiss: () => ref.read(clinicProvider.notifier).clearChatError()),
          Expanded(child: clinic.messages.isEmpty ? const _EmptyChat() : ListView.builder(
            controller: _scroll,
            padding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
            itemCount: clinic.messages.length + (clinic.chatLoading ? 1 : 0),
            itemBuilder: (context, index) => index == clinic.messages.length
                ? const _TypingIndicator() : _MessageBubble(message: clinic.messages[index]),
          )),
          _Composer(controller: _input, sending: clinic.chatLoading, onAttach: _upload, onSend: _send),
        ]),
      ))),
    );
  }
}

class _ChatHeader extends ConsumerWidget {
  const _ChatHeader();
  @override
  Widget build(BuildContext context, WidgetRef ref) => Container(
    width: double.infinity,
    padding: const EdgeInsets.fromLTRB(24, 22, 18, 20),
    decoration: const BoxDecoration(gradient: LinearGradient(colors: [Color(0xFF1D4ED8), Color(0xFF2563EB), Color(0xFF2EC6E9)]), borderRadius: BorderRadius.vertical(bottom: Radius.circular(30))),
    child: Row(children: [
      Container(width: 48, height: 48, decoration: BoxDecoration(color: Colors.white.withValues(alpha: .17), borderRadius: BorderRadius.circular(16)), child: const Icon(Icons.graphic_eq_rounded, color: Colors.white, size: 29)),
      const SizedBox(width: 14),
      const Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('Verity AI', style: TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800)),
        SizedBox(height: 2), Text('Private, evidence-led health guidance', style: TextStyle(color: Color(0xDFFFFFFF), fontSize: 13)),
      ])),
      IconButton(tooltip: 'Clear conversation', color: Colors.white, onPressed: () => ref.read(clinicProvider.notifier).clearChat(), icon: const Icon(Icons.delete_outline_rounded)),
    ]),
  );
}

class _ErrorBanner extends StatelessWidget {
  final VoidCallback onDismiss;
  const _ErrorBanner({required this.onDismiss});
  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.fromLTRB(20, 16, 20, 0), padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(color: Theme.of(context).brightness == Brightness.dark ? const Color(0xFF3A2819) : const Color(0xFFFFF7ED), borderRadius: BorderRadius.circular(18), border: Border.all(color: const Color(0xFFFED7AA))),
    child: Row(children: [const Icon(Icons.info_outline_rounded, color: Color(0xFFEA7B00)), const SizedBox(width: 10), const Expanded(child: Text('Verity could not complete that request. Please try again.', style: TextStyle(fontWeight: FontWeight.w600))), TextButton(onPressed: onDismiss, child: const Text('Dismiss'))]),
  );
}

class _EmptyChat extends StatelessWidget {
  const _EmptyChat();
  @override
  Widget build(BuildContext context) => Center(child: SingleChildScrollView(
    padding: const EdgeInsets.all(26),
    child: Column(mainAxisSize: MainAxisSize.min, children: [
      Container(width: 78, height: 78, decoration: const BoxDecoration(shape: BoxShape.circle, gradient: LinearGradient(colors: [AppTheme.blue, AppTheme.cyan])), child: const Icon(Icons.auto_awesome_rounded, color: Colors.white, size: 36)),
      const SizedBox(height: 18),
      Text('A safer way to ask about care', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.w800), textAlign: TextAlign.center),
      const SizedBox(height: 10), const Text('General questions get a general answer. Questions about your record only use verified details from your own account.', textAlign: TextAlign.center, style: TextStyle(color: AppTheme.muted, height: 1.5)),
      const SizedBox(height: 24), Wrap(spacing: 10, runSpacing: 10, alignment: WrapAlignment.center, children: const [_Suggestion('What is a balanced diet?'), _Suggestion('What is my recorded height?'), _Suggestion('How do I upload a report?')]),
    ]),
  ));
}

class _Suggestion extends StatelessWidget {
  final String text;
  const _Suggestion(this.text);
  @override
  Widget build(BuildContext context) => Container(padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10), decoration: BoxDecoration(color: Theme.of(context).colorScheme.surface, borderRadius: BorderRadius.circular(16), border: Border.all(color: const Color(0xFFDDE7FA))), child: Text(text, style: const TextStyle(color: AppTheme.blue, fontWeight: FontWeight.w700)));
}

class _TypingIndicator extends StatelessWidget {
  const _TypingIndicator();
  @override
  Widget build(BuildContext context) => const Align(alignment: Alignment.centerLeft, child: Padding(padding: EdgeInsets.only(top: 8, bottom: 16), child: Row(mainAxisSize: MainAxisSize.min, children: [_RoundIcon(icon: Icons.auto_awesome_rounded, color: AppTheme.cyan), SizedBox(width: 10), _Dots(), SizedBox(width: 9), Text('Verity is checking…', style: TextStyle(color: AppTheme.muted, fontWeight: FontWeight.w700))])));
}

class _Dots extends StatefulWidget {
  const _Dots();
  @override
  State<_Dots> createState() => _DotsState();
}

class _DotsState extends State<_Dots> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(vsync: this, duration: const Duration(milliseconds: 900))..repeat();
  @override
  void dispose() { _controller.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) => AnimatedBuilder(animation: _controller, builder: (_, __) => Row(children: List.generate(3, (index) {
    final active = ((_controller.value * 3).floor() % 3) == index;
    return AnimatedContainer(duration: const Duration(milliseconds: 160), margin: const EdgeInsets.symmetric(horizontal: 2), width: active ? 8 : 6, height: active ? 8 : 6, decoration: const BoxDecoration(color: AppTheme.blue, shape: BoxShape.circle));
  })));
}

class _Composer extends StatelessWidget {
  final TextEditingController controller;
  final bool sending;
  final VoidCallback onAttach;
  final VoidCallback onSend;
  const _Composer({required this.controller, required this.sending, required this.onAttach, required this.onSend});
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.fromLTRB(18, 10, 18, 16), decoration: BoxDecoration(color: Theme.of(context).colorScheme.surface.withValues(alpha: .94), border: const Border(top: BorderSide(color: Color(0xFFE5ECF8)))),
    child: Row(children: [
      IconButton.filledTonal(onPressed: sending ? null : onAttach, icon: const Icon(Icons.add_rounded), tooltip: 'Upload a report'), const SizedBox(width: 10),
      Expanded(child: TextField(controller: controller, minLines: 1, maxLines: 4, textInputAction: TextInputAction.send, onSubmitted: (_) => onSend(), decoration: const InputDecoration(hintText: 'Ask Verity anything…', contentPadding: EdgeInsets.symmetric(horizontal: 18, vertical: 14)))),
      const SizedBox(width: 10), IconButton.filled(onPressed: sending ? null : onSend, icon: sending ? const SizedBox(width: 19, height: 19, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white)) : const Icon(Icons.arrow_upward_rounded), tooltip: 'Send message'),
    ]),
  );
}

class _MessageBubble extends StatelessWidget {
  final ChatMessage message;
  const _MessageBubble({required this.message});
  @override
  Widget build(BuildContext context) {
    final isUser = message.isUser;
    final scheme = Theme.of(context).colorScheme;
    return Align(alignment: isUser ? Alignment.centerRight : Alignment.centerLeft, child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 650),
      child: Row(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.end, children: [
        if (!isUser) const Padding(padding: EdgeInsets.only(right: 9, bottom: 19), child: _RoundIcon(icon: Icons.auto_awesome_rounded, color: AppTheme.cyan)),
        Flexible(child: Container(margin: const EdgeInsets.only(bottom: 16), padding: const EdgeInsets.fromLTRB(16, 13, 16, 10), decoration: BoxDecoration(
          gradient: isUser ? const LinearGradient(colors: [Color(0xFF2257E6), Color(0xFF2E72E8)]) : null, color: isUser ? null : scheme.surfaceContainerHighest,
          borderRadius: BorderRadius.only(topLeft: const Radius.circular(20), topRight: const Radius.circular(20), bottomLeft: Radius.circular(isUser ? 20 : 5), bottomRight: Radius.circular(isUser ? 5 : 20)),
          boxShadow: const [BoxShadow(color: Color(0x120B1E49), blurRadius: 16, offset: Offset(0, 6))],
        ), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          if (message.attachment != null) Padding(padding: const EdgeInsets.only(bottom: 6), child: Row(mainAxisSize: MainAxisSize.min, children: [Icon(Icons.attach_file_rounded, size: 16, color: isUser ? Colors.white : AppTheme.blue), const SizedBox(width: 3), Text(message.attachment!, style: TextStyle(fontWeight: FontWeight.w800, color: isUser ? Colors.white : AppTheme.blue))])),
          Text(message.text, style: TextStyle(color: isUser ? Colors.white : scheme.onSurface, height: 1.5, fontSize: 15)), const SizedBox(height: 7), Text('${message.sentAt.hour.toString().padLeft(2, '0')}:${message.sentAt.minute.toString().padLeft(2, '0')}', style: TextStyle(fontSize: 10, color: isUser ? Colors.white70 : AppTheme.muted)),
        ]))),
      ]),
    ));
  }
}

class _RoundIcon extends StatelessWidget {
  final IconData icon;
  final Color color;
  const _RoundIcon({required this.icon, required this.color});
  @override
  Widget build(BuildContext context) => Container(width: 34, height: 34, decoration: BoxDecoration(color: color.withValues(alpha: .12), shape: BoxShape.circle), child: Icon(icon, color: color, size: 19));
}
