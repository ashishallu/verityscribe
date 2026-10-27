import 'dart:ui' show ImageFilter;

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/services/api_service.dart';
import '../providers/clinic_provider.dart';
import '../providers/app_providers.dart';
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
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        ref.read(clinicProvider.notifier).initializeChatSession();
      }
    });
  }

  @override
  void dispose() {
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  void _scrollToLatest() => WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scroll.hasClients) {
          _scroll.animateTo(_scroll.position.maxScrollExtent,
              duration: const Duration(milliseconds: 350),
              curve: Curves.easeOutCubic);
        }
      });

  void _send() {
    _sendText(_input.text);
  }

  void _sendText(String value) {
    final text = value.trim();
    final clinic = ref.read(clinicProvider);
    if (text.isEmpty || clinic.chatLoading || clinic.chatSessionLoading) return;
    _input.clear();
    ref.read(clinicProvider.notifier).sendMessage(text);
    _scrollToLatest();
  }

  Future<void> _pickReport(String reportType) async {
    if (ref.read(clinicProvider).chatLoading) return;
    final selected = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: const ['pdf', 'png', 'jpg', 'jpeg', 'webp'],
      withData: true,
    );
    if (!mounted || selected == null) return;
    if (selected.files.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
            content:
                Text('No file was selected. Please choose a PDF or image.')),
      );
      return;
    }
    final file = selected.files.first;
    final fileBytes = file.bytes;
    if (fileBytes == null || fileBytes.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
            content: Text(
                'The selected file could not be read. Please choose it again.')),
      );
      return;
    }
    Navigator.of(context).pop();
    await ref.read(clinicProvider.notifier).uploadReport(
          reportType: reportType,
          filename: file.name,
          bytes: fileBytes,
        );
    if (ref.read(clinicProvider).chatError == null) {
      ref.invalidate(patientReportsProvider);
    }
    _scrollToLatest();
    if (!mounted) return;
    final error = ref.read(clinicProvider).chatError;
    if (error != null) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(error is ApiException
            ? error.message
            : 'The report could not be uploaded. Please try again.'),
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
          decoration: BoxDecoration(
              color: scheme.surface,
              borderRadius: BorderRadius.circular(28),
              boxShadow: const [
                BoxShadow(color: Color(0x330B1E49), blurRadius: 30)
              ]),
          child: SafeArea(
              top: false,
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                Container(
                    width: 40,
                    height: 4,
                    margin: const EdgeInsets.only(bottom: 12),
                    decoration: BoxDecoration(
                        color: const Color(0xFFD8E1F3),
                        borderRadius: BorderRadius.circular(4))),
                const ListTile(
                  leading: _RoundIcon(
                      icon: Icons.lock_outline_rounded, color: AppTheme.blue),
                  title: Text('Add a private health document',
                      style: TextStyle(fontWeight: FontWeight.w800)),
                  subtitle: Text('PDF or image • up to 10 MB'),
                ),
                const SizedBox(height: 6),
                Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: options
                        .map((option) => ActionChip(
                              avatar: Icon(option.$3,
                                  color: AppTheme.blue, size: 18),
                              label: Text(option.$1),
                              side: const BorderSide(color: Color(0xFFDCE6FA)),
                              backgroundColor: scheme.surfaceContainerHighest,
                              onPressed: () => _pickReport(option.$2),
                            ))
                        .toList()),
              ])),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    // Re-scroll after the API response is inserted, not only on button tap.
    ref.listen(
      clinicProvider.select((state) => state.messages.length),
      (previous, next) {
        if (previous != next) _scrollToLatest();
      },
    );
    final clinic = ref.watch(clinicProvider);
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Stack(
      children: [
        Positioned.fill(
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: dark
                    ? const [
                        Color(0xFF281437),
                        Color(0xFF1A1028),
                        Color(0xFF090F20),
                        Color(0xFF071A36),
                      ]
                    : const [
                        Color(0xFFF0F5FF),
                        AppTheme.canvas,
                        Color(0xFFF9FBFF),
                      ],
                stops: dark ? const [0, .36, .73, 1] : null,
              ),
            ),
          ),
        ),
        if (dark) ...[
          const Positioned(
            top: 70,
            right: -130,
            child: _AmbientGlow(color: Color(0x664B247A), size: 340),
          ),
          const Positioned(
            bottom: -170,
            left: -80,
            child: _AmbientGlow(color: Color(0x55406ED8), size: 420),
          ),
        ],
        SafeArea(
            top: false,
            child: Center(
                child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 1100),
              child: Column(children: [
                const _ChatHeader(),
                if (clinic.sessionDocuments.isNotEmpty)
                  _AttachedDocuments(
                    documents: clinic.sessionDocuments,
                    onRemove: (id) => ref
                        .read(clinicProvider.notifier)
                        .removeSessionDocument(id),
                  ),
                if (clinic.chatError != null)
                  _ErrorBanner(
                    message: clinic.chatError is ApiException
                        ? (clinic.chatError as ApiException).message
                        : clinic.chatError.toString(),
                    onDismiss: () =>
                        ref.read(clinicProvider.notifier).clearChatError(),
                  ),
                Expanded(
                    child: clinic.messages.isEmpty
                        ? const _EmptyChat()
                        : ListView.builder(
                            controller: _scroll,
                            padding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
                            itemCount: clinic.messages.length +
                                (clinic.chatLoading ? 1 : 0),
                            itemBuilder: (context, index) =>
                                index == clinic.messages.length
                                    ? const _TypingIndicator()
                                    : _MessageBubble(
                                        message: clinic.messages[index]),
                          )),
                if (clinic.messages.isEmpty)
                  _QuickPromptRail(onSelect: _sendText),
                _Composer(
                    controller: _input,
                    sending: clinic.chatLoading || clinic.chatSessionLoading,
                    onAttach: _upload,
                    onSend: _send),
              ]),
            ))),
      ],
    );
  }
}

