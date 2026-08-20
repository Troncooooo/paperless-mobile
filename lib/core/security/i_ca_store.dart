abstract class ICustomCaStore {
  Future<String?> readCA();
  Future<void> saveCA(String b64Cert);
  Future<void> deleteCA();
}
