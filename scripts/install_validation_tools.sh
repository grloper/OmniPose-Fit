#!/usr/bin/env bash
set -euo pipefail
: "${RUNNER_TEMP:?isolated runner temporary directory required}"
destination="$RUNNER_TEMP/android-validation-tools"
mkdir -p "$destination"
curl --fail --location --retry 2 --max-time 120 --silent --show-error \
  https://github.com/rhysd/actionlint/releases/download/v1.7.12/actionlint_1.7.12_linux_amd64.tar.gz \
  --output "$destination/actionlint.tar.gz"
printf '%s  %s\n' 8aca8db96f1b94770f1b0d72b6dddcb1ebb8123cb3712530b08cc387b349a3d8 "$destination/actionlint.tar.gz" | sha256sum --check -
curl --fail --location --retry 2 --max-time 120 --silent --show-error \
  https://github.com/koalaman/shellcheck/releases/download/v0.11.0/shellcheck-v0.11.0.linux.x86_64.tar.gz \
  --output "$destination/shellcheck.tar.gz"
printf '%s  %s\n' b7af85e41cc99489dcc21d66c6d5f3685138f06d34651e6d34b42ec6d54fe6f6 "$destination/shellcheck.tar.gz" | sha256sum --check -
tar -xzf "$destination/actionlint.tar.gz" -C "$destination" actionlint
tar -xzf "$destination/shellcheck.tar.gz" -C "$destination" shellcheck-v0.11.0/shellcheck
cp "$destination/shellcheck-v0.11.0/shellcheck" "$destination/shellcheck"
chmod +x "$destination/actionlint" "$destination/shellcheck"
printf '%s\n' "$destination" >> "${GITHUB_PATH:?GitHub Actions path file required}"
"$destination/actionlint" -version
"$destination/shellcheck" --version