class _AttachedDocuments extends StatelessWidget {
  final List<ChatSessionDocument> documents;
  final ValueChanged<String> onRemove;
  const _AttachedDocuments({required this.documents, required this.onRemove});

  @override
  Widget build(BuildContext context) => Container(
        width: double.infinity,
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 4),
        child: Wrap(
          spacing: 8,
          runSpacing: 2,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            const Padding(
              padding: EdgeInsets.only(right: 2),
              child: Text('Available in this chat:',
                  style: TextStyle(fontSize: 12, color: AppTheme.muted)),
            ),
            ...documents.map((document) => InputChip(
                  avatar: const Icon(Icons.lock_outline_rounded, size: 15),
                  label: Text(document.name, overflow: TextOverflow.ellipsis),
                  onDeleted: () => onRemove(document.id),
                  materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                )),
          ],
        ),
      );
}

class _AmbientGlow extends StatelessWidget {
  const _AmbientGlow({required this.color, required this.size});

  final Color color;
  final double size;

  @override
  Widget build(BuildContext context) => IgnorePointer(
        child: Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            gradient:
                RadialGradient(colors: [color, color.withValues(alpha: 0)]),
          ),
        ),
      );
}

class _GlassPanel extends StatelessWidget {
  const _GlassPanel({
    required this.child,
    required this.borderRadius,
    this.enabled = true,
  });

  final Widget child;
  final BorderRadius borderRadius;
  final bool enabled;

  @override
  Widget build(BuildContext context) => ClipRRect(
        borderRadius: borderRadius,
        child: enabled
            ? BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
                child: child,
              )
            : child,
      );
}

class _GlassIconButton extends StatelessWidget {
  const _GlassIconButton({
    required this.icon,
    required this.foreground,
    required this.tooltip,
    required this.onPressed,
    this.circular = false,
  });

