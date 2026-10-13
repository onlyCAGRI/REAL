/// Build-time values. CI rewrites this file from repository secrets; the committed copy stays empty.
/// appCheckDebugToken: Firebase App Check debug token for test builds (simulator / devices without App Attest).
enum BuildSecrets {
    static let appCheckDebugToken = ""
}
