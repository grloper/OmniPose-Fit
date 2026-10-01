# Google Play release candidate checklist

## Candidate identity

Package: `com.grloepr.pushtrack` (preserve the existing ID).
Candidate version: code 1 / name 1.0, target API 36, minimum API 24. Confirm prior Play version history before finalizing the code. Current debug APKs are not Play upload artifacts. `bundleRelease` can produce an unsigned AAB; signing credentials have not been established.

No ads or billing are integrated. Do not choose a permanent free listing or promise income as part of technical setup. The owner must choose pricing; an app once offered free cannot become paid under the same package. [Google pricing policy](https://support.google.com/googleplay/android-developer/answer/6334373).

## Required evidence

- Run unit, lint, instrumentation and packaged-app checks at the exact candidate commit. Camera binding must remain stable across pose-driven recomposition and its executor must be released on camera switch/exit.
- Qualify real human pose behavior on an approved test device: front/back cameras, rotation/mirroring, occlusion, tempo, background/resume, permission denial/revocation, low-end latency, large font and TalkBack. Synthetic landmarks and an empty emulated-camera result do not prove exercise recognition accuracy.
- Verify all 64-bit native dependencies with ELF LOAD/RELRO checks, APK zip alignment and a 16 KB device run. Read [current Android guidance](https://developer.android.com/guide/practices/page-sizes); do not infer compliance from target SDK alone.
- Verify bundled preview provenance. Generated previews are illustrations and must not be listed as live tracking results or proven technique demonstrations.
- Publish the reviewed [privacy draft](PRIVACY_POLICY_DRAFT.md) with an owner-approved contact and public URL, provide it in app, and reconcile ML Kit performance/utilization metrics with Data safety. The merged manifest includes INTERNET even though the app's source manifest does not.
- Complete the health-app declaration, audience, content rating, app access and Data safety fields for the exact binary. Do not claim injury prevention, clinical accuracy or expert-certified exercise assessment.

## Store and account gates

As of October 1, 2026 new phone apps and updates require target API 36. [Target API policy](https://support.google.com/googleplay/android-developer/answer/11926878).

Personal developer accounts created after November 13, 2023 require at least 12 continuously opted-in closed testers for 14 days before applying for production access. Account type/date and previous production access are not yet verified. [Testing policy](https://support.google.com/googleplay/android-developer/answer/14151465).

The owner must provide existing Console access through an approved sign-in flow and review app identity/version/disclosures before final submission. No account creation, fee payment, signing-key creation, legal acceptance, pricing commitment or production submission has been performed.

## Draft listing

Title: OmniPose Fit

Short description: Explore calisthenics skills and estimate repetitions with camera motion tracking.

Description: Explore a calisthenics progression map, see illustrated movement previews, and start an on-device camera session. The app estimates selected joint angles, repetitions and timed holds. It discards incomplete attempts when tracking is interrupted or scoring joints are missing. Some skill variants use generic tracking and are labeled accordingly. Motion estimates are experimental and do not certify technique or replace professional assessment. No ads or in-app purchases are integrated in this candidate.

Screenshots must come from the tested installed app and include truthful capability labels. Final listing copy and monetization remain pending review.