  final IconData icon;
  final Color foreground;
  final String tooltip;
  final VoidCallback? onPressed;
  final bool circular;

  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final radius = circular ? 23.0 : 18.0;
    return _GlassPanel(
      borderRadius: BorderRadius.circular(radius),
      enabled: dark,
      child: Container(
        width: 46,
        height: 46,
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: dark ? .075 : .64),
          borderRadius: BorderRadius.circular(radius),
          border: Border.all(
              color: Colors.white.withValues(alpha: dark ? .14 : .8)),
        ),
        child: IconButton(
          tooltip: tooltip,
          onPressed: onPressed,
          color: foreground,
          icon: Icon(icon, size: 21),
        ),
      ),
    );
  }
}

class _ChatHeader extends ConsumerWidget {
  const _ChatHeader();
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final scheme = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(18, 10, 18, 12),
      child: Row(children: [
        _GlassPanel(
          borderRadius: BorderRadius.circular(18),
          enabled: dark,
          child: Container(
            width: 46,
            height: 46,
            decoration: BoxDecoration(
              color: dark ? const Color(0x664F3F72) : scheme.primaryContainer,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(
                  color: Colors.white.withValues(alpha: dark ? .14 : .8)),
            ),
            child: Icon(Icons.graphic_eq_rounded,
                color: dark ? const Color(0xFFD8C6FF) : AppTheme.blue,
                size: 24),
          ),
        ),
        const SizedBox(width: 12),
        Expanded(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('Verity AI',
                style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      color: scheme.onSurface,
                      fontWeight: FontWeight.w800,
                      letterSpacing: -.35,
                    )),
            const SizedBox(height: 2),
            Text('Private, evidence-led health guidance',
                style: Theme.of(context)
                    .textTheme
                    .labelMedium
                    ?.copyWith(color: scheme.onSurfaceVariant)),
          ]),
        ),
        _GlassIconButton(
          icon: Icons.delete_outline_rounded,
          foreground: scheme.onSurface,
          tooltip: 'Clear conversation',
          onPressed: () => ref.read(clinicProvider.notifier).clearChat(),
        ),
      ]),
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  final VoidCallback onDismiss;
  final String message;
  const _ErrorBanner({required this.onDismiss, required this.message});
  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 8, 16, 0),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: dark ? const Color(0xCC3A293F) : const Color(0xFFFFF7ED),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
            color: dark ? const Color(0x66FFB181) : const Color(0xFFFED7AA)),
      ),
      child: Row(children: [
        const Icon(Icons.info_outline_rounded, color: Color(0xFFFFA85C)),
        const SizedBox(width: 10),
        Expanded(
            child: Text(message,
                style: TextStyle(
                    fontWeight: FontWeight.w600,
                    color: Theme.of(context).colorScheme.onSurface))),
        TextButton(onPressed: onDismiss, child: const Text('Dismiss'))
      ]),
    );
  }
}

class _EmptyChat extends StatelessWidget {
  const _EmptyChat();
  @override
  Widget build(BuildContext context) => Center(
          child: SingleChildScrollView(
        padding: const EdgeInsets.all(26),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Container(
              width: 78,
              height: 78,
              decoration: const BoxDecoration(
                  shape: BoxShape.circle,
                  gradient:
                      LinearGradient(colors: [AppTheme.blue, AppTheme.cyan])),
              child: const Icon(Icons.auto_awesome_rounded,
                  color: Colors.white, size: 36)),
          const SizedBox(height: 18),
          Text('A safer way to ask about care',
              style: Theme.of(context)
                  .textTheme
                  .headlineSmall
                  ?.copyWith(fontWeight: FontWeight.w800),
              textAlign: TextAlign.center),
          const SizedBox(height: 10),
          const Text(
              'General questions get a general answer. Questions about your record only use verified details from your own account.',
              textAlign: TextAlign.center,
              style: TextStyle(color: AppTheme.muted, height: 1.5)),
        ]),
      ));
}

class _QuickPromptRail extends StatelessWidget {
  const _QuickPromptRail({required this.onSelect});

  final ValueChanged<String> onSelect;

  @override
  Widget build(BuildContext context) {
    const prompts = [
      'What is a balanced diet?',
      'What are my recorded allergies?',
      'How do I upload a report?',
    ];
    return SizedBox(
      height: 48,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 3),
        scrollDirection: Axis.horizontal,
        itemCount: prompts.length,
        separatorBuilder: (_, __) => const SizedBox(width: 9),
        itemBuilder: (context, index) => _Suggestion(
          prompts[index],
          onPressed: onSelect,
        ),
      ),
    );
  }
}

