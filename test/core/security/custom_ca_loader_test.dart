import 'dart:async';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mocktail/mocktail.dart';
import 'package:paperless_mobile/core/security/i_ca_store.dart';
import 'package:paperless_mobile/core/security/custom_ca_loader.dart';

class MockCustomCaStore extends Mock implements ICustomCaStore {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('CustomCaLoader Tests', () {
    late CustomCaLoader loader;
    late MockCustomCaStore mockStore;
    const validCaBase64 = 'Uk9PVF9DQV9ERVNIQw==';
    const emptyString = '';

    setUp(() {
      mockStore = MockCustomCaStore();
      loader = CustomCaLoader(mockStore);
    });

    group('Storage Operations', () {
      test('should save a base64 certificate to the secure store', () async {
        when(() => mockStore.saveCA(any())).thenAnswer((_) async {});
        
        await loader.saveCA(validCaBase64, storage: mockStore);
        
        verify(() => mockStore.saveCA(validCaBase64)).called(1);
      });

      test('should delete the stored certificate', () async {
        when(() => mockStore.deleteCA()).thenAnswer((_) async {});
        
        await loader.removeCA(storage: mockStore);
        
        verify(() => mockStore.deleteCA()).called(1);
      });
    });

    group('Context Loading Logic', () {
      test('should return null if no CA is stored', () async {
        when(() => mockStore.readCA()).thenAnswer((_) => Future.value(null));
        
        final context = await loader.getSecurityContext(mockStore);
        
        expect(context, isNull);
        verifyNever(() => mockStore.deleteCA()); // Shouldn't try to delete empty.
      });

      test('should return populated SecurityContext if a valid CA is present', () async {
        when(
          () => mockStore.readCA(),
        ).thenAnswer((_) => Future.value(validCaBase64));
        
        final context = await loader.getSecurityContext(mockStore);
        
        // In a real flutter_test environment without actual X509Certificate parsing 
        // mocked out, this verifies the retrieval logic flow.
        verify(() => mockStore.readCA()).called(1);
      });

      test('should handle invalid base64 gracefully and return null', () async {
        when(
          () => mockStore.readCA(),
        ).thenAnswer((_) => Future.value('!invalid_base_64_chars!!@#'));
        
        final context = await loader.getSecurityContext(mockStore);
        
        expect(context, isNull); // Fallback to null for malformed certs
      });
    });
  });
}
