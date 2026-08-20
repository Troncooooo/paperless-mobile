// TASK-02: UI for in-app certificate installation
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:file_picker/file_picker.dart';
import '../security/custom_ca_loader.dart';

class CustomCaPickerTile extends StatefulWidget {
  const CustomCaPickerTile({Key? key}) : super(key: key);

  @override
  State<CustomCaPickerTile> createState() => _CustomCaPickerTileState();
}

class _CustomCaPickerTileState extends State<CustomCaPickerTile> {
  String _status = "No certificate loaded";
  bool _isLoading = false;

  Future<void> _pickCerFile() async {
    setState(() { _isLoading = true; });
    try {
      FilePickerResult? result = await FilePicker.platform.pickFiles(
        type: FileType.custom, allowedExtensions: ['pem', 'crt', 'cer']
      );
      if (result != null && result.files.single.bytes != null) {
        // Convert picked file bytes to base64 for secure storage
        final b64 = base64.encode(result.files.single.bytes!);
        await CustomCaLoader.saveCustomCa(b64);
        setState(() => _status = "✅ Certificate loaded successfully.");
      }
    } catch (e) {
      setState(() => _status = "❌ Failed to load certificate.");
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListTile(
      title: const Text("Upload Custom CA Certificate"),
      subtitle: Text(_status),
      trailing: ElevatedButton(
        onPressed: _isLoading ? null : _pickCerFile,
        child: const Text('Select File'),
      ),
      onTap: _clearStorageAndReload,
    );
  }

  Future<void> _clearStorageAndReload() async {
    await CustomCaLoader.removeCustomCa(); // Remove secure data
    setState(() => _status = "Certificate removed. Reconnecting...");
    // Trigger session reset to fallback to system trust store:
    // await locator<SessionManager>().resetSettings(broadcast: true);
  }
}