class _Suggestion extends StatelessWidget {
  final String text;
  final ValueChanged<String> onPressed;
  const _Suggestion(this.text, {required this.onPressed});
  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final scheme = Theme.of(context).colorScheme;
    return _GlassPanel(
      borderRadius: BorderRadius.circular(24),
      enabled: dark,
      child: ActionChip(
        label: Text(text),
        side:
            BorderSide(color: Colors.white.withValues(alpha: dark ? .14 : .8)),
        backgroundColor: dark
            ? const Color(0x77423957)
            : scheme.surface.withValues(alpha: .92),
        labelStyle: TextStyle(
            color: dark ? const Color(0xFFD5C7FF) : AppTheme.blue,
            fontWeight: FontWeight.w700),
        onPressed: () => onPressed(text),
      ),
    );
  }
}

class _TypingIndicator extends StatelessWidget {
  const _TypingIndicator();
  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final accent = dark ? const Color(0xFFB89AFF) : AppTheme.cyan;
    return Align(
      alignment: Alignment.centerLeft,
      child: Padding(
        padding: const EdgeInsets.only(top: 8, bottom: 16),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          _RoundIcon(icon: Icons.auto_awesome_rounded, color: accent),
          const SizedBox(width: 10),
          const _Dots(),
          const SizedBox(width: 9),
          Text('Verity is checking…',
              style: TextStyle(
                  color: Theme.of(context).colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.w700))
        ]),
      ),
    );
  }
}

class _Dots extends StatefulWidget {
  const _Dots();
  @override
  State<_Dots> createState() => _DotsState();
}

class _DotsState extends State<_Dots> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
      vsync: this, duration: const Duration(milliseconds: 900))
    ..repeat();
  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
      animation: _controller,
      builder: (_, __) => Row(
              children: List.generate(3, (index) {
            final active = ((_controller.value * 3).floor() % 3) == index;
            return AnimatedContainer(
                duration: const Duration(milliseconds: 160),
                margin: const EdgeInsets.symmetric(horizontal: 2),
                width: active ? 8 : 6,
                height: active ? 8 : 6,
                decoration: BoxDecoration(
                    color: Theme.of(context).brightness == Brightness.dark
                        ? const Color(0xFF9A7BFF)
                        : AppTheme.blue,
                    shape: BoxShape.circle));
          })));
}

