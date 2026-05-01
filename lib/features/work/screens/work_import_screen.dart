import 'package:flutter/material.dart';

import '../../../core/services/media_save_service.dart';
import '../controllers/work_import_controller.dart';
import '../models/work_import_draft.dart';

class WorkImportScreen extends StatelessWidget {
  final WorkImportDraft draft;
  final Future<void> Function(WorkImportDraft draft)? onSave;
  final WorkImportController? controller;
  final void Function(List<MediaSaveResult> results)? onSaved;

  const WorkImportScreen({
    super.key,
    required this.draft,
    this.onSave,
    this.controller,
    this.onSaved,
  });

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('선택한 미디어 ${draft.items.length}개')),
      body: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: draft.items.length,
        separatorBuilder: (_, _) => const SizedBox(height: 12),
        itemBuilder: (context, index) {
          final item = draft.items[index];
          return ListTile(
            contentPadding: const EdgeInsets.symmetric(
              horizontal: 12,
              vertical: 8,
            ),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
              side: BorderSide(color: Theme.of(context).dividerColor),
            ),
            leading: Icon(
              item.mediaType == 'video'
                  ? Icons.videocam_outlined
                  : Icons.photo_outlined,
            ),
            title: Text(item.filePath),
            subtitle: Text('${item.mediaType} · ${item.fileSizeKb}KB'),
          );
        },
      ),
      bottomNavigationBar: SafeArea(
        minimum: const EdgeInsets.fromLTRB(16, 8, 16, 16),
        child: FilledButton(
          onPressed: () async {
            if (onSave != null) {
              await onSave!.call(draft);
              return;
            }

            if (controller != null) {
              final results = await controller!.saveDraft(draft);
              onSaved?.call(results);
            }
          },
          child: const Text('저장'),
        ),
      ),
    );
  }
}
