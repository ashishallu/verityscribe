import 'package:flutter/material.dart';

/// Loads a feature library on first navigation instead of including its
/// transitive code in the initial Flutter web bundle.
class DeferredRouteScreen extends StatefulWidget {
  const DeferredRouteScreen({
    required this.loadLibrary,
    required this.builder,
    super.key,
  });

  final Future<void> Function() loadLibrary;
  final WidgetBuilder builder;

  @override
  State<DeferredRouteScreen> createState() => _DeferredRouteScreenState();
}

class _DeferredRouteScreenState extends State<DeferredRouteScreen> {
  late final Future<void> _loading = _loadFeature();
  bool _routeReadyRecorded = false;

  Future<void> _loadFeature() async {
    final timer = Stopwatch()..start();
    await widget.loadLibrary();
    debugPrint(
        'VerityScribe navigation stage=deferred_feature_loaded elapsed_ms=${timer.elapsedMilliseconds}');
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<void>(
        future: _loading,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return const Scaffold(
              body: Center(child: Text('This section could not be loaded.')),
            );
          }
          if (snapshot.connectionState != ConnectionState.done) {
            return const Scaffold(
              body: Center(
                child: SizedBox.square(
                  dimension: 26,
                  child: CircularProgressIndicator(strokeWidth: 2.5),
                ),
              ),
            );
          }
          if (!_routeReadyRecorded) {
            _routeReadyRecorded = true;
            WidgetsBinding.instance.addPostFrameCallback((_) {
              debugPrint(
                  'VerityScribe navigation stage=deferred_route_interactive');
            });
          }
          return widget.builder(context);
        },
      );
}
