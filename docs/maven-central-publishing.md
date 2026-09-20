# Publishing to Maven Central

This guide covers the one-time setup and release process for publishing sqlsage4j to Maven Central.

## Prerequisites

- A [Sonatype Central](https://central.sonatype.com/) account
- GPG installed (`brew install gnupg` on macOS)
- Push access to the GitHub repository

## One-Time Setup

### 1. Create a Sonatype OSSRH Account

1. Go to https://central.sonatype.com/ and sign up
2. Create a new namespace claim for `io.github.imetaxas`
3. Sonatype will verify ownership — for `io.github.*` namespaces, this is automatic if the GitHub user `imetaxas` exists and you're logged in with that account
4. Once approved, you can publish artifacts under `io.github.imetaxas.*`

### 2. Generate a GPG Key

GPG signing is required by Maven Central for all published artifacts.

```bash
# Generate a new key (choose RSA 4096, use your GitHub email)
gpg --full-generate-key

# List keys to find your key ID (8-character short ID)
gpg --list-keys --keyid-format short

# Upload your public key to a key server (required for verification)
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# Export the private key (you'll store this as a GitHub secret)
gpg --armor --export-secret-keys YOUR_KEY_ID
```

### 3. Configure GitHub Repository Secrets

In your repository (`github.com/imetaxas/sqlsage4j`), go to **Settings → Secrets and variables → Actions** and add:

| Secret Name | Value |
|---|---|
| `OSSRH_USERNAME` | Your Sonatype Central username (or generated token name) |
| `OSSRH_TOKEN` | Your Sonatype Central password (or generated token value) |
| `GPG_PRIVATE_KEY` | Full output of `gpg --armor --export-secret-keys YOUR_KEY_ID` |
| `GPG_PASSPHRASE` | The passphrase you set when generating the GPG key |

> **Tip**: Use Sonatype's "Generate User Token" feature (in your account settings) instead of your actual password. This creates a dedicated token pair for CI.

## Publishing a Release

### Tag and Push

The release workflow (`.github/workflows/release.yml`) triggers automatically when you push a version tag:

```bash
# Ensure main is up to date
git checkout main
git pull

# Tag the release
git tag v0.1.0
git push origin v0.1.0
```

### What the Workflow Does

1. Checks out the code at the tagged commit
2. Sets the project version to match the tag (e.g., `v0.1.0` → version `0.1.0`)
3. Builds the project and verifies it compiles
4. Signs all artifacts (JAR, sources JAR, javadoc JAR, POM) with GPG
5. Deploys to Maven Central via the Sonatype Central Publishing plugin
6. Creates a GitHub Release with auto-generated release notes

### After Publishing

- Artifacts typically appear on Maven Central within **10–30 minutes**
- You can check availability at: https://central.sonatype.com/artifact/io.github.imetaxas/sqlsage4j
- Once published, users can add the dependency:

```xml
<dependency>
  <groupId>io.github.imetaxas</groupId>
  <artifactId>sqlsage4j</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Publishing Manually (Without CI)

If you need to publish from your local machine:

```bash
# Set the release version
mvn versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false

# Deploy with the release profile (requires GPG and Sonatype credentials in ~/.m2/settings.xml)
mvn deploy -P release -DskipTests
```

Your `~/.m2/settings.xml` must contain:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_SONATYPE_USERNAME</username>
      <password>YOUR_SONATYPE_TOKEN</password>
    </server>
  </servers>
</settings>
```

## Troubleshooting

| Problem | Solution |
|---|---|
| "Unauthorized" during deploy | Verify `OSSRH_USERNAME` and `OSSRH_TOKEN` secrets are correct |
| "No public key" GPG error | Ensure you uploaded your key: `gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID` |
| Namespace not verified | Complete the verification in the Sonatype Central portal |
| Artifacts not appearing on Central | Wait 30 minutes; check the Sonatype portal for staging/validation errors |
| GPG passphrase prompt hangs in CI | The `--pinentry-mode loopback` arg in `pom.xml` should handle this — verify the `GPG_PASSPHRASE` secret is set |