class _Composer extends StatelessWidget {
  final TextEditingController controller;
  final bool sending;
  final VoidCallback onAttach;
  final VoidCallback onSend;
  const _Composer(
      {required this.controller,
      required this.sending,
      required this.onAttach,
      required this.onSend});
  @override
  Widget build(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final scheme = Theme.of(context).colorScheme;
    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
        child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          _GlassIconButton(
            icon: Icons.add_rounded,
            foreground: dark ? const Color(0xFFE2D9FF) : scheme.primary,
            tooltip: 'Upload a report',
            onPressed: sending ? null : onAttach,
            circular: true,
          ),
          const SizedBox(width: 9),
          Expanded(
            child: _GlassPanel(
              borderRadius: BorderRadius.circular(26),
              child: Container(
                constraints: const BoxConstraints(minHeight: 48),
                decoration: BoxDecoration(
                  color: dark
                      ? const Color(0xB52E3045)
                      : scheme.surface.withValues(alpha: .94),
                  borderRadius: BorderRadius.circular(26),
                  border: Border.all(
                      color: Colors.white.withValues(alpha: dark ? .17 : .85)),
                  boxShadow: [
                    BoxShadow(
                        color: scheme.shadow.withValues(alpha: dark ? .2 : .06),
                        blurRadius: 20,
                        offset: const Offset(0, 6)),
                  ],
                ),
                child: TextField(
                  controller: controller,
                  minLines: 1,
                  maxLines: 4,
                  textInputAction: TextInputAction.send,
                  onSubmitted: (_) => onSend(),
                  cursorColor: dark ? const Color(0xFFB89AFF) : scheme.primary,
                  style: Theme.of(context)
                      .textTheme
                      .bodyLarge
                      ?.copyWith(color: scheme.onSurface),
                  decoration: InputDecoration(
                    hintText: 'Ask Verity anything…',
                    hintStyle: TextStyle(color: scheme.onSurfaceVariant),
                    contentPadding: const EdgeInsets.symmetric(
                        horizontal: 18, vertical: 13),
                    border: InputBorder.none,
                  ),
                ),
              ),
            ),
          ),
          const SizedBox(width: 9),
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              gradient: dark
                  ? const LinearGradient(
                      colors: [Color(0xFF9A65FF), Color(0xFF6848E8)],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    )
                  : const LinearGradient(
                      colors: [AppTheme.blue, Color(0xFF2E72E8)],
                    ),
              boxShadow: [
                BoxShadow(
                  color: (dark ? const Color(0xFF8158F6) : AppTheme.blue)
                      .withValues(alpha: .34),
                  blurRadius: 17,
                  offset: const Offset(0, 6),
                ),
              ],
            ),
            child: IconButton(
              onPressed: sending ? null : onSend,
              color: Colors.white,
              tooltip: 'Send message',
              icon: sending
                  ? const SizedBox(
                      width: 19,
                      height: 19,
                      child: CircularProgressIndicator(
                          strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.send_rounded, size: 20),
            ),
          ),
        ]),
      ),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  final ChatMessage message;
  const _MessageBubble({required this.message});
  @override
  Widget build(BuildContext context) {
    final isUser = message.isUser;
    final dark = Theme.of(context).brightness == Brightness.dark;
    final scheme = Theme.of(context).colorScheme;
    final reportSummary = message.reportSummary?.trim();
    final hasReportMessage = !isUser &&
        (message.reportTitle != null ||
            message.reportSummary != null ||
            message.extractedText != null ||
            message.text.toLowerCase().contains('report was saved') ||
            message.text
                .toLowerCase()
                .contains('summary is temporarily unavailable'));
    final hasUsableSummary = reportSummary != null &&
        reportSummary.isNotEmpty &&
        !reportSummary.toLowerCase().contains('could not be prepared') &&
        !reportSummary.toLowerCase().contains('temporarily unavailable');
    return Align(
        alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 650),
          child: Row(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.end,
              children: [
                if (!isUser)
                  Padding(
                      padding: EdgeInsets.only(right: 9, bottom: 19),
                      child: _RoundIcon(
                          icon: Icons.auto_awesome_rounded,
                          color:
                              dark ? const Color(0xFFB89AFF) : AppTheme.cyan)),
                Flexible(
                    child: _GlassPanel(
                        borderRadius: BorderRadius.only(
                            topLeft: const Radius.circular(20),
                            topRight: const Radius.circular(20),
                            bottomLeft: Radius.circular(isUser ? 20 : 5),
                            bottomRight: Radius.circular(isUser ? 5 : 20)),
                        enabled: dark,
                        child: Container(
                            margin: const EdgeInsets.only(bottom: 16),
                            padding: const EdgeInsets.fromLTRB(16, 13, 16, 10),
                            decoration: BoxDecoration(
                              gradient: isUser
                                  ? LinearGradient(
                                      colors: dark
                                          ? const [
                                              Color(0xFF7151C8),
                                              Color(0xFF5139A5)
                                            ]
                                          : const [
                                              Color(0xFF2257E6),
                                              Color(0xFF2E72E8)
                                            ])
                                  : null,
                              color: isUser
                                  ? null
                                  : dark
                                      ? const Color(0x99504461)
                                      : scheme.surface,
                              border: Border.all(
                                  color: Colors.white.withValues(
                                      alpha: dark
                                          ? .14
                                          : isUser
                                              ? 0
                                              : .62)),
                              borderRadius: BorderRadius.only(
                                  topLeft: const Radius.circular(20),
                                  topRight: const Radius.circular(20),
                                  bottomLeft: Radius.circular(isUser ? 20 : 5),
                                  bottomRight:
                                      Radius.circular(isUser ? 5 : 20)),
                              boxShadow: [
                                BoxShadow(
                                    color: dark
                                        ? const Color(0x33000000)
                                        : const Color(0x120B1E49),
                                    blurRadius: 16,
                                    offset: Offset(0, 6))
                              ],
                            ),
                            child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  if (message.attachment != null)
                                    Padding(
                                        padding:
                                            const EdgeInsets.only(bottom: 6),
                                        child: Row(
                                            mainAxisSize: MainAxisSize.min,
                                            children: [
                                              Icon(Icons.attach_file_rounded,
                                                  size: 16,
                                                  color: isUser
                                                      ? Colors.white
                                                      : AppTheme.blue),
                                              const SizedBox(width: 3),
                                              Text(message.attachment!,
                                                  style: TextStyle(
                                                      fontWeight:
                                                          FontWeight.w800,
                                                      color: isUser
                                                          ? Colors.white
                                                          : AppTheme.blue))
                                            ])),
                                  if (!hasReportMessage)
                                    if (isUser)
                                      Text(message.text,
                                          style: const TextStyle(
                                              color: Colors.white,
                                              height: 1.5,
                                              fontSize: 15))
                                    else
                                      _ChatResponseBody(
                                        content: message.text,
                                        style: TextStyle(
                                            color: scheme.onSurface,
                                            height: 1.5,
                                            fontSize: 15),
                                      ),
                                  if (hasReportMessage) ...[
                                    Padding(
                                      padding: const EdgeInsets.only(bottom: 4),
                                      child: _ReportAnalyzedNotice(
                                        summary: hasUsableSummary
                                            ? reportSummary
                                            : null,
                                      ),
                                    ),
                                  ],
                                  if (message.action == 'book_appointment')
                                    Padding(
                                      padding: const EdgeInsets.only(top: 12),
                                      child: FilledButton.icon(
                                        onPressed: () =>
                                            context.go('/book-appointment'),
                                        icon: const Icon(
                                            Icons.calendar_month_rounded),
                                        label: const Text('Book appointment'),
                                      ),
                                    ),
                                  const SizedBox(height: 7),
                                  Text(
                                      '${message.sentAt.hour.toString().padLeft(2, '0')}:${message.sentAt.minute.toString().padLeft(2, '0')}',
                                      style: TextStyle(
                                          fontSize: 10,
                                          color: isUser
                                              ? Colors.white70
                                              : AppTheme.muted)),
                                ])))),
              ]),
        ));
  }
}

class _ChatResponseBody extends StatelessWidget {
  final String content;
  final TextStyle style;

  const _ChatResponseBody({required this.content, required this.style});

  @override
  Widget build(BuildContext context) {
    final normalized = content
        .replaceAll('\r\n', '\n')
        .replaceAll(
          RegExp(r'\[uploaded document,\s*page\s*(\d+)\]',
              caseSensitive: false),
          r'(Source · p. $1)',
        )
        .replaceAll(RegExp(r'\n{3,}'), '\n\n')
        .trim();
    final paragraphs = normalized
        .split(RegExp(r'\n\s*\n'))
        .where((paragraph) => paragraph.trim().isNotEmpty)
        .toList();
    final scheme = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: paragraphs.asMap().entries.map((entry) {
        final lines = entry.value
            .split('\n')
            .map((line) => line.trimRight())
            .where((line) => line.trim().isNotEmpty)
            .toList();
        return Padding(
          padding: EdgeInsets.only(
              bottom: entry.key == paragraphs.length - 1 ? 0 : 10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: lines.asMap().entries.map((lineEntry) {
              final line = lineEntry.value;
              final bullet = RegExp(r'^\s*[-*•]\s+(.+)$').firstMatch(line);
              final numbered =
                  RegExp(r'^\s*(\d+)[.)]\s+(.+)$').firstMatch(line);
              final heading =
                  RegExp(r'^\s{0,3}#{1,6}\s+(.+)$').firstMatch(line);
              final text = bullet?.group(1) ??
                  numbered?.group(2) ??
                  heading?.group(1) ??
                  line;
              final lineStyle = heading == null
                  ? style
                  : style.copyWith(fontWeight: FontWeight.w700);
              final rendered = _inlineResponseText(text, lineStyle, scheme);

              if (bullet != null || numbered != null) {
                return Padding(
                  padding:
                      EdgeInsets.only(top: lineEntry.key == 0 ? 0 : 4, left: 2),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      SizedBox(
                        width: 20,
                        child: numbered != null
                            ? Text('${numbered.group(1)}.',
                                style: style.copyWith(
                                  color: scheme.primary,
                                  fontWeight: FontWeight.w700,
                                ))
                            : Padding(
                                padding: const EdgeInsets.only(top: 8),
                                child: Icon(Icons.circle,
                                    size: 6, color: scheme.primary),
                              ),
                      ),
                      Expanded(child: rendered),
                    ],
                  ),
                );
              }

              return Padding(
                padding: EdgeInsets.only(top: lineEntry.key == 0 ? 0 : 5),
                child: rendered,
              );
            }).toList(),
          ),
        );
      }).toList(),
    );
  }

  Widget _inlineResponseText(
      String text, TextStyle baseStyle, ColorScheme scheme) {
    final tokens =
        RegExp(r'\*\*[^*]+?\*\*|\*[^*]+?\*|`[^`]+`|\(Source · p\. \d+\)');
    final spans = <TextSpan>[];
    var cursor = 0;
    for (final match in tokens.allMatches(text)) {
      if (match.start > cursor) {
        spans.add(TextSpan(text: text.substring(cursor, match.start)));
      }
      final token = match.group(0)!;
      if (token.startsWith('**')) {
        spans.add(TextSpan(
          text: token.substring(2, token.length - 2),
          style: baseStyle.copyWith(fontWeight: FontWeight.w700),
        ));
      } else if (token.startsWith('*')) {
        spans.add(TextSpan(
          text: token.substring(1, token.length - 1),
          style: baseStyle.copyWith(fontStyle: FontStyle.italic),
        ));
      } else if (token.startsWith('`')) {
        spans.add(TextSpan(
          text: token.substring(1, token.length - 1),
          style: baseStyle.copyWith(
            fontFamily: 'monospace',
            backgroundColor: scheme.surfaceContainerHighest,
          ),
        ));
      } else {
        spans.add(TextSpan(
          text: token.substring(1, token.length - 1),
          style: baseStyle.copyWith(
            color: scheme.onSurfaceVariant,
            fontSize: (baseStyle.fontSize ?? 15) - 2,
            fontWeight: FontWeight.w500,
          ),
        ));
      }
      cursor = match.end;
    }
    if (cursor < text.length) {
      spans.add(TextSpan(text: text.substring(cursor)));
    }
    return SelectableText.rich(
      TextSpan(style: baseStyle, children: spans),
      style: baseStyle,
    );
  }
}

class _ReportAnalyzedNotice extends StatelessWidget {
  final String? summary;

  const _ReportAnalyzedNotice({this.summary});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('Report analyzed.',
          style: TextStyle(color: scheme.onSurface, height: 1.5, fontSize: 15)),
      if (summary != null && summary!.isNotEmpty)
        Padding(
          padding: const EdgeInsets.only(top: 4, bottom: 4),
          child: Text(summary!,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                  color: scheme.onSurface, height: 1.5, fontSize: 15)),
        ),
      Text('Would you like to know anything? Type below.',
          style: TextStyle(color: scheme.onSurface, height: 1.5, fontSize: 15)),
    ]);
  }
}

class _RoundIcon extends StatelessWidget {
  final IconData icon;
  final Color color;
  const _RoundIcon({required this.icon, required this.color});
  @override
  Widget build(BuildContext context) => Container(
      width: 34,
      height: 34,
      decoration: BoxDecoration(
          color: color.withValues(alpha: .12), shape: BoxShape.circle),
      child: Icon(icon, color: color, size: 19));
}
